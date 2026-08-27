# agent-harness × DeepSeek Harness 对比

> 日期：2026-08-22 ｜ 对比对象：本仓库 `agent-harness`（agent-harness-core + agent-harness-autoconfig + agent-spring-boot-starter）vs DeepSeek Harness（dsh，基于 Cordis 的参考实现）
> 依据：dsh 官方 reference 架构/agent-lifecycle 文档 + agent-harness 实际代码核对。
> 相关文档：`2026-08-22-deepseek-harness-architecture.md`（dsh 侧）、`2026-08-22-harness-gap-analysis.md`（12 项改造轨迹）。

## 一、总览

| 维度 | DeepSeek Harness（dsh） | agent-harness |
|---|---|---|
| 底座 | Cordis 插件框架（一切皆插件） | Spring Boot 3.4.5 + AgentScope Java v2（HarnessAgent） |
| 定位 | 通用 AI Agent 运行时参考实现 | 平台化智能体运行时（配置驱动、多智能体、可观测） |
| 扩展模型 | 插件树（bundle 可运行时安装/卸载） | Spring Bean + 动态插件（URLClassLoader 热加载） |
| 配置来源 | Profile（具名组装）× 组合包 × patch 叠加 | DB（ai_managed_agent）+ yml + 运行时 Profile 补丁 |

## 二、逐项对比

### 2.1 架构哲学：插件树 vs 模块化 + 动态插件

| | dsh | agent-harness |
|---|---|---|
| 理念 | 无特权内核：模型适配器/工具注册表/会话日志/agent loop **本身都是插件** | Spring Boot 自动装配（autoconfig + imports）为骨架，业务以模块（core+autoconfig）组织 |
| 注册副作用 | 注册即副作用，卸载自动撤销 | Bean 注入（启动期固定）；`DynamicPluginManager` 动态注册/撤销（能力/钩子/沙箱/补丁快照） |
| 隔离 | 插件 realm/scope 隔离 | URLClassLoader 子优先隔离（单插件级） |
| 结论 | — | 静态骨架 = 模块化；**动态扩展 = 已对标**（AiPlugin SPI + onLoad/onUnload） |

### 2.2 启动组装：Profile×bundle×patch vs DB + autoconfig + 补丁

| | dsh | agent-harness |
|---|---|---|
| 组装输入 | Profile（命名参数集合）→ 组合包 → `cordis.patch.yml` 多层叠加 | `AiManagedAgent`（persona/model/skillIds/agentType/agentConfig）→ `AiAgentProfile` |
| 装配 | `--dump-config` 查看实际配置树 | `AiHarnessAgentFactory.create(profile)` → HarnessAgent（sysPrompt 类型化/模型/toolkit/workspace/maxIters/compaction） |
| 运行时改写 | patch 覆盖层（配置树合并） | `AiProfilePatchProvider.patch(original, request)` → Router 四出口统一应用 ✅ |

### 2.3 执行循环：turn/step vs 会话记忆 + 事件发布

| | dsh | agent-harness |
|---|---|---|
| 循环单元 | turn（零或多步）/ step（一次模型请求+工具调用） | HarnessAgent.run（AgentScope 内部 turn 循环） |
| 步骤事件 | step/start → request → llm/stream → assistant → tool/call* → step/end | `AgentStepEvent(begin/end)` 由 `AiAgentService.reply` 发布；工具流水线由 `ToolExecutionListener` 发布 |
| 轮次事件 | turn/start · turn-stopping · turn/end | `ConversationTurnEvent`（appendTurn 每轮发布） |
| 结论 | — | 语义对标；但 dsh 有 pre-step 拦截/改写权威决策点，项目侧无对应 hook（可补） |

### 2.4 事件体系：三类事件域

| | dsh | agent-harness |
|---|---|---|
| 会话域 | 会话事件（持久回放） | `ConversationTurnEvent` → L0 持久化 ✅ |
| Agent 域 | Agent 事件（实时协调） | `AgentStepEvent` → 事件总线 ✅ |
| 能力域 | 能力事件（seam 附加策略） | `ToolCallEvent`（pre/post/error）✅ |
| 分发 | waterfall/serial 事件模型 | Spring `ApplicationEventPublisher`（同步、单机轻量）🟡 |
| 结论 | — | 三类域齐备；分发模型简单（单机够用，分布式需换 MQ） |

### 2.5 会话日志不变量：模型可见即已记录

| | dsh | agent-harness |
|---|---|---|
| 不变量 | 模型看到的必须能从会话日志重建 | L0 全量事件流（role=user/assistant，traceId=convo-*） |
| 回放 | 会话日志回放 | `AiConversationMemory.restore(sessionId)` 跨重启重建 ✅ |
| fork | sessions fork | `AiConversationMemory.fork(source, child)`（事件流补记）✅ |
| 标题 | sessionTitle | `AiConversationMemory.title()`（首条用户消息 20 字）✅ |
| 结论 | — | **已完全对标**（三阶段改造①⑨⑩） |

### 2.6 能力 Seam

