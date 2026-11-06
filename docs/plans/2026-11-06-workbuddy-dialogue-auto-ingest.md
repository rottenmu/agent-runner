# 让 WorkBuddy 每一轮问答自动进入 agent-memory 的 L0（jsonl 增量导入器）

> 状态：已全部落地（M1 后端通道 / M2 导入器 / M3 定时自动化；M3 频率因平台限制由「每 5 分钟」降级为「每小时」，见第十一节）
> 起草日：2026-11-06
> 关联模块：`modules/agent-memory/agent-memory-core`（MCP 工具 + 记忆服务）、`modules/agent-memory/tools`（新建导入器）、外部数据源 `~/.workbuddy/projects/<project>/<sessionId>.jsonl`
> 规范依据：`docs/rules/PLAN_DOC_RULES.md`

本文件上半部分是方案，第十一节是已回填的实施记录与偏差（方案确认后 M1/M2/M3 均已实施）。触发原因：2026-11-06 18:20 用户提问「删除 module-security 模块对项目有什么影响吗」，事后发现只写入了结论摘要 `analysis.module-security-deletion`，**问答原文没进 agent-memory**，只能从宿主会话记录手工补录。根因是 MCP 没有逐轮自动落库能力，`memory_write` 必须模型显式调用。

---

## 一、置信度与剩余风险

- 当前置信度：82%
- 主要剩余风险：`AsyncLogSyncTask` 每分钟才把 H2 L0 同步到 Arrow OLAP，而前端/`/analytics/messages` 查的是 OLAP 宽表；若 ETL 因 Arrow 文件锁或启动参数缺失卡住，会出现「L0 已写入但查询恒为 0」，被误判成「又没存进去」。

---

## 二、目标

本次要达成：

- WorkBuddy 宿主每一轮问答（`user_query` 原文 + 该轮全部 `assistant` 输出文本）**自动、幂等**写入 agent-memory 的 **L0 RawLog**，`role=user/assistant`、`source=user_message/assistant_message`，同一轮共用 `trace_id = conversationRequestId`。
- 不再依赖「模型自觉调用 `memory_write`」；数据源改为宿主已全量落盘的 jsonl，导入器离线扫描，失败不阻断对话。
- 支持对既有会话做全量补录与按时间增量导入；默认只导入 `d-codehub-agent_runner` 一个项目，其他项目需显式白名单。

本次明确不做：

- 不改 WorkBuddy 宿主代码、不依赖宿主 hook（宿主没有 post-response hook，拿不到 AI 回答）。
- 不导入 `reasoning` / `function_call` / `function_call_result`（体量与噪声，见第九节）。
- 不改四层模型、不改表结构、不改前端页面、不动主应用的 `ChatTurnMemoryRecorder`。
- 不追求实时（< 1s）写入；本期是「定时/手动触发的准实时」。
- 不删除任何已写入的 L0 数据（append-only）。

---

## 三、成功标准

每条都能在真机上用一次操作判真伪：

