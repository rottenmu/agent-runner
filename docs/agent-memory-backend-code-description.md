# agent-memory 后端代码描述

- 日期：2026-09-10（**已按 P1/P2 重构后结构更新**；上一版 2026-08-29）
- 模块路径：`modules/agent-memory/`（GitHub: `rottenmu/agent-memory`）
- 定位：Agent **四层金字塔记忆系统**（L0 RawLog / L1 AtomicMemory / L2 SceneBlock / L3 Persona），
  H2 MVStore OLTP + Arrow/Calcite OLAP 双库混合存储，内置 MCP 接入端点。

> 代码规模：core 51 个类 / 4506 行；application 1 个类。

## 一、模块结构（Maven 多模块）

```
modules/agent-memory/
├── pom.xml                     # 聚合 POM（parent = modules，被父工程 reactor 聚合）
├── agent-memory-core/          # 核心业务 + 自动装配 + 装配资源（51 类）
└── agent-memory-application/   # 独立可执行模块（java -jar 单独运行，无需 MySQL）
```

**相对上一版的结构变化**：`agent-memory-autoconfig` 模块已取消，其 6 个类（4 个 `autoconfig/` + 
`memoryarch/MemoryArchController`）与 `META-INF/spring/AutoConfiguration.imports` 已平移到
`agent-memory-core`。因此 core 同时承载「业务代码 + 自动装配」，单模块即可被父工程与独立启动共用。

core 包结构（按文件数）：
`storage`(10) / `memory`(5) / `model`(4) / `memoryfile`(4) / `memoryarch`(4) / `mcp`(4) /
`autoconfig`(4) / `chat`(3) / `analytics`(2) / `vector`(1) / `sync`(1) / `security`(1)

随模块发布的集成资产：
- `resources/mcp/memory-mcp.json` — WorkBuddy `mcp.json` 注册模板（HTTP transport）
- `resources/skill/memory-manager/SKILL.md` — 记忆管理技能定义（触发词：记住/回忆/查询记忆…）

## 二、四层记忆模型（core/model）

| 层 | 类 | 说明 |
|---|---|---|
| L0 | `L0RawLog` | 原始对话日志（10 参 record），`traceId` 全局溯源键；含 `source`（Trajectory 来源标识）；`id` 为 H2 自增主键供 ETL 游标 |
| L1 | `L1AtomicMemory` | 原子记忆，不可再分；类型：`preference/fact/habit/task/custom` + `session_var` + `global`；预留 `embedding` 向量 |
| L2 | `L2SceneBlock` | 场景块，按会话聚合上下文；含 `summary` 摘要 + `l1Ids` 关联原子记忆 |
| L3 | `L3Persona` | 用户画像顶层，跨会话稳定记忆；类型：`persona/preference/habit/history`；`version` 支持版本演进 |

`L0RawLog` 提供两个静态工厂：8 参（`source=null`）与 9 参（携带 source）。

## 三、存储层（双库分离 + SPI 插拔）

### 3.1 分层设计

```
业务层（AiMemoryService / MemoryAnalyticsService）
        │ 只依赖
        ▼
MemoryStorageFacade ──┬── OltpMemoryRepository（运行时 CRUD + 钻取召回，仅 H2 主库）
                      └── OlapAnalyticsRepository（后台离线分析，仅 OLAP 副库）
        ▲
        │ 由装配层经 StorageRouter 路由后产出（无中间工厂类）
        ▼
StorageRouter（通用路由 + 回退，来自 module-datasource-storage）
        │
        ▼
StorageProvider&lt;T&gt;（通用存储 SPI 接口）
   ├── H2OltpStorageProvider（engine=h2，T=OltpMemoryRepository，内置默认）
   └── ArrowOlapStorageProvider（engine=arrow，T=OlapAnalyticsRepository，内置默认）
```

- **SPI 已通用化**：`StorageContext` / `StorageProvider<T>` / `StorageRouter` 位于
  `com.zimo.module.ds.storage`（agent-datasource 的零依赖子模块 module-datasource-storage），
  可被任意模块复用；agent-memory 私有 spi 包已删除。详见
  `docs/refactor/2026-09-10-datasource-storage-spi.md`。

- **读写负载分离**：运行时记忆查询只走 H2 OLTP；OLAP 分析只走 Arrow 副库，不承担运行时流量。
- **SPI 插拔**：新增 MySQL/DuckDB 等只需实现 Provider 接口 + 注册 Bean + 改配置，业务零改动。
- **H2 豁免**：`agentMemoryDataSource` 独立 Hikari 数据源（MVStore 文件库，
  `jdbc:h2:file:./data/agent-memory;MODE=LEGACY`），全仓库唯一豁免嵌入式存储禁令的模块。

### 3.2 H2 四表结构（无外键、下划线命名）

`l0_raw_log`（id 自增 / trace_id / session_id / user_id / ts / role / content / tokens / meta_json / **source**）、
`l1_atomic_memory`（id VARCHAR(32) PK / embedding BLOB）、`l2_scene_block`（l1_ids CLOB JSON）、
`l3_persona`（version INT）。
另有 `memory_arch_config` 表（第二套记忆面，见 §4.2）。

- L3 画像叠加 **Caffeine 进程缓存**（key=userId:personaType，最大 1 万条，2h 过期），会话启动优先加载。
- 溯源链路：L3 → L2 → L1 均携带 `traceId`，`drillDownToRawLog(traceId)` 反向定位 L0 原始日志。

### 3.3 OLAP（Arrow + Calcite，纯 Java 无 JNI）

`ArrowOlapAnalyticsRepository`：
- 内存宽表 `olap_l0_log`（**8 列**：trace_id/session_id/user_id/ts/role/content/tokens/source），
  数据持久化于 `.arrow` IPC 文件；
- Calcite 将 SQL 下推内存 `ScannableTable`，支持会话聚合 / 用户时序 / 蒸馏评估 / trace 溯源 / 自定义 SQL；
- ETL 游标持久化于 `.cursor` 旁文件（已同步的 L0 最大 id）。
- 升级前的七列 `.arrow` 文件继续可读，历史行的 `source` 为空；消息、会话消息和 trace 查询均可返回来源字段。

