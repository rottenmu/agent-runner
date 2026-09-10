# agent_runner 全项目代码解读（基于 CodeGraph 知识图谱）

> 生成方式：`codegraph` 1.5.0 建立代码知识图谱后系统通读
> 索引时间：2026-09-10 | 工具：`codegraph status/files/query/node/callers/callees/explore`

---

## 一、索引概况

| 指标 | 数值 |
|---|---|
| 索引文件 | **881**（Java 663 主码 + 168 测试 + 配置/脚本） |
| 知识节点 | **18,151** |
| 关系边 | **36,342** |
| 语言 | java、xml、yaml、properties、python |
| 类 / 接口 / 方法 | 804 / 146 / 6,743 |
| REST 路由 | **298** |
| 命名空间 | 831 |

> ⚠️ **前端未纳入索引**：根 `.gitignore` 第 5 行 `frontend/` 明确排除（本仓库仅托管后端代码），CodeGraph 遵循 gitignore 规则，故 `frontend/web-shell`、`frontend/modules/*`、`frontend/agent-memory-ui` 均不在图谱内。磁盘上存在但非仓库托管范围。

---

## 二、顶层架构

根 `pom.xml` 三个一级模块，严格遵循 `AGENTS.md` 的三层分工：

```
agent_runner/
├── framework/           # 共享框架层（跨模块能力）
│   ├── framework-common/      # 契约与工具，无 Spring 上下文
│   └── framework-autoconfig/  # 全局自动配置
├── modules/             # 业务插件层（12 个插件）
└── agent-application/   # 应用组装层（唯一启动入口）
```

**主入口** `agent-application/pom.xml` 依赖 13 个 `*-autoconfig`，通过自动装配组装应用：

```
framework-autoconfig → module-sys-auth → module-ai(agent-harness)
→ module-feishu → module-datasource → rag → agent-intent
→ module-tools → module-security → agent-trace → agent-memory
```

`AgentApplication.java` 仅 11 行，纯 `@SpringBootApplication` + `SpringApplication.run`，不含任何业务配置——符合"应用组装层"定位。

---

## 三、框架层（framework/，57 文件）

### 3.1 framework-common —— 跨模块契约

| 类别 | 类 | 说明 |
|---|---|---|
| **统一契约** | `ApiResponse`、`PageQuery`、`ErrorCode`、`BizException` | 全局返回体/分页/错误码/业务异常 |
| **插件契约** | `PluginRegister`（接口） | 插件身份声明：`pluginId / pluginName / apiPrefix / frontendRoute / frontendModule / agentName / order` |
| **安全门面** | `SecurityFacade` + `NoopSecurityFacade` | 业务模块可插拔的安全能力抽象 |
| **文件存储 SPI** | `FileStorageService`、`FileStorageProvider`、`FileStorageContext`、`FileStorageFactory` | 存储后端可插拔（本地 / RocksDB） |
| **AI 事件** | `AiEventPublisher`、`AgentStepEvent`、`ToolCallEvent`、`ConversationTurnEvent` | 智能体执行过程事件模型 |
| **工具** | `DocNoGenerator`、`ValidationUtil`、`IntentConfidence` | 单据号生成、校验、意图置信度 |

### 3.2 framework-autoconfig —— 全局自动配置

核心五套能力，全部带条件装配：

| 配置类 | 职责 |
|---|---|
| `FrameworkWebAutoConfiguration` | Web MVC 全局配置 |
| `MybatisPlusConfig` | MyBatis-Plus 分页/逻辑删除 |
| `FrameworkApiRegistryAutoConfiguration` | **API 注册表**（核心，见下） |
| `FileStorageAutoConfiguration` / `RocksdbStorageAutoConfiguration` | 文件存储后端切换 |
| `ModuleControllerScanRegistrar` | **模块控制器扫描**（核心，见下） |

#### 机制 A：插件注册（PluginRegistry）

```java
public interface PluginRegister {
    String getPluginId();      // 插件唯一标识
    String getApiPrefix();     // 后端接口前缀
    String getFrontendRoute(); // 前端路由
    String getFrontendModule();// 前端模块名
    String getAgentName();     // 绑定智能体名
    default int getOrder() { return 100; }
}
```