1. 执行 `python ingest_workbuddy_turns.py --project d-codehub-agent_runner --session f8da8a70-ef02-4e04-ab62-0af32ba10758 --dry-run` → 输出 `eligible` 计数，且 `conversationRequestId=01a1b05f828e75c986ebcaa407a1d41b` 这一轮被识别为 **1 条 user + 3 条 assistant**（2 条过程说明 + 1 条最终回答），不把 `<conversation_history_summary>` 算进去。
2. 去掉 `--dry-run` 执行后返回 `inserted=N, skipped=0, failed=0`；等待 ≤ 90s 后请求 `GET /api/agent-memory/analytics/messages?sessionId=f8da8a70-ef02-4e04-ab62-0af32ba10758&size=500` → `total` 相对执行前增加 **恰好 N**，新增行的 `source` 只出现 `user_message` 与 `assistant_message`，且该轮首条 `content` 与 `删除 module-security模块对项目有什么影响吗` 一致（脱敏后）。
3. 同一命令**连跑两次**，第二次输出 `inserted=0, skipped=N`，且 `total` 与第一次结束后完全相同。
4. 删除导入器 state 文件后重跑全量 → 仍输出 `inserted=0, skipped=N`，`total` 不变（证明服务端二次判重生效，不只靠本地文件）。
5. 构造一条含 `13812345678` 与 `a@b.com` 的测试消息导入 → OLAP 中该行 `content` **不含**这两个原文，分别出现 `138****5678` 与 `***@***`。
6. 新发一轮对话，**不做任何手工记忆写入**，触发一次导入（或等一个调度周期）→ `total` 增加 ≥ 2，且包含该轮用户提问文本。
7. `tools/list` 返回 **5** 个工具且含 `memory_ingest_turns`；对 `memory_ingest_turns` 传 `turns` 为空数组 → 返回 JSON-RPC `result` 内 `error` 字段，HTTP 状态为 200，不出现 500。
8. 停掉 9900 服务、用 `run.sh` 重启后再执行一次导入 → `total` 不重复增长（H2 文件持久化 + 判重仍生效）。

---

## 四、参照行为（对标）

| 参照项 | 它怎么做 | 我们要复刻到什么程度 |
| --- | --- | --- |
| `agent-harness` 的 `ChatTurnMemoryRecorder` | 旁路异步写、队列满丢弃、连续失败升级 ERROR、每轮独立 key，`saveSessionVar` 是追加语义 | 复刻「记录失败绝不阻断主流程」「计数可观测」「同 key 不覆盖」；**不复刻**写 session 变量（本方案写 L0） |
| `MemoryAwarePromptBuilder.afterReply()`（:260-286） | 一轮写两条 L0：`user` + `assistant`，共用同一 `traceId` | 复刻「双条写入 + 同一 traceId」的语义；不复刻其内存队列（导入器是离线批处理） |
| `AsyncLogSyncTask` | 按 L0 自增 id 游标增量同步到 Arrow | 复刻「不动 ETL、靠现有同步让前端可见」；接受 ≤ 60s 延迟 |

---

## 五、现状差距

### 能力缺失

- MCP 只有 `memory_write/read/delete/search` 四个工具（`MemoryMcpToolkit.listTools():51-103`），**没有批量写对话原文的入口**；`AiMemoryService` 的三个写入分支只产生 `SESSION_VAR:` / `USER_MEMORY:` / `GLOBAL_MEMORY:` 的 `role=system` L0 轨迹（`:101-114`、`:203-204`）。
- 宿主 jsonl 与 agent-memory 之间**没有任何导入器**；`ChatTurnMemoryRecorder` 属于主应用后端对话链路，管不到 WorkBuddy 会话。
- `l0_raw_log` 没有 `external_msg_id` 列，**没有天然幂等键**。
- 实测坑（2026-11-06）：`saveSessionVar` 每次生成新 UUID，同 key 再写是**新增副本不是更新**；`memory_read` 的 `id` 就是 key。所以「每轮一条固定 key」的写法会造重复，不能沿用。

### 只是没接线

- `TrajectoryRecorder` 已有 `SOURCE_USER_MESSAGE` / `SOURCE_ASSISTANT_MESSAGE`（`:34-35`）与通用 `record(sessionId,userId,traceId,role,source,content,metaJson)`（`:101-114`）。
- `AiMemoryService` 已持有 `trajectoryRecorder`、`sensitiveFilter`、`sanitizeIfEnabled`（`:65-90`）；`AiMemorySensitiveFilter.sanitize()` 覆盖手机/身份证/银行卡/邮箱/API Key/Bearer/内网 IP 七类。
- `OltpMemoryRepository.listRawLogsByTrace(traceId)` 已存在，可直接用于服务端判重，命中 `idx_l0_trace`。
- MCP 端点已有准入：`MemoryMcpAccessGuard` 未配 token 时仅放行回环，配了则校验 `Authorization`。
- 宿主 jsonl **已全量落盘**：每行有 `id`、`timestamp`、`role`、`content[].type`、`providerData.conversationRequestId`，user 侧含 `<user_query>` 标签；无需改宿主即可拿到完整问答。
- ETL 每分钟同步 L0 → OLAP；`GET /api/agent-memory/analytics/messages` 已在前端使用。