## 四、业务服务层

### 4.1 主记忆服务（四层金字塔）

| 类 | 职责 |
|---|---|
| `AiMemoryService` | 四层记忆统一服务：会话变量→L1(session_var)、用户长期记忆→L3/L1、全局记忆→L1(global)；写入时同步落 L0 原始日志；类别白名单 + 敏感脱敏 |
| `AiMemoryController` | REST `/api/ai/memory/**`（session/user/global/policy，10 个端点） |
| `MemorySecurityConfig` | 安全策略：`whitelistCategories`（空=放行全部）+ `sensitiveFiltering` 开关 |
| `AiMemorySensitiveFilter` | 7 类敏感脱敏：手机号/身份证/银行卡/邮箱/API Key/Bearer Token/内网 IP（保留首尾，如 `138****8000`） |
| `AiMemoryAgentTool` | AgentScope 工具适配（memory_write/read），供智能体对话中主动读写记忆 |
| `AiConversationMemory` | 智能体会话记忆：内存模式（LinkedHashMap + 会话上限淘汰）/ 共享模式（RocksDB 持久化 + revision 乐观并发） |
| `TrajectoryRecorder` | 会话轨迹采集器：9 类来源事件写入 L0（**详见 §9 缺陷 D1：当前未被装配**） |

### 4.2 第二套记忆面（memoryarch，行政配置向）

`MemoryArchService` / `MemoryArchRepository` / `MemoryArchController` 维护
`memory_arch_config` 表，以 `type` 区分 **USER**（用户档案）与 **SOUL**（AI 身份配置），
风格接近管理后台的「档案卡」模型，与 §4.1 的四层金字塔**并存但语义不重叠**。

> 注意：`AgentMemoryAutoConfiguration.seedArchIfEmpty()` 在表为空时**硬编码写入 11 条 USER +
> 2 条 SOUL 种子数据**（见 §9 缺陷 D4）。

### 4.3 文件兼容模式（memoryfile）

`MemoryFileService` / `MemoryFileStore`：按 `{fileBaseDir}/{agentId}/MEMORY.md` 读写检索，
用于与 OpenClaw / Claude Code 等生态互通；`FileStorageService` 存在时记忆文件落 RocksDB，否则落本地文件。

## 五、ETL 同步（异步，非阻塞主链路）

`AsyncLogSyncTask`（由 `AgentMemorySyncAutoConfiguration` 的 `@Scheduled` 调度，默认每分钟）：
- **游标增量**：仅拉取 `id > cursor` 的 L0 日志追加到 OLAP，推进游标并落盘；
- **首轮全量重建**：分页拉全部，按 `traceId#ts#role` 去重后整体替换；
- 异常自愈（捕获仅记日志），运行时查询绝不访问 OLAP。

`toWideRow()` 映射 **8 列**并传递 `source`；七列旧行读取后会补空来源，见 §9 缺陷 D2 的修复记录。

## 六、对外接口（四组 REST + 一组 MCP）

| 分组 | 路径 | 端点 | 鉴权 |
|---|---|---|---|
| 记忆 CRUD | `/api/ai/memory/**` | session / user / global / policy（10） | 需鉴权 |
| 分析 OLAP | `/api/agent-memory/analytics/**` | session-stats / user-activity / distillation-stats / trace / query / messages / session-messages（7） | 需鉴权 |
| 分层架构 | `/api/agent-memory/arch/**` | stats / configs CRUD / extract（8） | 需鉴权 |
| 文件兼容 | `/api/agent-memory/file/**` | `{agentId}` 读写删 + search（4） | 需鉴权 |
| **MCP 端点** | `/api/agent-memory/mcp` | HTTP JSON-RPC 2.0：initialize / notifications / tools/list / tools/call | POST 由 `MemoryMcpAccessGuard` 校验；配置令牌时校验 Bearer/裸令牌，未配置时仅放行回环来源；GET SSE 只返回固定空事件 |

MCP 工具 4 个：`memory_write` / `memory_read` / `memory_delete` / `memory_search`，
均以 `target`（session / user / global）区分记忆层级；`memory_search` 支持 `userId` 限定。

登录拦截器排除列表（`AuthProperties.Interceptor#excludePaths`）中与本模块相关的**仅** `/api/agent-memory/mcp` 一条；POST 的独立准入由 `MemoryMcpAccessGuard` 执行。

## 七、自动装配（core/autoconfig，已并入 core）

- `AgentMemoryAutoConfiguration`（273 行）：H2 数据源 → StorageContext → 双 Provider → 工厂 →
  双仓储 → 门面 → 安全/服务/控制器/MCP/ETL **全链路 20+ 个 `@Bean` 手工声明**。
  注意：控制器亦以 `@Bean` 方式注册（`AiMemoryController` / `MemoryArchController` /
  `MemoryFileController` / `AgentMemoryAnalyticsController`），**不依赖组件扫描**。
- `AgentMemorySyncAutoConfiguration`：`@EnableScheduling` + `@Scheduled(cron=agent-memory.sync-cron)`，
  与主装配**刻意分离**（注释说明：避免与 `aiHarnessAgentFactory` 成环）。
- `AgentMemoryProperties`（prefix=`agent-memory`）：h2-url / olap-arrow-data-file / sync-cron /
  whitelist-categories / sensitive-filtering / oltp-engine / olap-engine / file-base-dir，均带默认值。
- `AutoConfiguration.imports` 注册上述两个自动配置类，业务插件零配置接入。

## 八、独立启动（agent-memory-application）

- `AgentMemoryApplication`（包 `com.zimo.agentmemory.app`，刻意避开 core 组件扫描冲突）+
  `application.yml`（端口 9900）+ `run.sh` 一键脚本。
