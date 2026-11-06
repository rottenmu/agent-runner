# 基于 agent-harness 构建完整生产级 Agent 运行时（类 LangGraph/AutoGen 一体化 Harness）

> 状态：M1–M4 已完成并真机验证（2026-10-20）；收尾项「MySQL DDL 与方言兼容」已完成（2026-10-21）
> 起草日：2026-10-20
> 关联模块：`modules/agent-harness`（管控面 + 本次新增运行时内核）、`framework/framework-ai`（运行时基底：sandbox / MCP / A2A / agent 循环）、`modules/agent-memory`（记忆，经 MCP 解耦）
> 规范依据：`docs/rules/PLAN_DOC_RULES.md`

<!--
本方案目标：把当前偏"管控面"的 agent-harness，升级为"管控面 + 一体化运行时"的完整生产级 harness。
核心不是再加一个功能，而是引入一个统一的【图编排运行时（Graph Runtime）】，把现有散落的
对话循环、工作流引擎、多智能体协作统一抽象为"节点 + 边 + 状态 + 检查点"的执行模型。
-->

---

## 一、置信度与剩余风险

- 当前置信度：78%
- 主要剩余风险：升级 `WfWorkflowEngine` 为 `GraphEngine` 时对**存量工作流 JSON 的兼容**——现有 `loop` 节点语义（节点内循环 vs 图回边）与新增的并行 / 回边 / checkpoint 模型若不一致，会导致已上线工作流跑出不同结果；该风险会以"老工作流执行路径偏移、或 checkpoint 表与现有 `wf_workflow_run_log` 表语义冲突"的形式暴露。实施首步必须先把存量图 JSON 采样跑一遍基线。

---

## 二、目标

本次要达成：

- **统一图编排模型（AgentGraph）**：把"对话循环、工作流、多智能体协作"统一为 `节点(Node) + 边(Edge) + 状态(State)` 一张图，支持分支 / 循环(回边) / 并行(fan-out/fan-in) / 子图(subflow) / 人工(human) 节点。
- **状态机驱动的执行引擎（GraphEngine）**：取代现有 `WfWorkflowEngine` 的单向线性遍历，支持每节点后 **checkpoint 落库**、**中断-恢复**、**回放**；流式输出对接现有双通道（同步 / SSE）。
- **统一 Agent 运行时循环（AgentRuntime）**：标准化 `think → act(tool) → observe` 循环，替代散落在 `AiChatStreamController`/`AiChatController` 内的对话逻辑，作为图中 `agent` / `llm` 节点的执行体。
- **工具市场（ToolRegistry + ToolProvider）**：统一工具的注册 / 发现 / 版本 / 权限 / 调用审计；整合 `framework-ai` 的 sandbox（代码执行）与 MCP `ToolBridge`（已有），harness 侧只做"目录 + 治理"，不重复造运行时。
- **多智能体编排（supervisor / worker 图模式）**：把现有工单式 `MultiAgentCollaborationService` 升级为图式 supervisor 动态路由，保留 `conflict` 部分失败语义。

本次明确不做（延后项见第九节）：

- 输入侧护栏 / 提示词注入防护（缺口 B6）：本期仅做**工具调用授权**，注入防护延后。
- Token 计量与成本核算（缺口 B1）、租户配额限流（B2）、语义缓存（B5）、多模态（B7）：属平台级，不在 harness 运行时范围。
- 前端审批 UI 重写：复用现有 `CollabController` 审批界面，仅做最小适配。

---

## 三、成功标准

> 每条都可在真机直接判定。以下"图 JSON / API"指本方案实施后在 `agent-application` 暴露的端点。

1. **分支 + 循环 + 并行**：提交含 `condition` 分支（两出口）+ `loop` 回边（上限 3 次）+ `parallel`（2 子节点 fan-out/fan-in）的图 JSON 并启动 → 观察 step 记录：分支按条件走正确边；`loop` 恰好执行 **3 次**（不多不少）；`parallel` 两子节点并发完成；整图最终状态 `success`，且 `parallel` 段耗时 **< 两子节点串行耗时之和**（证明并行生效）。
2. **Human-in-the-loop 中断-恢复**：在图中插入 `human` 节点，执行到该节点 → 观察状态变为 `suspended`（非 `failed`/`done`），API 返回 `waiting` 且携带 `resumeToken`；调用 `resume` API 传入审批结果 → 观察状态推进到下一节点且最终 `success`。
3. **进程崩溃恢复**：图执行进行过半（已完成节点数 ≥ 2）时 `kill -9` 后端进程，重启后调用 `resume` → 观察从**最近 checkpoint** 续跑：已完成节点**不重跑**（step 数 = 中断前已完成数 + 续跑新增，无重复副作用），最终 `success`。
4. **工具市场 + 授权**：在 `ToolRegistry` 注册一个 MCP 工具，图中 `tool` 节点引用它 → 观察工具被调用且结果回写节点输出；调用**无权限**工具 → 观察被拒（返回 guarded 标记 / 4xx），且**对话线程不崩**（失败隔离，符合现有"旁路 fail-silent"原则）。
5. **Supervisor 多智能体 + 部分失败**：提交 supervisor 图（supervisor 动态路由到 2 个 worker），构造其中一个 worker 返回 `conflict` → 观察最终状态 `conflict`，**另一 worker 结果保留**（部分成功语义与现有 `MultiAgentCollaborationService` 一致），且 supervisor 收到两路回执。

---

## 四、参照行为（对标）

| 参照项 | 它怎么做 | 我们要复刻到什么程度 |
| --- | --- | --- |
| LangGraph 状态图 | 节点 + 边 + 共享 State 对象；checkpoint 持久化；`interrupt`/`resume` 支持人工挂起 | 复刻图模型 + checkpoint + interrupt（对应本方案 `human` 节点）；**不复刻**其 Python 特定的 reducer 语义 |
| LangGraph 多 Agent | supervisor 节点据状态动态路由到 worker，worker 返回后回 supervisor | 复刻 supervisor/worker 作为图的一种编排模式；复用现有 `conflict` 部分失败语义 |
| AutoGen group chat | 对话式多 agent、群聊轮转 | 复刻"群聊轮转"为图的 `parallel` + 汇聚模式，不引入独立 group-chat 运行时 |
| 工具 sandbox 执行 | 隔离执行不可信代码 | 直接复用 `framework-ai` 的 sandbox（已 10 类），harness 不做运行时只做目录治理 |
| DeepSeek/通用 harness | 模型运行外壳：chat template + 推理循环 + 工具调用标准化 | 复刻"统一推理循环（AgentRuntime）"概念；模型适配（chat template / reasoning token）作为独立延后项，本期不实现 |