### 不打算解决的问题

- 实时写入：宿主没有回答后钩子，只能定时/手动；延迟按调度间隔计。
- 权限与部门：不在本方案范围（按既定口径不展开）。

---

## 六、技术方向

### 1. 后端：新增 MCP 工具 `memory_ingest_turns`（不新增 Controller、不新增表）

职责：接收一批已结构化的对话消息，脱敏 → 判重 → 逐条写 L0，返回真实计数。

关键接口 / 数据结构：

```text
POST /api/agent-memory/mcp   tools/call   name="memory_ingest_turns"
arguments: {
  sessionId, userId, tenantId?,
  turns: [ { msgId, requestId, ts, role, content } ]   // role ∈ {user, assistant}
}

AiMemoryService.ingestTurns(...) -> { inserted, skipped, failed, traceIds }

每条落库：
  L0RawLog.forInsert(traceId=requestId, sessionId, userId, ts,
      role, sanitizeIfEnabled(content), null, metaJson, source)
  source = role=="user" ? SOURCE_USER_MESSAGE : SOURCE_ASSISTANT_MESSAGE
  metaJson = {"src":"workbuddy","msgId":"<宿主 message.id>","h":"<sha256(msgId|role|content)[:16]>","turn":<序号>}
```

判重：先 `listRawLogsByTrace(requestId)` 取该轮已有行，比对 `meta_json.h`；命中即 `skipped++`。不改 DDL —— 表结构存在 Java 自举与 `resources/db/module-agent-harness/V*.sql` 两处副本且有 parity 测试，加列成本高、收益低。**不把 `msgId` 塞进 `trace_id`**：`traceId` 的语义是「一次智能体交互」，改成消息级会让 `/api/ai/memory/span/{traceId}` 只能查到单条，破坏溯源。

取舍理由（为什么不新增 REST Controller）：`AgentMemoryAutoConfiguration` 里所有 Controller 都是显式 `@Bean`（`:315`、`:329`、`:550`），类上又带 `@RestController`，新增类有重复装配 / `Ambiguous mapping` 风险，还要碰组件扫描边界；走 MCP 只改 `MemoryMcpToolkit` 一个既有类 + `AiMemoryService` 一个方法，**零新增 Bean、零 DDL、零 Controller**，且自动复用现有回环/令牌鉴权。

⚠️ 静默失败风险：`TrajectoryRecorder.record()` 是 `void` 且内部吞异常只打 WARN（`:111-113`），**不能凭「调用没抛错」判定写入成功**。`ingestTurns` 必须逐条写后回查该 `traceId` 的 `h` 集合，回查不到就计入 `failed`，由导入器决定是否推进 state；同时保留 `inserted/skipped/failed` 三计数供对账。

### 2. 导入器：`modules/agent-memory/tools/ingest_workbuddy_turns.py`

职责：离线扫描宿主 jsonl，结构化成 turns，调用上面的 MCP 工具，维护本地游标。

关键行为：

```text
参数：--project <目录名>(默认 d-codehub-agent_runner，白名单) / --session <sid> /
      --since <ISO 时间> / --full / --dry-run / --limit <批大小>
解析：逐行 jsonl，只取 type=="message" && role∈{user,assistant}
      user      → 正则取 <user_query>…</user_query>，剥掉所有 <system-reminder>
      assistant → 取 content[].type=="output_text"，按 timestamp 升序全收
      跳过      → <conversation_history_summary> 压缩摘要、纯 reasoning、function_call*
幂等：data/ingest-workbuddy-state.json 记录已成功 msgId；服务端按 h 二次兜底
失败：单批失败不推进 state、退出码非 0、打印 failed=N；网络用 ProxyHandler({}) 禁代理
输出：scanned / eligible / inserted / skipped / failed 五个计数
```

