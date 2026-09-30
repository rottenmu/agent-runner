# 为现有 L0 业务记录接入 TrajectoryRecorder 来源标注

> 状态：已落地
> 起草日：2026-09-29
> 关联模块：`modules/agent-memory`
> 规范依据：`docs/rules/PLAN_DOC_RULES.md`

---

## 一、置信度与剩余风险

- 当前置信度：84%
- 主要剩余风险：会话消息与记忆变更由不同服务写入，来源命名或异常处理若不统一，会出现分类不一致或主流程受 L0 写入影响。

---

## 二、目标

本次要达成：

- 让 `AiConversationMemory` 的对话消息 L0 记录经 `TrajectoryRecorder` 写入，并标记 `user_message`、`assistant_message`；会话 fork 产生的副本沿用对应来源。
- 让 `AiMemoryService` 的记忆变更 L0 记录经 `TrajectoryRecorder` 写入并标记 `memory_write`；沿用服务现有脱敏后的内容。
- 保持 `MemoryAwarePromptBuilder` 已有的 `context_injection` 来源与元数据行为。
- 统一由 `TrajectoryRecorder` 承担 L0 轨迹写入失败的隔离，避免记录失败阻断对话记忆和四层记忆主流程。

本次明确不做（延后项见第九节）：

- 不启用 `system_prompt`、`chain_of_thought`、`tool_call`、`tool_result`、`sub_agent` 等事件的内容采集；不增加这些事件的持久化范围。
- 不新增数据库字段、OLAP 列、接口协议或保留期限配置；`source` 列已在 D2 落地。

---

## 三、成功标准

本方案完成后，以下每一条都应能在真机上直接判定为真：

1. 对隔离仓储调用 `AiConversationMemory.appendTurn()` 并完成一次 OLTP→OLAP 同步后，恰有一条 `role=user, source=user_message` 和一条 `role=assistant, source=assistant_message`；fork 后角色与来源对应，Arrow 重载后记录不变。
2. 平台 `/api/biz/ai/chat` 的 Controller 路由在 stub `AiAgentService` 下返回一轮回复，并恰好调用一次 `ChatTurnMemoryRecorder.record()`，参数含同一会话、用户消息和最终回复；此入口通过 MCP 记为一条 `system/memory_write`，正文同时含 `[user]` 与 `[assistant]`。
3. 启用记忆预召回并完成一轮对话后，查询结果中上下文注入记录的 `source=context_injection` 和元数据保持现状，用户与助手消息仍各只有一条。
4. 通过 MCP `memory_write` 写入一条含可识别敏感模式的记忆后，L0 中对应记录的 `source=memory_write`，记录正文与记忆读取结果均保留 `AiMemoryService` 现有脱敏形态，不出现输入中的完整敏感值。
5. 暂停或模拟 L0 仓储写入失败后，聊天请求仍成功返回，记忆写入接口仍按原业务结果返回；日志中出现一条可定位的轨迹写入失败记录。

---

## 四、参照行为（对标）

无。本方案只复用本仓库已有的 `L0RawLog.source`、`TrajectoryRecorder` 和来源查询能力。

---

## 五、现状差距

### 能力缺失

- `TrajectoryRecorder` 有单元测试但没有生产调用方；已实现的来源分类无法覆盖常规对话与记忆变更记录。
- `AiConversationMemory.recordTurnEvent()` 和 fork 直接调用仓储并使用不带 `source` 的 `forInsert`；分析端收到的来源值为空。
- `AiMemoryService` 把已脱敏的记忆变更写入 L0 时也未填 `source`；L0 写入异常可能穿透到记忆业务流程。

### 只是没接线

- L0 模型已支持 `source`；D2 已把该字段传到 Arrow/Calcite 和消息、会话消息、trace 响应。
- `TrajectoryRecorder.record(...)` 已能按来源写入 L0，并在失败时隔离异常；现有生产对象都已经持有相应的 L0 仓储。
- `MemoryAwarePromptBuilder` 已经直接记录 `context_injection`，并附带召回元数据；本次只需保留并回归验证该行为。

### 不打算解决的问题

- 不采集额外模型上下文或工具载荷；这些数据的访问边界、脱敏规则和保留政策未定义，留待单独评审。
- 不处理 D15 的 `user_id` 归属缺失；它影响统计隔离语义，和本次来源分类相互独立。

---

## 六、技术方向

### 1. 复用 TrajectoryRecorder 记录会话事件