- 关键点：需显式声明绑定 H2 的 `JdbcTemplate`（`memoryArchRepository` 依赖）；
  `env -u SERVER__PORT` 防止环境变量覆盖端口；`--add-opens=java.base/java.nio=ALL-UNNAMED` 供 Arrow 内存访问。
- 依赖树：`agent-memory-core` → `framework-common` / `framework-autoconfig` / H2 / Arrow / Calcite /
  Caffeine / agentscope-harness（传递）。

## 九、阅读中发现的缺陷与风险（状态见下表）

> 第一批 **D1–D6** 为 2026-09-10 通读时定位；第二批 **D7–D21** 为 2026-09-15 复读时定位。
> 两批各自按严重度排序，**状态见下表**（✅ 已修复 / 🟨 部分接线或待真机验证 / ⬜ 未修）。
>
> **2026-09-15 M1 修复批次**：D8b / D9 / D10 / D11 / D12 / D13 已随「M1 数据与隔离地基」一并修复。
> 变更清单与方案偏差见 `docs/plans/2026-09-15-agent-memory-module-prd.md` §十六。主要手段：
> L1/L3 加租户列并原地迁移、所有查询强制「租户 + 用户」双条件、列表接口改分页信封
> （`total` / `truncated`）、删除返回真实影响行数、跨层分页下推 SQL `UNION ALL`。
> 下文各节保留缺陷的**原始分析**，作为修复依据与回归案例（对应单测见
> `AiMemoryServiceTest` / `H2OltpMemoryRepositoryTest`）。

| 编号 | 严重度 | 主题 | 状态 |
| --- | --- | --- | --- |
| D7 | 高 | 敏感脱敏过滤器（抛异常 / 漏检 / 假脱敏） | ✅ 2026-09-15 |
| D3 | 高 | MCP 端点无鉴权 | ✅ 2026-09-15 |
| D8 | 高 | `memory_search` 硬编码租户 | ✅ 2026-09-15（另见 D8b） |
| D1 | 中 | Trajectory 事件未接线 | 🟨 2026-09-29 基础来源已接线，其他事件待评审 |
| D2 | 中 | OLAP 缺 `source` 列 | ✅ 2026-09-29（10 个定向用例通过，含 H2→ETL→MockMvc 路由） |
| D4 / D6 | 中 / 低 | 装配层种子数据、代码味道 | ⬜ |
| D5 | 低 | 存储路由回退传未解析引擎名 | ✅ 2026-09-10 |
| D8b | 中 | L1/L2/L3 无租户列、工具 schema 未声明 `tenantId` | ✅ 2026-09-15（`l2` 未加列，见 §九 说明） |
| D9 | 高 | 不传 category 丢全部 L1（**同源拖垮 D13 清空**） | ✅ 2026-09-15 |
| D10 | 高 | 先截断再分页，静默丢数据 | ✅ 2026-09-15 |
| D11–D13 | 中 | 时间口径、删除语义 | ✅ 2026-09-15 |
| D14 | 中 | 向量召回链路不存在 | ⬜（待定方向） |
| D15–D21 | 中 / 低 | 归属字段、共享模式、ETL、指标失真等 | ⬜ |

### D1（中）Trajectory 事件来源仅覆盖基础业务记录，敏感载荷未接线

2026-09-29 接线后，`AiConversationMemory` 的对话追加与 fork 记录经 `TrajectoryRecorder` 写入，来源分别为
`user_message` / `assistant_message`；`AiMemoryService` 的会话变量和用户记忆变更标记为 `memory_write`，
传给 recorder 的正文沿用服务已脱敏结果。`MemoryAwarePromptBuilder` 原有 `context_injection` 来源和召回元数据保持不变。

独立应用的 MCP HTTP 验收已通过：`tools/call(memory_write)` 写入的 L0 行经 ETL 出现在 `/api/agent-memory/analytics/messages`，
`source=memory_write` 且正文保留脱敏值；Arrow 文件重启重载后仍能查到该来源。完整平台的模型聊天运行时和真实预召回链路未启动。
独立应用运行 Arrow 导出时需增加 JVM 参数 `--add-opens=java.base/java.nio=ALL-UNNAMED`；不带该参数时未生成 Arrow 文件。

平台 `/api/biz/ai/chat` 的 HarnessAgent 路径在 Controller 返回后调用 `ChatTurnMemoryRecorder`，再经 MCP
`memory_write` 将 `[user]` 与 `[assistant]` 合并写成一条 `system/memory_write` 会话记录；它不走
`AiConversationMemory.appendTurn()`。该 Controller 路由已由 MockMvc 与 stub 服务验证记录器委托；未调用真实模型。
`ChatTurnMemoryRecorderTest` 进一步覆盖异步 MCP 参数与正文格式、停用/空会话跳过和 MCP 故障隔离。
`AiConversationMemory.appendTurn()` 的 `user_message` / `assistant_message` 来源对应独立的旧版 `legacyChat` 路径。

仍未接入 `system_prompt`、`chain_of_thought`、`tool_call`、`tool_result`、`sub_agent` 等内容来源；这些数据的采集范围、
脱敏、访问权限与保留策略需单独评审。本次改动只给已存在的 L0 业务记录标注来源，不扩大持久化内容范围。

### D2（中）OLAP 数据集缺 `source` 列，ETL 未传递该字段 —— ✅ 2026-09-29

原实现中 `ArrowOlapAnalyticsRepository.buildRoot()` 只建 7 列，`AsyncLogSyncTask.toWideRow()` 也只映射 7 项
（不含 `source`）。即便 L0 已有来源值，**分析层仍无法按来源过滤/分组**；固定列位置读取也无法直接兼容旧 Arrow 文件。

2026-09-29 的代码改动将来源字段加入 ETL 行和 Arrow/Calcite 八列 schema，旧七列行会补 `source=null`；
消息、会话消息与 trace 查询可以返回该字段。10 个定向用例通过，覆盖隔离 H2→ETL→MockMvc 路由、全量/增量、重启和旧文件追加；
独立应用进程与鉴权过滤器未单独启动验证，完整结果见 `docs/plans/2026-09-29-agent-memory-olap-source.md` §十一。