---

## 五、现状差距

### 能力缺失

- **通用图执行引擎**：现有 `WfWorkflowEngine` 是**单向 DAG 线性遍历**（按 `edges` + `branch` 条件推进），无并行节点、无图回边循环、无 checkpoint 落库中断恢复。`loop` 是节点内循环，不是图层面的通用循环。距"生产级图编排"差一个量级。
- **统一 Agent 运行时循环**：对话的 `think → act → observe` 逻辑散落在 `AiChatStreamController`（流式）与 `AiChatController`（同步）两个控制器内，与"图节点"没有统一抽象，导致工作流的 `llm` 节点与聊天通道是两套执行路径。
- **工具市场治理层**：`framework-ai` 已有 sandbox + MCP `ToolBridge`（运行时），但 harness **没有工具的注册 / 版本 / 权限 / 审计目录**。现有 `AiMcpConfigController` 只有 MCP 连接 CRUD，无"工具级"治理（见缺口 A1）。
- **图式多智能体编排**：`MultiAgentCollaborationService` 是**工单式**（任务派给多 agent → 各自回复 → 汇总），非图式 supervisor 动态路由；无法表达"supervisor 根据中间结果再决定下一个 worker"。

### 只是没接线

- **状态持久化基底**：现有 `wf_workflow_run_log` 已记录节点执行轨迹，只需在其上扩充"可恢复的 checkpoint 快照"字段即可，无需新造持久化机制。
- **Human / 审批**：工作流的"人工驳回"（`end` 节点 failed）、协作的 `approval` 审批链已存在，只需统一抽象为图的 `human` 节点（suspend/resume），前端审批 UI 可复用。
- **可观测**：`observ` 包已有 trace/step 两层模型与"埋点全吞异常"的韧性设计，只需把图节点执行映射为一个 `step`，无需新建观测体系。
- **记忆旁路**：`ChatTurnMemoryRecorder` + MCP 已打通，图执行完成后挂同一旁路即可，无需改动记忆侧。

### 不打算解决的问题

- 模型适配层（DeepSeek chat template / reasoning token / native tool call 隔离）：本期不做，避免范围蔓延（见第九节）。
- 商业闭环（计费 / 配额 / 反馈 / 引用溯源）：属平台级，不在 harness 运行时。

---

## 六、技术方向

### 1. 统一图模型 AgentGraph

职责：定义图的静态结构（节点 + 边）与运行时状态（State），与具体执行解耦。

关键接口 / 数据结构：

```text
GraphDef { id, name, nodes: List<NodeDef>, edges: List<EdgeDef>, version }
NodeDef  { id, type∈{start,end,llm,agent,tool,condition,parallel,human,subflow},
           config:JsonNode, next:List<EdgeRef> }
EdgeDef  { from, to, when:String /*条件表达式或分支标签*/ }
GraphState { runId, currentNodeId, data:Map<String,Object>, status∈{running,suspended,success,failed,conflict},
             checkpoint:JsonNode /*可序列化的中间态快照*/ }

新增表（MySQL，V 脚本）：
  graph_def        -- 图定义（JSON 持久化，支持版本）
  graph_run        -- 一次运行的当前状态（status/checkpoint/resume_token）
  graph_run_step   -- 每节点执行的不可变记录（替代/补充现有 wf_workflow_run_log）
```

取舍理由：复用现有 `WfWorkflowEngine` 的 JSON 图配置格式（节点 `type` 字段已存在），**渐进式**升级为 `GraphEngine`，不推翻重写——降低对存量工作流的破坏风险（对应第一节风险）。

### 2. 状态机驱动的执行引擎 GraphEngine

职责：按 `GraphDef` 驱动执行，支持分支 / 循环(回边) / 并行 / 子图 / human 挂起，每节点后写 checkpoint。

关键接口 / 数据结构：

```text
GraphEngine.execute(GraphDef, initState) -> CompletableFuture<GraphState>
  - 每节点执行完：写 graph_run_step + 更新 graph_run.checkpoint（落 MySQL）
  - condition: 按 edge.when 选下一节点（复用现有 branch 匹配逻辑）
  - loop: 支持 graph 回边（edge.to 指向已访问节点），受 maxIterations 上限保护
  - parallel: fan-out 并发执行子节点，fan-in 等待全部完成（用 CompletableFuture.allOf）
  - human: 置 graph_run.status=suspended + 生成 resumeToken，释放线程，不阻塞
  - 异常：单节点失败 → 仅记 step 失败 + 置 conflict/failed，不污染其他并行分支
```

取舍理由：用 `CompletableFuture` 做并行与 resume 回调（与现有"协作用委派回调而非轮询"的设计哲学一致）；checkpoint 落 MySQL 而非内存，解决"重启可恢复"（对应成功标准 3）。

### 3. 统一 Agent 运行时循环 AgentRuntime

职责：标准 `think → act(tool) → observe` 循环，作为图中 `llm` / `agent` 节点的执行体；对接现有双通道与 `AiChatPreflight` 单闸门。

关键接口 / 数据结构：

```text
AgentRuntime.run(turn, modelCfg, toolSet) -> Stream<Chunk>   /* 复用现有 SSE 通道 */
  loop:
    1. 调模型（经现有 model-config 层，含连通性测试能力）
    2. 解析输出：若为 tool_call → 经 ToolRegistry 取 ToolProvider 执行（sandbox/MCP）
    3. observe 结果回填 State，继续下一轮
    4. 达 maxSteps 或终态 → 退出
  脱敏：复用现有 ChatTurnMemoryRecorder 的"逐块+整段双净化"逻辑
```

取舍理由：把对话循环从控制器抽成可复用运行时，使"聊天"与"工作流里的 llm 节点"走同一执行体，消除两套路径（对应现状差距"只是没接线"中的执行路径分裂）。

### 4. 工具市场 ToolRegistry + ToolProvider

职责：工具元数据目录（注册 / 发现 / 版本 / 权限 / 审计），运行时委托 `framework-ai` 的 sandbox 与 MCP `ToolBridge`。

关键接口 / 数据结构：