只打 9900 的 MCP 端点，**不直接写 H2 文件**（独立应用与主应用不得同时写同一 H2 文件）。凭据若配置 `AGENT_MEMORY_TOKEN`，脚本从环境变量读取，不写进仓库或文档。

### 3. 触发方式

- M1/M2 手动执行，先把全量历史补录并验证。
- M3 可选：创建 WorkBuddy 定时自动化，每 5 分钟执行一次导入器（**需用户确认后才创建**，本方案不擅自建）；或保持手动。
- 取舍：不用「每轮让模型调 `memory_write`」—— 本次漏记正是这条路径失效的证据；不用宿主 hook —— 拿不到 AI 回答；不推荐常驻 watcher —— 与既有「不起后台服务」的协作约定冲突。

### 4. 失败分支与回滚

- 导入器失败 / 后端 9900 不可用：不推进 state，下次重跑补上；对话本身不受影响（完全旁路）。
- 单条写入静默失败：由 `failed` 计数暴露，不推进该批 state。
- 回滚：停掉自动化 / 不再运行脚本即停止新增；工具是纯增量，`tools/list` 多一项不影响既有四工具调用；已写入 L0 行按 append-only 保留，不做自动删除。
- 兼容性破坏面：MCP 客户端若缓存了 `tools/list`，新工具可能暂时不可见 → 需刷新/重连客户端；旧四个工具签名与行为不变。

---

## 七、里程碑

### M1. 后端写入通道

- 产出：`MemoryMcpToolkit` 第 5 个工具 + `AiMemoryService.ingestTurns()` + 单元/集成测试（判重、脱敏、空参数、写入失败计数）。
- 验证：`tools/list` 返回 5 个工具；对同一批 turns 调两次，`inserted` 第二次为 0。

### M2. 导入器与全量补录

- 产出：Python CLI + state 文件；把 `f8da8a70-…` 会话历史问答补录进 L0。
- 验证：第三节标准 1~5、7、8 全部通过。

### M3. 定时触发（可选）

- 产出：一条每 5 分钟的自动化，或明确记为「保持手动」。
- 验证：第三节标准 6 通过。

---

## 八、自测计划

### 场景 A：补录既有会话

1. 记录基线：`curl --noproxy '*' "http://127.0.0.1:9900/api/agent-memory/analytics/messages?sessionId=f8da8a70-ef02-4e04-ab62-0af32ba10758&size=1"` → 记下 `total`。
2. 跑 `--dry-run` → 预期 `eligible > 0`、`failed=0`，且终端列出的 `01a1b05f…` 轮为 `user=1 / assistant=3`。
3. 去掉 `--dry-run` 跑一次 → 预期 `inserted=N, skipped=0, failed=0`。
4. 等 ≤ 90s（ETL cron 每分钟）再取 `total` → 预期 `total - 基线 == N`；抽查 `source` 与首条 `content`。

### 场景 B：幂等与脱敏

1. 立刻重跑同一命令 → 预期 `inserted=0, skipped=N`，`total` 不变。
2. 删除 `data/ingest-workbuddy-state.json` 后重跑 → 预期仍是 `inserted=0`，`total` 不变。
3. 用 `--test-sensitive` 注入含 `13812345678` / `a@b.com` 的一条消息 → 预期 OLAP 中脱敏为 `138****5678` / `***@***`，原文不出现。
4. 停 9900 → `run.sh` 重启 → 重跑 → 预期 `total` 不增长。

### 场景 C：新对话自动进入