### D3（**高**）MCP 端点无鉴权，但模板与注释均声称有 —— ✅ 已修复（2026-09-15）

- `AuthProperties.excludePaths` 将 `/api/agent-memory/mcp` 列入白名单，注释写「由端点内自行校验 Bearer/裸 token」；
- `mcp/memory-mcp.json` 模板要求客户端发送 `Authorization: Bearer ${AGENT_MEMORY_TOKEN}`；
- **但 `MemoryMcpEndpoint.handle()` 完全没有校验逻辑**——方法签名接收了
  `HttpServletRequest servletRequest` 参数却**从未使用**，是「打算加校验但漏了」的典型痕迹。

→ 后果：任何能访问该端口者均可**无凭据读写删全部记忆**。因记忆会被注入 LLM 上下文，
**写入任意记忆等价于向后续所有会话注入提示词**（prompt injection 面），危害高于普通越权。

**修复方式**：新增 `mcp/MemoryMcpAccessGuard`，在 `POST` 入口完成准入，`handle()` 返回
`ResponseEntity<McpResponse>`，未通过时给 **HTTP 401 + JSON-RPC 错误体**（HTTP 状态码让客户端能
识别「需要凭据」，文案说明缺什么）。策略为两档，取 `getRemoteAddr()` 而非可伪造的 `X-Forwarded-For`：

| 条件 | 行为 | 场景 |
| --- | --- | --- |
| 配置了 `agent-memory.mcp-token` | 校验 `Authorization`（`Bearer <token>` 或裸 token，定长比较） | 跨机/生产部署 |
| 未配置 | **仅放行回环来源**（`127.0.0.0/8`、`::1`、`::ffff:127.x`） | 本机 MCP 客户端零配置可用 |

未配置时不是「完全放行」，避免出现「忘记配令牌 = 裸奔」；启动时按档位打 INFO/WARN 日志。
`GET`（SSE 探测）**刻意不校验**：响应体是固定常量、零信息量，而部分客户端建 SSE 通道时不带自定义头。
`AuthProperties` 的注释同步改为指向真实实现。

### D4（中）自动装配硬编码 11+2 条种子数据

`AgentMemoryAutoConfiguration.seedArchIfEmpty()` 内联 11 条 USER 与 2 条 SOUL 示例，
且内容明显取自真实环境（如「测试工程师，排查流程画布保存前端传参问题」）。
生产装配路径不应写演示数据，建议改为配置开关（默认关）或迁至测试资源。

### D5（低）存储路由回退时传递未解析的引擎名 —— ✅ 已修复（2026-09-10）

改造前 `MemoryStorageFactory.createOltp/createOlap`：当配置引擎无匹配 Provider 而回退默认时，
`provider` 已是默认实现，但仍调用 `withEngine(engine)` 传入**原始未知引擎名**（如 `mysql`），
导致 `StorageContext.engine` 与实际实现不一致。

**修复方式**：路由与回退逻辑整体下沉到 `com.zimo.module.ds.storage.StorageRouter`
（agent-datasource 下的零依赖模块 module-datasource-storage），`MemoryStorageFactory` 因只剩转发职责
**已被删除**，装配层直接调用 `StorageRouter.route(...)`。回退分支显式执行
`engine = defaultEngine` 后再 `context.withEngine(engine)`，Provider 收到的即实际生效引擎名。
回归用例 `StorageRoutingTest#fallbackPassesResolvedEngineNameToProvider` 锁定该行为。

详见 `docs/refactor/2026-09-10-datasource-storage-spi.md`。

> 附带说明：`AgentMemoryAutoConfiguration.memoryStorageContext()` 把 context 的 `engine` 固定为
> `DEFAULT_OLTP_ENGINE`（h2），仅作通用上下文的初始值；实际路由由 `StorageRouter` 依配置覆写，
> 因此 OLAP 路径上 Provider 收到的是自身引擎名。

### D6（低）其他代码味道

- `L1AtomicMemory.l1IdsToJson()` / `L2SceneBlock.l1IdsFromJson()` 为**手写 JSON 拼接/解析**，
  未用 Jackson；ID 含逗号或引号即解析错乱。
- `MemoryMcpEndpoint` 每次调用 `new ObjectMapper()`，可复用单例。
- `AgentMemoryProperties.sensitiveFiltering` 为原始 `boolean`，**未显式配置时默认 false**
  （即默认不脱敏）；仓库内 `application.yml` 已显式设为 true，但默认值方向偏危险。
- `GET /api/agent-memory/analytics/query?sql=` 为**原始 SQL 直通**（Calcite 执行）。
  当前不在鉴权白名单内，风险可控；仍建议限制为只读白名单模板而非任意 SQL。
- `memory_arch_config` 与四层金字塔是**两套并行记忆模型**，语义边界需在文档中明确，避免后续重复建设。

## 九之二、2026-09-15 复读新增缺陷（D7–D21）

> 本批全部经**源码逐行核对**；D7 另有 JDK 17 运行时实测证据。均**尚未修复**。

### D7（**高**）敏感脱敏过滤器：既会抛异常、又没遮住真正敏感的数据 —— ✅ 已修复（2026-09-15）

`security/AiMemorySensitiveFilter` 原实现有**三个独立缺陷**（不止「`$2` 越界」一个）：

**缺陷 1（会崩）** `id_card` 规则只有 **1 个捕获组**，掩码却引用 `$2`：

```java
new Rule("id_card", Pattern.compile("(?<![0-9])([1-9][0-9]{5})(?:[0-9]{2})(?:0[1-9]|1[0-2])"
        + "(?:0[1-9]|[12][0-9]|3[01])(?:[0-9]{3})(?:[0-9Xx])(?![0-9])"), "$1********$2")
```

**缺陷 2（漏检）** 该正则总长 16 位（年份只写 2 位），**真实 18 位身份证完全不命中** —— 漏检比误报危险。

