# agent-memory 后端代码描述

- 日期：2026-08-29
- 模块路径：`modules/agent-memory/`（GitHub: `rottenmu/agent-memory`）
- 定位：Agent **四层金字塔记忆系统**（L0 RawLog / L1 AtomicMemory / L2 SceneBlock / L3 Persona），H2 MVStore OLTP + Arrow/Calcite OLAP 双库混合存储，内置 MCP 接入端点。

## 一、模块结构（Maven 多模块）

```
modules/agent-memory/
├── pom.xml                     # 聚合 POM（dependencyManagement：h2 2.3.232 / arrow 17.0.0 / calcite 1.37.0 / caffeine 3.1.8）
├── README.md
├── agent-memory-core/          # 核心业务（模型 / 存储 / 服务 / MCP / 工具）
├── agent-memory-autoconfig/    # Spring Boot 自动装配（@AutoConfiguration + @ConfigurationProperties）
└── agent-memory-application/   # 独立可执行模块（java -jar 单独运行，无需 MySQL）
```

## 二、四层记忆模型（core/model）

| 层 | 类 | 说明 |
|---|---|---|
| L0 | `L0RawLog` | 原始对话日志（record），`traceId` 全局溯源键；含 `source`（Trajectory 来源标识）；`id` 为 H2 自增主键供 ETL 游标 |
| L1 | `L1AtomicMemory` | 原子记忆，不可再分；类型：`preference/fact/habit/task/custom` + `session_var` + `global`；预留 `embedding` 向量 |
| L2 | `L2SceneBlock` | 场景块，按会话聚合上下文；含 `summary` 摘要 + `l1Ids` 关联原子记忆 |
| L3 | `L3Persona` | 用户画像顶层，跨会话稳定记忆；类型：`persona/preference/habit/history`；`version` 支持版本演进 |

## 三、存储层（双库分离 + SPI 插拔）

### 3.1 分层设计

```
业务层（AiMemoryService / MemoryAnalyticsService）
        │ 只依赖
        ▼
MemoryStorageFacade ──┬── OltpMemoryRepository（运行时 CRUD + 钻取召回，仅 H2 主库）
                      └── OlapAnalyticsRepository（后台离线分析，仅 OLAP 副库）
        │
        ▼
MemoryStorageFactory（按 agent-memory.oltp-engine / olap-engine 配置路由）
        │
        ▼
OltpStorageProvider / OlapStorageProvider（SPI 接口）
   ├── H2OltpStorageProvider（engine=h2，内置默认）
   └── ArrowOlapStorageProvider（engine=arrow，内置默认）
```

- **读写负载分离**：运行时记忆查询只走 H2 OLTP；OLAP 分析只走 Arrow 副库，不承担运行时流量。
- **SPI 插拔**：新增 MySQL/DuckDB 等只需实现 Provider 接口 + 注册 Bean + 改配置，业务零改动。
- **H2 豁免**：`agent-memory-h2` 独立 Hikari 数据源（MVStore 文件库，`jdbc:h2:file:./data/agent-memory;MODE=LEGACY`），是仓库中唯一豁免嵌入式存储的模块。

### 3.2 H2 四表结构（无外键、下划线命名）

`l0_raw_log`（id 自增 / trace_id / session_id / user_id / ts / role / content / tokens / meta_json / **source**）、`l1_atomic_memory`（id VARCHAR(32) PK / embedding BLOB）、`l2_scene_block`（l1_ids CLOB JSON）、`l3_persona`（version INT）。

- L3 画像叠加 **Caffeine 进程缓存**（key=userId:personaType，最大 1 万条，2h 过期），会话启动优先加载画像。
- 溯源链路：L3 → L2 → L1 均携带 `traceId`，`drillDownToRawLog(traceId)` 反向定位 L0 原始日志。

### 3.3 OLAP（Arrow + Calcite，纯 Java 无 JNI）

`ArrowOlapAnalyticsRepository`：
- 内存宽表 `olap_l0_log`（7 列：trace_id/session_id/user_id/ts/role/content/tokens），数据持久化于 `.arrow` IPC 文件；
- Calcite 将 SQL 下推内存 `ScannableTable`，支持会话聚合 / 用户时序 / 蒸馏评估 / trace 溯源 / 自定义 SQL；
- ETL 游标持久化于 `.cursor` 旁文件（已同步的 L0 最大 id）。

