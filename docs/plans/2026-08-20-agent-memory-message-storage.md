# 智能体内存管理 — 消息存储原理与架构设计

> 模块：`modules/agent-memory`（com.zimo.module.agentmemory）
> 日期：2026-08-20 | 版本：基于 AgentScope-Java 2.0.2 四层记忆金字塔（L0~L3）
> 存储豁免：经用户 2026-08-18 批准，本模块使用嵌入式 H2 MVStore（OLTP）+ Arrow/Calcite（OLAP），其余模块禁止复用

## 一、定位与分层

智能体对话产生的**消息/记忆数据**按"原始日志 → 原子记忆 → 场景块 → 人物画像"四层组织，并拆分
**运行时读写（OLTP）**与**离线分析（OLAP）**两条存储链路：

```text
        ┌────────────────────────────────────────────────────────────┐
        │                    业务/外部调用方                            │
        │  REST(/api/ai/memory) · Agent 工具 · MCP(/api/agent-memory) │
        └───────────────┬──────────────────────────┬──────────────────┘
                        │ 写                        │ 读
        ┌───────────────▼──────────────────────────▼──────────────────┐
        │            AiMemoryService（门面：脱敏→白名单→落库）          │
        └───────┬──────────────────────────────────────────┬──────────┘
                │ write                                      │ query
   ┌────────────▼───────────┐                    ┌───────────▼────────────┐
   │   OLTP 运行时仓储        │                    │   OLAP 离线分析仓储      │
   │  H2 MVStore（文件库）    │                    │  Arrow IPC 列式文件      │
   │  l0_raw_log / l1 / l2   │  ← ETL 增量同步 →  │  olap_l0_log 宽表        │
   │  / l3 + 索引             │  （AsyncLogSyncTask）│  + .cursor 游标文件     │
   └────────────┬───────────┘                    └───────────┬────────────┘
                │                                             │
   ┌────────────▼─────────────────────────────────────────────▼──────────┐
   │  MemoryStorageFacade（门面）+ MemoryStorageFactory（SPI 路由）        │
   │  OLTP Provider：h2(默认)/mysql/rocksdb · OLAP Provider：arrow(默认)/duckdb │
   │  文件存储：RocksdbFileStorageService(默认)/Local（framework.storage.engine）│
   └──────────────────────────────────────────────────────────────────────┘
```

## 二、消息存储原理（写链路）

### 2.1 一次记忆写入产生什么

`AiMemoryService` 是唯一写入口（会话变量 / 用户记忆 / 全局记忆 / 检索），每次写操作**同步落两类数据**：

| 操作 | L0 原始日志（l0_raw_log） | 派生层 |
|---|---|---|
| `saveSessionVar` | `SESSION_VAR:{key}={value}`（role=system，traceId 生成） | L1 `l1_atomic_memory`（TYPE_SESSION_VAR） |
| `saveUserMemory` | `USER_MEMORY:{category}={value}`（sessionId=`user-{userId}`） | persona/preference → **L3** `l3_persona`；history/custom → L1 |
| `saveGlobalMemory` | `GLOBAL_MEMORY:{key}={value}` | L1（TYPE_GLOBAL） |

写入前统一经过 `AiMemorySensitiveFilter`（**敏感脱敏在前**：手机号→`138****5678`、身份证、银行卡、邮箱、API Key、Bearer token、内网 IP），随后白名单校验（`whitelistCategories`）。

### 2.2 L0 消息实体

```text
l0_raw_log: id(BIGINT 自增) · trace_id · session_id · user_id · ts(ms) · role · content(CLOB) · tokens · meta_json(CLOB)
索引：idx_l0_trace(trace_id) · idx_l0_session(session_id)
```

- `id` 自增单调——是 ETL 增量同步的**天然游标**；
- `trace_id` 关联一次交互，支撑溯源；`content` 采用 `TYPE:key=value` 约定格式（可扩展结构化解析）。

## 三、ETL 增量同步（OLTP → OLAP）

`AsyncLogSyncTask`（Scheduled cron 默认 `0 */1 * * * ?`，可配 `agent-memory.sync-cron`）：

```text
run():
  cursor = olap.syncCursor()          # 读 .cursor 文件（已同步的 H2 L0 最大 id）
  if cursor < 0: fullRebuild()        # 游标未初始化 → 全量重建（分页 500，按 traceId+ts+role 去重，整体替换 + 导出文件）
  else:
    while 分页拉取 oltp.listRawLogsSince(cursor, 500):   # 增量游标扫描
      每行 → toWideRow() 宽表行
      cursor 推进至本页最大 id
    olap.appendRows(newRows)          # 内存列式追加 + 导出 Arrow IPC 文件
    olap.updateSyncCursor(maxId)      # 持久化游标
  失败仅告警，不影响主链路（写 H2 与同步解耦）
```