**缺陷 3（假脱敏）** `bank_card` 掩码 `$1****` 引用的 `$1` 就是整串，等于「原样输出 + 补四个星号」；
`private_ip` 只匹配到网段前缀，主机位原样留在后面。

用 JDK 17 的 jshell 逐字符复刻实测（探针 `tmp/verify/d7-before-after.jsh`，含新旧对照）：

| 实测项 | 修复前 | 修复后 |
| --- | --- | --- |
| 真实 18 位身份证 `110101199003077758` 是否命中 | **false（完全检不出）** | 命中 |
| 16 位变体 `1101011903077778` 掩码 `$1********$2` | **抛 `IndexOutOfBoundsException: No group 2`** | 不再出现（正则已重写） |
| 18 位身份证脱敏结果 | —（不可用） | `110101********7758` |
| 银行卡 `6222021234567890123` | `6222021234567890123****`（**全量泄漏**） | `****0123` |
| 内网 IP `192.168.1.100` | `192.168.***1.100`（**主机位泄漏**） | `192.168.*.*` |

触发面：`AiMemoryService.sanitizeIfEnabled()` 在 `saveSessionVar` / `saveUserMemory` /
`saveGlobalMemory` 三处都会调用，而仓库内两份配置**都显式设了 `sensitive-filtering: true`**
（`agent-application/src/main/resources/application.yml:157`、
`agent-memory-application/src/main/resources/application.yml:27`），**即当前是开着的**。

→ 修复前后果一：写入任何含 16 位数字（长订单号、流水号）的记忆会直接 500 / MCP 工具报错。
→ 修复前后果二：真正该被遮住的 18 位身份证与银行卡号，**一个都没遮住**。

**修复方式**：
1. 七类规则全部重写 —— 身份证按 **18 位 + 15 位历史格式**两条规则、银行卡保留末 4 位、内网 IP 按
   三段网段（`10.` / `192.168.` / `172.16-31.`）各一条、主机位整体遮蔽。
2. **类加载期校验掩码引用**：静态块遍历所有规则，用 `\$(\d+)` 抽出掩码里的组引用与
   `pattern.matcher("").groupCount()` 比对，越界即抛 `IllegalStateException`。
   这类错误必须在**启动时**暴露，而不是等用户写入记忆时才崩 —— 这是让同类 bug 不再复现的关键。
3. 规则顺序在 Javadoc 与注释里写清语义约束：`id_card` **必须**排在 `bank_card` 之前
   （18 位身份证同时满足「16~19 位连续数字」的银行卡形态，顺序颠倒会丢掉地区码前缀）。
4. 新增 `AiMemorySensitiveFilterTest`（11 例），锁定「不抛异常 / 检出 18 位 / 脱敏后不可还原 /
   公网 IP 与普通文本不误伤」四组断言。

> 实测复现：修复后在真实服务上通过 MCP `memory_write` 写入含 18 位身份证 + 银行卡的记忆，
> 返回正常（修复前会报错），读回内容为 `110101********7758` 与 `****0123`，原始串未落库。

### D8（**高**）MCP `memory_search` 硬编码租户 `default` —— ✅ 已修复（2026-09-15）

`MemoryMcpToolkit.call()` 从入参取出 `tenantId` 并传给 `write/read/delete`，但
**`search(args)` 的签名里根本没有 tenantId**，方法体内两处写死 `"default"`：

```java
memoryService.listUserMemory("default", userId, null, 500, 0);
memoryService.listGlobalMemory("default", 500, 0);
```

→ 多租户下调用方即使传了 tenantId，检索仍在 default 租户里做：**搜不到自己的数据，
却可能搜到 default 租户的**（跨租户信息泄漏方向）。

**修复方式**：`search(String tenantId, Map<String,Object> args)` 透传租户，`call()` 改为
`case "memory_search" -> search(tenantId, args)`；顺带在方法 Javadoc 里写明「租户必须透传，
曾经写死 default 导致与同进程的 read/write 语义不一致」。全局记忆的租户隔离是成立的
（其 `user_id` 列存的就是 tenantId，见 `saveGlobalMemory`），所以这一处修复真正堵住了泄漏。

**实测**：以 `tenantId=e2e-tenant-x` 写入全局记忆后，同一租户检索命中、另一租户检索为 0 条。

> ⚠️ **修复时新发现（D8b，未修）**：租户维度只到「全局记忆」为止。
> - `l1_atomic_memory` / `l2_scene_block` / `l3_persona` **三张表都没有 tenant 列**
>   （`H2OltpMemoryRepository.initSchema()`），`listPersonas(userId)` / `recallAtomicByType(userId, ...)`
>   也不带租户条件 → **用户长期记忆在不同租户间共用同一份数据**。所以 `memory_search` 的
>   「用户记忆」分支即便传对租户也做不到隔离。
> - 四个工具的 `inputSchema` **都没有声明 `tenantId`**，而 `call()` 却从 args 里读它 ——
>   规范客户端永远不会传，实际恒为 `default`。
>
> 要真正多租户，需给 L1/L2/L3 加租户列并迁移既有 H2 库，属**表结构变更**，未在本轮动手。

### D9（**高**）`listUserMemory` 不指定 category 时丢掉全部 L1 记忆

```java
for (L1AtomicMemory memory : oltp.recallAtomicByType(userId, hasText(category) ? category : "", 500)) {
```

category 为空时传的是**空串**，而 SQL 是 `WHERE user_id = ? AND memory_type = ?`
（`H2OltpMemoryRepository:181-186`）→ `memory_type = ''` 永不匹配 → **恒返回空列表**。

触发面很广：`GET /api/ai/memory/user/{userId}`（不传 category）、
MCP `memory_read`（`nullableStr` 得到 null）与 `memory_search`（同）全部踩中。
→ 「查用户长期记忆」实际只返回 L3 画像，`history` / `custom` 类别的 L1 **一条都拿不到**。

**真机确认（2026-09-15）**：写入一条 `category=custom` 的用户记忆后，
`memory_read{category:"custom"}` 读到 1 条、`memory_read{不带 category}` 读到 **0 条** ——
同一条数据「带上类别才可见」。

