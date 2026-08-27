# module-agent-memory：四层金字塔记忆 + OLTP/OLAP 双库方案

> 状态：已确认决策，待实施
> 日期：2026-08-18
> 决策（用户）：A 豁免用 H2 / A 物理迁移 / B 纯 Java 方案（无 DuckDB JNI）

## 0. 已确认决策

1. **OLTP = H2 MVStore**（豁免 AGENTS.md 禁令，新增例外条款；运行时记忆迁移到 H2 四层表）
2. **物理迁移**：starter 记忆代码（`memory/` + `chat/AiConversationMemory` + 敏感过滤/工具/Controller）搬入 module-agent-memory，starter 依赖新模块
3. **OLAP = 纯 Java**：Arrow 内存列式 + Calcite 查询 + Parquet 离线导出；**不引入 DuckDB JNI**；`olap.engine=arrow`（预留 duckdb 开关位）
4. RocksdbAgentStateStore 保持不动（AgentScope 框架状态存储，非记忆管理层）

## 1. 需求与现状冲突分析（必须先决策）

### 1.1 需求（来自设计文档）
- 四层记忆金字塔：L0 RawLog → L1 AtomicMemory → L2 SceneBlock → L3 Persona
- OLTP 主库 **H2 MVStore**（行式、纯 Java 无 JNI）：承载运行时记忆 CRUD 与 L3→L2→L1→L0 钻取召回
- OLAP 副库 **DuckDB**（列式、JDBC 嵌入式、可开 VSS）：L0 日志副本 + trace + token + 会话时序，后台异步 ETL 同步
- **零-JNI 降级**：Arrow + Calcite（纯 Java）
- 存储门面（MemoryStorageFacade：OltpMemoryRepository / OlapAnalyticsRepository）+ AsyncLogSyncTask + MemoryAnalyticsService + PureJavaVectorUtil

### 1.2 现状冲突
| 冲突点 | 仓库现状 | 影响 |
|---|---|---|
| **H2 禁令** | `AGENTS.md`：*禁止 SQLite、H2 等本地数据库，只能面向 MySQL* | 需求 OLTP 用 H2 **直接违反仓库硬约束** |
| **实际 OLTP 存储** | 当前记忆持久化在 **RocksDB**（`FileStorageService`，framework 提供，JNI） | 需求假设"原有 H2"不成立；且 RocksDB 为 JNI，与需求"纯 Java 无 JNI"矛盾 |
| **L1 进程内记忆** | `AiConversationMemory`（内存/RocksDB 共享） | 需求 L1 AtomicMemory 需落 H2 持久化（新语义） |
| **记忆代码归属** | starter 的 `memory/`（AiMemoryService 等 4 类）+ `chat/` + `agent/memory/` | 需迁移到 module-agent-memory |

### 1.3 需要用户决策
1. **OLTP 存储选型**：
   - A. **豁免禁令用 H2**（按需求原文；需改 AGENTS.md，新增 H2 依赖，运行时记忆迁移到 H2 表）
   - B. **RocksDB 继续充当 OLTP**（当前实现即如此；四层模型建在 RocksDB 键空间上，省去迁移；RocksDB 是 JNI）
   - C. **MySQL 充当 OLTP**（符合仓库禁令；但失去"嵌入式零部署"特性）
2. **迁移方式**：物理搬移 starter 记忆代码到 module-agent-memory（改依赖：starter 依赖新模块 或 模块独立注册），还是新模块独立实现 + starter 保留旧实现
3. **DuckDB JNI 依赖**：沙盒需从 Maven 拉取 duckdb-jdbc（含 native），是否允许

## 2. 目标架构（按需求 + 现状适配）

```text
┌─ Agent 运行时（读写分离，只走 OLTP）───────────────────────┐
│  L3 Persona（Caffeine 进程缓存 + OLTP 持久化）               │
│  L2 SceneBlock（会话场景块，OLTP）                          │
│  L1 AtomicMemory（原子记忆，OLTP）                          │
│  L0 RawLog（原始日志，OLTP 写入 + 异步副本→OLAP）            │
│  钻取召回：L3 → L2 → L1 → L0（traceId 反向定位）            │
└──────────────────────────────────────────────────────────┘
┌─ 后台异步（不阻塞主链路）───────────────────────────────────┐
│  AsyncLogSyncTask：定时增量同步 H2/RocksDB 的 L0 → DuckDB   │
│  MemoryAnalyticsService：会话统计/时序/蒸馏评估 API          │
│  MemoryStorageFacade（门面）：OltpMemoryRepository          │
│                              OlapAnalyticsRepository        │
└──────────────────────────────────────────────────────────┘
```

