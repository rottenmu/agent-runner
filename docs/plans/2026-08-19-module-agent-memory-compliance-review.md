# module-agent-memory 需求符合性核验报告

> 核验对象：`modules/module-agent-memory`（2026-08-18 交付 + 08-19 优化后）
> 核验日期：2026-08-19

## 一、结论摘要

| 需求维度 | 状态 |
|---|---|
| 四层记忆金字塔（L0/L1/L2/L3） | ✅ 已实现 |
| OLTP 主库（H2-MVStore 行式、纯 Java） | ✅ 已实现 |
| OLAP 副库（Arrow+Calcite，纯 Java 无 JNI） | ✅ 已实现 |
| 读写负载严格分离 | ✅ 已实现 |
| 无外键 / 下划线字段 / trace 溯源 | ✅ 已实现 |
| 存储门面模式（业务与存储解耦） | ✅ 已实现 |
| 异步 ETL 增量同步 | ✅ 已实现（游标增量） |
| 分析服务（会话统计/时序/蒸馏） | ✅ 已实现 |
| **两套可切换方案（DuckDB/Arrow）** | ⚠️ **仅 Arrow 一套**（DuckDB 按用户 08-18 决策未引入） |
| **L2 SceneBlock 运行时写入链路** | ⚠️ 仓储完整，业务链路未接入 |
| **VSS 向量扩展（SQL 级）** | ⚠️ 仅字段与工具预留 |
| **DDL / DuckDB 建表 SQL 独立脚本** | ⚠️ 表结构内联，无独立 .sql 交付 |
| **Parquet 导出** | ⚠️ 以 Arrow IPC（.arrow）等价替代 |

## 二、逐条核验明细

### 1. 四层记忆金字塔 ✅
- `model/`：`L0RawLog`（含 id 自增主键 + traceId）/ `L1AtomicMemory` / `L2SceneBlock` / `L3Persona` 四个 record
- H2 表：`l0_raw_log` / `l1_atomic_memory` / `l2_scene_block` / `l3_persona`（`H2OltpMemoryRepository.initSchema` 幂等建表）

### 2. OLTP 主库（H2-MVStore 行式） ✅
- Hikari + `jdbc:h2:file:./data/agent-memory`（MVStore 文件模式，纯 Java 无 JNI）
- L1/L2/L3 持久化：`saveAtomicMemory`（MERGE upsert + embedding BLOB）/ `saveSceneBlock` / `savePersona`
- **L3 Caffeine 缓存**：`personaCache`（10k / 2h），`getPersona` 先缓存后库
- 钻取召回：`drillDownToRawLog(traceId)`（L0 溯源）+ 各层 recall 方法

### 3. OLAP 副库（Arrow + Calcite） ✅
- `ArrowOlapAnalyticsRepository`：Arrow `VectorSchemaRoot` 列式内存 + `ArrowFileWriter` 导出 IPC 文件（加载/导出双向）
- Calcite `ScannableTable` 暴露 `olap_l0_log` 宽表；**聚合 SQL 已修复**（大写列名 + 大小写双表名注册），SELECT/WHERE/GROUP BY/DISTINCT 均可用
- 数据源：`AsyncLogSyncTask` 从 H2 L0 增量同步（游标持久化 `.cursor`）

### 4. 硬性约束 1：读写负载严格分离 ✅
- 运行时记忆（AiMemoryService/工具/Controller）仅调用 `OltpMemoryRepository`（H2）；
- OLAP 仓储仅被 `MemoryAnalyticsService`（分析 API）与 `AsyncLogSyncTask`（ETL）调用，运行时查询零穿透（grep 已验证调用方）。

### 5. 硬性约束 2：无外键 / 下划线 / trace 溯源 ✅
- 四张表均无 FOREIGN KEY（仅 PRIMARY KEY + 5 个索引）；
- 字段全下划线命名（trace_id/session_id/memory_type/updated_ts…）；
- 每条 L1/L2/L3 记忆携带 `traceId`，`listRawLogsByTrace(traceId)` / `drillDownToRawLog` 反向定位 L0 原始日志。