→ 修复方向：category 为空时应走「按用户取全部类型」的重载，而不是拿空串当类型值。
⚠️ 不能简单改成「不加 memory_type 条件」：`l1` 表里还存着 `global`（`user_id` 列放的其实是 tenantId）
与 `session_var`，不加类型过滤会把它们混进「用户长期记忆」结果里。建议显式排除
`global` / `session_var`（用户记忆类别只有 persona / preference / history / custom）。

> **同一处根因还导致 D13 的「清空」分支失效**（见下）：
> `deleteUserMemory` 在 id 为空时同样调用 `recallAtomicByType(userId, "", 10_000)`，
> 拿到空列表 → **一条都删不掉**，却返回 `deleted: true`。真机实测：MCP `memory_delete`
> 不带 id 返回 `deleted: true` 后，重新读取记录仍在（count=1）；改为按 id 删除才生效。
> 因此修 D9 时用同一处「按用户取全部类型」的能力，可以**一并解掉 D13 的清空分支**。

### D10（**高**）分页是「先截断再分页」，500 条以上静默丢数据

`getSessionVars` / `listUserMemory` / `listGlobalMemory` 三者同构：先 `recall*(..., 500)` 取一页，
在内存里 sort，再 `paginate(records, limit, offset)`。

→ 上限 500 条是硬截断：第 **501** 条起永远不可见，前端翻页到底也看不到，且**无任何提示**。
表面上接口有 `limit`/`offset`，语义却不成立。真正的分页必须下推到 SQL（`LIMIT/OFFSET`），
或至少用游标式翻页。

**难点（决定了修法）**：返回结果是 **L3 画像 + L1 原子记忆两路合并后在内存里按时间排序**的，
所以不能只给某一路的 SQL 加 `LIMIT/OFFSET` —— 那会把「全局最新」变成「各表最新」。两条路线：

| 路线 | 做法 | 代价 | 适用 |
| --- | --- | --- | --- |
| A（正确） | 单条 `UNION ALL`（l3 ∪ l1）带 `ORDER BY ts DESC LIMIT ? OFFSET ?`，另给 `COUNT(*)` | 需改 `OltpMemoryRepository` 契约（接口 + H2 实现 + 测试里的 Fake 实现），改动面中等 | 数据量会涨上去 |
| B（务实） | 保留内存合并排序，但把 500 的硬上限提到可配置值，并在返回体里带上 `total` / `truncated` 标记 | 小，几分钟 | 单机、量级可控 |

→ **建议先做 B 消除「静默丢数据」**（哪怕只加一个「结果被截断」的明确信号），
等量级真的上来再上 A。无论哪条，都必须把「上限」变成**可见**的，否则前端永远不知道自己少看了数据。

### D11（中）列表返回的时间不是记忆时间，导致排序声明失效

`AiMemoryService.record()` 把所有记录的 `ts` 写成 `System.currentTimeMillis()`、
`timestamp` 写成 `nowText()`（当前时刻），**丢弃了 `L1AtomicMemory.ts()` 与 `L3Persona.updatedTs()` 的真实时间**。

→ `listUserMemory` / `listGlobalMemory` 里的 `records.sort(by ts desc)` 排的是「查询时刻」，
「按时间降序」不成立（实际接近遍历顺序）；
→ 前端拿到的 `ts`/`timestamp` 全是查询时刻，**记忆的真实发生时间在接口层丢失**。

### D12（中）`deleteUserMemory(id)` 删不掉 L3 画像

```java
if (hasText(id)) { oltp.deleteAtomicMemory(id); }   // 只删 L1
```

而 `listUserMemory` 返回的 record 里 `id` **既可能是 `L3Persona.id` 也可能是 `L1AtomicMemory.id`**。
拿 persona 的 id 回来删 → `DELETE FROM l1_atomic_memory WHERE id = ?` 影响 0 行，
**画像纹丝不动**，接口却仍回 `deleted: true`（叠加 D13）。

### D13（中）删除类接口恒返回 `deleted: true`

`AiMemoryController` 三个 `@DeleteMapping` 与 MCP `memory_delete` 都是**无条件**
`Map.of("deleted", true)`，不参考任何影响行数。

叠加两处放大：
- D12：删不到也报成功；
- MCP `memory_delete` 的 `target=session` 且 `key` 为空时 → `deleteSessionVar(..., "")`
  → `hasText("")` 为 false → **直接 return，什么都没删**；
  但工具描述写的是「id/key 为空时清空该范围全部记忆」——**声明的语义没实现，且静默成功**。

→ 调用方（WorkBuddy）会据此认为删除已完成。

### D14（中）向量召回链路整体不存在

| 事实 | 证据 |
| --- | --- |
| `PureJavaVectorUtil` 零调用方 | 全仓库 grep 只命中自身与 `L1AtomicMemory` 的 Javadoc |
| `embedding` 恒为 null | `AiMemoryService` 三处 `new L1AtomicMemory(...)` 第 7 个参数全传 `null` |
| 无向量检索方法 | `OltpMemoryRepository` 只有 `recallAtomicByType/BySession`，均按 `ORDER BY ts DESC` |

→ `embedding` BLOB 列（含 `toBytes`/`fromBytes` 序列化）**从未被真实路径走到**；
所谓「记忆召回」实际是**时间倒序**，没有任何语义相似度。
附带：`rankScores(query, corpus, topK)` 的 `topK` 参数**被完全忽略**（算完所有分数直接返回、未排序）。

**决定性事实（2026-09-15 补充读代码）**：`PureJavaVectorUtil.featureVector()` 是
**字符级哈希袋** —— `bucket = ch * 31 % dimensions`，对每个字符计数后 L2 归一化。
它**不是嵌入模型**，没有语义：中文下余弦相似度几乎只反映「共用字符比例」
（「猫粮」与「猫砂」会因共用一个「猫」而高分，「猫咪」与「猫科动物」也仅因字形重合而相似）。
即「补齐调用点」也只能得到**字符重叠度**，不是语义检索 —— 不能靠接线把它变成宣传中的能力。

