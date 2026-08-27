# agent-harness vs DeepSeek Harness（dsh）功能差距分析

- 日期：2026-08-24
- 目标：评估 `modules/agent-harness`（+ `agent-spring-boot-starter` 编排内核）是否完全具备 DeepSeek Harness（deepseek-ai/dsh，Cordis 插件架构）的完整功能
- 参考：dsh 官方发布信息 + 3 篇深度架构分析（memo.d.foundation / pandaily / atomicbot.ai）

---

## 一、DeepSeek Harness（dsh）能力基线

dsh = `npx @deepseek-ai/dsh web`，MIT 开源，核心口号 **"一切皆插件"（Everything is a Plugin）**，基于 Cordis 元框架。架构特征：

| # | 能力域 | 说明 |
|---|---|---|
| A1 | **插件化架构** | 模型适配器/工具注册表/会话日志/沙箱/存储/循环/调度/UI **全部是插件**，可热插拔、注册可逆（卸载自动回滚）；无特权核心 |
| A2 | **控制脊柱 6 服务** | session log / agent registry / loop driver / tool registry / prompt assembly / model adapter registry |
| A3 | **追加式会话日志（不变式）** | "Model-visible means logged"——模型可见的一切必须可由追加式会话日志重建；fork/resume/replay/transcript/token 计量全部派生自单一日志流 |
| A4 | **Turn Loop + Waterfall** | step（一次模型请求+工具调用）、turn（多 step）；around-middleware 瀑布事件：`next()` 委托或短路接管 |
| A5 | **工具流水线** | pre-execute hooks → 权限检查 → 单调守卫 → 一次性审批（HITL）→ 执行包装（超时/重试）→ post-execute 重写 → 结果冻结入日志 |
| A6 | **4 种工作模式** | Standard（全功能）/ PTC（程序化工具调用，TypeScript 链式）/ Minimal（bash+编辑器）/ Creation（自定义 preset）；模式=不同插件组合 |
| A7 | **沙箱** | 文件系统/子进程共享执行世界，可切换 E2B 远程执行；Landlock/Windows ACL |
| A8 | **互操作** | AGENTS.md/CLAUDE.md 原生读取；Claude Code hooks 桥接；Skills；MCP 客户端；**可驱动 Claude Code/Codex 为子 agent（meta-harness）** |
| A9 | **模型适配** | DeepSeek 一等公民 + catalog + 通用 OpenAI 兼容适配器（40+ 模型，Kimi/OpenAI/Anthropic/Google） |
| A10 | **Web UI** | 本地控制台 localhost:3080，会话检查 |
| A11 | **会话存储** | append-only 格式 v0，不承诺升级兼容 |

---

## 二、agent-harness 现状盘点

工程结构：`modules/agent-harness`（core 129 类 + autoconfig 12 装配）+ `agent-spring-boot-starter`（编排执行内核）。

| 包/模块 | 核心类 | 能力 |
|---|---|---|
| starter `AiAgentService` | invokeHarness / 拦截器链 / 会话压缩重试 | **主循环**：意图路由 → HarnessAgent.call → 响应；拦截器任一 DENY 终止请求 |
| starter `agent/` | AiHarnessAgentRegistry（租户/配置指纹 LRU）、AiHarnessAgentFactory、AiAgentProfileResolver | **agent registry + 模型适配**（AgentScope HarnessAgent，DashScope 统一） |
| starter `skill/` | AiSkillRegistry / AiApiSkill / AiSkillCallController | **技能注册表 + API 技能执行**（timeout 配置） |
| starter `plugin/` | AiPlugin SPI / DynamicPluginManager（URLClassLoader）/ AiPluginAdminController | **动态插件机制**（类加载级，非 Cordis 事件级） |
| starter `mcp/` | McpController / ToolBridge + AiMcpConfigController | **MCP 配置/桥接** |
| starter `sandbox/` | SandboxBackend / LocalSandboxBackend | **本地沙箱** |
| starter `observ/` | TraceCollector / TraceObserver / GenAiTrace | 链路观测分发（OTel GenAI 语义） |
| harness `observ/` | ObservController / DashboardService / AlertService / TestCase | 观测中心：仪表盘 / 告警 / 测试用例（**dsh 无，增强**） |
| harness `workflow/` | WfWorkflowEngine（LLM/HTTP/Code/Template） | 工作流编排（**dsh 无，增强**） |
| harness `collab/` | MultiAgentCollaborationService / Session / Task / TaskFlow | 多智能体协同（**dsh 无，增强**） |
| harness `intent/`（starter） | LlmIntentChecker / RuleIntentChecker / 路由决策 | 意图路由（**dsh 无，增强**） |
| harness `management/` | AiManagedAgent / AiApiDoc / AiAbTest / AiAgentCapability | 智能体管理 / API 管理 / A/B 测试 / 能力管理 |
| harness `modelconfig/` | AiModelConfigService / Repository | 模型配置 CRUD + 连通性测试 |

---

## 三、逐项对比（✅完全 / 🟡部分 / ❌缺失）

