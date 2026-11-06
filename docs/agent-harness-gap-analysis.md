# agent-harness 能力缺口分析（Gap Analysis）

> 审计时间：2026-10-13
> 审计范围：`modules/agent-harness/agent-harness-core`（171 源文件）+ 全平台交叉核验（`framework/`、`modules/`）
> 方法：先枚举现有 REST/服务/包，再用关键词矩阵全平台 grep，区分「真空白」与「在兄弟模块」

---

## 0. 最重要的前提：harness 是「管控面」，不是运行时

不先纠正这一点，会得出完全错误的结论。本平台的 AI 能力横跨多个模块：

```text
┌──────────────────────────── 管控面 (Control Plane) ────────────────────────────┐
│  modules/agent-harness   智能体/技能/模型/MCP/提示词/工作流/协作/可观测/评测/AB │
│  modules/agent-auth      认证鉴权                                              │
│  modules/module-sys      系统管理 / module-security 安全 / module-feishu 飞书   │
└────────────────────────────────────────────────────────────────────────────────┘
┌──────────────────────────── 运行时 (Data Plane) ───────────────────────────────┐
│  framework/framework-ai  Agent 循环、sandbox(10类)、MCP(ToolBridge)、A2A、     │
│                         channel、skill、intent、middleware、AgentState         │
│  modules/agent-rag       向量检索/Embedding    modules/agent-intent  意图路由   │
│  modules/agent-memory    四层记忆 L0~L3        modules/agent-trace  GenAI 追踪  │
│  modules/module-tools    31 个工具实现         modules/agent-datasource 数据源  │
└────────────────────────────────────────────────────────────────────────────────┘
```

**所以本文的「缺」分两类**：
- **A 类（控制台缺口）**：底层有，但 harness 没给出管理入口 / 闭环不完整。
- **B 类（平台真空白）**：全平台都没有，harness 自然也无从管理。

---

## 1. 先说 harness 已经很强的部分（避免误判）

避免「为赋新词强说愁」。以下能力 **已在 harness 内做成闭环**，不属于缺口：

| 能力 | 证据 |
|---|---|
| 智能体管理 | `AiAgentAdminController`（CRUD） |
| 技能管理 + 安全导入 | `AiSkillAdminController` + `AiSkillZipStructureValidator`（解压前拦 Zip Bomb） |
| 模型配置 + 连通性测试 | `AiModelConfigController`（9 端点，含 test） |
| 提示词模板 + **版本管理** | `AiPromptTemplateAdminController` + `AiPromptVersionController` |
| **A/B 测试** | `AiAbTestController`（6 端点） |
| 工作流 + 模板 + **版本** + 运行日志 | `WfWorkflowController`（14 端点） |
| **多智能体协作**（含审批链 / 分叉恢复） | `CollabController` + `SessionForkResumeController` |
| **可观测**（trace/step + 告警规则 + 看板） | `ObservController` + 24 个 observ 类 |
| **评测**（用例 + 运行 + 结果） | `ObservTestCase` / `ObservTestRun` / `TestRunnerService` |
| **多租户** | `tenantId` 命中 115 处 |
| 插件管理 / API 文档导入 | `AiPluginAdminController` / `CurlApiDocImporter` |

**判断**：作为控制台，覆盖度已属生产可用水平。下面的缺口是「锦上添花」与「真欠账」的混合。

---

## 2. A 类：harness 控制台层的缺口（底层有能力，入口没补齐）

### A1. MCP 配置只增删改查，**没有连通性测试 / 工具发现** ⚠️ 最不对称
`AiMcpConfigController` 只有 list / create / update / delete（5 端点）；而 `AiModelConfigController` 有 9 端点且**含 test**。

- 现象：用户配了 MCP server，无法在控制台验证「能不能连上、暴露了哪些工具」。
- 代价：配置错误只能等对话时才发现（而 MCP 工具级失败还是 HTTP 200，极难排查）。
- 建议：补 `POST /mcp-configs/{id}/test`（握手 + `tools/list` 回显工具清单）。

### A2. 规划模式只有开关，**无计划实体 / 历史 / 回放**
`plan/AiPlanAdminController` 仅 3 个端点：`/enter`、`/exit`、`/status`（进入/退出规划模式）。

- 现象：能切换「是否规划」，看不到「规划出了什么、执行到哪、以前怎么规划的」。
- 建议：补计划持久化（plan 表）+ 计划详情/历史查询，否则规划能力是黑盒。

### A3. 技能调用缺「试运行 / 调试台」
`AiSkillCallController` 仅 3 端点。对比模型配置有 test，技能却无法「填参数 → 看真实返回」地调试。

### A4. 评测缺「数据集 + 基线回归」
有 test case / test run，但**未见到数据集版本化与基线对比**（如「本次改动 vs 上次通过的基线，退化了几条」）。这是评测能否守住质量的关键一环。

