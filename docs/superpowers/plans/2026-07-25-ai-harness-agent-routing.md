# 多 HarnessAgent 分层路由管理实施计划

## 目标

按内部租户、渠道绑定和智能体配置安全路由并复用多个独立 HarnessAgent，同时保留旧版 AiChatClient 调用兼容性。

## 架构

- 路由器负责按显式 profile、渠道绑定、租户内渠道默认和全局默认的顺序选择 profile。
- 注册表按 tenantId、agentId 和完整配置指纹管理实例，使用 LRU、租约和延迟关闭控制生命周期。
- 工厂负责模型、Toolkit、工作区和压缩配置。
- 会话键工厂负责无碰撞的五维状态隔离。
- module-ai 负责 tenantId 持久化映射、租户内默认查询和管理变更后的运行时失效。
- 飞书适配器负责校验外部 tenantKey，并生成结构化服务端绑定。

## 全局约束

- Java 17、Spring Boot 3.4.5、AgentScope Java 2.0.0-RC3、MySQL、MyBatis-Plus。
- 数据库不使用启动初始化器；`ai_managed_agent.tenant_id` 由用户手工执行 MySQL SQL 完成迁移。
- 空白 tenantId 默认取 userId，运行时只使用持久化/规范化后的 tenantId。
- 公共 Java API 使用中文 JavaDoc；单方法不超过 80 行，Service 有效代码不超过 500 行。
- 不提交本次代码改动，由用户决定后续提交方式。

## Task 1：路由模型、tenantId 与会话隔离

- [x] 为 `AiAgentProfile` 增加 tenantId、enabled 和旧构造兼容。
- [x] 为 `AiManagedAgent`、Entity、Request、Repository、Resolver 增加 tenantId 映射；空白值默认 userId 并 trim。
- [x] 实现 `AiAgentRouteRequest` 和四层 `AiHarnessAgentRouter`。
- [x] 增加 `resolveDefaultForChannel(channel, tenantId)`，在查询阶段限制内部租户。
- [x] 实现五维会话键，并使用长度前缀避免冒号和空值占位符碰撞。
- [x] 通过路由、tenantId 和会话碰撞测试。

## Task 2：HarnessAgent 工厂、技能与注册表

- [x] 实现 `AiHarnessAgentKey`，指纹覆盖 profile 和所有影响实例构造的全局配置。
- [x] 实现 `AiHarnessAgentFactory`，隔离 tenant/agent 工作区并装配上下文压缩。
- [x] 默认 `dashscope_chat + compatible-mode/v1` 使用 `OpenAIChatModel`；显式 `dashscope_native` 使用原生模型。
- [x] 实现 `AiSkillAgentTool`，仅把 profile `skillIds` 白名单中的管理技能注册到 AgentScope Toolkit。
- [x] 实现并发单例、失败不缓存、容量 128、访问顺序 LRU 的注册表。
- [x] 实现 `withAgent` 租约、失效/淘汰/关闭时延迟回收活动实例。
- [x] 通过工厂、模型类型、Toolkit、并发、指纹、失效、LRU 和生命周期测试。

## Task 3：服务调用链与自动配置

- [x] 为 `AiAgentService` 增加带 `AiAgentRouteRequest` 的 Harness 调用入口。
- [x] 使用路由结果创建 sessionId 和 RuntimeContext，并通过注册表租约调用 HarnessAgent。
- [x] 保留旧 reply/chat 重载的 AiChatClient、会话历史与上下文压缩兼容行为。
- [x] 自动配置 Factory、Registry、Router、SessionKeyFactory，并在应用关闭时回收 Registry。
- [x] 默认 profile 按请求 tenantId 生成，不跨租户复用。
- [x] 通过 starter 服务与自动配置回归测试。

## Task 4：飞书可信绑定与管理变更失效

- [x] `RuntimeFeishuConfigProvider` 按事件 tenantKey 核对活动配置绑定。
- [x] `FeishuAiChannelMessageHandler` 清理普通绑定属性并写入 `AiChannelAgentBinding` 结构化对象。
- [x] resolver/handler 双重校验外部 tenantKey 与 agentId 后，才采用智能体内部 tenantId。
- [x] 普通 `attributes.agentId` 不允许触发飞书跨租户映射。
- [x] 管理端更新、删除智能体后显式失效旧运行时实例。
- [x] API 技能创建、更新、配置更新和删除后失效全部引用智能体，避免旧 description/readOnly 权限元数据残留。
- [x] 快捷技能命令执行智能体 skillIds 白名单。
- [x] 通过 module-ai、飞书绑定、技能失效和渠道白名单测试。

## Task 5：审查与交付验证

- [x] 请求独立只读代码审查。
- [x] 修复审查发现的模型 URL 类型、技能 Toolkit、tenant trim、技能失效、会话碰撞、飞书信任边界和测试编译问题。
- [x] 运行 starter、module-ai、module-ai-autoconfig、module-feishu 相关完整测试。
- [x] 运行 `git diff --check`、代码行数和工作区边界检查。
- [x] 汇总实现、数据库前提、验证结果和未提交状态。