```text
ToolRegistry {
  register(ToolMeta)        // 元数据：id, name, version, providerType∈{sandbox,mcp,local}, schema, requiredPermission
  resolve(toolId, tenantId) // 返回 ToolProvider + 校验权限（无权限 → 抛 GuardedException，被 GraphEngine 隔离）
  listByPermission(tenantId, perm)
}
ToolProvider { invoke(args) -> result }   /* 委托 sandbox / MCP，harness 不持有运行时 */

新增表（MySQL）：
  tool_meta      -- 工具目录与权限
  tool_invoke_log-- 调用审计（谁、何时、哪个图、结果）
```

取舍理由：harness 只做治理目录，运行时复用 `framework-ai`（已具备），避免重复造 sandbox/MCP 执行器（对应缺口 A1：补"工具级"治理能力）。

### 5. 多智能体编排 supervisor / worker 图模式

职责：把现有工单式协作抽象为图模式：supervisor 节点动态路由 → worker `agent` 节点 → 回 supervisor 汇总；保留 `conflict` 部分失败。

关键接口 / 数据结构：

```text
SupervisorNode: 读 State → 选择 worker 集合（可并行）→ 收集回执
WorkerNode:     agent 节点执行，返回 {result | conflict}
汇总:           任一 conflict → 图最终 status=conflict，其余结果保留（复用现有语义）
```

取舍理由：复用现有 `MultiAgentCollaborationService` 的 `conflict` 部分失败语义与委派回调机制，仅把它"图化"，不另起炉灶。

---

## 七、里程碑

### M1. 图模型 + GraphEngine（替代线性遍历）

- 产出：`GraphEngine` + `GraphDef/GraphState` 模型 + MySQL 三张表 V 脚本 + 存量工作流 JSON 兼容性基线报告。
- 验证：成功标准 1（分支/循环/并行）+ 成功标准 3（崩溃恢复）通过；存量图 JSON 抽样执行结果与升级前一致（偏差 < 5%）。

### M2. AgentRuntime + 工具市场接入

- 产出：`AgentRuntime`（替代控制器内散落循环）+ `ToolRegistry/ToolProvider` + 工具目录表。
- 验证：成功标准 4（工具调用 + 授权隔离）；聊天双通道仍正常（回归现有 14/14 用例）。

### M3. Human-in-the-loop 节点

- 产出：`human` 节点 suspend/resume + `resumeToken` + 复用前端审批 UI 最小适配。
- 验证：成功标准 2（中断-恢复）。

### M4. 多智能体 supervisor/worker 图模式

- 产出：supervisor 节点 + worker 路由 + 现有协作迁移到图模式（保留 conflict）。
- 验证：成功标准 5（部分失败 conflict）。

---

## 八、自测计划

### 场景 1：分支 / 循环 / 并行图（对应成功标准 1）

1. 调用 `POST /api/ai/graph/def` 提交含 `condition`(两出口) + `loop`(上限 3) + `parallel`(2 子节点) 的图 JSON。
2. 调用 `POST /api/ai/graph/run` 启动，传入使 `condition` 走 A 分支的入参。
3. 轮询 `GET /api/ai/graph/run/{runId}`，直到 `status=success`。
4. 验证：
   - `graph_run_step` 中 `loop` 节点出现**恰好 3 行**（不多不少）。
   - `parallel` 两子节点的 `step.start_ts` 时间戳重叠（并发），且 `parallel` 段 wall-time **< 两子节点耗时之和**（从 `step` 时间差计算）。
   - 最终 `status=success`。

### 场景 2：Human 节点中断-恢复（对应成功标准 2）

1. 提交含 `human` 节点的图并启动。
2. 轮询至 `status=suspended`，记录返回的 `resumeToken`。
3. 验证：此时 `status` 非 `failed`/`done`；响应体含 `resumeToken` 字段且非空。
4. 调用 `POST /api/ai/graph/resume` 带 `resumeToken` + 审批结果。
5. 验证：轮询至 `status=success`，且 `human` 节点后的节点已执行（step 存在）。

### 场景 3：崩溃恢复（对应成功标准 3）

1. 提交一个多节点图（≥5 节点）并启动。
2. 待已完成节点数 ≥ 2，对后端进程 `kill -9`。
3. 重启 `agent-application`，调用 `resume`。
4. 验证：最终 `status=success`；`graph_run_step` 中**前 2+ 个节点只有 1 行**（未重跑）；总 step 行数 = 中断前已完成 + 续跑新增，无重复。

### 场景 4：工具市场 + 授权隔离（对应成功标准 4）

1. 注册一个 MCP 工具到 `ToolRegistry`，图中 `tool` 节点引用它并启动。
2. 验证：工具被调用，`tool` 节点 `step.output` 含工具返回。
3. 用**无权限**租户调用同一 `tool` 节点。
4. 验证：返回 guarded 标记 / 4xx；图未整体失败（其他节点照常），且调用线程未抛未捕获异常（日志无 ERROR 级对话中断）。

### 场景 5：Supervisor 部分失败（对应成功标准 5）

1. 提交 supervisor 图（路由到 workerA / workerB），构造 workerB 返回 `conflict`。
2. 启动并轮询至终态。
3. 验证：`status=conflict`；`graph_run_step` 中 workerA 的 `output` 仍保留；supervisor 节点的 `input` 含两路回执。

---

## 九、已知延后项

- **模型适配层（DeepSeek chat template / reasoning token / native tool call 隔离）** —— 原因：范围过大且依赖具体模型厂商协议，本期聚焦"运行时内核"而非"多模型外壳"，避免分散精力。
- **输入侧护栏 / 提示词注入防护（缺口 B6）** —— 原因：本期仅做工具授权（输出/调用侧），输入侧注入防护需独立的语义审核链路，单独排期。
- **Token 计量 / 成本核算（B1）、租户配额限流（B2）、语义缓存（B5）、多模态（B7）** —— 原因：属平台级商业闭环，不在 harness 运行时范围，纳入后续平台级方案。
- **前端审批 UI 重写** —— 原因：复用现有 `CollabController` 审批界面，仅最小适配 `human` 节点的 `resumeToken` 流程。

---

## 十、实施顺序