`PluginRegistry` 收集全部实现，`LoadedModuleLogger`（`ApplicationRunner`）在启动完成时打印已加载插件清单。

**当前仅 3 个插件实现**：

| pluginId | 名称 | apiPrefix | 前端路由 | order |
|---|---|---|---|---|
| `ai` | AI智能体 | `/api/ai` | `/ai` | 4 |
| `feishu` | 飞书平台 | `/api/feishu` | `/integration/feishu` | 5 |
| `sys` | 系统管理 | `/api/biz/sys` | `/biz/sys` | 6 |

> 其余 9 个模块（memory/rag/tools/security/auth/intent/datasource/trace）**不声明插件身份**，属于无前端入口的能力模块，仅通过 autoconfig 装配 Bean。

#### 机制 B：模块控制器扫描（@ModuleControllerScan）

解决"启动类在 `com.zimo.agentapplication` 包，无法扫描到各模块 controller"的问题：

```java
// ModuleControllerScanRegistrar
scanner = new ClassPathScanningCandidateComponentProvider(false);  // 关闭默认过滤器
scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));  // 只收 @RestController
registry.registerBeanDefinition(beanClassName, candidate);  // Bean 名 = 全限定类名
```

- `useDefaultFilters=false` → **只注册 controller**，绝不连带 service/entity/mapper
- Bean 名用全限定类名 → 避免跨模块同名类冲突

#### 机制 C：API 注册表（api_registry）

启动时自动扫描全部 `@RestController`，把接口元数据（路径、方法、参数、描述、hash）落库，供前端菜单/权限/接口文档消费。

链路：`ApiEndpointScanner` → `ApiHashGenerator`（内容 hash 判重）→ `ApiRegistryRepository`（**原子 UPSERT**）→ `ApiRegistrySchemaInitializer`（建表）→ `ApiRegistryStartupRunner`（启动执行）

框架建表脚本 `db/framework/api_registry.sql` 建 `api_registry` 表，含 `uk_api_registry_hash` 唯一索引。

---

## 四、业务插件层（modules/，808 文件）

### 4.1 模块规模总览

| 模块 | 主码 | 测试 | 控制器 | 定位 |
|---|---:|---:|---:|---|
| **agent-harness** | 149 | 15 | 24 | AI 智能体管理面（最大模块） |
| **agent-spring-boot-starter** | 104 | 36 | 5 | AI Agent 运行时引擎 |
| **module-feishu** | 124 | 48 | 8 | 飞书开放平台集成（测试覆盖最高） |
| **module-sys** | 61 | 32 | 9 | 系统管理 + RBAC 权限 |
| **agent-memory** | 45 | 8 | 4 | 四层金字塔记忆（独立可执行） |
| **agent-rag** | 33 | 0 | 2 | 检索增强生成 |
| **module-tools** | 31 | 0 | 2 | 工具治理与执行 |
| **module-security** | 26 | 0 | 2 | 安全合规（审批/审计/敏感词） |
| **agent-auth** | 15 | 8 | 2 | 认证（Sa-Token） |
| **agent-intent** | 16 | 2 | 2 | 意图识别（规则+LLM 混合） |
| **agent-datasource** | 10 | 0 | 2 | 多数据源连接器 |
| **agent-trace** | 8 | 1 | 0 | GenAI 可观测性（OTel 语义） |

### 4.2 agent-harness —— AI 智能体管理面

**职责**：智能体的"配置与治理中心"，不含推理内核（内核在 starter）。

| 子域 | 关键类 |
|---|---|
| **智能体管理** | `AiAgentManagementService`、`AiManagedAgent`、`AiManagedAgentContributor`、`AiManagedAgentRuntimeInvalidator` |
| **能力编排** | `AiAgentCapability(Service)`、`AiManagedAgentProfileResolver` |
| **技能管理** | `AiManagedSkill`、`AiSkillZipImportService`（ZIP 包导入）、`AiSkillZipParser`、`AiSkillZipStructureValidator` |
| **提示词** | `AiPromptTemplate`、`AiPromptVersion`、`AiPromptTemplateGenerator`、`CurlApiDocImporter` |
| **模型配置** | `AiModelConfig*`（多模型接入、连通性测试） |
| **MCP** | `AiMcpConfig(Service)` |
| **多智能体协作** | `MultiAgentCollaborationService`、`AgentSession`、`AgentTask`、`AgentTaskFlow`、`SessionForkResumeService` |
| **工作流** | `WfWorkflowEngine`、`WfRunService`、`WfWorkflowVersion` |
| **可观测性** | `ObservTraceService`、`AlertService`、`DashboardService`、`TestRunnerService`、`SessionEventLogService` |
| **A/B 测试** | `AiAbTest(Service)` |