### 6. 硬性约束 3：存储门面模式 ✅
- `MemoryStorageFacade`（oltp()/olap()）→ `OltpMemoryRepository` / `OlapAnalyticsRepository` 接口 → H2/Arrow 实现；
- AiMemoryService 等业务仅依赖接口与门面，可替换底层实现。

### 7. 硬性约束 4：两套可切换方案 ⚠️
- **已实现**：纯 Java Arrow+Calcite 无 JNI 方案（`ArrowOlapAnalyticsRepository`）；
- **未实现**：DuckDB(JNI) 方案。原因：用户 2026-08-18 决策选择"纯 Java 方案（无 DuckDB JNI）"；因此无 duckdb 依赖、无 duckdb 建表 SQL、无 `olap.engine=duckdb|arrow` 切换开关（仅注释预留开关位）。**如后续需恢复 DuckDB 方案，需补：依赖、OlapAnalyticsRepository 的 DuckDB 实现、引擎工厂 + 开关配置。**

### 8. OLAP 能力 ✅（可选 VSS 除外）
- 会话聚合统计（sessionStats）/ 用户行为时序（userDailyActivity）/ 记忆蒸馏评估（memoryDistillationStats）/ trace 事件链 / 自定义 SQL；
- **VSS 向量扩展 ⚠️**：`L1AtomicMemory.embedding` 字段 + `PureJavaVectorUtil`（余弦/内积）已预留，但无 SQL 级向量检索（需 DuckDB VSS 或 Arrow 向量计算接入）。

### 9. 零-JNI 降级方案 ✅（Parquet 用 Arrow IPC 替代）
- Arrow 内存列式 + Calcite 查询 + `l0_log.arrow` 文件落盘（Arrow IPC 格式）；
- **Parquet 导出 ⚠️**：当前导出为 Arrow IPC（.arrow）而非 Parquet（Parquet 写需 parquet-hadoop 依赖，文档已注明可替换）。

### 10. 配套后台组件 ✅
| 组件 | 实现 |
|---|---|
| MemoryStorageFacade | ✅ 门面接口 |
| OltpMemoryRepository / OlapAnalyticsRepository | ✅ H2 / Arrow 实现 |
| AsyncLogSyncTask | ✅ 游标增量 + 首轮全量重建，@Scheduled 后台线程 |
| MemoryAnalyticsService | ✅ 分析服务 + /api/agent-memory/analytics/* |
| PureJavaVectorUtil | ✅ 余弦/内积/特征向量 |

### 11. 交付产物核验
| 交付物 | 状态 |
|---|---|
| H2 四层 DDL（无外键） | ⚠️ 内联于 `H2OltpMemoryRepository.initSchema`，无独立 .sql 文件 |
| DuckDB OLAP 建表 SQL | ❌ 未交付（用户决策排除 DuckDB） |
| 异步 ETL 完整实现 | ✅ `AsyncLogSyncTask` |
| OLAP 分析查询示例 | ✅ 4 个内置分析接口 + Calcite SQL 接口 + 单元测试 |
| 双方案切换开关 | ❌ 未实现（单 Arrow 方案） |

## 三、建议补齐项（按优先级）

1. **独立 DDL 脚本**：从 initSchema 抽离 `resources/db/module-agent-memory/V1__four_layer_memory.sql`（H2 语法），并附 DuckDB 参考建表 SQL（文档形式，不引入依赖）；
2. **L2 SceneBlock 运行时写入**：在对话收尾/会话归档处调用 `saveSceneBlock`（摘要 + 关联 L1），打通 L3→L2→L1→L0 完整钻取；
3. **引擎开关**：定义 `agent-memory.olap-engine=arrow|duckdb` 属性 + 仓储工厂（当前仅 arrow 实现，开关预留）；
4. **VSS 向量检索**：基于 PureJavaVectorUtil + embedding 字段实现 Top-K 召回（SQL 级需 DuckDB VSS 或应用层计算）。