1. **先固化基线**：抽样现有 `WfWorkflowEngine` 支持的图 JSON，跑一遍记录节点执行路径与耗时，作为 M1 兼容性对照基准。
2. **M1 图模型 + GraphEngine**：建表（V 脚本）→ 实现 `GraphDef/GraphState` → 实现 `GraphEngine`（分支/循环/并行/checkpoint）→ 迁移现有 workflow 执行入口到 GraphEngine。
3. **M2 AgentRuntime + ToolRegistry**：抽对话循环为 `AgentRuntime` → 实现 `ToolRegistry/ToolProvider` → 接 `framework-ai` sandbox/MCP → 聊天双通道回归。
4. **M3 human 节点**：suspend/resume + resumeToken + 前端审批复用。
5. **M4 supervisor/worker**：图式多智能体 + 现有协作迁移 + conflict 语义保留。
6. 每里程碑结束回填自测场景结论。

---

## 十一、实施记录

- 实施日期：2026-10-20（M1 阶段完成）
- 实际改动清单：

| 文件 / 模块 | 改动 |
| --- | --- |
| `modules/agent-harness/agent-harness-core/src/main/java/com/zimo/module/ai/graph/GraphDefinition.java` | 新增：图定义解析（节点索引、普通出边、条件分支、并行子节点、回边上限、exitTarget） |
| `.../graph/GraphState.java` | 新增：可序列化执行状态（input/nodeOutputs/executedNodes/loopCounters/resumeToken），兼容旧 contextJson |
| `.../graph/GraphNodeExecutor.java` | 新增：节点执行（start/end/task/async_task/llm/http/code/template/condition/manual/human/fallback/parallel），含 delayMs |
| `.../graph/GraphEngine.java` | 新增：状态机执行引擎（分支/回边/并行/每节点 checkpoint/崩溃续跑） |
| `.../graph/GraphRunService.java` | 新增：运行控制服务（start/resume/status/logs） |
| `.../controller/GraphController.java` | 新增：`/api/biz/ai/graph` 运行、恢复、状态、轨迹端点 |
| `.../autoconfig/AiGraphAutoConfiguration.java` | 新增：引擎、节点执行器、并行线程池装配 |
| `.../resources/META-INF/spring/...AutoConfiguration.imports` | 注册 `AiGraphAutoConfiguration` |
| `tmp/verify/verify-graph-engine.mjs` | 新增：分支 / 反例 / 并行 / 回边验证脚本 |
| `tmp/verify/verify-graph-crash-recovery.mjs` | 新增：进程崩溃续跑验证脚本 |
| `tmp/verify/baseline-workflows.json` | 新增：9 个存量工作流定义兼容性基线 |

### 与方案的偏差

| 原方案 | 实际做法 | 原因 |
| --- | --- | --- |
| 新建 `graph_def/graph_run/graph_run_step` 三张表 | 复用 `wf_workflow`/`wf_workflow_run`/`wf_workflow_run_log`，checkpoint 写 `context_json` | 现有表已具备定义快照、checkpoint、step 轨迹；新增表会与存量 run 数据割裂，且 harness 模块无 db 脚本目录 |
| M1 迁移现有 workflow 执行入口到 GraphEngine | 双引擎并存：存量仍走 `WfWorkflowEngine`，新图能力走 `/api/biz/ai/graph` | 零破坏存量 9 个工作流；待新引擎稳定后再迁移入口 |
| 回边超限走 `nextTarget` | 改为 `config.exitTarget`（缺省结束流程） | 静态审查发现双向回边会无限弹回 |
| `maxIterations` 语义未明确 | 定义为节点总执行次数（首次计 1） | 与成功标准「恰好 3 次」对齐 |

### 真机验证结果

- `tmp/verify/verify-graph-engine.mjs`：**15/15 通过**。场景 1 分支 `flag=true` 走 `t1`、并行 `p1/p2` 均完成；场景 2 反例 `flag=false` 走 `t2`、不走 `t1`（证明断言有鉴别力）；场景 3 回边节点 `a` 恰好执行 **3** 次并经 `exitTarget` 正常收尾。
- `tmp/verify/verify-graph-crash-recovery.mjs`：**10/10 通过**。`kill -9` 后端（pid=14744）后重启并 `resume`，最终 `status=success`，`d1.enter=1`（已完成节点未重跑），`d3` 正常执行，恢复轨迹存在。

### 过程中新发现的坑

- **回边无限循环**：回边超限若回落 `nextTarget`，双向回边（a↔b）会互相弹回。必须引入 `exitTarget` 或显式结束。
- **Arrow `--add-opens`**：直接 `java -jar` 启动会因 agent-memory Arrow OLAP 缺 `--add-opens=java.base/java.nio=...` 而启动失败；必须走 `start-backend.py`。
- **鉴权口径**：`/api/biz/**` 需 `Authorization: <token>`（不带 Bearer），token 来自 `POST /api/auth/login`（admin/admin）。
- **本地数据源为 SQLite**（`application.yml` 默认），与 AGENTS.md「只用 MySQL」规范不一致；本机验证沿用现有 SQLite，新表若落地需补 MySQL V 脚本。

### M2 阶段（工具市场 + 统一运行时）

- 实施日期：2026-10-20（同日，M2 完成）
- 实际改动清单：