1. 在 WorkBuddy 发一句带唯一标记的问题（如 `INGEST-PROBE-<随机串>`），**不调用任何记忆工具**。
2. 触发一次导入（手动或等自动化周期）→ 预期 `inserted >= 2`。
3. 等 ≤ 90s 查 `messages` → 预期能检索到该唯一标记，`role=user` 与 `role=assistant` 各至少 1 条。

⚠️ **语义缺陷声明（通过 ≠ 正确）**：`/analytics/messages` 查的是 **OLAP 宽表**，不是 H2 L0。若第 4 步 `total` 不增长，只能说明「ETL 未同步或查询侧异常」，**不能**据此判定「导入失败」；必须先查 `inserted` 计数、`agent-memory-backend.log` 中 ETL 日志与 Arrow 文件状态。反之 `inserted>0` 也不能单独证明前端可见，两个判据都要过。

---

## 九、已知延后项

- `reasoning` / `tool_call` / `tool_result` 导入 —— 原因：单会话 jsonl 已达 9MB / 2226 行，工具结果体量会把 Arrow 宽表撑大且噪声高；`TrajectoryRecorder` 的 source 枚举已预留，后续可按开关增量开启。
- 实时（< 1s）写入 —— 原因：宿主无回答后钩子，只能定时或常驻 watcher，本期取成本最低的定时/手动。
- 其他项目会话 —— 原因：默认白名单只含本项目，避免把无关项目对话灌进同一个 H2 库；需要时按 `--project` 显式授权。
- 前端「对话回放」页 —— 原因：现有 `/analytics/messages` 已能按 sessionId 查 L0 消息，本期只补数据不补 UI。
- L2 场景块生产者/消费者 —— 原因：本就是结构预留，与本方案无关。
- 已写入 L0 的批量删除 / 回滚工具 —— 原因：append-only 语义下删除需人工裁决，避免误删溯源证据。
- 非回环（跨机）访问 —— 原因：需显式配置 `AGENT_MEMORY_TOKEN`，本期脚本固定走 `127.0.0.1`。

---

## 十、实施顺序

1. 用户确认本方案。
2. M1：`AgentMemoryAutoConfiguration` 不动；改 `AiMemoryService` + `MemoryMcpToolkit`，补测试。
3. 用 `./mvnw` 构建 `agent-memory-core`（`classifier=exec` 必须保留），停 9900 → 删旧 exec jar → 用 `run.sh` 重启（含 Arrow 的 `--add-opens` 与 `-Darrow.allocation.manager.type=Netty`）。
4. M2：写导入器，先 `--dry-run` 对账，再全量补录。
5. 按第八节逐条验证；回填第十一节。
6. M3：按用户决定创建（或不创建）定时自动化。

---

## 十一、实施记录

- 实施日期：2026-11-06 18:43 ~ 18:59（M1/M2）；19:02 ~ 19:07（M3 定时自动化，用户第二次「确认」后）
- 实际改动清单：

| 文件 / 模块 | 改动 |
| --- | --- |
| `agent-memory-core/.../memory/DialogueTurnIngestService.java` | **新增**。批量导入 L0：脱敏 → 按 `meta_json.h` 判重 → 写 `L0RawLog` → 写后回查 → `inserted/skipped/failed` 三计数 |
| `agent-memory-core/.../memory/AiMemoryService.java` | 加 1 字段 + 构造装配 + `ingestTurns()` 委托方法（约 12 行），逻辑不进本类 |
| `agent-memory-core/.../mcp/MemoryMcpToolkit.java` | 新增第 5 个工具 `memory_ingest_turns`（含 inputSchema）、`call()` 分支、`ingest()` 参数校验、Javadoc |
| `agent-memory-core/src/test/.../DialogueTurnIngestServiceTest.java` | **新增** 5 个测试：L0 写入语义、跨调用幂等、脱敏、非法入参、MCP 工具暴露 |
| `modules/agent-memory/tools/ingest_workbuddy_turns.py` | **新增** 增量导入器：jsonl 解析、`<user_query>` 提取、state 幂等、`--since/--until/--full/--dry-run/--probe/--test-sensitive`、五计数输出 |
| `modules/agent-memory/tools/data/ingest-workbuddy-state.json` | 运行时产物；`modules/agent-memory/.gitignore` 已有 `data/` 规则，无需新增忽略文件 |
| `modules/agent-memory/tools/ingest_workbuddy_turns.py`（M3 追加） | 新增 `AUTO_MARKER = "[WB-AUTO-INGEST]"` 与 `drop_automation_turns()`：按 `requestId` 整轮剔除定时任务自身会话，防止自引用增长 |
| WorkBuddy 定时自动化（M3，非代码文件） | id `3a4ee661-db23-475f-b66b-dd760bd7c0ab`，ACTIVE，`FREQ=HOURLY;INTERVAL=1`，cwds=`modules/agent-memory/tools`，首次运行 20:06 |

