# 项目改造清单 — 对照 DeepSeek Harness 架构

> 输入：`docs/plans/2026-08-22-deepseek-harness-architecture.md`（dsh 原理/架构）
> 对象：agent_runner（AgentScope HarnessAgent + agent-ai + agent-memory + agent-spring-boot-starter）
> 日期：2026-08-22 | 优先级：P0 关键 / P1 重要 / P2 可选

## 一、差距总览（dsh 理念 vs 项目现状）

| dsh 理念 | 项目现状 | 差距 |
|---|---|---|
| SessionEvent 追加日志（可回放/可审计） | `AiConversationMemory` 进程内内存态；`agent-memory` L0 只记**记忆相关**消息 | 🔴 会话消息未全量持久化事件化 |
| "模型可见即已记录"不变量 | L0 已近似（记忆写入留痕），但对话消息链路未覆盖 | 🔴 不变量仅部分成立 |
| 类型化事件域（会话/Agent/能力） | 无事件总线；observ trace 为持久表记录 | 🔴 无实时扩展点 |
| 能力 Seam（Def/Provider/Consumer） | FileStorage SPI 已有；skillRegistry 工具注册已有 | 🟡 部分具备，未统一"能力"抽象 |
| 工具流水线把关（pre/post-execute） | Toolkit 直接调用，无钩子 | 🟡 缺把关/策略挂点 |
| Compaction（剪枝+摘要+严格重试） | `prepareCompression/applyCompression` + trim 已有 | 🟡 有压缩，缺剪枝与重试规则 |
| 沙箱（ctx.sandbox 包装 argv） | 无沙箱，工具直接执行 | 🔴 缺进程/命令隔离 |
| Profile×组合包×patch 可替换 | DB 配置驱动装配（AiManagedAgent） | 🟡 无 patch 覆盖层，模型/工具不可运行时替换 |
| 会话 fork/恢复 | 无 | 🟡 缺分支/恢复能力 |
| 遥测 seam | observ onBegin/onStep/onEnd + trace/step 表 | ✅ 已具备，可强化事件化 |

## 二、改造清单

### P0 — 会话事件化与"模型可见即已记录"

| # | 改造 | 说明 | 落点 |
|---|---|---|---|
| 1 | **对话消息事件流** | 将 `AiConversationMemory.appendTurn` 的每次用户/助手消息追加为持久事件（`session/event` 式），而非仅内存 + L0 记忆子集 | agent-memory chat 包 + H2 `l0_raw_log`（session 维度全量） |
| 2 | **事件回放** | 新增 `replay(sessionId)`：从事件流重建 `AiConversationMemory` 会话（重启/多实例恢复） | AiConversationMemory + MemoryStorageFacade |
| 3 | **事件总线（轻量）** | 进程内 `ApplicationEventPublisher` 桥接：会话事件/Agent 步骤事件/工具调用事件三类域（对应 dsh 三域） | agent-spring-boot-starter 或 framework-autoconfig |

### P0 — 沙箱与工具把关

| # | 改造 | 说明 | 落点 |
|---|---|---|---|
| 4 | **工具执行钩子** | Toolkit 注册时包装：`pre-execute`（审批/白名单/预算）→ `execute` → `post-execute`（审计/遥测），对应 dsh `tools/*` waterfall | AiHarnessAgentFactory.managedToolkit |
| 5 | **沙箱抽象** | `ctx.sandbox` 等价物：命令行/脚本工具（如 Bash、代码运行时）经沙箱后端包装 argv（本地=直接，远端=远程执行）；先做本地直通实现 + SPI | agent-ai 或 starter 新增 sandbox 包 |

### P1 — 上下文压缩增强

| # | 改造 | 说明 | 落点 |
|---|---|---|---|
| 6 | **工具结果剪枝** | `applyCompression` 前增加 prune（丢弃低价值 tool 结果消息），对应 dsh compaction 第一步 | AiConversationMemory |
| 7 | **压缩后严格重试** | 压缩成功后若仍触发上下文溢出，开启新轮次重试（当前无重试逻辑） | chat service 层 |

### P1 — 能力化与可替换性