自动装配共 9 个 `@AutoConfiguration`：`AiModuleAutoConfiguration`、`AiSkillCoreAutoConfiguration`、`AiSkillAdminAutoConfiguration`、`AiWorkflowAutoConfiguration`、`AiCollabAutoConfiguration`、`AiObservAutoConfiguration`、`AiModelConfigAutoConfiguration`、`AiControllerScanAutoConfiguration`、`AiSkillMultipartWebAutoConfiguration`。

### 4.3 agent-spring-boot-starter —— AI 运行时引擎

**职责**：智能体推理执行内核，被 harness 依赖。可独立作为 Starter 发布。

| 能力域 | 关键类 | 说明 |
|---|---|---|
| **服务入口** | `AiAgentService` | 50 符号，被 25 个文件依赖——**全系统最核心类** |
| **编排路由** | `AiHarnessAgentRouter`、`AiHarnessAgentRegistry`、`AiHarnessSessionKeyFactory`、`AiHarnessAgentFactory` | 多智能体路由 + 会话键 + LRU 注册表 |
| **运行时** | `AiAgentRuntime`、`AiAgentRuntimeFactory`、`AiAgentRuntimeStatus` | 运行时生命周期 |
| **模型接入** | `AiChatClient` → `OpenAiCompatibleChatClient` | OpenAI 兼容协议（对接 DashScope） |
| **技能体系** | `AiSkill`、`AiSkillRegistry`、`AiApiSkill`、`ToolPipeline` | 技能注册与工具管线 |
| **工具治理** | `ToolHook`、`ToolGuard`、`ToolApprovalHandler`、`ToolCallContext` | 调用前/中/后钩子 + 审批 |
| **中间件** | `AiAgentMiddleware`、`MiddlewareChain`、`AiRequestInterceptor`、`AiRequestContext` | 请求拦截与中间件链 |
| **沙箱** | `SandboxBackend` → `LocalSandboxBackend` / `HttpRemoteSandboxBackend` | 命令与文件执行隔离 |
| **意图** | `IntentChecker` → `RuleIntentChecker` / `LlmIntentChecker` / `VectorIntentChecker`、`SkillIntentCompositeChecker` | 三级意图判定 |
| **插件热加载** | `DynamicPluginManager`、`PluginContext`、`PluginEventBus` | 运行时插件装载 |
| **MCP** | `McpController`、`ToolBridge` | JSON-RPC 工具桥 |
| **A2A** | `A2aController`、`A2aAgentCard` | Agent-to-Agent 协议 |
| **互操作** | `AgentInteropService`、`ExternalHarnessSubagent`、`InstructionFileSkill` | 外部 Harness 子智能体 |
| **状态存储** | `RocksdbAgentStateStore`、`RocksdbBaseStore` | 会话状态 RocksDB 落盘 |

**核心对话链路**（`codegraph callees` 实证）：

```
AiChatController
   ├─→ AiAgentService.chat(...)
   │      ├─ runInterceptors()        请求拦截器链
   │      ├─ router.route()           智能体路由
   │      ├─ registry.withAgent()     获取/创建 Agent 实例
   │      ├─ conversationMemory       会话记忆（AiConversationMemory）
   │      ├─ driveRuntime()           
   │      │    └─ executeCore()  →  MiddlewareChain → AiAgentRuntime
   │      │         └─ AiSkillRegistry → ToolPipeline（Hook/Guard/Approval）
   │      └─ tryCompressConversation() 超阈值压缩（summarizeWithRetry）
   ├─→ IntentRecognitionService       意图识别
   ├─→ IntentAwareSkillRouter         意图驱动技能路由
   ├─→ AiAgentManagementService       智能体配置
   └─→ SecurityFacade                 安全门面
```