| | dsh | agent-harness |
|---|---|---|
| 角色 | Service Definition / Provider / Consumer 三角色 | `AiCapabilityProvider`（name + contribute(Toolkit, profile)） |
| 替换 | 替换 Provider 即替换能力（如远程沙箱搬移 Bash/PTY/LSP） | 替换 Bean 或动态插件注册即替换；技能/记忆工具为既有能力实例 ✅ |
| 结论 | — | 已对标（阶段2⑦）；未做 Service Definition 声明式契约（🟡 可演进） |

### 2.7 工具流水线

| | dsh | agent-harness |
|---|---|---|
| 钩子 | tools/pre-execute → execute → post-execute | `ToolExecutionListener.onPreExecute`（可拒绝）/ `onPostExecute` / `onError` / `onToolRegistered` |
| 组合 | 事件流多监听 | 多 Bean 链式组合（factory 聚合）✅ |
| 审计 | 事件可审计 | 默认 `LoggingToolExecutionListener` + `EventPublishingToolExecutionListener` ✅ |
| 结论 | — | 已对标（阶段1③） |

### 2.8 沙箱

| | dsh | agent-harness |
|---|---|---|
| 抽象 | ctx.sandbox 包装 argv | `SandboxBackend.wrapCommand/isAllowed` SPI |
| 默认 | 沙箱化执行 | `LocalSandboxBackend`（本地直通，零行为变化）🟡 |
| 演进 | 远程沙箱能力 | 替换 Bean 即可（remote/container 实现）✅ |
| 结论 | — | SPI 已对标；生产默认仍是直通（需按部署环境实现隔离） |

### 2.9 上下文压缩

| | dsh | agent-harness |
|---|---|---|
| 步骤 | 剪枝 → 摘要；仅推进 replacement generation 才开全新重试轮次 | `pruneForCompression`（>5120 丢弃、>2048 截断）→ 摘要 |
| 重试 | 严格重试规则 | 无显式重试（🟡 阶段2⑥ 仅完成剪枝） |
| 结论 | — | 剪枝已对标；**严格重试未落地**（可补） |

### 2.10 遥测

| | dsh | agent-harness |
|---|---|---|
| 形态 | observ 事件化（业务与落库解耦） | `ObservEventBridge`（@EventListener 订阅三域 → ObservTraceService onBegin/onStep，trace/step 落库 + 告警）✅ |
| 结论 | — | 已对标（阶段3⑪） |

### 2.11 动态插件（对标 dsh 插件树语义）

| | dsh | agent-harness |
|---|---|---|
| 加载 | bundle 运行时安装/卸载/替换 | `DynamicPluginManager.loadJar/unload/list/close`（URLClassLoader + META-INF 清单反射实例化）✅ |
| 生命周期 | 插件 onLoad/onUnload | `AiPlugin.onLoad(PluginContext)/onUnload()` ✅ |
| 注册能力 | 插件贡献任意能力 | PluginContext.registerCapability/Listener/Sandbox/ProfilePatch ✅ |
| 管理 API | CLI / Web | REST `/api/ai/plugins`（list/upload/load/unload）✅ |
| 结论 | — | 已对标（方案 B 落地） |

## 三、结论

### ✅ 已对齐（11 项）
会话事件化 + 回放、fork/标题、三类事件域、工具把关钩子、能力抽象、profile 运行时补丁、压缩剪枝、遥测事件化、动态插件（安装/卸载/热恢复）、沙箱 SPI、L0 全量留痕。

### 🟡 差距（4 项，均可平滑补齐）
| 差距 | 说明 | 状态 |
|---|---|---|
| pre-step 权威拦截 | dsh 有 agent/pre-step（拦截/改写/拒绝请求的权威决策点） | ✅ **已实施（2026-08-22）**：AiRequestInterceptor（PASS/REWRITE/DENY）+ AiRequestContext + 拦截链接入 legacyChat/invokeHarness 双入口 |
| 压缩严格重试 | dsh 压缩失败/推进失败时开全新重试轮次 | ✅ **已实施（2026-08-22）**：`summarizeWithRetry`（maxRetries 可配置，默认 2）+ 摘要为空/异常均重试 + 耗尽回退保持会话 + applyCompression 失败日志 |
| 事件分发模型 | Spring 事件为单机同步 | 分布式场景替换为 MQ 桥（AiEventPublisher 已是 SPI） |
| 能力声明式契约 | dsh 有 Service Definition（能力 schema 声明） | AiCapabilityProvider 增加 declare() 返回能力描述/参数 schema |

### 📌 演进建议（按优先级）
1. ~~**P0**：请求拦截器~~ ✅ 已完成（AiRequestInterceptor，5 单测全绿）
2. ~~**P1**：压缩严格重试~~ ✅ 已完成（summarizeWithRetry，4 单测全绿）
3. **P2**：能力 Service Definition——插件/能力自描述，支撑能力市场
4. **P2**：分布式事件桥（RabbitMQ/Kafka 实现 AiEventPublisher）

> 总体评价：经三阶段改造（12 项）+ 动态插件落地，agent-harness 已覆盖 dsh 的**事件化、可插拔、可观测、可热扩展**四类核心骨架；剩余差距集中在"权威拦截点"与"重试语义"，属增强项而非结构项。
