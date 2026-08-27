# 多 HarnessAgent 分层路由与注册表设计

## 目标

在 `ai-agent-spring-boot-starter` 中运行多个相互隔离的 `HarnessAgent`。每条渠道消息先按租户和绑定关系选定智能体，再由注册表复用或创建对应实例；模型、提示词、工具、工作区、会话状态和上下文压缩配置均以路由后的智能体为边界。

## 范围与数据前提

- `ai_managed_agent` 已增加独立 `tenant_id` 字段；历史记录已由用户手工执行 MySQL SQL，以 `user_id` 回填并建立非空与索引约束。
- 后端创建或更新智能体时，空白 `tenantId` 默认使用 `userId`；领域对象统一去除租户标识首尾空白。
- 不增加数据库初始化器，不在启动阶段执行 DDL；数据库结构继续由人工 MySQL 脚本维护。
- 首期接入已有通用渠道和飞书调用链，不新增用户选择子智能体页面或外部路由网关。
- 旧版 `reply/chat` 重载继续走 `AiChatClient`，保留原会话内存与上下文压缩兼容语义；只有携带 `AiAgentRouteRequest` 的新调用链进入 HarnessAgent。

## 分层路由

`AiAgentRouteRequest` 包含内部 `tenantId`、channel、userId、conversationId、可选 `explicitProfile` 和完整渠道消息。`AiHarnessAgentRouter` 按以下顺序解析：

1. 调用方显式传入的 `explicitProfile`；
2. `AiAgentProfileResolver.resolveForMessage` 返回的渠道绑定智能体；
3. `AiAgentProfileResolver.resolveDefaultForChannel(channel, tenantId)` 返回的租户内渠道默认智能体；
4. starter 按当前请求内部租户生成的全局默认 profile。

所有候选必须满足：ID 非空、`enabled=true`、`profile.tenantId` 非空且与路由请求内部租户完全一致。候选不合格时继续下一层，不允许降级到其他租户。

## 飞书外部租户映射

飞书事件携带的是飞书 `tenant_key`，而智能体内部 `tenantId` 当前取管理用户 `userId`，两者不是同一命名空间。映射流程如下：

1. `RuntimeFeishuConfigProvider` 按事件 `tenant_key` 核对当前活动飞书配置；只有活动配置的 `tenantKey` 一致时才返回绑定 `agentId`。
2. `FeishuAiChannelMessageHandler` 删除消息原始 attributes 中可能存在的绑定保留键，只把服务端校验后的绑定写成 `AiChannelAgentBinding` 结构化对象。
3. `AiManagedAgentProfileResolver` 仅接受该结构化对象，按绑定 ID 查询已启用智能体；普通字符串 `attributes.agentId` 不具备跨租户映射资格。
4. `AiChannelHandler` 再次核对结构化绑定的外部租户和候选 agentId，核验通过后才使用智能体持久化的内部 `tenantId` 构造路由请求。
5. 未绑定、租户不匹配、智能体不存在或停用时，只尝试与请求租户一致的渠道默认配置；飞书外部租户通常不会匹配内部用户租户，因此最终安全降级到请求租户作用域的全局默认智能体。

## 注册表与生命周期

`AiHarnessAgentRegistry` 使用以下不可变键管理实例：

```text
tenantId + agentId + profileFingerprint
```

指纹覆盖 profile 名称、模型、提示词、技能 ID，以及全局名称、模型类型、模型地址、采样参数、系统提示词、最大迭代次数、工作区配置和压缩配置。注册表规则：

- 同一 key 的并发请求只执行一次工厂创建；创建异常不缓存。
- 配置指纹变化后创建新实例。
- 管理端更新、删除智能体，或创建、更新、删除其绑定 API 技能时，通过 `AiManagedAgentRuntimeInvalidator` 显式失效该租户与智能体的全部版本。
- 容量默认 128，超过容量按访问顺序执行 LRU 淘汰。
- 业务调用统一使用 `withAgent` 租约；淘汰、显式失效或应用关闭会阻止新请求复用，活动请求结束后才真正关闭实例。
- 应用停止时注册表 `close()` 回收全部实例；单纯诊断用 `getOrCreate` 不提供租约保护。

## HarnessAgent 构建

`AiHarnessAgentFactory` 负责构造实例：

- `dashscope_chat` 默认配置使用 `OpenAIChatModel` 对接 `compatible-mode/v1`；只有显式 `modelType=dashscope_native` 才使用 `DashScopeChatModel`，此时 `baseUrl` 应是原生根地址。
- 模型对象、系统提示词、最大迭代次数和上下文压缩在实例创建时固定。
- 工作区按内部 tenantId 和 agentId 的可读片段加 SHA-256 短哈希隔离，避免路径冲突和非法字符。
- `AiSkillAgentTool` 把 `AiSkillRegistry` 中的 Spring Bean/API 技能适配为 AgentScope `Toolkit` 工具；每个 HarnessAgent 只注册 profile `skillIds` 白名单内的技能。
- 快捷“技能 xxx”渠道命令同样要求先解析到智能体，并校验该技能属于其 `skillIds`，不能绕过白名单。

## 会话隔离

会话维度固定为：

```text
tenantId, agentId, channel, conversationId, userId
```

每个字段以 `字符长度:内容` 编码后顺序拼接，空白字段按长度 0 的空内容编码。该格式避免字段中包含冒号，或真实值为 `_` 时产生碰撞。相同维度同时写入 `RuntimeContext` 的 sessionId、userId 和扩展 tenant/channel/conversation 信息。

## 错误处理与兼容性

- 未配置 API Key、路由无可用智能体、实例创建或调用失败时返回可诊断业务错误。
- 异常链最底层消息会执行 API Key 替换，避免密钥进入响应。
- 旧版 `reply(message)`、`chat(message, sessionId)` 和显式 profile 的旧重载保留 `AiChatClient` 行为，不强制切换 HarnessAgent。
- 注册表、会话和工作区不会因缓存未命中而跨租户回退。

## 验证范围

- 路由优先级、禁用/空 ID/跨租户候选拒绝和租户内默认查询。
- 飞书活动配置 tenantKey 校验、结构化绑定、普通 agentId 属性拒绝和内部 tenantId 映射。
- 同 key 并发单例、指纹变化、显式失效、失败不缓存、LRU、租约延迟关闭。
- OpenAI 兼容与原生 DashScope 模型选择、技能 Toolkit 白名单和工具调用。
- 会话键五维变化、冒号边界和空白/下划线碰撞。
- 旧 API、飞书消息处理、module-ai 管理服务和 MyBatis-Plus tenantId 映射回归。