职责：

- 在会话消息追加及 fork 复制的现有 L0 写入点调用 `TrajectoryRecorder`，保留 trace、session、user、role、content 与时间信息。
- 为 user/assistant 两类消息固定来源值；仓储为空时继续支持纯内存模式。

关键接口 / 数据结构：

```text
user     -> source=user_message
assistant -> source=assistant_message
TrajectoryRecorder.record(sessionId, userId, traceId, role, source, content, metaJson)
```

取舍理由：`source` 已进入 OLAP 数据链路，通过现有 recorder 标记事件即可查询分类；不在会话服务中再造一套 L0 写入逻辑。

### 2. 标记四层记忆变更来源

职责：

- 将 `AiMemoryService` 现有 L0 记忆变更记录改由 recorder 落盘并填 `source=memory_write`。
- 仅传递该服务已脱敏的记录正文，不把原始输入交给 recorder。

关键接口 / 数据结构：

```text
role=system, source=memory_write, content=<AiMemoryService 当前脱敏后的内容>
```

取舍理由：来源可区分对话与记忆写入，同时不扩大现有记录内容；L0 故障由 recorder 隔离，避免改变记忆主流程结果。

### 3. 保留上下文注入的既有路径

职责：

- 保持 `MemoryAwarePromptBuilder` 写入 `context_injection` 与召回元数据的现有实现。
- 通过回归验收确认一次注入只形成一条该来源记录。

关键接口 / 数据结构：

```text
role=system, source=context_injection, meta_json=<召回条数、算法与阈值>
```

取舍理由：该生产路径已经接线并且元数据对溯源有用；替换它会增加无关风险。

---

## 七、里程碑

### M1. 对话事件来源接线

- `appendTurn` 和 `fork` 产生的 L0 消息由 `TrajectoryRecorder` 写入并带来源。
- 验证：隔离 L0/OLAP 查询分别得到一条 user、一条 assistant 记录，来源完全匹配。

### M2. 记忆变更来源接线

- `AiMemoryService` 的 L0 变更记录统一标记 `memory_write`，正文沿用已脱敏值。
- 验证：MCP 写入后按来源查询为 1 条，敏感测试值不以完整形式出现。

### M3. 主流程故障隔离与既有路径回归

- L0 写入失败不影响聊天、记忆读写；`context_injection` 来源和元数据保持不变。
- 验证：故障注入调用与启用预召回的端到端请求均符合第三节阈值。

---

## 八、自测计划

### `AiConversationMemory` 来源

1. 在隔离 H2 上调用 `AiConversationMemory.appendTurn()`，执行 OLTP→Arrow 同步后查询 `olap_l0_log`。
2. 验证恰有 `user/user_message/1` 与 `assistant/assistant_message/1`；重启后重复查询，行数和来源一致。
3. 对测试会话执行 fork，再查询子会话；验证复制的 user/assistant 记录各 1 条，来源与角色对应。

### 平台聊天入口

1. 用 MockMvc 调用 `/api/biz/ai/chat`，stub `AiChatPreflight` 与 `AiAgentService`，不请求真实模型。
2. 验证 HTTP 响应含 stub 回复，`ChatTurnMemoryRecorder.record()` 恰好调用一次，参数中的用户、租户、会话及回复正确。
3. MCP HTTP `memory_write` 单独验收已覆盖 `system/memory_write`、脱敏、OLAP 查询和 Arrow 重启恢复。

### 记忆变更与脱敏

1. 使用测试租户通过 MCP `memory_write` 写入包含测试手机号的用户记忆。
2. 查询该次 trace 的 L0 记录，并调用记忆读取接口。
3. 验证 L0 行 `source=memory_write`，两个正文都只有掩码形态，均不包含输入的完整手机号。

### 故障隔离和上下文注入

1. 在隔离测试仓储中令 `saveRawLog` 抛出运行时异常，分别执行一轮聊天记忆追加和一次记忆写入。
2. 验证两条业务调用正常返回，且日志各有可定位的轨迹写入失败记录。
3. 启用预召回并发起一轮会话，按 session 查询 L0；验证上下文注入来源仍为 `context_injection`、元数据非空，且 user/assistant 消息没有重复行。

---

## 九、已知延后项