| dsh 能力 | agent-harness 现状 | 判定 |
|---|---|---|
| A1 一切皆插件（Cordis 事件级可逆） | `AiPlugin` SPI + URLClassLoader 类加载级插件 + **PluginEventBus 事件订阅 + 插件中间件贡献 + 卸载可逆回滚**（2026-08-25 P5 补齐，对齐 Cordis ctx.emit/on） | ✅ 具备 |
| A2 控制脊柱 6 服务 | 有 agent registry（AiHarnessAgentRegistry）、tool registry（AiSkillRegistry）、model adapter（modelconfig+Factory）、prompt 组装（intent+profile）；session log 由 observ_session_event 承担（P0 落地） | ✅ 具备 |
| A3 追加式会话日志不变式 | `observ_session_event` append-only 事件日志（P0 阶段1），fork/resume/token 计量均派生自事件流 | ✅ 具备 |
| A4 Turn Loop + Waterfall 中间件 | around-middleware `next()` 委托 + 短路（P0 阶段2），插件可经 registerMiddleware 参与瀑布（P5） | ✅ 具备 |
| A5 工具流水线 | pre/post hooks + 守卫 + HITL 审批 + 重试 + 冻结打点（P1，ToolPipeline） | ✅ 具备 |
| A6 4 种工作模式 | AiAgentPreset 注册表（conversation/rag/tool/plan/graph，模式=能力+配置组合，P2-2） | ✅ 具备 |
| A7 沙箱（共享执行世界/远程） | 本地 + HTTP 远程命令沙箱（P2-1）+ SandboxFileSystem 工作区文件抽象（P4，本地/远程一致，越界防护） | ✅ 具备 |
| A8 互操作（AGENTS.md/hooks/子 agent） | AGENTS.md/CLAUDE.md 读取 + 外部 harness provider + AGENTS.md hook 桥接（P3，Claude Code JSON 决策协议） | ✅ 具备 |
| A9 模型适配 | 统一 OpenAI 兼容（DashScopeChatModel）+ 模型配置管理 ✓ 语义等价 | ✅ 具备 |
| A10 Web UI | 有完整管理台（前端 agent_runner web-shell）✓ | ✅ 具备 |
| A11 会话存储 | observ_trace 持久化 + append-only 事件流 + fork/resume（P1） | ✅ 具备 |

---

## 四、结论

### 结论：**不具备 dsh 的完整功能**——具备核心骨架，缺失 3 个架构级特征与 6 项外围能力。

**✅ 已具备（骨架级）**：模型适配、agent 注册表、技能/工具注册、turn loop 主循环、MCP、本地沙箱、观测中心、Web 管理台；并在编排/管理/观测/工作流/协同上**超出 dsh**（告警、测试用例、工作流引擎、多智能体协同、A/B 测试、意图路由均为 dsh 没有的平台能力）。

**❌ 关键差距（3 个架构级）**：
1. **A3 追加式会话日志不变式**——dsh 最核心的设计（一切派生自单一 append-only 日志），agent-harness 采用采集式 trace + 分层记忆，无"model-visible means logged"强约束 → 无法天然获得 fork/resume/完整重放/精确 token 计量
2. **A4 Waterfall 中间件**——around-middleware 委托模型缺失，插件只能在入口拦截（DENY 短路），无法在循环任意节点包裹/接管/短路
3. **A5 工具流水线**——hooks/审批/守卫/重写四件套缺失，仅超时

**❌ 外围差距（6 项）**：fork/resume、4 工作模式、远程沙箱（E2B）、AGENTS.md/hooks 互操作、subagent provider（meta-harness）、Claude Code 桥。

### 定位差异
- **dsh**：通用 agent **运行时**（Node/Cordis），面向开发者本地/CLI，强组合性、弱平台化
- **agent-harness**：Java/Spring 平台内嵌的 agent **管理+编排后端**（AgentScope HarnessAgent 内核），强平台化（租户/观测/审批/工作流）、弱运行时组合性

两者是"运行时"与"平台"的定位差，**不是同一层面的完整对标**。

---

## 五、差距收敛建议（按优先级）

| 优先级 | 建议 | 对应 dsh 能力 | 工作量 |
|---|---|---|---|
| P0 | 引入 **append-only 会话事件日志**（agent-memory L0 RawLog 已有雏形）作为 trace/replay/token 计量唯一事实源 | A3 | 高 |
| P0 | 拦截器链升级为 **around-middleware 瀑布**（`next()` 委托 + 短路 + 前后包裹） | A4 | 中 |
| P1 | 工具执行加 **pre/post hooks + HITL 一次性审批 + 守卫** | A5 | 中 |
| P1 | 会话 **fork/resume**（基于事件日志重放） | A3 派生 | 中 |
| P2 | 沙箱抽象扩展远程后端（E2B/容器） | A7 | 中 |
| P2 | 引入 **agent preset/模式**（模式=插件+配置组合） | A6 | 中 |
| P3 | AGENTS.md/CLAUDE.md 读取 + 外部 harness 子 agent provider | A8 | 低 |

> 若以"dsh 对标"为长期目标，**P0 的 A3+A4 是地基**，建议先行；P1-P3 可渐进。