`AiAgentService` 字段构成即其能力全景：`properties / skillRegistry / runtime / chatClient / router / registry / sessionKeyFactory / conversationMemory / oltpMemoryRepository / eventPublisher / requestInterceptors / middlewares / pluginEventBus`。

### 4.4 module-feishu —— 飞书集成

**规模最大、测试最充分**（124 主码 + 48 测试）。

| 子域 | 关键类 |
|---|---|
| **网关** | `FeishuAgentGatewayMessageHandler`、`FeishuAgentDispatchService`、`FeishuAgentCommandRouter`、`FeishuAgentRateLimiter` |
| **凭证** | `FeishuAgentCredentialService(Impl)`、`FeishuAgentCredentialValidator`、`FeishuAgentCredentialInterceptor` |
| **渠道** | `FeishuChannelMessageListener`、`FeishuChannelClientManager`、`FeishuAiChannelMessageHandler` |
| **CLI 服务** | `FeishuCliExecutor`（`ProcessFeishuCliExecutor`）、`FeishuBitableCliService`、`FeishuDocumentCliService`、`FeishuCalendarCliService`、`FeishuTaskCliService` |
| **消息/回复** | `FeishuMessageService`、`FeishuAgentReplyService`、`FeishuCardTemplateFactory`、`FeishuProjectCardRenderer` |
| **AI 技能** | `FeishuBitableCreateRecordAiSkill`、`FeishuDocumentCreateAiSkill`、`FeishuDocumentAppendAiSkill` |
| **事件** | `FeishuEventController` → `FeishuEventHandler`（`NoopFeishuEventHandler` 兜底） |
| **映射** | `FeishuUserMappingService`、`FeishuUserPermissionBinder` |
| **日志** | `FeishuMessageLogService`、`FeishuCliCallLogService`（调用审计） |

特色：支持**独立数据源**（`FeishuDataSourceHolder` + `FeishuDataSourceProperties`，仅允许 MySQL/SQLite）、官方客户端封装（`Official*Client` 系列）、CLI 进程执行器。

### 4.5 module-sys —— 系统管理与权限

| 子域 | 关键类 |
|---|---|
| **RBAC** | `SysRbacService(Impl)`、`SysRoleService`、`SysMenuService`、`SysUserService` |
| **权限拦截** | `PermissionInterceptor`（含 `DEFAULT_WHITELIST_PATHS`）、`PermissionService`、`PermissionCache`、`UserPermissionContext` |
| **数据权限** | `@DataScope` + `DataScopeMybatisPlugin`（MyBatis 插件改写 SQL）、`DataScopeService`、`DataScopeEnum` |
| **字段脱敏** | `@FieldDesensitize` + `FieldMaskService` + `DesensitizeUtil` |
| **操作日志** | `OperLogInterceptor`、`OperLogService` |
| **接口注册** | `SysApiRegistry*`（消费框架 api_registry） |
| **安全** | `SqlFilterUtil`（SQL 注入过滤）、`SysUserPasswordPolicy` |

注解体系：`@HasPerm`、`@IgnorePermission`、`@DataScope`、`@FieldDesensitize`。

### 4.6 agent-memory —— 四层金字塔记忆

**唯一允许嵌入式存储的模块**（AGENTS.md 明确豁免）。三子模块：`core` / `autoconfig` / `application`（独立可执行）。

| 层 | 模型 | 存储 |
|---|---|---|
| L0 | `L0RawLog`（traceId + source 来源标识） | H2 OLTP + Arrow OLAP |
| L1 | `L1AtomicMemory` | H2 |
| L2 | `L2SceneBlock` | H2 |
| L3 | `L3Persona` | H2 + Caffeine 缓存 |