| # | 改造 | 说明 | 落点 |
|---|---|---|---|
| 8 | **能力注册抽象** | 将 skillRegistry/FileStorage/记忆工具统一为"能力提供方"注册（Service Definition/Provider/Consumer 三件套），向 HarnessAgent 注入 | agent-ai management 包 |
| 9 | **运行时替换** | `AiAgentProfileResolver` 增加"默认 profile 由配置提供方 patch 覆盖"（对应 dsh profile/patch），支持不改代码切换模型/工具集 | AiHarnessAgentRouter |

### P2 — 会话运维能力

| # | 改造 | 说明 | 落点 |
|---|---|---|---|
| 10 | **会话 fork/恢复** | `AiConversationMemory.fork(sessionId, boundary)` + 事件流复制子会话（dsh `ctx.sessions.fork`） | agent-memory chat 包 |
| 11 | **会话标题生成** | 唯一 `ctx.sessionTitle` 等价提供方（首次消息摘要），接入 observ/session 列表 | agent-memory |

### P2 — 遥测强化

| # | 改造 | 说明 | 落点 |
|---|---|---|---|
| 12 | **遥测事件化** | observ onStep 改为订阅事件总线（#3），解耦 trace 写入与业务代码 | agent-ai observ 包 |

## 三、实施状态（2026-08-22 更新：12 项全部完成 ✅）

| 项 | 状态 | 落点 |
|---|---|---|
| ① 对话消息事件流 | ✅ | AiConversationMemory.eventLog → L0（role=user/assistant） |
| ② 事件回放 | ✅ | AiConversationMemory.restore(sessionId) |
| ③ 工具执行钩子 | ✅ | ToolExecutionListener + AiSkillAgentTool 包装 |
| ④ 沙箱抽象 | ✅ | SandboxBackend + LocalSandboxBackend |
| ⑤ 事件总线（三域） | ✅ | framework-common ai/event + SpringAiEventPublisher |
| ⑥ 压缩剪枝 | ✅ | AiConversationMemory.pruneForCompression |
| ⑦ 能力注册抽象 | ✅ | AiCapabilityProvider + factory 消费 |
| ⑧ 运行时替换 | ✅ | AiProfilePatchProvider + Router applyPatches |
| ⑨ 会话 fork | ✅ | AiConversationMemory.fork |
| ⑩ 会话标题 | ✅ | AiConversationMemory.title |
| ⑪ 遥测事件化 | ✅ | ObservEventBridge（@EventListener → ObservTraceService） |
| ⑫ 遥测事件化解耦 | ✅ | 随 ⑪ 一并完成 |

### 补充（2026-08-22 追加）：动态插件机制 ✅

| 能力 | 状态 | 落点 |
|---|---|---|
| 运行时插件加载（安装/卸载/热恢复） | ✅ | AiPlugin SPI + DynamicPluginManager（URLClassLoader，data/plugins/*.jar，META-INF/ai-plugin.properties 清单） |
| 插件生命周期 | ✅ | onLoad(PluginContext) / onUnload()；@Bean(destroyMethod=close) 关闭时全量卸载 |
| 插件注册能力 | ✅ | PluginContext.registerCapability/Listener/Sandbox/ProfilePatch → factory 装配合并 |
| 插件管理 REST | ✅ | /api/ai/plugins（list/upload/load/unload） |

> 详见 docs/plans/2026-08-22-dynamic-plugin-mechanism.md

## 四、实施顺序建议

```
阶段 1（P0，一次迭代）：
  #1 对话消息事件流 + #2 事件回放 → 打通"模型可见即已记录"
  #4 工具执行钩子 → 审批/审计挂点
  #5 沙箱抽象（本地直通实现）

阶段 2（P1，一次迭代）：
  #3 轻量事件总线（三类域）
  #6/#7 压缩剪枝 + 重试规则
  #8/#9 能力注册抽象 + 运行时替换

阶段 3（P2）：
  #10 fork/恢复  #11 会话标题  #12 遥测事件化
```

## 四、风险与边界

- **会话全量事件化**可能增大 L0 写入量：建议事件流按会话粒度入 `l0_raw_log`（复用现有 H2/ETL/OLAP），按 `session_id` 分区查询，控制单条消息体积
- **沙箱**先做本地直通（不改变现有工具行为），远端执行留 SPI 空实现，避免回归
- 事件总线轻量实现（Spring 事件）即可满足单机；分布式如需跨实例再做消息中间件
- 所有改造遵守仓库规则：H2 仅限 agent-memory 模块、构造器注入、定点模块回归验证