**设计要点**：游标文件（`.arrow.cursor`）持久化崩溃恢复；全量重建幂等（去重）；分析延迟 ≤1 分钟
（与前端"记忆总览 ETL 延迟"提示一致）。

## 四、OLAP 分析存储

`ArrowOlapAnalyticsRepository`（默认 `./data/olap/l0_log.arrow` + `.cursor`）：

- **宽表列**（与 H2 L0 对齐，下划线命名）：`trace_id / session_id / user_id / ts / role / content / tokens`
- **双形态**：内存列式 `List<Object[]>`（查询）+ Arrow IPC 文件（重启加载 `loadFromFile()` 恢复）
- **查询**（`MemoryAnalyticsService` → Calcite 内存表 `olap_l0_log`）：
  - `sessionStats` 会话聚合（消息数 / token 消耗 / 活跃时长）
  - `userDailyActivity` 用户行为时序（按天）
  - `memoryDistillationStats` 蒸馏质量（按 role/type）
  - `traceEvents(traceId)` 事件链（溯源分析页）
  - `sessionMessages` 会话消息预览（消息内容 + MEMORY.md 文档名 + 文件地址）
  - `query(sql)` 自定义 SQL（`/api/agent-memory/analytics/query`）

## 五、四层表结构（H2 MVStore，共 5 表）

| 表 | 主键 | 关键列 | 索引 | 用途 |
|---|---|---|---|---|
| `l0_raw_log` | id 自增 | trace_id/session_id/ts/role/content CLOB | trace_id, session_id | 原始消息日志（ETL 源） |
| `l1_atomic_memory` | id(32 hex) | trace_id/memory_type/content/embedding BLOB/ts | (user_id, memory_type) | 原子记忆（向量可选） |
| `l2_scene_block` | id(32 hex) | session_id/scene_name/summary/start_ts/end_ts/l1_ids | session_id | 场景块（聚类结果） |
| `l3_persona` | id(32 hex) | user_id/persona_type/content/version/updated_ts | user_id | 人物画像（L3 缓存于 Caffeine） |
| 无外键、幂等建表（`CREATE TABLE IF NOT EXISTS`），`MODE=LEGACY` 兼容 | | | | |

## 六、SPI 可插拔架构

| 抽象 | 默认实现 | 切换配置 |
|---|---|---|
| `OltpStorageProvider` → `OltpMemoryRepository` | H2 MVStore | `agent-memory.oltp-engine=h2\|mysql\|rocksdb` |
| `OlapStorageProvider` → `OlapAnalyticsRepository` | Arrow + Calcite | `agent-memory.olap-engine=arrow\|duckdb` |
| `FileStorageProvider` → `FileStorageService` | RocksDB（KV 分离 Blob） | `framework.storage.engine=rocksdb\|local\|minio\|s3\|oss` |

`MemoryStorageFactory` 按引擎名路由（未知回退默认并告警），业务只依赖 `MemoryStorageFacade`。

## 七、关键设计决策

1. **双链路读写分离**：Agent 推理召回走 H2（低延迟、强一致）；统计/分析走 Arrow（列式、可离线）；ETL 解耦写主链路，失败不影响业务。
2. **游标增量 + 全量兜底**：自增 id 天然有序，`<0` 触发全量重建，崩溃可恢复。
3. **脱敏前置**：敏感过滤发生在 L0 落库之前，原始值永不落盘（含 OLAP 副本）。
4. **文件存储中间件化**：记忆文件（MEMORY.md 兼容模式）可一键切 RocksDB / 本地磁盘 / MinIO 等（`FileStorageProvider` SPI）。
5. **MCP 免 token 白名单**（`/api/agent-memory/mcp` 在 AuthProperties 放行 + 端点自校验兼容 Bearer/裸 token），响应符合 MCP 规范（result/error 互斥、initialize 直出对象）。

## 八、消息生命周期时序（示例：写入一条用户记忆）

```text
前端/MCP memory_write(user, "偏好周报邮件")
  → AiMemoryService.saveUserMemory
    → 敏感过滤（138****5678 脱敏）
    → l0_raw_log.insert  (USER_MEMORY:preference=... , sessionId=user-xxx)
    → l3_persona.upsert  (version+1)
  → ETL（≤1min）：AsyncLogSyncTask 游标扫描 → 宽表行 → olap_l0_log.arrow 追加 + 游标推进
  → 前端"记忆总览 → 消息预览"：session-messages 读 OLAP 宽表返回消息 + 文档名 + 文件地址
```

## 九、边界与后续

- **已知约束**：L0 为追加日志不删除（合规留痕）；分析副本最多落后 1 分钟（同步 cron 可调小）。
- **可演进**：L2 场景块聚类落地（当前仅建表）、embedding 向量化（`PureJavaVectorUtil` 特征向量，无外部模型）、OLAP 换 DuckDB、存储引擎切换 MySQL/OSS 中间件。