- **双库架构**：`MemoryStorageFacade` 统一门面，`OltpMemoryRepository`（H2 读写负载分离）/ `OlapAnalyticsRepository`（Arrow + Calcite）
- **SPI 插拔**：`OltpStorageProvider` / `OlapStorageProvider` + `MemoryStorageFactory`
- **ETL**：`AsyncLogSyncTask` 游标增量同步 + 首轮全量去重重建
- **安全**：`AiMemorySensitiveFilter` 敏感脱敏（已验证 `138****5678`）
- **对外**：`AiMemoryController`（CRUD）、`AgentMemoryAnalyticsController`（OLAP）、`MemoryArchController`（分层配置）、`MemoryFileController`（文件兼容）、`MemoryMcpEndpoint`（MCP）
- **对话记忆**：`AiConversationMemory`（被 `AiAgentService` 直接依赖）
- **轨迹**：`TrajectoryRecorder`（9 类来源事件采集）

> 详细解读见 `docs/agent-memory-backend-code-description.md`

### 4.7 其余能力模块

| 模块 | 核心类与定位 |
|---|---|
| **agent-rag** | `RagPipelineService`（解析→切分→清洗→向量化→重排）、`RagRetrieveService`、`RagKbPermissionService`、`RagEvaluationService`、`RagVersionService`、`RagRetrieveSkill`。8 张表：知识库/文档/分片/版本/权限/检索日志/评估 |
| **module-tools** | `ToolRegistry` + `ToolGovernanceService` + `ToolGovernor`；执行器 `GenericHttpExecutor`、`PythonScriptExecutor`（Nashorn/JSR223）；内置工具 `CalcTool`、`CodeTool`、`EmailTool`、`FileTool`、`HttpTool`、`ScheduleTool`、`TableTool`、`TransformTool` |
| **module-security** | `ContentSafetyService`（敏感词）、`ApprovalService`（审批流）、`AuditLogService`（审计）、`DataScopeService`、`ResourcePermissionService`、`OrgSyncService`（组织同步）；`SecurityFacadeImpl` 实现框架门面 |
| **agent-auth** | Sa-Token 体系：`SysStpInterface`、`SaTokenAuthSessionService`、`AuthController`（登录/注册）、`CurrentLoginUserUtils` |
| **agent-intent** | 混合识别：`IntentRuleStore` + `IntentEvaluator`（规则）→ `HeuristicIntentLlmParser` / `HttpIntentLlmParser`（LLM）；`IntentEntityExtractor`（槽位）、`IntentRuleReviewer`；`IntentConfidence` 三级置信度（明确/模糊/兜底） |
| **agent-datasource** | `DsConnectorFactory` + `DsConnectors`（多源连接器）、`DsDataSourceService`、`DsQuerySkill` |
| **agent-trace** | GenAI 语义可观测：`GenAiSpan`、`GenAiSpanExporter`（`LogGenAiSpanExporter`）、`GenAiTraceObserver`、`GenAiAttributeNames`（对齐 OTel GenAI 语义约定） |

---

## 五、数据层

### 5.1 持久化技术

- **ORM**：MyBatis-Plus（`mapper-locations: classpath*:mapper/**/*.xml`，开启驼峰映射、逻辑删除 `deleted` 字段）
- **数据源**：主应用 `jdbc:sqlite:./data/agent_runner.db`
- **文件存储**：`FileStorageFactory` SPI，支持 Local / RocksDB（`framework.storage.rocksdb.enabled: true`，路径 `data/rocksdb`）
- **记忆存储**：H2 文件库 `./data/agent-memory` + Arrow 列存 `./data/olap/l0_log.arrow`
- **建表方式**：`*SchemaInitializer` Java 初始化器为主（`ApiRegistrySchemaInitializer`、`FeishuSchemaInitializer`、`FeishuAgentSchemaInitializer`），SQL 脚本仅 1 个（`api_registry.sql`）

### 5.2 关键配置（`application.yml`）

```yaml
server.port: 9900
framework.api-registry.enabled: true        # 启动时扫描注册接口
framework.storage.rocksdb.enabled: true
sa-token: timeout 86400 / uuid / Authorization
ai.agent: model-name qwen3.7-max / dashscope_chat / max-iters 5
          memory-enabled: true / flush-trigger 600s / 保留期 90~180 天
plugin: sys=true, ai=true, intent=true, auth=true, feishu=true
agent-memory: h2-url MODE=LEGACY / sync-cron 每分钟 / sensitive-filtering: true
```

---

