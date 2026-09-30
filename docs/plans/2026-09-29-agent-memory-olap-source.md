# 将 L0 来源字段完整同步并暴露到 OLAP

> 状态：已落地
> 起草日：2026-09-29
> 关联模块：`modules/agent-memory`
> 规范依据：`docs/rules/PLAN_DOC_RULES.md`

---

## 一、置信度与剩余风险

- 当前置信度：86%
- 主要剩余风险：旧版七列 Arrow 文件的读取与重写若兼容处理不完整，升级后可能无法打开历史分析数据或在追加时丢失旧行。

---

## 二、目标

本次要达成：

- 将 `L0RawLog.source` 从 OLTP 增量同步和全量重建一路传到 Arrow/Calcite 宽表。
- 让分析查询、会话消息和 trace 响应能读到 `source`，并兼容已有七列 Arrow 文件。

本次明确不做（延后项见第九节）：

- 不在本方案接入 `TrajectoryRecorder` 的事件生产方；本次只修复已有来源字段的数据传递与读取。

---

## 三、成功标准

本方案完成后，以下每一条都应能在真机上直接判定为真：

1. 在隔离的 agent-memory H2 数据库写入两条带来源的 L0 记录（`user_message`、`tool_result`），执行全量重建后请求 `/api/agent-memory/analytics/messages` 和 `/api/agent-memory/analytics/session-messages`，两个响应中对应记录的 `source` 值分别与写入值完全一致，记录总数为 2。
2. 在同一隔离实例追加第三条 `source=context_injection` 的 L0 记录并触发一次增量同步；再次查询时三条记录的来源分别正确，且每条记录只出现 1 次。
3. 使用升级前的七列 `.arrow` 文件启动实例并追加一条带来源记录；查询结果保留全部旧行，旧行 `source` 为 `null`，新行 `source` 为写入值，OLAP 加载日志没有数据文件加载失败记录。

---

## 四、参照行为（对标）

无。本方案只补齐本仓库已有 L0 来源字段的数据路径，不对标外部产品。

---

## 五、现状差距

### 能力缺失

- Arrow 宽表没有 `source` 列，因此 Calcite SQL 无法按来源过滤或分组；消息接口也没有返回该字段。
- Arrow 文件读取按固定的七个列位置解码，不识别旧文件与新文件的列数差异。

### 只是没接线

- H2 的 `l0_raw_log` 和 `L0RawLog` 已有 `source` 字段，九参数 `forInsert` 可写来源值。
- `AsyncLogSyncTask` 的增量和全量路径都调用同一个 `toWideRow()`；只需扩展统一行结构即可覆盖两条同步路径。
- trace 查询按宽表列名映射输出，列定义补齐后可以一并返回来源值。

### 不打算解决的问题

- 生产事件目前没有调用 `TrajectoryRecorder`，所以修好 OLAP 传递后，不代表生产数据已经有完整来源分类；该接线单独处理，见第九节。

---

## 六、技术方向

### 1. 固定八列 OLAP 行结构

职责：

- 统一增量同步、全量重建和 Arrow 持久化使用的行列顺序。

关键接口 / 数据结构：

```text
trace_id, session_id, user_id, ts, role, content, tokens, source
toWideRow(L0RawLog) -> Object[8]
```

取舍理由：`source` 是 L0 已有字段，无需改 H2 表结构或新增数据库迁移；统一在 ETL 映射可避免增量与重建出现不同结果。

### 2. 兼容七列历史 Arrow 文件

职责：

- 识别升级前七列文件；为历史行补 `source=null`，新导出统一使用八列 schema。

关键接口 / 数据结构：

```text
旧文件：trace_id, session_id, user_id, ts, role, content, tokens
新文件：旧七列 + source
```

取舍理由：历史数据没有来源值，不能推导或伪造；保留历史行并置空可维持行数和既有统计口径。

### 3. 将来源字段加入分析响应

职责：

- 让 `/messages` 与 `/session-messages` 返回 `source`；`/trace` 和自定义 Calcite SQL 也可读取 `source`。

关键接口 / 数据结构：

```text
GET /api/agent-memory/analytics/messages       rows[].source
GET /api/agent-memory/analytics/session-messages messages[].source
SELECT source, COUNT(*) FROM olap_l0_log GROUP BY source
```

取舍理由：响应只增加字段，保留现有分页、过滤和查询方式；不在本次新增另一套来源筛选协议。

---

## 七、里程碑

### M1. OLTP 到 Arrow 的来源传递

- 增量同步与全量重建均写入八列行。
- 七列历史文件可打开、查询并在追加后保留旧行。
- 验证：按 `source` 分组的行数与 H2 测试数据一致。

### M2. 分析接口暴露来源

- 分页消息、会话消息、trace 和自定义 SQL 响应包含 `source`。
- 验证：HTTP 响应中的来源值和 H2 原始记录一致。

---

## 八、自测计划

### 全量与增量同步

1. 使用隔离的数据目录启动 agent-memory，向测试 H2 的 `l0_raw_log` 写入同一会话的两条记录，来源分别为 `user_message` 与 `tool_result`。
2. 触发 `AsyncLogSyncTask` 全量重建，调用 `/api/agent-memory/analytics/query` 执行 `SELECT source, COUNT(*) AS cnt FROM olap_l0_log GROUP BY source`。
3. 验证查询恰有两组，计数各为 1；调用 `/messages` 与 `/session-messages` 后，两条记录的 `source` 均与原始记录一致。
4. 再写入一条来源为 `context_injection` 的记录并触发增量同步；重复查询，三组计数各为 1，总行数为 3。