- 系统提示词全文采集 —— 可能包含密钥、内部策略和动态上下文，需先确定清理与权限策略。
- 思维链采集 —— 可能写入模型内部推理内容；本次不接入，需先单独确认产品与治理边界。
- 工具参数、结果和子 Agent 任务/结果全文 —— 可能包含业务数据或凭据；需明确逐类 allowlist、脱敏和长度上限后再设计。
- 删除记忆对应的 L0 事件来源 —— 当前 `AiMemoryService` 删除路径没有统一审计记录；是否新增删除事件留待审计需求评审。

---

## 十、实施顺序

1. 为 `TrajectoryRecorder` 补充并固定会话消息与记忆变更的来源常量，保留既有异常隔离语义。
2. 将 `AiConversationMemory` 的追加和 fork L0 写入接到 recorder；将 `AiMemoryService` 的现有变更 L0 写入接到 recorder。
3. 补齐来源、脱敏、故障隔离和 context injection 回归用例；执行模块定向验证。
4. 更新后端代码说明中的 D1 状态、来源分类及剩余延后项，并回填本方案实施记录。

---

## 十一、实施记录

- 实施日期：2026-09-29
- 实际改动清单：

| 文件 / 模块 | 改动 |
| --- | --- |
| `agent-memory-core/src/main/java/com/zimo/module/agentmemory/memory/TrajectoryRecorder.java` | 新增 `SOURCE_MEMORY_WRITE` 来源常量，并在来源说明中登记记忆变更事件。 |
| `agent-memory-core/src/main/java/com/zimo/module/agentmemory/chat/AiConversationMemory.java` | 对话追加和 fork 改由 `TrajectoryRecorder` 写入；user/assistant 行分别携带 `user_message` / `assistant_message`，L0 写入失败不再中断会话追加或 fork。 |
| `agent-memory-core/src/main/java/com/zimo/module/agentmemory/memory/AiMemoryService.java` | 会话变量和用户记忆的 L0 行经 `TrajectoryRecorder` 写入，统一标记 `memory_write`；传入内容仍是服务脱敏后的值。 |
| `agent-memory-core/src/test/java/com/zimo/module/agentmemory/chat/AiConversationMemoryEventLogTest.java` | 覆盖对话追加、fork 的来源值和 L0 故障隔离。 |
| `agent-memory-core/src/test/java/com/zimo/module/agentmemory/memory/AiMemoryServiceTrajectoryTest.java` | 覆盖记忆变更来源、手机号脱敏和 L0 故障时 L1 仍落库。 |
| `agent-memory-core/src/test/java/com/zimo/module/agentmemory/sync/TrajectorySourcePipelineTest.java` | 使用隔离 H2 执行真实业务写入与 OLTP→Arrow 同步，检查来源、脱敏正文和 Arrow 重载。 |
| `modules/agent-harness/agent-harness-core/src/test/java/com/zimo/module/ai/controller/AiChatControllerMemoryTest.java` | 不调用模型，通过 MockMvc 验证聊天 Controller 把最终回复和会话维度交给记忆记录器。 |
| `modules/agent-harness/agent-harness-core/src/test/java/com/zimo/module/ai/memory/ChatTurnMemoryRecorderTest.java` | 覆盖异步 MCP 写入参数及正文格式、停用/空会话跳过，以及记忆服务异常不影响聊天调用。 |
| `agent-memory-application/src/main/java/com/zimo/agentmemory/app/AgentMemoryApplication.java` | 修正独立应用启动说明，注明 Maven fork JVM 与独立 JAR 所需的 Arrow `java.nio` 开放参数。 |
| `docs/agent-memory-backend-code-description.md` | 更新 D1/D2 状态与描述；D15 仅保留 user_id 归属缺失。 |

### 与方案的偏差

| 原方案 | 实际做法 | 原因 |
| --- | --- | --- |
| 经 `/api/biz/ai/chat`、MCP `memory_write` 和独立运行实例逐条完成验收。 | MCP 与独立应用按下文完成真实 HTTP 验收；聊天 Controller 另由 MockMvc 和 stub 服务验证回复后恰好调用一次 `ChatTurnMemoryRecorder`。代码检查确认该 HarnessAgent 入口经 MCP 记录一条合并的 `system/memory_write`，`AiConversationMemory` 的 `user_message` / `assistant_message` 属于独立的旧版 `legacyChat` 路径。未请求真实模型或运行完整平台应用。 | 用 stub 验证 Controller 委托，避免调用外部模型；应用级模型运行及预召回仍需真实模型依赖。独立应用验收需使用 `--add-opens=java.base/java.nio=ALL-UNNAMED`，否则 Arrow 导出未生成文件。 |
| `TrajectoryRecorder` 的所有来源常量都由本次新增。 | `user_message` 与 `assistant_message` 常量原已存在，本次只新增 `memory_write`。 | 代码检查确认已有定义，无需重复新增。 |
| 标准聊天端点会把一轮用户/助手消息作为两个角色事件写入 L0。 | HarnessAgent 主聊天路径由 `ChatTurnMemoryRecorder` 经 MCP 写成一条 `system/memory_write` 会话变量；两条分角色事件仅由 `legacyChat` 使用的 `AiConversationMemory.appendTurn()` 产生。 | 代码追踪发现原标准描述与实际路由不符，已按当前调用链修正成功标准和验收步骤。 |