## 六、测试体系

| 维度 | 数据 |
|---|---|
| 测试文件 | **168** |
| 主码/测试比 | 663 : 168 ≈ **3.9 : 1** |
| 覆盖最好 | agent-spring-boot-starter（36）、module-feishu（48）、module-sys（32） |
| **零测试模块** | agent-rag（0）、module-tools（0）、module-security（0）、agent-datasource（0） |

**架构守护测试**（ArchUnit + 契约测试）：
- `PluggableArchitectureTest`（根：插件架构约束）
- `AuthBoundaryContractTest`、`SysControllerPermissionContractTest`、`PermissionAnnotationContractTest`（权限边界）
- `AiManagementControllerContractTest`、`AiManagementMapperContractTest`
- `SysStarterDependencyContractTest`
- `ApiRegistryOwnershipTest`、`AdminAuthBoundaryTest`
- 多个 `*JsonContractTest`（JSON 序列化契约）

---

## 七、架构一致性风险（阅读中发现）

| # | 问题 | 位置 | 说明 |
|---|---|---|---|
| 1 | **主数据源使用 SQLite** | `application.yml:6`（`jdbc:sqlite:./data/agent_runner.db`）、`api_registry.sql`（`AUTOINCREMENT`/`TEXT`/`sqlite_master`） | `AGENTS.md` 明确"本仓库禁止使用 SQLite、H2 等本地数据库"，唯一豁免是 agent-memory 的 H2+Arrow。当前主库 SQLite 属于**规则冲突**，迁移 MySQL 前需注意 SQL 方言差异（`AUTOINCREMENT` vs `AUTO_INCREMENT`、`TEXT` vs `VARCHAR`、`datetime('now')` vs `NOW()`） |
| 2 | 建表脚本缺失 | `db/` 下仅 1 个 SQL | `AGENTS.md` 要求"数据库结构变更使用模块化、可审计的 MySQL 版本脚本 `V<version>__<desc>.sql`"，当前主要靠 Java `*SchemaInitializer` 建表，不可审计、无版本管理 |
| 3 | 三模块零测试 | rag / tools / security / datasource | 安全合规模块（`module-security`）零测试风险最高 |
| 4 | 插件身份覆盖率低 | 12 个模块仅 3 个实现 `PluginRegister` | 9 个模块无前端入口声明，`LoadedModuleLogger` 只能打印 3 个；如需前端纳管需补齐 |
| 5 | 前端不在版本控制 | `.gitignore` 排除 `frontend/` | 前端（`web-shell`、`agent-memory-ui`）无法纳入 CodeGraph 静态分析，也无法在仓库内追溯 |

---

## 八、阅读路径建议（CodeGraph 命令）

```bash
# 全局状态与结构
codegraph status                                   # 索引统计
codegraph files --filter modules --max-depth 3     # 模块树

# 定位符号
codegraph query AiAgentService                     # 查符号定义
codegraph node -f <file> --symbols-only            # 单文件符号图 + 被依赖文件

# 调用链分析
codegraph callees AiAgentService                   # 它调用了谁
codegraph callers  AiAgentService                  # 谁调用了它
codegraph impact   AiAgentService -d 3             # 改动影响面

# 语义探查
codegraph explore "权限拦截器与数据权限"            # 一次性拿源码+调用路径
```

> 注意：`explore` 为语义检索，对中文长句可能命中无关模块，建议对已知符号优先用 `query` + `callees/callers` 精确分析。

---

## 九、一句话总结

`agent_runner` 是一个 **Java 17 / Spring Boot 3.4.5 的可插拔多模块 AI 智能体平台**：框架层提供插件契约 + 控制器扫描 + API 自动注册三大机制；`agent-spring-boot-starter` 提供推理内核（路由/技能/工具/沙箱/意图），`agent-harness` 提供管理面（智能体/技能/提示词/模型/工作流/协作/可观测），`module-feishu` 提供企业级渠道接入，`agent-memory` 提供四层金字塔记忆（唯一豁免的嵌入式存储），其余模块提供 RAG、工具治理、安全合规、认证、意图、数据源、链路追踪能力；总计 **663 个主码文件、298 条 REST 路由、168 个测试文件**。