**未改动**：`AgentMemoryAutoConfiguration`（零新增 Bean）、任何 DDL / 表结构、任何 Controller、前端、主应用 `ChatTurnMemoryRecorder`、WorkBuddy 宿主。

### 与方案的偏差

| 原方案 | 实际做法 | 原因 |
| --- | --- | --- |
| 复用 `TrajectoryRecorder.record()` 写 L0 | 直接 `oltp.saveRawLog(L0RawLog.forInsert(..., ts, ...))` + catch/WARN 降级，写后回查保留 | `record()` 不接收 ts、内部固定取当前时间，会把 18:20 的历史消息全部写成导入时刻，破坏时间线；自己写才能保留宿主原始 ts。异常降级语义照搬 |
| 逻辑放在 `AiMemoryService.ingestTurns()` 内 | 拆到独立 `DialogueTurnIngestService`，`AiMemoryService` 只留委托 | `docs/rules/CODE_SIZE_RULES.md`：Service 单文件有效代码上限 500 行；原类已 455 行 |
| state 位于 `data/ingest-workbuddy-state.json` | 位于 `modules/agent-memory/tools/data/` | 与后端 H2 的 `core/data/` 隔离，避免误当业务数据；仓库已有 `data/` 忽略规则 |
| 参数只列 `--since/--full/--dry-run` | 另加 `--until` 固定时间窗 | 宿主 jsonl 在会话进行中持续追加（scanned 从 2480 涨到 2539），不固定窗口则两次运行的 `eligible/skipped` 不可比较，幂等无法严格判定 |
| 标准 3 期望第二次 `skipped=N` | 第二次为 `eligible=0 skipped=0`；服务端判重由标准 4（移走 state）验证 | 本地 state 优先过滤，是设计如此；两层幂等都实测生效，只是观察量落在不同步骤 |
| 标准 6「新对话 user+assistant 各 ≥1」 | 只验证增量捕获 `inserted=2`、`total` 270→272，两条均为助手过程文本 | 本轮用户只发了「确认」，之后没有新的用户提问；完整一轮需用户下一条消息（或 M3 自动化触发）后再验。**不虚报通过** |
| M3 每 5 分钟自动化 | **已创建，但频率降级为每小时一次**（`FREQ=HOURLY;INTERVAL=1`） | 平台 rrule 只支持 DAILY/HOURLY/WEEKLY/MONTHLY/YEARLY，**没有 MINUTELY**；实测 `FREQ=HOURLY;INTERVAL=1;BYMINUTE=0,5,…,55` 被忽略（`nextRunAt` 落在 +59.8 分钟），`FREQ=DAILY` 配多值 `BYHOUR` 直接被拒（`BYHOUR must be an integer between 0 and 23`）。5 分钟粒度在该调度器内无可用表达；要真 5 分钟需改用系统级计划任务或常驻 watcher，属新方案 |
| M3 自动化未考虑自身会话 | cwds 固定在 `modules/agent-memory/tools` 子目录 + 脚本按 `AUTO_MARKER` 整轮剔除，双保险 | 自动化每次运行本身就是一个 WorkBuddy 会话，若落进被扫描的项目目录，下一轮会把自己的运行日志灌进 L0，按每小时计约 24×N 条/天垃圾；单测验证整轮剔除生效 |
| M3 首次运行只导入当前会话 | 不带 `--session` 全量扫描项目下 5 个 jsonl，一次性 `inserted=8400`（历史回填） | 导入器设计就是「自动发现新会话」，此前所有验证都带 `--session` 单文件（约 2500 行）；不带参数时扫 60819 行。append-only + state/内容哈希双层判重，无重复写入；副作用是 4 个历史会话的问答也一并入库，方案第九节只排除了「其他项目」，本项目历史会话在范围内 |