三条路线，需**先定方向再动手**：

| 路线 | 做法 | 代价 | 结果 |
| --- | --- | --- | --- |
| A（下架，推荐） | 删 `PureJavaVectorUtil` 与 `embedding` 列的使用，明确 `memory_search` 就是子串检索 | 近乎为零 | 消除误导，无新风险 |
| B（降级补齐） | 写入时算 `featureVector`、查询时按余弦 Top-K | 小 | 仍是字符重叠度，价值有限，须改名说清 |
| C（真语义） | 接入嵌入模型（项目已用 DashScope）算向量，写 L1 时落库、查询时余弦 Top-K + 阈值 | 大：新增网络依赖、每次写入多一次远程调用、必须设计失败降级 | 真正的语义召回 |

→ 若这个能力现在没人用，A 最干净；若确实需要「模糊找得到」，C 才值得投入，B 属于自我安慰。

### D15（中）会话消息写入 L0 时 `user_id` 恒为 null

`AiConversationMemory.recordTurnEvent()`：

```java
trajectoryRecorder.record(sessionId, null, traceId, "user",
        TrajectoryRecorder.SOURCE_USER_MESSAGE, userMessage, null);
```

第 3 个参数即 `userId`，**写死 null**。
→ 对话产生的 L0 行没有用户归属，`ArrowOlapAnalyticsRepository.userDailyActivity(userId)`
与任何「按用户的会话统计」**永远匹配不到会话消息**。D1 已补来源分类；本条只剩用户归属维度待处理。

### D16（中）共享模式下 `fork` / `title` / `restore` 三个方法失效

`AiConversationMemory` 支持两种后端，但只有 `snapshot`/`appendTurn`/`prepareCompression`/
`applyCompression` 走 `loadSession()`（后端无关）；`fork()`、`title()`、`restore()` 直接读进程内 `sessions`。

→ 共享模式（RocksDB）下：`fork()` 对「只存在于存储里」的会话返回 0；
且 fork 结果只 `sessions.put(...)` **不回写存储**（等于丢失）；`title()` 返回空串。
→ 修复方向：三个方法统一改走 `loadSession()` + `storeSession()`。

### D17（低）「今日」的定义在 OLTP 与 OLAP 里不一致

- `H2OltpMemoryRepository.countL0Today()` 用 `java.util.Calendar`（JVM **本地时区**）；
- `ArrowOlapAnalyticsRepository.userDailyActivity()` 用 `ts / 86_400_000L`（**UTC 日界**）。

→ 中国时区（UTC+8）下，OLAP 的「今天」在**本地 08:00 之前是错的**（还在 UTC 的前一天），
且同一份数据的两个「今日」数字对不上。建议统一为 `ZoneId.systemDefault()` 日界。

### D18（低）`framework-ai` 依赖 `agent-memory-core`（框架依赖业务模块）

- `framework/framework-ai/pom.xml` 声明依赖 `agent-memory-core`；
- `framework/framework-ai/.../AiAgentService.java` 直接
  `import com.zimo.module.agentmemory.chat.AiConversationMemory;` 并在第 120 行 `new` 出来。

与 `AGENTS.md`「共享能力放 `framework/`、业务插件放 `modules/`」的**依赖方向相反**：
框架层无法脱离该业务模块单独使用，业务模块的演进会反向牵动框架。
→ 建议把 `AiConversationMemory` / `AiChatMessage` 上移到框架层，或抽接口由业务侧实现注入。

### D19（低）ETL 每次追加都全量重写 OLAP 文件

`ArrowOlapAnalyticsRepository.appendRows()` 先 `new ArrayList<>(this.rows)` **整体拷贝**，
再 `exportToFile()` **重写整个 `.arrow` 文件**（TRUNCATE_EXISTING + 全量 writeBatch）。

→ 每分钟一次定时任务，数据集越大耗时越长（O(N) 每次）；且全量 L0 的 `content` 常驻内存。
另：`AsyncLogSyncTask.fullRebuild()` 用 `offset` 分页遍历，**同步期间的并发写入会让偏移漂移**（跳行）。

### D20（低）`MemoryArchRepository.list` 的 limit 与 offset 裁剪口径不一致

```java
args.add(Math.max(1, Math.min(size, 200)));      // limit 裁到 200
args.add(Math.max(0, page) * Math.max(1, size)); // offset 用的是未裁剪的 size
```

→ `size > 200` 时（如 size=500、page=1）offset=500 而 limit=200，**跳掉 300 行**。

### D21（低）种子数据污染「已提取」统计指标

`MemoryArchRepository.countExtracted()` 统计 `type='USER' AND source='自动'`，
而 `AgentMemoryAutoConfiguration.seedArchIfEmpty()` 写入的 11 条演示 USER **`source` 全是「自动」**。

→ 全新库上「已提取」卡片直接显示 **11**，而这些并非任何真实抽取结果。
与 D4 同源，但后果不同：D4 是「生产库多了演示数据」，D21 是**指标失真**。

---

### 本次复读的正面观察（记录以免后续改动踩掉）

- **双库分离 + SPI 插拔**：`MemoryStorageFacade` → `OltpMemoryRepository` / `OlapAnalyticsRepository`，
  再由 `StorageRouter` 按 `engine` 路由到 `StorageProvider`。业务层不感知底层实现，扩展新库只需加 Provider。
- **`MemoryFileStore` 的并发设计**：进程内 `ReentrantReadWriteLock` + 跨进程 `FileLock` 双级锁，
  写路径 `tmp` → `ATOMIC_MOVE` 原子替换；且注释解释了「RocksDB 后端为何不需要文件锁」。
- **`AiConversationMemory` 的压缩协议**：`prepareCompression` 产出候选（带 `revision` 版本号）→
  模型摘要 → `applyCompression` 用 `revision` + `matchesCandidateMessages` 前缀比对做乐观校验，
  版本过期即整体放弃。这是「不破坏会话状态」的正确做法。