| 文件 / 模块 | 改动 |
| --- | --- |
| `.../ai/tool/ToolMeta.java` + `ToolMetaMapper` | 新增：图侧工具目录（`ai_tool_meta`，租户维度 + provider 三元组 local/mcp/sandbox + 参数 Schema + 所需权限） |
| `.../ai/tool/GraphToolInvokeLog.java` + `GraphToolInvokeLogMapper` | 新增：编排侧调用审计（`ai_graph_tool_invoke_log`，带 `runId`/`nodeId`） |
| `.../ai/tool/ToolProvider.java` + `ToolInvokeResult` | 新增：执行后端 SPI（治理目录与运行时的接缝） |
| `.../ai/tool/LocalToolProvider.java` | 新增：内置确定性工具（echo/upper/sum/now + 可注册），让治理链路可被稳定验证 |
| `.../ai/tool/McpToolProvider.java` | 新增：委托 `framework-ai` 的 `ToolBridge`（生产实现即 `module-tools` 的 `ToolGovernanceService`） |
| `.../ai/tool/SandboxToolProvider.java` | 新增：委托 `SandboxBackend`（白名单 + wrapCommand + 超时/退出码归一） |
| `.../ai/tool/ToolRegistryService(+Impl)` | 新增：注册/目录/权限/跨租户拦截/调用审计（审计失败只告警） |
| `.../ai/tool/ToolSchemaInitializer.java` | 新增：幂等建表，按方言分支（SQLite / MySQL） |
| `.../ai/controller/ToolController.java` | 新增：`/api/biz/ai/tool` 注册、目录、试调用、审计查询 |
| `.../ai/runtime/AgentRuntime.java` + `AgentRunRequest` + `AgentRunResult` | 新增：一次智能体执行的统一入口（闸门 → 工具白名单 → 路由 → 调用 → 脱敏 → 记忆 → 结果归一） |
| `.../ai/runtime/RuntimeNodeHandler.java` | 新增：把 `agent` / `tool` 节点接到运行时与工具市场，失败以输出形式返回 |
| `.../ai/graph/GraphExpressions.java` | 新增：条件求值 / `{{var}}` 渲染 / JSON 参数解析从节点执行器下沉（`GraphNodeExecutor` 535 → 442 行） |
| `.../ai/graph/GraphNodeExecutor.java` | 修改：新增 `agent` / `tool` 节点；渲染与求值改为委托 `GraphExpressions` |
| `.../ai/graph/GraphState.java` + `GraphEngine.java` | 修改：state 增 `runId`（checkpoint 兼容），使工具审计可关联到具体运行 |
| `.../ai/autoconfig/AiToolAutoConfiguration.java` | 新增：后端、治理服务、运行时、节点处理器装配 + `@MapperScan(annotationClass=Mapper)` |
| `.../ai/autoconfig/AiGraphAutoConfiguration.java` | 修改：`graphNodeExecutor` 注入 `RuntimeNodeHandler` |
| `.../resources/META-INF/spring/...AutoConfiguration.imports` | 注册 `AiToolAutoConfiguration` |
| `.../test/.../tool/ToolRegistryServiceImplTest.java` | 新增：5 条单测（成功转交 / 权限拦截 / 跨租户拦截 / 审计失败不反噬 / 工具不存在不冒充拦截） |
| `tmp/verify/verify-tool-market.mjs` | 新增：工具市场 + 授权隔离 + 图节点 + 失败隔离验证脚本 |

#### 与方案的偏差（含原因）

| 原方案 | 实际做法 | 原因 |
| --- | --- | --- |
| 新建 `tool_meta` / `tool_invoke_log` 两表 | `ai_tool_meta` + `ai_graph_tool_invoke_log`，且**不重建工具运行时** | `module-tools` 已有完整工具治理：`ToolDefinition`、`tool_agent_permission`、`ToolGovernor`（限流/熔断）、`tool_invoke_log`，其 `ToolGovernanceService` 本身就是 `ToolBridge`。harness 只补图侧目录绑定与编排审计，运行时一律经 `ToolBridge` 复用 |
| `AgentRuntime` 抽 think-act-observe 循环 | 循环仍留在 `framework-ai` agent 执行体内；harness 只统一闸门/工具白名单/路由/脱敏/记忆/结果归一 | 重写循环会产生两套超时、两套工具权限判断，口径不一致时最难排查；管控面不该复制执行引擎 |
| 无权限抛 `GuardedException`，由引擎隔离 | 返回带 `guarded` 标记的结果，不抛异常 | 抛异常会让整图进 fallback 或直接失败，与成功标准「图未整体失败，其他节点照常」相反 |
| M2 改造同步/流式控制器改用 AgentRuntime | 本期不动存量双通道，AgentRuntime 先服务图节点 | 双通道已全绿（含记忆旁路），迁移收益低而回归面大；列为后续可选收敛项 |

#### 真机验证结果

- `ToolRegistryServiceImplTest`：**5/5 通过**（含「审计写失败不影响调用结果」与「工具不存在不得冒充权限拦截」两条反例）。
- `tmp/verify/verify-tool-market.mjs`：**19/19 通过**。关键项：
  - 注册 → 目录可见 → 直接调用返回 `ABC`，且 `guarded=false`（反例对照，证明 guarded 断言不恒真）；
  - 跨租户调用 `guarded=true`，原因含归属租户 `u001`；
  - 工具不存在时 `success=false && guarded=false`（区分配置错误与安全拦截）；
  - 图 `tool` 节点返回 `status=success`、`result=GRAPH`，且审计记录 `runId` 与运行实例一致；
  - 引用不存在工具的图仍 `status=success`（失败隔离），同时该节点 `status=failed`（证明「没崩」不是「悄悄成功了」）；
  - 不存在的 `agentId` 经 agent 节点返回 `status=denied`，证明新增通道**未绕过闸门**。
- M1 回归 `verify-graph-engine.mjs`：**15/15 通过**（表达式下沉重构未破坏分支/并行/回边）。

#### 过程中新发现的坑

- **Mapper 同名 Bean 会让容器直接启动失败**：`module-tools` 已有 `ToolInvokeLogMapper`，本次同名类触发 `ConflictingBeanDefinitionException: Annotation-specified bean name 'toolInvokeLogMapper' ... conflicts`。新增 Mapper/Service 前必须全局 grep 类名，跨模块重名在 Spring 里没有"包名不同就没事"这回事。
- **`@MapperScan(basePackages=...)` 会把包内所有接口注册成 Mapper**：本包还有 `ToolProvider` 这类 SPI 接口，必须加 `annotationClass = Mapper.class` 收窄。
- **表名与语义要成对设计**：工具审计存在两个视角（运行时 / 编排），混用一张表会既缺 `runId` 又缺限流熔断状态；拆表时必须在 JavaDoc 里写清边界，否则下一个人一定会合并它们。

### M3 阶段（人工审批 suspend / resume）

- 实施日期：2026-10-20（同日，M3 完成）
- 目标：图执行到 `human` / `manual` 节点时挂起并释放线程，外部携带裁决与令牌恢复，**不重跑已完成节点**。
- 实际改动清单：