## 四、业务服务层

| 类 | 职责 |
|---|---|
| `AiMemoryService` | 四层记忆统一服务：会话变量→L1(session_var)、用户长期记忆→L3/L1、全局记忆→L1(global)；写入时同步落 L0 原始日志；类别白名单 + 敏感脱敏 |
| `AiMemoryController` | REST `/api/ai/memory/**`（session/user/global/policy） |
| `MemorySecurityConfig` | 安全策略：`whitelistCategories`（空=放行全部）+ `sensitiveFiltering` 开关 |
| `AiMemorySensitiveFilter` | 7 类敏感脱敏：手机号/身份证/银行卡/邮箱/API Key/Bearer Token/内网 IP（保留首尾，如 `138****8000`） |
| `TrajectoryRecorder` | 会话轨迹采集器：9 类来源事件（system_prompt/chain_of_thought/tool_call/tool_result/sub_agent/context_injection/user_message/assistant_message/system_event）写入 L0；采集失败降级不影响主链路 |
| `AiMemoryAgentTool` | AgentScope 工具适配（memory_write/read），供智能体对话中主动读写记忆 |
| `AiConversationMemory` | 智能体会话记忆：内存模式（LinkedHashMap + 会话上限淘汰）/ 共享模式（RocksDB 持久化 + revision 乐观并发） |

## 五、ETL 同步（异步，非阻塞主链路）

`AsyncLogSyncTask`（@Scheduled 调度，默认每分钟）：
- **游标增量**：仅拉取 `id > cursor` 的 L0 日志追加到 OLAP，推进游标并落盘；
- **首轮全量重建**：分页拉全部，按 `traceId#ts#role` 去重后整体替换；
- 异常自愈（捕获仅记日志），运行时查询绝不访问 OLAP。

## 六、对外接口（四组）

| 分组 | 路径 | 说明 |
|---|---|---|
| 记忆 CRUD | `/api/ai/memory/**` | 会话变量 / 用户长期记忆 / 全局记忆 / 记忆策略 |
| 分析 OLAP | `/api/agent-memory/analytics/**` | session-stats / user-activity / distillation-stats / trace / query / messages / session-messages |
| 分层架构 | `/api/agent-memory/arch/**` | stats / configs CRUD（SOUL 配置 + USER 档案，含种子数据 11+2 条） |
| 文件兼容 | `/api/agent-memory/file/**` | `{agentId}/MEMORY.md` 读写检索，QClaw/OpenClaw/Claude Code 互通（RocksDB 或本地文件双模式） |
| MCP 端点 | `/api/agent-memory/mcp` | HTTP JSON-RPC 2.0：initialize / tools/list / tools/call（memory_write/read/delete/search） |

## 七、自动装配（autoconfig）

- `AgentMemoryAutoConfiguration`：H2 数据源 → StorageContext → 双 Provider → 工厂 → 双仓储 → 门面 → 安全/服务/控制器/MCP/ETL 全链路 Bean。
- `AgentMemorySyncAutoConfiguration`：`@EnableScheduling` + `@Scheduled(cron=agent-memory.sync-cron)`。
- `AgentMemoryProperties`（prefix=`agent-memory`）：h2-url / olap-arrow-data-file / sync-cron / whitelist-categories / sensitive-filtering / oltp-engine / olap-engine / file-base-dir，均带默认值。
- `AutoConfiguration.imports` 注册两个自动配置类，业务插件零配置接入。

## 八、独立启动（agent-memory-application）

- `AgentMemoryApplication`（包 `com.zimo.agentmemory.app`，刻意避开 core 组件扫描冲突）+ `application.yml`（端口 9900）+ `run.sh` 一键脚本。
- 关键点：需显式声明绑定 H2 的 `JdbcTemplate`；`env -u SERVER__PORT` 防止环境变量覆盖端口；`--add-opens=java.base/java.nio=ALL-UNNAMED` 供 Arrow 内存访问。
- 依赖树：agent-memory-autoconfig → core → framework-common/autoconfig、H2、Arrow、Calcite、Caffeine、agentscope-harness（传递）。

## 九、测试覆盖（core/src/test）

TrajectoryRecorder、MemoryFileStore（含 RocksDB）、MemoryStorageFactory、AsyncLogSyncTask、Arrow OLAP 查询、AiConversationMemory 事件日志与阶段化剪枝等 7 个测试类。