### 存储分层（按最终选型落地）
- **OLTP 主库**（决策 1 结果）：L1/L2/L3 持久化；L3 叠加 Caffeine 缓存；traceId 贯穿
- **OLAP 副库**（DuckDB，决策 3）：L0 宽表（会话/角色/内容/token/时序）；VSS 可选；`duckdb.enabled` 开关
- **降级**：`duckdb.enabled=false` → PureJavaVectorUtil（内存向量）+ L0 导出 Parquet 离线分析（Arrow 写 Parquet 纯 Java）

### 四层记忆模型（新表结构，下划线命名、无外键）
```sql
-- OLTP（以 H2 或 RocksDB 键空间等价物呈现）
L0_raw_log(id, trace_id, session_id, ts, role, content, tokens, meta_json)
L1_atomic_memory(id, trace_id, session_id, memory_type, content, embedding, ts)
L2_scene_block(id, session_id, scene_name, summary, start_ts, end_ts, l1_ids, ts)
L3_persona(id, user_id, persona_type, content, version, updated_ts)
-- OLAP（DuckDB 宽表）
olap_l0_log_wide(trace_id, session_id, user_id, ts, role, content, tokens, ...)
olap_session_stats / olap_memory_quality ...
```

## 3. 模块结构与实施步骤（待决策后细化）

```text
modules/module-agent-memory/
├── module-agent-memory-core/       # 门面/仓储接口/四层模型/ETL/分析/向量工具（业务与存储解耦）
└── module-agent-memory-autoconfig/ # 存储实现装配（OLTP 选型 + DuckDB/Arrow 切换开关）+ 定时任务
```

步骤：
1. 决策 1/2/3 确认 → 更新本方案
2. 建模块骨架（pom：h2 或 rocksdb、caffeine、duckdb-jdbc 或 arrow+calcite）
3. 四层模型 + MemoryStorageFacade + OltpMemoryRepository 实现
4. AsyncLogSyncTask（定时增量 ETL）+ DuckDB 建表 SQL + OlapAnalyticsRepository
5. MemoryAnalyticsService + 分析查询示例
6. PureJavaVectorUtil + VSS（可选）/降级 Parquet 导出
7. starter 记忆代码迁移接线（决策 2）+ 编译 + 定向验证

## 4. 验证清单
- [ ] 定向编译 module-agent-memory + 依赖方
- [ ] 运行时记忆读写/钻取召回走 OLTP，OLAP 零运行时查询
- [ ] 开关切换：duckdb on/off 均可用（off → Arrow 降级）
- [ ] ETL 增量同步任务执行 + 分析 API 返回

## 5. 执行结果（2026-08-18 已完成）

### 模块结构（com.zimo.module.agentmemory）

```text
modules/module-agent-memory/
├── module-agent-memory-core/
│   ├── model/        L0RawLog / L1AtomicMemory / L2SceneBlock / L3Persona
│   ├── storage/      MemoryStorageFacade / OltpMemoryRepository / OlapAnalyticsRepository
│   │   └── impl/     H2OltpMemoryRepository（四层表 + L3 Caffeine 缓存 + traceId 溯源）
│   ├── analytics/    MemoryAnalyticsService + ArrowOlapAnalyticsRepository（Arrow+Calcite）
│   ├── sync/         AsyncLogSyncTask（分页增量 ETL → Arrow IPC 文件）
│   ├── vector/       PureJavaVectorUtil（余弦/内积/特征向量，无 JNI）
│   ├── memory/       AiMemoryService / AiMemoryAgentTool / AiMemoryController /
│   │                 MemorySecurityConfig（自 starter 物理迁移，四层适配）
│   ├── chat/         AiConversationMemory / AiConversationCompressionCandidate / AiChatMessage（迁移）
│   └── security/     AiMemorySensitiveFilter（迁移）
└── module-agent-memory-autoconfig/
    ├── AgentMemoryAutoConfiguration（H2 数据源/仓储/门面/分析/记忆 Bean/Controller）
    ├── AgentMemorySyncAutoConfiguration（@Scheduled ETL 调度，独立避免循环依赖）
    ├── AgentMemoryAnalyticsController（/api/agent-memory/analytics/*）
    └── AgentMemoryProperties（h2-url/olap-arrow-data-file/sync-cron/白名单/脱敏开关）
```

### 迁移与接线
- starter 的 AiMemoryService/AiMemoryAgentTool/AiMemoryController/AiMemorySensitiveFilter/
  AiConversationMemory/AiConversationCompressionCandidate/AiChatMessage 全部迁入
  module-agent-memory（starter → module-agent-memory-core 单向依赖）；
- 记忆存储由 RocksDB（mem/ 前缀）改为 H2 四层表（L1 session_var/global、L3 persona 等）；
- AGENTS.md 增加 module-agent-memory 专用 H2 豁免条款（用户批准）；
- 相关测试：@MockBean/withBean 补齐 AiMemoryService（A2a/Mcp/Intent/AutoConfiguration 测试）。