| 文件 / 模块 | 改动 |
| --- | --- |
| `.../graph/GraphApproval.java` | 新增：审批决策值对象（approved/comment/operatorType/operatorId/resumeToken/form）+ `toPayload()` 落节点输出 |
| `.../graph/GraphState.java` | 修改：新增 `pendingNodeId`/`pendingMeta` 并纳入 checkpoint；`pending()/clearPending()` 成对清理挂起现场 |
| `.../graph/GraphDefinition.java` | 新增：`branchTargetExact()`（严格分支，不回退普通出边）与 `approvalTarget()`（approveTarget/rejectTarget → 分支边 → 普通出边/失败） |
| `.../graph/GraphNodeExecutor.java` | 修改：`human`/`manual` 节点产出挂起载荷（提示语渲染 + `requireComment` + `form`），`NodeOutcome.suspend(payload)` |
| `.../graph/GraphEngine.java` | 修改：`suspend()` 记录挂起现场、令牌不落日志；`advance()` 仅正常到达终点才 `finish()`；`resume()` 拒绝挂起态；新增 `approve()`（状态/令牌/节点三重校验 + 决策落库 + 沿决策出口推进） |
| `.../graph/GraphRunService.java` | 修改：新增 `approve()` 与 `pending()`（审批界面数据源），注入 `ObjectMapper` 解析 checkpoint |
| `.../controller/GraphController.java` | 新增：`GET /runs/{id}/pending`、`POST /runs/{id}/approve` |
| `.../autoconfig/AiGraphAutoConfiguration.java` | 修改：`graphRunService` 增加 `ObjectMapper` 注入 |
| `.../test/.../graph/GraphHumanApprovalTest.java` | 新增：8 条单测（批准推进 / 错误令牌 / 挂起态 resume / 重复审批 / 驳回失败 / 驳回分支 / 挂起不被误判成功 / 失败不被误判成功） |
| `tmp/verify/verify-human-approval.mjs` | 新增：28 项真机断言（含 8 个场景与反例对照） |

#### 与方案的偏差（含原因）

| 原方案 | 实际做法 | 原因 |
| --- | --- | --- |
| 前端复用 `CollabController` 审批 UI | 未改前端；新端点请求体与 `collab/tasks/{id}/approve` **同形**（approved/comment/operatorType/operatorId），前端只需换 URL | 复用的是"契约"而不是"代码"：任务审批对象是 `AgentTask`，图审批对象是 `wf_workflow_run`，硬套同一个接口会让两边字段互相污染 |
| 方案未定义 `resume` 与挂起恢复的关系 | `resume()` 明确拒绝 `suspended`，恢复挂起一律走 `approve()` | 从 currentNodeId（human 节点）继续推进会二次挂起并换掉令牌，表现为"恢复后仍挂起、旧令牌失效"，极难排查 |
| 方案未定义驳回无出口的行为 | 驳回且无 `rejectTarget`/`branch=false` 时判 **failed** | 回退普通出边就是"驳回后照常执行"——不报错、只静默做错事 |
| 缺省 `approved` 语义 | 缺省按 **驳回**（fail-closed） | 缺字段默认批准时，前端漏传/拼错字段都会变成自动放行，且无任何报错 |

#### 真机验证结果

- `GraphHumanApprovalTest`：**8/8 通过**。
- `tmp/verify/verify-human-approval.mjs`：**28/28 通过**。关键项：挂起后 pending 返回渲染后的提示语（`请审批订单 A100`）与一次性令牌；错误令牌与普通 `resume` 均被拒且运行仍为 `suspended`；正确令牌批准后 `status=success` 且只执行成功分支；二次审批被拒；驳回无出口 → `failed` 且 `errorMessage` 含驳回原因；驳回有 `branch=false` → 走驳回分支。
- 回归：`verify-graph-engine.mjs` **15/15**、`verify-tool-market.mjs` **19/19**、`verify-graph-crash-recovery.mjs` **10/10**（M3 改动未影响 M1/M2 语义）。

#### 过程中新发现的坑

- **`advance()` 无条件 `finish()` 会把挂起/失败覆盖成 success（存量缺陷，M3 才暴露）**：`suspend()` 与 `fail()` 都靠"返回 null 结束推进"，而循环末尾无条件 `finish()` 会把 `status` 覆盖为 success、写 `finishedAt`。后果是挂起现场被当成"成功结束"，任何审批都会以"运行已结束"被拒；失败同理被静默改成成功。修法是只在 `status == running` 时收尾，并补两条"挂起/失败不得被误判成功"的单测锁死。
- **M1/M2 全绿不代表挂起语义正确**：M1/M2 的验证脚本没有一条用例走到 `human` 节点，`suspended` 分支从未被执行过。凡"新增了分支但没给它写反例"的地方，绿灯都只证明别的分支没坏。
- **令牌不能进运行日志**：原实现把 `resumeToken` 拼进 `manual_wait` 的日志消息，等于把"谁能恢复这次运行"写进审计表；改为日志只记节点名，令牌仅经 `pending` 接口下发。
- **`mock` 的 `insert` 不回填主键**：单测里 `runMapper.insert()` 是 mock，`run.id` 始终为空，`state.runId()`、日志 `runId` 都跟着空。必须用 `thenAnswer` 手动回填自增 ID，否则测试会在"看起来无关的地方"抛空指针。

### M4 阶段（supervisor / worker 图式多智能体，部分失败语义）

- 实施日期：2026-10-20（同日，M4 完成）
- 目标：把「主管动态路由 → 多个 worker → 汇总回执」表达为图的一种编排模式，并保留现有协作的 `conflict` 部分失败语义。
- 实际改动清单：

| 文件 / 模块 | 改动 |
| --- | --- |
| `.../graph/SupervisorAggregator.java` | 新增：worker 回执归一（success / conflict / denied / failed）与汇总（total / succeeded / receipts / conflicts） |
| `.../graph/NodeFanout.java` | 新增：并行节点与主管节点的子节点调度（**两种失败语义分开实现**：parallel 抛异常、supervisor 折成回执） |
| `.../graph/NodeOutcome.java` | 新增：从 `GraphNodeExecutor` 抽出的调度契约；用 `Kind` 枚举替代 4 个互斥布尔位，新增 `supervisor()` / `strategy()` |
| `.../graph/GraphState.java` | 修改：新增 `conflictedNodes` 并纳入 checkpoint（`markConflict/hasConflicts`） |
| `.../graph/GraphDefinition.java` | 新增：`conflictTarget()`（主管冲突时的补偿出口，缺省 null = 带伤继续） |
| `.../graph/GraphNodeExecutor.java` | 修改：新增 `supervisor` 节点类型（读 `children` + `strategy`）；`taskType=conflict` 产出自报冲突回执；`NodeOutcome` 外提后 526 → 394 行 |
| `.../graph/GraphEngine.java` | 修改：新增 `STATUS_CONFLICT` 终态；`handleSupervisor()`（扇出 → 汇总 → 冲突标记 → 出口）；`finish()` 按冲突改终态；`isTerminal()` 纳入 conflict；并行执行下沉到 `NodeFanout` |
| `.../test/.../graph/GraphSupervisorTest.java` | 新增：7 条单测 |
| `tmp/verify/verify-supervisor.mjs` | 新增：24 项真机断言（7 个场景） |