- **`ArrowOlapAnalyticsRepository.toString()`**：显式 `UTF_8` 解码并注释点明
  「Windows 默认 GBK 会乱码」——避开了真实的跨平台坑。
- **`MemoryFileService.sanitizeAgentId`**：先拒 `..` / 路径分隔符，再替换非法字符，防路径穿越。
- **行数规范**：本模块主要文件有效行全部达标（最大 `H2OltpMemoryRepository` 300 行，
  上限 500；`AiConversationMemory` 226；`AiMemoryService` 174）。

---

## 九之三、2026-09-15 修复记录（D7 / D3 / D8）

### 改动文件

| 文件 | 改动 |
| --- | --- |
| `security/AiMemorySensitiveFilter.java` | 七类规则重写（身份证 18/15 位、银行卡留末 4、内网 IP 三段网段）；新增**类加载期掩码组引用校验**；Javadoc 写明规则顺序语义 |
| `mcp/MemoryMcpAccessGuard.java` | **新增**：两档准入策略（配令牌则校验，未配则仅回环）+ 定长比较 + 可诊断拒绝文案 |
| `mcp/MemoryMcpEndpoint.java` | 注入守卫；`handle()` 改返回 `ResponseEntity`，未授权给 **HTTP 401 + JSON-RPC 错误体**；`dispatch()` 拆出；GET 不校验的理由写入 Javadoc |
| `mcp/MemoryMcpToolkit.java` | `search` 透传 `tenantId`（去掉硬编码 `default`） |
| `autoconfig/AgentMemoryProperties.java` | 新增 `mcpToken` 字段 |
| `autoconfig/AgentMemoryAutoConfiguration.java` | 新增守卫 Bean；按档位打 INFO/WARN 日志 |
| `application.yml`（agent-application / agent-memory-application） | 新增 `mcp-token: ${AGENT_MEMORY_TOKEN:}` |
| `agent-auth/.../AuthProperties.java` | 注释改为指向真实实现的守卫（此前是「声称有校验但实际没有」） |

### 验证证据

1. **单测**：`mvn -pl modules/agent-memory/agent-memory-core -am test`
   → **42 tests / 0 failures**（含新增 16 例：脱敏 11 + 准入 5）；`framework-autoconfig` 24 例同绿。
2. **JDK 17 实测对照**：`tmp/verify/d7-before-after.jsh` 复刻新旧规则，修复前 18 位身份证
   `find=false`、掩码抛 `IndexOutOfBoundsException: No group 2`、银行卡输出
   `6222021234567890123****`、内网 IP 输出 `192.168.***1.100`；修复后分别为
   `110101********7758`、`****0123`、`192.168.*.*`。
3. **真机（重启后打真实 HTTP）**：`tmp/verify/memory-fixes-check.py`
   - `auth-on`（配 `AGENT_MEMORY_TOKEN`）→ **14 pass / 0 fail**：无凭据 401、错令牌 401、
     正确令牌 200、拒绝文案含 `Authorization`；含身份证写入不再报错且落库已脱敏；
     非默认租户全局记忆可被同租户检索、对异租户不可见。
   - `auth-off`（未配令牌）→ **10 pass / 0 fail**：回环来源无凭据仍 200（本机客户端不受影响）。
   - 启动日志分别出现「已启用令牌鉴权」INFO 与「仅放行回环来源」WARN。

> 验证期间写入的测试记忆已按 id 清除（清空分支是坏的，见 D9/D13 关联），
> 最终 `memory_read` / `memory_search` 复核均为 0 条残留。

---

## 十、测试覆盖（core/src/test）

7 个原有测试类：`TrajectoryRecorderTest`、`MemoryFileStore`（含 RocksDB）、`StorageRoutingTest`、
`AsyncLogSyncTaskTest`、Arrow OLAP 查询、`AiConversationMemory` 事件日志与阶段化剪枝等；
2026-09-15 新增 `AiMemorySensitiveFilterTest`（11 例）与 `MemoryMcpAccessGuardTest`（5 例），
合计 **42 例**。

> 说明：迁移后 `AsyncLogSyncTaskTest` 已同步至 `L0RawLog` 10 参构造；
> `TrajectoryRecorderTest` 中两个接口不存在的空方法桩已移除。
>
> 2026-09-29 D2 追加 4 个定向用例：来源 ETL 全量/增量/重启、Arrow 来源查询与重载、七列旧文件追加，以及分析 Controller 响应来源。

---

## 十一、短期会话记忆与闲置清理

标准 Harness 聊天将 AgentScope `AgentStateStore` 中的同会话消息状态作为短期上下文；已有 `CompactionConfig` 负责历史压缩。该状态与 agent-memory 的 L1 `session_var` 变量/归档、旧版 `AiConversationMemory` 属于不同能力，聊天不会再把 `session_var` 归档重复拼入提示词。

`RocksdbAgentStateStore` 使用 `astate/{agentId}/{sessionId}/{stateKey}` 保存状态，并在独立的 `astate-meta/{agentId}/{sessionId}` 键中维护最后活动时间。成功读取或写入 AgentState 后刷新时间；每小时运行的清理任务读取 `short-term-retention-days`，默认 7 天，删除超过该闲置期限的完整会话状态前缀和 metadata。首次扫描到没有 metadata 的历史会话时，先写入当前时间并给予完整 7 天宽限期，避免首次上线立刻删除存量会话。

管理员可在 agentmemory「记忆策略」页将保留期限设置为 1 至 365 天。页面通过 `/api/biz/sys/agent-setting` 保存设置，系统设置服务进行范围校验；应用组装层将生效值提供给清理任务，改动最迟在下一次每小时清理任务中生效。原有 `memory-session-retention-days` 仍表示 AgentScope 长期记忆配置，不作为 Harness AgentState 的清理期限；L0 轨迹、L1 `session_var` 与旧版会话内存不在本次清理范围。