### A5. 可观测缺 token / 成本维度
`observ/` 有 trace/step/告警，但**没有 token 用量与成本**这一最受业务关注的维度（见 B1）。

---

## 3. B 类：平台真空白（全平台 grep 佐证）

以下能力在 `framework/` + `modules/` 全量搜索均**未找到实现**（命中数来自 grep -rli）：

| # | 缺失能力 | grep 证据 | 影响 |
|---|---------|-----------|------|
| **B1** | **Token 计量与成本核算** | `TokenUsage`=0、`计费`=0；仅 `agent-trace/GenAiAttributeNames` 定义了 `gen_ai.usage.input_tokens` **语义属性**，无聚合/无落库/无账单 | 不知道一次对话花多少钱，无法做预算与优化 |
| **B2** | **配额 / 额度（Quota/Balance）** | `Quota`=3、`Balance`=1、`余额`=0 | 无法限制某租户/用户的调用量或费用上限 |
| **B3** | **用户反馈闭环** | `Feedback`=0、`用户反馈`=0（"点赞/评分"均无有效命中） | 拿不到"这条回答好不好"的标注，评测只能靠人工造用例 |
| **B4** | **答案级引用 / 溯源（Citation）** | `Citation`=1（且非答案级）；memory 里的"溯源"指 trace 事件链，非回答出处 | RAG 有向量检索，但回答**不带出处标注**，无法核验、易幻觉 |
| **B5** | **语义缓存** | `SemanticCache`=0、`语义缓存`=0 | 相似问题重复走大模型，重复付费、重复等待 |
| **B6** | **输入侧护栏 / Prompt Injection 防护** | `Guardrail`=0、`PromptInjection`=0（"提示词注入"=1，是注释）；现有脱敏是**输出侧** | 用户可用注入攻击越权/套取系统提示词，无阻断 |
| **B7** | **多模态（图文音）** | `Multimodal`=0、`多模态`=0；`Vision` 命中 5 处为误命中 | 无法接收图片/音频、无法生成图片，能力面偏纯文本 |
| **B8** | **Webhook / 外部事件触发 Agent** | `Webhook`=0；`@Scheduled` 仅用于 `AgentStateRetentionCleanup` 内存清理 | Agent 只能被"人问"触发，不能被外部系统事件触发 |
| **B9** | **面向租户/用户的配额式限流** | `限流` 仅存在于 `framework-ai/mcp/ToolBridge`（**工具级**治理：限流/熔断/日志）与 429 归因诊断 | 无"这个租户每分钟最多 N 次"级别的准入控制 |

---

## 4. 「薄」但不算「缺」（有代码，深度不足，供参考）

| 位置 | 现状 | 建议 |
|---|---|---|
| `plan/` | 仅 1 个 AdminController | 见 A2 |
| `skill/` | 仅 `AiPluginStatusSkill` 1 个类 | 技能生态偏少，靠 module-tools 补 |
| 调度 | 只有内部清理用 `@Scheduled` | 若要「定时跑 Agent」，需新建调度域（见 B8） |
| 审计 | `Audit`/`审计` 命中 28/50 但分散 | 无统一审计查询中心，建议收敛 |

---

## 5. 优先级建议

```text
P0（信任与成本，不做会出事）
  ├─ B1 Token 计量与成本核算  ← agent-trace 已有 OTel 属性，落地成本最低
  └─ A1 MCP 连通性测试/工具发现  ← 直接消除"静默配错"，投入极小

P1（质量闭环）
  ├─ B4 答案引用/溯源        ← RAG 已具备检索，补引用标注投入可控
  ├─ A4 评测数据集+基线回归
  └─ B3 用户反馈闭环

P2（效率与安全）
  ├─ B6 输入侧护栏/防注入
  ├─ B5 语义缓存
  └─ B2/B9 配额与租户级限流

P3（能力扩张）
  └─ B7 多模态 / B8 事件触发
```

**性价比最高的两个起点**：**B1**（有 OTel 属性打底，只差聚合与账单）和 **A1**（控制器加一个 test 端点即可，消灭一类难查故障）。

---

## 6. 结论

- agent-harness 作为**管控面**，覆盖度已相当完整（智能体/技能/模型/提示词版本/AB/工作流/协作/可观测/评测/多租户）。
- 真正的欠账集中在**「商业闭环」（成本/配额/反馈）**与**「安全与可信」（注入防护/引用溯源）**两条线——这是所有 Agent 平台从「能跑」走向「能运营」的必经关卡。
- 另有若干**控制台不对称**（MCP 无测试、规划无实体、技能无调试台），投入小、见效快。

> 注：本文是**缺口分析**，不是实施计划。若要启动其中任何一项，按仓库规范应先落 `docs/plans/YYYY-MM-DD-<topic>.md` 并评审后再执行。