### 真机验证结果（逐条对账第三节）

1. ✅ `dry-run --probe 01a1b05f…` → `user=1 assistant=3`；`scanned=2480 eligible=253`，压缩摘要与 system-reminder 被排除。
2. ✅ 全量补录 `inserted=255 skipped=0 failed=0`；OLAP `total` 基线 **14 → 270**（+256 = 255 补录 + 1 探针），35s 内同步完成（≤90s）；`source` 为 `user_message/assistant_message` 的行 **256** 条；精确命中提问原文 1 条、最终回答 1 条，该行 `ts=1793960411789`（18:20:11）、`trace_id=01a1b05f…`、`role=user`。
3. ✅ 固定窗口第二次运行 `eligible=0 inserted=0 skipped=0 failed=0`；移走 state 后重跑 `eligible=254 inserted=0 skipped=254 failed=0`。
4. ✅ 移走 state 重跑与重启后 `--full` 重导均为 `inserted=0 skipped=254`，`total` 不变。
5. ✅ 探针落库内容 `脱敏探针 138****5678 ***@***`，原文 `13812345678` / `a@b.com` 不出现。
6. ⚠️ 部分通过（M3 阶段补验 user 侧）：首轮增量 `scanned=2529 eligible=3 inserted=2 skipped=1 failed=0`，`total` 270→272，两条均为助手过程文本。19:02 用户发出真实提问「确认」后按定时任务形态（不带任何参数）实跑 `scanned=60819 eligible=8400 inserted=8400 skipped=0 failed=0`，OLAP 精确命中 `source=user_message`、`content="确认"`、`ts=19:02:09` 的行；本会话 `total` 272→280，抽查历史会话 `f9f32bf2…` 的 `total=3355` 与 state 完全一致。**user 侧已证；assistant 最终回答要等本轮结束后落盘，由 20:06 首次自动化捕获才算完整一轮，不虚报通过**。
7. ✅ `tools/list` 返回 5 个工具含 `memory_ingest_turns`；`turns=[]` → `{inserted:0,skipped:0,failed:0}`，`turns="bad"` → `{error:"turns 必须是数组"}`，均 HTTP 200 无 500。
8. ✅ 停 9900 → `run.sh` 重启（`Started AgentMemoryApplication in 9.734s`，pid 41424）→ `--full` 重导 `inserted=0 skipped=254`，`total` 保持 **272**。
9. ✅ M3 自动化已创建并 ACTIVE：id `3a4ee661-…`，`FREQ=HOURLY;INTERVAL=1`，`nextRunAt` 实测为创建后约 60 分钟（20:06）；`tools/list` 仍为 5 个工具，9900 健康（日志无 ERROR）。自引用过滤单测通过（含标记的整轮 `R1` 被剔除，正常轮 `R2` 保留），脚本 `py_compile` 通过、真实数据 `--dry-run` 无回归。

补充：新增测试 5/5 通过；`agent-memory-core` 全量 **322/322** 通过，无回归。构建产物 `agent-memory-core-1.0.0-exec.jar` mtime 18:54（158068371 字节），新类已确认在 `BOOT-INF/classes/` 内。

### 过程中新发现的坑