#### 图配置示例

```json
{
  "nodes": [
    {"id": "s",   "type": "start"},
    {"id": "sup", "type": "supervisor", "config": {
        "children": ["wA", "wB"],
        "strategy": "parallel",
        "conflictTarget": "fix"
    }},
    {"id": "wA",  "type": "agent", "config": {"agentId": "a-001", "prompt": "分析财报"}},
    {"id": "wB",  "type": "agent", "config": {"agentId": "a-002", "prompt": "核对口径"}},
    {"id": "fix", "type": "task",  "config": {"prompt": "生成差异清单交人工裁决"}},
    {"id": "e",   "type": "end"}
  ],
  "edges": [
    {"source": "s", "target": "sup"},
    {"source": "sup", "target": "e"},
    {"source": "fix", "target": "e"}
  ]
}
```

#### 与方案的偏差（含原因）

| 原方案 | 实际做法 | 原因 |
| --- | --- | --- |
| `SupervisorNode` 与 `WorkerNode` 各自独立节点类型 | 只有 `supervisor` 一种新节点类型；worker 就是**图里已有的节点**（`task` / `agent` / `tool` 任意） | 若再定义 `worker` 类型，就只剩 `agent` 一种实现，等于给 `agent` 节点换个名字；让 worker = 普通节点，才能"主管编排任意能力"，也不必在节点执行器里加分支 |
| 冲突时图判失败或抛异常 | 冲突**默认带伤继续**（沿普通出边），只标终态 `conflict`；需要补偿支路才配 `conflictTarget` | 与 `MultiAgentCollaborationService` 一致（子任务失败 → 父任务 conflict、其余结果保留、等人工处理）。当场抛出会让主管已拿到的兄弟结果失去复核价值 |
| 「现有协作迁移到图模式」 | 存量 `MultiAgentCollaborationService`（工单式）**不动**；提供映射规则 + 图模板（见上例） | 工单表（`agent_task`/`agent_task_flow`）承载审批链、转交、委派回调等状态机，硬迁到图会丢这些语义；先并存，待有真实业务要"动态路由"时再按图重写 |
| （未写）worker 失败如何判定 | 回执状态由**输出推导**：`denied` → denied、`failed`/`guarded` → failed、`conflict` → conflict，其余 success | 让失败判定只有一处；否则各类节点（agent/tool/task）会各自定义什么算失败，最终出现"工具没权限却算成功" |

#### 迁移映射规则（工单式 → 图式）

| 工单式协作 | 图式等价物 |
| --- | --- |
| 父任务（`AgentTask` 树根） | `supervisor` 节点 |
| 子任务（父任务的一级子节点） | supervisor `config.children` 指向的 worker 节点 |
| 子任务执行体（`agentId` + `instruction`） | `agent` 节点的 `config.agentId` / `config.prompt` |
| 子任务失败/驳回 → 父任务 `conflict` | 回执非 success → 主管汇总 conflict → 图终态 `conflict` |
| `needsApproval` 审批 | `human` 节点（M3）插在 `sup` 与 `e` 之间 |
| `resolveConflict(approved/rejected/retry)` | `human` 审批的 approved / rejected 分支 |

#### 真机验证结果

- `GraphSupervisorTest`：**7/7 通过**（全成功 / 单失败 / 自报冲突 / 补偿分支 / 非法 worker / 串行 / 终态不可续跑）。
- `tmp/verify/verify-supervisor.mjs`：**24/24 通过**。关键项：
  - 全成功 → `status=success`、`receipts=2`、`conflicts=[]`；
  - 一个 worker 失败 → `status=conflict`（**不是** `failed`），兄弟 worker 输出 `wA.result=A` 仍保留，`succeeded=1`，`conflicts[0].nodeId=wB`；
  - worker 自报冲突 → `conflicts[0].status=conflict`，成功计数不受影响；
  - 配 `conflictTarget` → `fix` 节点执行，终态**仍** `conflict`；
  - worker 用真实 `agent` 节点（`not_exists_agent_m4`）→ 回执 `denied`、节点输出含 `error` 原因，证明走的是真闸门而不是桩；
  - `sequential` → `w2.result=A-B`（后一个 worker 读到前一个输出）；
  - `conflict` 运行 `resume` 被拒（终态）。
- 全量 harness 单测 **143/143**；回归 M1 **15/15**、M2 **19/19**、M3 **28/28**。

#### 过程中新发现的坑

- **`parallel` 与 `supervisor` 不能共用一套失败处理**：最初想把两者合并成"扇出后统一 try/catch"，但它们的语义相反 —— 并行节点子节点失败应让整段停下（流水线），主管节点的 worker 失败必须只影响自己（多智能体 + 人工裁决）。合并后无论选哪种，都会有一侧被静默改变。最终拆成 `NodeFanout` 里两个方法，把差异写在类注释的对照表里。
- **回执状态只能在一处推导**：若让 agent 节点自己判"我算不算失败"、tool 节点再判一次，迟早出现"工具返回 guarded（无权限）却被记成成功"。现在统一由 `SupervisorAggregator.statusOf()` 推导，`guarded` 归入 `failed`。
- **测试里 `outputJson` 是"根对象"不是"nodeOutputs"**：断言 `outputs.path("w2")` 拿到空节点、`asText()` 返回 `""`，看起来像"渲染失败"，实际是取错层级。断言输出时先 `.path("nodeOutputs")`，否则会被空字符串误导。
- **`NodeOutcome` 内嵌导致节点执行器越界**：M4 加两个节点类型后 `GraphNodeExecutor` 涨到 526 行（超 500 上限）。把调度契约外提为顶层类后 394 行 —— 顺带把 4 个互斥布尔位换成 `Kind` 枚举，"同时为真"的非法状态从此不可表示。

### 收尾阶段（MySQL DDL 与方言兼容）