### 验证
- 定向 verify（starter 81 测试 + auth 5 测试）BUILD SUCCESS；
- 启动回归：/api/ai/memory 写入读取正常（脱敏 138****5678、sensitiveMasked=true）；
  H2 落库 data/agent-memory.mv.db；OLAP 分析接口全 200；
- ETL 定时同步生效（AsyncLogSyncTask 完成 1 行 → session-stats 返回数据）；
- Calcite SQL：SELECT * 返回真实数据；GROUP BY/DISTINCT 在 ScannableTable 上受限
  （内置 session-stats/distillation-stats 用 Java 内存聚合，已满足分析需求）。

### 关键启动要求
```bash
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -jar agent-application/target/agent-application-1.0.0.jar --server.port=9900
```
（Arrow MemoryUtil 需 add-opens；缺省会 MemoryUtil 初始化失败）

### 已知限制/后续
- Calcite 聚合 SQL 受限（建议用内置分析接口）；如需完整 SQL 聚合可改 EnumerableTable；
- L0 同步为全量重建（数据量可控；游标增量可后续演进）；
- DuckDB 方案（JNI）按用户决策未引入，olap 引擎开关位预留。

## 6. 后续优化（2026-08-19 已完成）

### Calcite 聚合 SQL 修复
- 根因：Calcite 将未加引号的表名/列名大写化（role→ROLE），而 ScannableTable 声明的是小写列名，WHERE/GROUP BY/DISTINCT 均报 `Column 'ROLE' not found`；
- 修复：`L0LogTable.getRowType` 列名改大写（匹配校验）+ 查询结果列标签 `toLowerCase()` 还原小写命名；
- 验证：SELECT * / WHERE / GROUP BY / DISTINCT 全部返回真实数据（单元测试 4 例 + 接口实测）。

### ETL 游标增量同步
- `L0RawLog` 增加 `id`（H2 自增主键，`forInsert` 便捷构造）；
- `OltpMemoryRepository.listRawLogsSince(afterId, limit)` 按 id 增量拉取；
- `OlapAnalyticsRepository` 增加 `appendRows` / `syncCursor` / `updateSyncCursor`；游标持久化于 `l0_log.arrow.cursor`；
- `AsyncLogSyncTask` 改为游标增量：`cursor < 0`（首轮/升级无游标文件）→ 全量重建去重；否则按 id 增量追加并推进游标；
- 验证：单元测试（首轮/增量/无新增跳过/重启恢复）+ 接口实测（total 1→2、游标文件 2）。

## 7. 存储后端抽象（SPI + 工厂 + 引擎开关，2026-08-19 已完成）

### 设计（适配其他 OLAP/OLTP/关系型数据库）
```text
storage/
├── spi/
│   ├── StorageContext        # 装配上下文（engine/h2Url/olapArrowDataFile/dataSource）
│   ├── OltpStorageProvider   # OLTP 后端 SPI（engine() + create(context)）
│   ├── OlapStorageProvider   # OLAP 后端 SPI（engine() + create(context)）
│   ├── H2OltpStorageProvider # 内置：engine=h2（包装 H2OltpMemoryRepository）
│   └── ArrowOlapStorageProvider # 内置：engine=arrow（包装 ArrowOlapAnalyticsRepository）
├── MemoryStorageFactory      # 按 oltp-engine/olap-engine 路由；未知引擎回退默认并告警
```
- `AgentMemoryProperties` 新增 `oltp-engine`（默认 h2）/ `olap-engine`（默认 arrow）；
- 仓储 Bean 改由 `MemoryStorageFactory` 输出（接口类型注入，ETL 不再依赖具体 Arrow 类）；
- `OlapAnalyticsRepository` 提升 `replaceRows` 到接口（ETL 全量重建与引擎解耦）。

### 接入新数据库步骤（文档化）
1. 实现 `OltpStorageProvider`（如 engine=mysql，内部实现 MySQL 方言仓储）或 `OlapStorageProvider`（如 engine=duckdb）；
2. 注册为 Spring Bean（实现类可放任何模块）；
3. `agent-memory.oltp-engine=mysql` / `agent-memory.olap-engine=duckdb` 一键切换。

### 验证
- 单元测试：MemoryStorageFactoryTest（路由命中/未知回退/无 Provider 抛错）3 例；memory-core 共 8 测试全绿；
- 端到端：H2 OLTP + Arrow OLAP 经 Factory 装配正常（记忆读写、分析接口、ETL），无引擎回退告警；
- 配置示例（application.yml）：
  ```yaml
  agent-memory:
    oltp-engine: h2
    olap-engine: arrow
  ```