- `TrajectoryRecorder.record()` 不仅吞异常（方案已预判），还**不接受 ts**；补录历史数据时复用它会让时间线整体失真，必须在服务端直接构造 `L0RawLog`。
- **OLAP 可见性延迟实测约 35s**：`/analytics/messages` 查的是 Arrow 宽表，ETL 每分钟跑一次；导入返回 `inserted=255` 后立刻查仍是 14，需等同步。复现「查不到 ≠ 没写入」的完整链路，与第八节声明一致。
- **宿主 jsonl 在会话进行中持续追加**：同一次验证里 `scanned` 从 2480 → 2507 → 2525 → 2539。任何「两次运行计数相等」的断言都必须用 `--until` 锁窗口，否则会把新增助手输出误判成重复写入。
- MCP `tools/call` 的业务返回体是 `result.content[0].text` 里的 **JSON 字符串**，需要二次 `json.loads`；直接读 `result` 会拿到空壳。
- `tools/data/` 已被仓库既有 `data/` 忽略规则覆盖，无需新增 `.gitignore`；state 备份为 `ingest-workbuddy-state.json.bak`（验证时移走原文件产生）。
- 走 MCP 工具而非新增 REST Controller 的决策有效：全程未碰 `AgentMemoryAutoConfiguration`，启动无 `Ambiguous mapping`，`tools/list` 一次就绪。
- **平台 rrule 不支持分钟级频率**：可用 FREQ 只有 DAILY/HOURLY/WEEKLY/MONTHLY/YEARLY。HOURLY 的 `BYMINUTE` 多值被静默忽略（不报错，`nextRunAt` 仍为 +1 小时）；DAILY 的 `BYHOUR` 多值被显式拒绝。判定频率**必须看 `nextRunAt` 换算后的时间**，不能只看 rrule 字符串——只看字符串会误以为已是每 5 分钟。
- **不带 `--session` 的调用形态与单会话验证完全不同**：`scanned` 从约 2500 行跳到 60819 行（5 个会话文件），首次会一次性回填 8400 条。任何"按定时任务形态"的实跑都要预期这个量级；state 双层判重保证不会重复写，但 OLAP 体积会一次性增长。
- **定时任务自引用**：自动化自身会话若不隔离，会形成"每次运行 → 产生对话 → 下次运行导入"的线性垃圾增长。cwds 隔离依赖宿主按 cwd 划分项目目录这一约定，尚未到 20:06 真机观察；`AUTO_MARKER` 整轮剔除是不依赖该约定的兜底，已单测覆盖。
- 服务不可达时脚本按批打 `BATCH_FAILED` 到 stderr、退出码 1、**不推进 state**，因此定时任务停机期间不会丢数据，恢复后下一周期自动补上；自动化 prompt 已要求先做健康检查再决定是否跳过。

### 尚未完成 / 需用户决定

- **频率降级已知会商**：当前是每小时一次，不是方案写的每 5 分钟。若确需 5 分钟，只能改为系统级计划任务 / 常驻 watcher（新方案），且要评估每天 288 个 agent 会话带来的额度成本（用户此前遇到过 429 频率限制）。可随时暂停或改频，自动化 id `3a4ee661-db23-475f-b66b-dd760bd7c0ab`。
- **首次自动化真机效果待观察**（20:06）：确认 cwds 隔离是否使自动化会话不落进 `d-codehub-agent_runner` 目录、L0 是否出现含 `[WB-AUTO-INGEST]` 的行、`inserted` 是否为 0 或小量增量。
- 标准 6 的 assistant 侧完整验证，需等本轮最终回答落盘后由首次自动化捕获；目前只证到 user 侧。
- 本次改动（后端 4 个 Java 文件 + 1 个 Python 脚本 + 1 份方案文档）**未提交 git**；M3 另有 8400 条历史回填数据（append-only，方案第九节明确批量删除为延后项，不擅自清理）。