### 七列 Arrow 文件兼容

1. 将升级前生成的七列 `.arrow` 文件复制到隔离数据目录，并记录文件原有行数。
2. 使用该数据目录启动升级后的实例，查询 `SELECT COUNT(*) AS cnt FROM olap_l0_log`。
3. 验证返回行数等于原有行数，历史行的 `source` 均为 `null`，且日志没有 Arrow 数据文件加载失败。
4. 追加一条带 `source=tool_result` 的 L0 记录并完成增量同步，再重启实例。
5. 验证重启后总行数为原有行数加 1，历史行仍为 `null`，新增行来源为 `tool_result`。

---

## 九、已知延后项

- `TrajectoryRecorder` 生产接线（D1）—— 接入点涉及系统提示词、工具参数、工具结果等模型可见内容写入持久化 L0；需要先确定采集开关、敏感数据处理和框架依赖边界。只注册 Bean 不会产生事件，因此不把它当成本方案的修复。
- `seedArchIfEmpty()` 的演示数据（D4）与向量召回（D14）—— 与来源字段 OLAP 数据通路无直接依赖，另行评审。

---

## 十、实施顺序

1. 扩展 `AsyncLogSyncTask.toWideRow()` 和 Arrow 列定义、读写逻辑，补齐增量、重建、重启兼容验证。
2. 将 `source` 加入消息、会话消息查询投影，确认 trace 与自定义 SQL 能访问该列。
3. 更新后端代码说明中的列数、接口行为和 D2 状态，并记录验证结果。

---

## 十一、实施记录

- 实施日期：2026-09-29
- 实际改动清单：

| 文件 / 模块 | 改动 |
| --- | --- |
| `modules/agent-memory/agent-memory-core/src/main/java/com/zimo/module/agentmemory/sync/AsyncLogSyncTask.java` | 增量与全量 ETL 行均传递 `source`。 |
| `modules/agent-memory/agent-memory-core/src/main/java/com/zimo/module/agentmemory/analytics/impl/ArrowOlapAnalyticsRepository.java` | 增加八列 schema；兼容七列历史行和 Arrow 文件；保留空来源为 `null`。 |
| `modules/agent-memory/agent-memory-core/src/main/java/com/zimo/module/agentmemory/autoconfig/AgentMemoryAnalyticsController.java` | `/messages` 与 `/session-messages` 响应增加 `source`。 |
| `modules/agent-memory/agent-memory-core/src/test/java/com/zimo/module/agentmemory/sync/AsyncLogSyncTaskTest.java` | 覆盖全量、增量、重启后的来源值保持。 |
| `modules/agent-memory/agent-memory-core/src/test/java/com/zimo/module/agentmemory/analytics/impl/ArrowOlapAnalyticsRepositoryQueryTest.java` | 覆盖来源查询、持久化重载及七列旧文件追加。 |
| `modules/agent-memory/agent-memory-core/src/test/java/com/zimo/module/agentmemory/autoconfig/AgentMemoryAnalyticsControllerTest.java` | 通过 MockMvc 覆盖消息、会话消息和 trace 路由的 HTTP JSON 响应来源字段。 |
| `docs/agent-memory-backend-code-description.md` | 更新 OLAP 列数、接口行为及 D2 状态。 |

### 与方案的偏差

| 原方案 | 实际做法 | 原因 |
| --- | --- | --- |
| 在已启动的独立应用上用 HTTP 请求完成三条真机验收。 | 在隔离 H2 数据库中写入 L0，经真实 ETL 同步到 Arrow，再通过 MockMvc 发起 HTTP 路由请求；另用七列 Arrow 文件执行追加、重启和查询。未启动独立服务监听端口。 | 本方案验证的是来源字段的数据通路和接口序列化；独立部署的鉴权过滤器与定时调度不属于本次改动范围。 |

### 真机验证结果

- `mvn -pl agent-memory-core '-Dtest=AsyncLogSyncTaskTest,ArrowOlapAnalyticsRepositoryQueryTest,AgentMemoryAnalyticsControllerTest' test`：10 例通过，0 失败，0 错误。
- 七列 Arrow 文件加载后追加八列来源记录并重启：定向测试观察到旧行 `source=null`、新行 `source=tool_result`，总行数为 2。
- 全量与增量 ETL 后查询三种来源并重启：定向测试观察到每种来源各 1 行，总行数为 3。
- 隔离 H2 到接口的串联验收：两条 H2 L0 记录经全量 ETL 后，MockMvc 请求 `/messages` 返回来源值 `tool_result`、`user_message`，`/session-messages` 返回 `user_message`、`tool_result`，`/trace` 返回 `tool_result`。
- 独立应用进程与鉴权过滤器未单独启动验证；本次改动由隔离 H2、真实 ETL、Arrow 文件和 MockMvc 路由用例覆盖。

### 过程中新发现的坑

- Java 对单个 `Object[]` 调用 `List.of(...)` 会触发 varargs 类型推断歧义；测试中显式写成 `List.<Object[]>of(...)` 后编译通过。