### 真机验证结果

- `mvn -pl agent-memory-core '-Dtest=AiConversationMemoryEventLogTest,AiMemoryServiceTrajectoryTest,AiMemoryServiceTest,MemoryAwarePromptBuilderTest,TrajectoryRecorderTest,TrajectorySourcePipelineTest,AsyncLogSyncTaskTest,ArrowOlapAnalyticsRepositoryQueryTest,AgentMemoryAnalyticsControllerTest' test`：66 例通过，0 失败，0 错误。
- `mvn -pl modules/agent-harness/agent-harness-core -am '-Dtest=AiChatControllerMemoryTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`：1 例通过，0 失败，0 错误；覆盖正常聊天 Controller 在模型回复后将数据传给记录器。
- `mvn -pl modules/agent-harness/agent-harness-core -am '-Dtest=ChatTurnMemoryRecorderTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`：3 例通过，0 失败，0 错误；覆盖异步写入契约及失败隔离。
- `TrajectorySourcePipelineTest` 中 `AiConversationMemory.appendTurn()` 产生 `user/user_message`、`assistant/assistant_message` 各 1 条；`AiMemoryService.saveUserMemory()` 产生 `system/memory_write` 1 条。增量同步后查询恰有 3 条，Arrow 文件重载后来源仍完全一致。
- 同一 OLAP 记录中的测试手机号为 `138****5678`，未包含完整输入值 `13812345678`。
- L0 故障用例中会话仍保留 2 条消息，用户长期记忆仍成功写入 1 条 L1；日志输出 `user_message`、`assistant_message` 和 `memory_write` 三类轨迹失败警告。
- `mvn -pl agent-memory-application -am -DskipTests package` 成功；独立应用以隔离临时目录启动后，MCP `tools/list` 返回 4 个工具，`tools/call(memory_write)` 返回 `138****5678` 且 `sensitiveMasked=true`。
- 临时 5 秒 ETL 周期后，`/api/agent-memory/analytics/messages` 查到 `system/memory_write` 行，正文为 `SESSION_VAR:contact=测试联系电话 138****5678`；`l0_log.arrow` 与游标文件均已生成。
- 停止并重启独立应用后，Arrow 从文件加载 1 行；再次查询仍返回 `source=memory_write` 和脱敏正文。
- 独立应用启动必须为 Arrow 开放 `java.nio`（本次使用 `--add-opens=java.base/java.nio=ALL-UNNAMED`）；未带该参数的首次尝试没有生成 Arrow 文件，故未计为通过。
- `AiChatControllerMemoryTest`（MockMvc + stub `AiAgentService`）验证 `/api/biz/ai/chat` 返回回复并将最终正文、用户、租户及会话交给 `ChatTurnMemoryRecorder` 一次；不调用模型。
- `ChatTurnMemoryRecorderTest` 验证记录器向 MCP 传入 `target=session`、会话/用户/租户标识、独立 key 及 `[user]` / `[assistant]` 正文；记录开关关闭或会话为空时不写入，MCP 异常留在异步旁路中。
- 平台聊天的真实 HarnessAgent/模型集成与预召回链路未启动验证；MCP 存储端点及其脱敏、ETL、Arrow 重启恢复已由独立应用真实 HTTP 验收覆盖。

### 过程中新发现的坑

- `AiConversationMemory` 仍依赖 `L0RawLog` 读取类型（`restore()`）；将写入切到 recorder 时保留该 import，避免编译失败。
- 会话变量现有 L0 映射把 `tenantId` 放在原始日志 `user_id` 位置；本次为保持既有字段语义，传入 recorder 时继续沿用该值，归属字段治理留给 D15。