- 实施日期：2026-10-21
- 目标：补齐方案遗留的「MySQL DDL 脚本」，同时让 `@PostConstruct` 建表在 MySQL 上真的能跑通。
- 起因：`AGENTS.md` 明确要求"数据库结构变更使用模块化、可审计的 MySQL 版本脚本
  `V<version>__<description>.sql`"，并要求运行/开发/验证只面向 MySQL（仅 agent-memory 的 H2 例外）。
  而实际工程跑在 SQLite 上，建表全靠 Java 自举，既不可审计、也没有 MySQL 版本。

#### 过程中发现的两个真实缺陷

| 缺陷 | 现象 | 根因 |
| --- | --- | --- |
| `CREATE INDEX IF NOT EXISTS` 发给 MySQL | 切库后**应用启动直接失败**（Error 1064） | MySQL 不支持索引级 `IF NOT EXISTS`。而该语句在 SQLite 上完全合法且幂等，本地开发永远看不到 |
| `observ_session_event` 建表是纯 SQLite 写法 | 同上，启动失败 | `INTEGER PRIMARY KEY AUTOINCREMENT` 与 `CLOB` 在 MySQL 分别是语法错和未知类型 |

两条都属同一类错误：**把"本方言语义"当成"SQL 语义"**。它们不会在任何 SQLite 用例里显形，
只会等到第一次切生产库时集中爆发。

#### 实际改动清单

| 文件 / 模块 | 改动 |
| --- | --- |
| `.../ai/schema/SchemaDdl.java` | 新增：方言探测（MySQL/MariaDB 归一，探测失败回落 SQLite）+ `ensureIndex()`（MySQL 先查 `information_schema.statistics` 再建，SQLite 用 `IF NOT EXISTS`）+ 纯函数 `createIndexSql()` |
| `.../ai/tool/ToolSchemaInitializer.java` | 修改：改用 `SchemaDdl`，删除自带的 `isMysql()`；补建 `idx_graph_tool_log_run (run_id, node_id)`；MySQL 常量改为包可见供一致性测试比对 |
| `.../ai/observ/SessionEventLogServiceImpl.java` | 修改：建表拆成 `SQLITE_DDL` / `MYSQL_DDL` 两份并走方言分支，索引交给 `SchemaDdl` |
| `.../ai/workflow/WfWorkflowRun.java` | 修改：`status` 注释补齐图引擎 5 个终态（含 M3 的 `suspended`、M4 的 `conflict`） |
| `resources/db/module-agent-harness/V1.0.0__graph_runtime_tool_tables.sql` | 新增：`ai_tool_meta` + `ai_graph_tool_invoke_log` 的 MySQL 8.0 版本脚本（含列注释、索引、`information_schema` 判存的补丁段） |
| `resources/db/module-agent-harness/V1.0.1__observ_session_event.sql` | 新增：`observ_session_event` 的 MySQL 版本脚本 |
| `test/.../ai/schema/SchemaDdlTest.java` | 新增：8 条单测（含"MySQL 不得带 IF NOT EXISTS"负向断言、索引存在/不存在/计数为 null 三条分支、方言判定、无数据源回落） |
| `test/.../ai/schema/DdlColumns.java` | 新增：测试辅助，把 DDL 解析成「列名 + 类型」二元组 |
| `test/.../ai/tool/HarnessMysqlDdlParityTest.java` | 新增：3 条，自举 DDL 与版本脚本**逐列**比对 + 禁 SQLite 关键字 |
| `test/.../ai/observ/ObservMysqlDdlTest.java` | 新增：2 条，同上（observ 表） |

#### 与方案的偏差（含原因）

| 原方案 | 实际做法 | 原因 |
| --- | --- | --- |
| 「待补 MySQL DDL 脚本」只提到补脚本 | 同时改了 Java 自举的方言分支 | 只补脚本不够：`@PostConstruct` 仍然会先跑一遍 SQLite 语句，跑到 MySQL 上照样启动失败。脚本是给 DBA 的审计路径，自举是运行路径，两条都得通 |
| （未写）表结构存在两处副本如何防漂移 | 加 `HarnessMysqlDdlParityTest` 逐列比对 | 两份内容都能跑，只改一边的症状是"本地好好的、生产缺列"，报错点（insert 时 unknown column）离改动点很远。测试把漂移前移到编译期 |
| （未写）observ 表是否纳入范围 | 纳入 | 它是同一类启动失败，且就在同一模块；只修工具表等于留一颗同型号的雷 |
| （未写）脚本放哪 | `resources/db/module-agent-harness/` | 遵循 `AGENTS.md` 标准目录（`db/module-<domain>/V<version>__<desc>.sql`）。仓库里 `agent-application/src/main/resources/db/{local,mysql}` 是运行时占位目录，不承担迁移脚本职责 |

#### 验证结果

- `SchemaDdlTest` **8/8**、`HarnessMysqlDdlParityTest` **3/3**、`ObservMysqlDdlTest` **2/2**。
- 全量 harness 单测 **156/156**（M4 时 143，新增 13）。
- **负向对照（鉴别力审计）**：故意在 `MYSQL_TOOL_META` 常量里插入一列 `drift_col VARCHAR(8)`，
  `HarnessMysqlDdlParityTest` 立刻变红并在失败消息里打出该列 —— 证明这条守卫不是恒真断言。撤回后重跑全绿。
- 真机：重建 jar（237MB）后启动 `status=UP`，SQLite 下三张表的索引全部就位；
  **`idx_graph_tool_log_run` 是本次新增的**，它出现在真实库里即证明 `SchemaDdl.ensureIndex()` 生效；
  另外两个已存在的索引被重复 ensure 未报错，证明幂等。
- 回归：`verify-graph-engine` **15/15**、`verify-tool-market` **19/19**、
  `verify-human-approval` **28/28**、`verify-supervisor` **24/24**（合计 86 项真机断言）。

#### 未完成 / 已知边界

- **MySQL 侧未做真机验证**：本机没有 MySQL 实例（3306 未监听、无 mysql CLI、无 Docker）。
  上述 MySQL 正确性的依据是「MySQL 8.0 语法 + `information_schema` 判存逻辑 + 逐列一致性测试」，
  不是实机跑过。接了真实 MySQL 后应先跑这两份脚本，再启动应用确认 `@PostConstruct` 不报错。
- **全量库表仍无 MySQL 基线**：本次只覆盖 harness 新增/改建的 3 张表。
  其余 60+ 张表（`wf_*`、`ai_*`、`rag_*`、`sec_*` 等）仍是 SQLite 自举建的，切库需要另立任务做完整基线导出。
