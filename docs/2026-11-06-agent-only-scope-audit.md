# Agent Runner 收敛为「AI 智能体平台」的范围审计（2026-11-06）

> 状态：**决策已定 —— 全量保留，不删除任何模块/工程**（2026-11-06 16:26 用户明确否决删除清单）
> 范围：后端 `framework/` + `modules/` + `agent-application/`，前端 `frontend/web-shell/` + `frontend/modules/`
> 方法：跨模块编译期 import 全量统计 + 后端/前端两份深度子审计 + 11 项关键结论亲自复核
> 前提：本期只做登录，不做权限体系；不讨论权限强弱，只讨论功能归属与补齐顺序
>
> ⚠️ **口径变更**：本文第 1~5 节的「可删/不可删」分析保留为**依赖事实与耦合面记录**，
> 但**不再作为删除建议**。产品收敛方式由「删模块」改为「**保留全量 + 补齐功能 + 修数据真实性**」。
> 后续行动以第六节为准。

---

## 零、一页结论

| 问题 | 结论 |
|---|---|
| **已完整可用** | **10 项**：智能体 CRUD/能力配置、技能 CRUD、提示词模板与版本、同步/流式对话、多智能体协同、工作流与图编排主流程、RAG 知识库、工具治理、意图规则、记忆召回注入链路 |
| **智能体范围内需完善** | **6 个功能域，约 20 项**，其中「建库不可复现」是唯一前置阻塞；数据源、飞书、API 文档、告警相关的前端 BUG **因保留决策恢复为必修** |
| **删除** | 🚫 **全部否决**。后端 3 个模块（飞书、链路 OTel 导出、数据源管理主体）+ harness 内 2 个子功能 + 前端 2 个模块 + 2 个遗留工程，**一律保留**。主壳 `Home.vue` / `MainLayout.vue` 等仅做**假数据与死链清理，不删页面、不删功能** |
| **不能删除** | 9 个模块/子模块 + 3 张关键表 + 主库文件；其中 `module-sys`、`module-security`、`workflow` 三个最容易被误删（见第五节）。**现追加**：上述 9 类保留对象同属不可删 |
| **保留带来的新增必做** | ① 两个遗留工程与 `web-shell` 同占 `16600` 端口，须错开；② `query_datasource` 技能、飞书渠道、OTel 导出、API 文档/告警、评测工作台全部要能跑通，而不是留着不接 |

**三条最容易踩的坑**：

1. **「没人 import 它」≠「可以删」**。依赖图里 `agent-harness`、`module-tools`、`agent-trace` 都是叶子节点（无人反向 import），但前两个是智能体核心，靠 Spring 装配生效，删了平台就没了。
2. **`module-sys` 删掉会让应用启动失败**。`agent-application` 有 2 个类硬 import 它（`DataInitializer`、`ShortTermMemorySettingsConfiguration`），且 `ai` 前端的技能编辑器和 `agentmemory` 的记忆设置各调用它的 1 个端点。
3. **`module-security` 是混合模块**：里面既有 RBAC/数据范围，也有**对话内容过滤**（敏感词、输出脱敏、危险 URL）和**工具审批**。整模块删 = 这两项智能体能力一起消失。

---

## 一、依赖事实（决定耦合面与改动半径）

### 1.1 跨模块编译期 import 反向依赖（全量统计）

| 模块 | 被谁依赖（import 数） | 含义 |
|---|---|---|
| `framework-common` | 14 个模块（59+31+20+17+…） | 全局基础，不可删 |
| `framework-ai` | `agent-harness`(59)、`module-tools`(13)、`module-feishu`(6)、`agent-trace`(5)、`agent-rag`(3)、`agent-application`(1) | 智能体运行时核心 |
| `framework-autoconfig` | 7 个模块 | 全局配置/Controller 扫描/API 注册表 |
| `agent-auth` | `module-security`(8)、`module-sys`(7)、`agent-application`(2) | 登录唯一实现 |
| `agent-memory` | **`framework-ai`(11)** | 被运行时硬依赖，**不可删** |
| `agent-datasource` | `agent-memory`(7) | 实为 `com.zimo.module.ds.storage`（storage 子模块），**不是** datasource-core 主体 |
| `module-sys` | `agent-application`(5)、`module-feishu`(4) | 见坑 2 |
| `agent-intent` | `agent-harness`(3) | 对话前置每轮调用 |
| `agent-rag` | `agent-harness`(1) + pom 显式依赖 | RAG 增强 |
| `module-feishu` / `module-security` / `module-tools` / `agent-trace` / `agent-harness` / `agent-application` | **无人 import** | 叶子节点，需按功能价值判断，不能只看依赖 |

### 1.2 五条亲自复核的硬依赖链

| # | 事实 | 证据 |
|---|---|---|
| 1 | **`SysUser` / `SysUserMapper` 在 `agent-auth`，不在 `module-sys`**（修正前几轮口径） | `modules/agent-auth/module-auth-core/src/main/java/com/zimo/module/auth/entity/SysUser.java`、`mapper/SysUserMapper.java`；`module-sys/.../test/.../AuthBoundaryContractTest.java` 明确断言 sys 内不存在这两个类 |
| 2 | `agent-application` 有 2 处硬 import sys → 删 sys 会 `NoSuchBeanDefinitionException` 启动失败 | `DataInitializer.java:6-9`（`SysRole`/`SysUserRole`/两个 Mapper）、`config/ShortTermMemorySettingsConfiguration.java:4`（`AgentSettingService`） |
| 3 | `framework-ai` 硬依赖 `agent-memory` | `AiHarnessAgentFactory.java:22-23`、`AiAgentService.java:12,16,52,66` |
| 4 | `agent-memory` 依赖的是 `ds.storage` 子模块，不是数据源业务 | `AgentMemoryAutoConfiguration.java:33-35`、`storage/spi/ArrowOlapStorageProvider.java:5-6` |
| 5 | 前端 `ai` 模块直接 import `workflow` 组件；`ai/menus.js` 是全平台唯一菜单聚合点 | `frontend/modules/ai/src/views/AiAgentManage.vue:616`、`frontend/modules/ai/menus.js:9-58` |

### 1.3 表与库

- 主库唯一：`jdbc:sqlite:./data/agent_runner.db`，约 62 张 `@TableName` 表共用；**删任何模块都不能删库文件**。
- `ps_sys_user` 属 `agent-auth`，登录必需，**必须保留**；`ps_sys_role*`/`ps_sys_menu*` 属 `module-sys`。
- `api_registry` 由 `framework-autoconfig` 建表、所有插件共享，不可删。
- `agent-memory` 是唯一独立库（H2 `./data/agent-memory` + Arrow `.arrow`）。
- `observ_*` 8 张表实体在 `agent-harness`（`com.zimo.module.ai.observ`），**不在** `agent-trace` jar 内。

---

## 二、已经完整可用（✅ 10 项）

> 判定口径：前端 `api/*.js` 路径 ↔ 后端 `@RequestMapping` ↔ 页面调用三者对齐，且增删改查闭环。
> **均为静态证据，未在运行态逐页实测**（沿用 `fullstack-gap-audit.md` 的 82% 置信度口径）。

| # | 功能 | 前端 | 后端 |
|---|---|---|---|
| 1 | 智能体 CRUD + 能力配置 | `ai/src/api/agent.js:4-16`、`AiAgentManage.vue:1009-1034` | `AiAgentAdminController` `/api/biz/ai/agents`、`AiAgentCapabilityController` |
| 2 | 技能 Skill CRUD / 导入 / 提示词绑定 | `agent.js:20-42`、`AiSkillEditorDrawer.vue:176` | `AiSkillAdminController`、`AiSkillCallController` `/api/biz/ai/skills` |
| 3 | 提示词模板 / 版本快照 | `agent.js:46-62`、`AiPromptTemplateManage.vue` | `AiPromptTemplateAdminController`、`AiPromptVersionController` `/api/biz/ai/prompt-templates` |
| 4 | 同步 + 流式对话 | `AiChatPage.vue:239`、`AiChatStreamController` 对应流式调用 | `AiChatController:28`、`AiChatStreamController:54` `/api/biz/ai/chat` |
| 5 | 多智能体协同（群组/任务分发/委派/冲突/回调） | `ai/src/api/collab.js:6-84`、`MultiAgentCollaboration.vue` | `CollabController` `/api/biz/ai/collab` |
| 6 | 工作流 + 图编排主流程 | `workflow/src/api/wf.js:6-82`、`WorkflowDesigner.vue:295-297`（VueFlow） | `WfWorkflowController`、`WfTemplateController`、`GraphController` `/api/biz/ai/graph` |
| 7 | RAG 知识库（库/文档/版本/评测/检索日志） | `rag/src/api/rag.js:6-120`、`RagManage.vue` | `RagController` `/api/biz/rag` |
| 8 | 工具中心 / 工具治理 | `tools/src/api/tools.js:6-70`、`ToolCenter.vue` | `ToolGovernanceController` `/api/biz/ai/tools` |
| 9 | 意图规则与评测 | `AiIntentRuleDialog.vue`、`AiIntentEvaluateDrawer.vue` | `IntentController` `/api/biz/intent`；`AiChatPreflight` 每轮 `parse`（规则档） |
| 10 | 记忆召回注入链路 | `MemoryAwarePromptBuilder` 等 | `AiAgentService:638 → MemoryPromptMiddleware:104 → personaFragment/recallFragment`；L3 画像注入已生效 |

**边界说明**：第 6 项「主流程可用」不含停止/超时（缺 `cancel` 端点）；第 10 项指「召回与注入已接通」，不含 L2 场景层。

### 2.1 「接通但有已知缺陷」——不计入完整可用

| 功能 | 缺陷 |
|---|---|
| MCP 配置 | `AiAgentManage.vue:1176,1198` 响应层级判错，保存成功也弹「保存失败」 |
| 模型配置「测试连接」 | **纯模拟**：只校验三字段非空 + 假延迟，不发真实 HTTP |
| 内置工具库 / 工作台 | 裸 `fetch` 不带 token → 401 被吞成空列表 |
| 记忆画像配置 | `MemoryDashboard.vue` 缺 `element-plus` 导入 → ReferenceError |
| 链路分叉/续跑/上下文 | `agent-trace-ui/src/api/fork.js` 双 `/api` 前缀 → 404 |
| 数据源新建态测试连接 | `DataSourceManage.vue:246` → `/datasources/null/test` 400 |

---

## 三、智能体范围内仍需完善（6 域约 20 项）

### 3.1 前置阻塞：建库不可复现（**一切改进的前提**）

`data/` 被 gitignore；62 张 `@TableName` 中约 55 张全仓无建表语句，Java 内 `CREATE TABLE` 去重仅覆盖 7 张；`resources/db/**` 3 个脚本运行时从不加载（无 Flyway）。**换机器 clone 后启动即崩**，今天已因空库撞过一次 `no such table: ai_managed_agent`。

要做的：Flyway + 全量双 dialect DDL、收编 7 处 `@PostConstruct` 与 3 个孤儿脚本、补非权限类种子（admin 用户、菜单、模型配置、内置工具）。

### 3.2 对话链路

| 项 | 现状 |
|---|---|
| 工具审批闭环 | `ToolGovernanceService:116-123` 只 `return "需审批: ..."`，toolName+arguments 当场丢弃；通过后无重放、无结果回注 |
| 内容过滤虽在但字段脱敏没接 | `interceptInput`/`sanitizeOutput`/`checkDangerousUrl` 已接入对话与工具；但 `FieldMaskService` 全仓 0 个业务调用点，手机号邮箱返回明文 |
| 审批「发起得了执行不了」的文案 | 提示「审批通过后自动执行」，代码里不存在该能力 |
| L2 场景层 | 有模型、有建表、有仓储 3 方法，但 `MemoryManager`/`AiMemoryService`/`MemoryAwarePromptBuilder`/`MemoryMcpToolkit` 全不引用 → 四层记忆实际只跑三层 |
| `habit` 类别恒空 | `FactExtractor` 从不产出 `habit`，提示词构造每次多发一次恒空查询 |
| 记忆 JSONL 落盘 | Windows 上 sessionKey 含 `:` 被当文件名，日志实测 6 次 `Illegal char <:>` |
| 启动诊断 | 空库/错目录启动只抛裸 `SQLITE_ERROR`，应翻译成可操作提示 |

### 3.3 工具与编排

- 图引擎无 `STATUS_CANCELED` 使用点、无 `cancel` 端点，中断走 `failed`；节点无 `timeoutMs`、`RestClient.create()` 无超时；并行池固定 4 线程；`conflict` 只标记不裁决。
- 重试仅 `task`/`async_task` 支持，`llm`/`http`/`code`/`agent`/`tool` 无。
- 工具调用审批参数丢弃（见 3.2）。

### 3.4 记忆与 RAG

- OLAP(Arrow) **只写不读**：`ArrowOlapAnalyticsRepository` 全部查询在内存 `List<Object[]> rows` 上循环，数据量大时 `/analytics/*` 会 OOM。
- RAG embedding 熔断是**永久**的（`RagEmbedder:33` 一次失败到进程重启都走本地 2-gram 向量），与库中真向量不同空间 → 排序静默劣化。
- 切片参数写死 `paragraph/800/100`，不读配置；`rag.top-k` 零引用；`RagRetrieveSkill` 的 `kbId` 恒 null → 全库检索。
- 死配置 7 组，其中 `ai.agent.memory-*` 与 `agent-memory.*` **同名双份**，改错一个不生效。

### 3.5 前端必修（点了就报错）

`fork.js` 双前缀 · `MemoryDashboard` 缺导入 · 两处裸 `fetch` · `AiAgentManage` 响应判错 · `DataSourceManage` 新建态 `/null`（**数据源模块已决定保留，此项恢复为必修 BUG**）· `agent-trace-ui/fork.js` 对应的链路分叉与续跑需与 OTel 导出一并联调。

### 3.6 体验与数据真实性

18 个列表页仅 2 个有分页；6 个无空态、4 个无 loading；`TraceList` 固定 `limit=50`；`Home.vue` 的「插件数×3」「在线」字面量、一键部署死链；`MainLayout` 角色标签恒「普通用户」；`MemoryDashboard` 的 `trend: 72`、模型清单硬编码；死调用 `/analytics/personas*`、`/channel/deploy`。

---

## 四、依赖事实与耦合面（原「可删除」候选清单，🚫 已否决）

> 🚫 **决策（2026-11-06 16:26，用户明确）**：本节列出的
> **后端 3 个模块 + harness 内 2 个子功能 + 前端 2 个模块 + 2 个遗留工程，全部保留，不删除**。
> 用户不接受「删掉后平台仍能跑但会少一块能力」这种取舍 —— 飞书渠道、`query_datasource` 技能、
> OTel GenAI span 导出、评测工作台组件、API 文档管理、通用告警都是**要保留的功能**。
>
> 以下表格与 §4.4 因此**只作为依赖事实、装配面与耦合点记录**，供后续维护、排障和补齐功能时查阅，
> **不再作为删除依据**。原「删除操作面」一列改读作「**改动时必须同步的位置**」。

### 4.1 后端：编译期零反向依赖（装配面记录）

| 模块 | 依赖事实 | 改动时须同步的位置 |
|---|---|---|
| `module-feishu` | 全库 `import com.zimo.module.feishu` 全部落在自身目录；唯一外部引用是 `FrameworkApiRegistryAutoConfiguration:40` 的 `afterName` **字符串**；飞书 AI Skill 是 feishu → framework-common 单向注册 | `agent-application/pom.xml:35` + `application.yml:50-55/122` 的 `feishu.*` / `plugin.feishu.datasource` + `afterName` 指向的配置类 + `ps_feishu_*` 4 张表（须进新 DDL） |
| `agent-trace` | 仅单向注册 `GenAiTraceObserver` 到 `TraceCollector`；无自有 Controller、无表；自研 `TraceCollector` 不依赖它，两者是并存的双通道 | `agent-application/pom.xml:59` + `otel-*` 配置 + 根 pom 的 `opentelemetry.version`；exporter endpoint 需可用 |
| `module-datasource-core` **主体** | `DsDataSourceController` `/api/biz/ds/datasources` 是独立数据源管理；`agent-memory` 依赖的是 `ds.storage` 子模块（已复核） | `agent-application/pom.xml:39`；`module-datasource-storage` 与 core 必须共存；`DsQuerySkill:25` 是本次要保留的智能体技能 |

> `module-security` 同样编译期零反向依赖（业务侧走 `Optional<SecurityFacade>`），按本次决策**完整保留**；其内容过滤与工具审批见第五节。

### 4.2 后端 harness 内 2 个子功能（🚫 保留）

| 子功能 | 端点 | 依赖事实 / 改动时须同步的位置 |
|---|---|---|
| API 文档管理（YApi 导入） | `AiApiDocController` `/api/biz/ai/api-docs` | 全仓无业务消费者；前端在 `ai/menus.js:17`、`ai/routes.js:18`、`ai/src/api/api-doc.js`。改动需同步 `AiControllerScanAutoConfiguration` 的包扫描与 `AiSkillCoreAutoConfiguration:107` 的 Bean |
| 通用运维告警 | `ObservController` `/alerts/rules`、`/alerts/events` | `ObservAlertRule` 字段无 agent 维度；前端在 `ai/src/api/observ.js:56-68`。`ObservController:24` 是单体 Controller，改动需同步 `AiObservAutoConfiguration:39` 的 Bean 与两个 Mapper |

**同属保留**：`AiAbTestController`（提示词 A/B 实验）、`AiPromptTemplate*`（Agent 定义核心）、`CollabController`（多智能体协同）、`/test-cases` `/test-runs`（`ObservTestCase` 带 `agentId`，是智能体评测）。

### 4.3 前端 2 个模块与主壳区块（耦合面记录）

| 对象 | 保留状态 / 耦合点 |
|---|---|
| `frontend/modules/datasource` | 🚫 **保留**。耦合点：`local-modules.js:18` + `shellDefaultPlugins:27` + `ai/menus.js:44-50` + `Home.vue`/`MainLayout.vue` 图标映射 + 后端 `DsPluginRegister` Bean |
| `frontend/modules/feishu` | 🚫 **保留**。耦合点：`local-modules.js:16` + 后端 `FeishuPluginRegister` Bean；`ai` 模块内「智能体-飞书绑定」三件套（`ai/routes.js:6-10`、`AiFeishuBindingManage.vue`、`api/feishu-binding.js`）**同步保留** |
| `modules/agent-memory/frontend/agent-memory-ui`（2026-11-06 由 `frontend/` 归位）、`frontend/agent-harness-ui` | 🚫 **保留**。两者与 `web-shell` 无引用、不在 Maven 构建内；`agent-harness-ui` 与 `web-shell` 都占 `16600`（`strictPort`）→ **保留后必须错开端口**，否则无法同时启动。`HarnessWorkbench` 等评测组件主壳没有，保留即等于保留这套能力。归位详情见 `docs/plans/2026-11-06-agent-memory-inrepo-runnable.md` |
| `Home.vue` | 仅**清理假数据与死链**：一键部署（无 `/channel/deploy` 路由）、`plugins.length * 3`、`'在线'` 字面量、死图标映射。**不删页面**（`router/index.js:13` 的 redirect 保持） |
| `MainLayout.vue` | 仅**清理**：角色标签恒「普通用户」、`filterMenusByPermissions` 恒真链、死图标映射。不删布局 |
| `permission-guard.js` / `utils/permissions.js` | 可简化为纯 token 守卫（现有菜单不带 `permission`，过滤恒真）；属代码清理，不删守卫能力 |
| `Login.vue` 注册 tab 与部门字段 | 🚫 **保留**。有 `login-department-contract.test.mjs` 契约测试 + 后端 `/auth/login` 强校验 `department`，改动需前后端同步 |

> ⚠️ **改前端模块必须四处同步**：后端 `PluginRegister` Bean + `local-modules.js` key + `shellDefaultPlugins` 兜底条目 + 模块目录。任一侧漏改 → `/api/plugins` 与本地清单对不齐 → `plugin-loader.js:16` 每次刷新打印 `Frontend module not found for plugin: xxx`。
> 现成样本：`sys` 前端本就不存在，**当前就在持续告警**（保留全量后这条也仍要修）。

### 4.4 二轮复核修订（2026-11-06，更正 4.1~4.3 的不精确表述）

| # | 上版说法 | 复核后更正 |
|---|---|---|
| 1 | 飞书是「非智能体功能」 | **错**。模块内含 `FeishuAgentGateway` / `FeishuAgentDispatchService` / `FeishuAgentCredentialController`，是**把智能体对话接到飞书**的渠道。删它 = 决定不做飞书渠道，会整条丢掉对外服务通道，属产品取舍而非免费删除 |
| 2 | `agent-trace` 是「OTel 导出主体」可删 | 表述应更准：它是**第二条导出通道**。`observ_*` 8 张表实体与 `TraceCollector` 采集/大盘/回放都在 `agent-harness`，删后 `/api/biz/ai/observ/traces` 等仍工作，只是没有标准化 GenAI span 外发 → **观测能力降级** |
| 3 | API 文档管理 / 通用告警「与智能体能力无关，可裁」 | 结论仍成立，但**上版漏写前端引用面**：`ai/menus.js:17`「API 管理」、`ai/routes.js:18`、`ai/src/api/api-doc.js`（7 个方法）、`ai/src/api/observ.js:56-68`（4 个告警调用）。改动须后端 + 前端四处同步 |
| 4 | 裁告警「需处理 `AiObservAutoConfiguration` 的 `@MapperScan`」 | 实际是 **`:39` 的 `observAlertService` Bean 方法** + 两个 Mapper/实体/表。且 `ObservController` 是**单体 Controller**（dashboard/traces/events/test-cases/alerts 混在 `:24` 起），任何调整只能针对 `:152-177` 四个方法与构造器的 `AlertService` 参数，**不能整类移除** |
| 5 | 两个遗留工程「与 web-shell 无引用」即可删 | `agent-memory-ui` 的 6 个页面与 `web-shell/modules/agentmemory/src/views` **同名一一重合**，属重复实现。但 `agent-harness-ui` 的 `HarnessWorkbench.vue` / `CaseTree.vue` / `CaseAssertEditor.vue` / `ExecutionGraph.vue` 等**评测工作台组件，主壳没有对应实现**（`agent-trace-ui` 只有 TraceList/TraceDetail）→ 保留后须决定其定位（见 §7 待确认 3） |
| 6 | `Login.vue` 注册 tab / 部门字段列为「可删」 | 存在 `login-department-contract.test.mjs` 契约测试，且后端 `/auth/login` 强校验 `department`，不是纯前端改动。**已拍板保留**（§7），改动须后端契约 + 测试同步 |

**补充证据**：`AiApiDoc`（`@TableName("ai_api_doc")`）全仓除自身 Controller/Service/Mapper 与
`AiSkillCoreAutoConfiguration:107` 的 Bean 注册外**无任何业务消费者**；技能编辑器选 API 走的是另一套
`SysApiRegistryController /api/biz/sys/api-registry`。`ObservAlertRule:16-25` 的字段
（id/name/type/threshold/level/enabled/createdAt/deleted）**无任何 agent 维度**，与带 `agentId` 的
`ObservTestCase` 形成对照 —— 这两项是「重复登记簿 / 通用运维告警」，不是智能体评测。

**决策后结论（2026-11-06 16:26，用户明确「不能接受，不能删除」）**：两档**一律保留**。
- 原「零能力损失」档：保留；仅 `Home.vue` / `MainLayout` 的假数据与死链按 §3.6 做**展示层清理**，不删页面与功能。
- 原「产品取舍」档：用户不接受「平台仍能跑但少一块能力」这种取舍 → 飞书渠道、`DsQuerySkill:25`、OTel 导出、`agent-harness-ui` 评测组件**全部保留**，并须补齐到真正可用（见 §6）。
- §4.4 表第 6 行的 `Login.vue` 注册 tab / 部门字段：拍板结果为**保留**（不再作为待定项）。

---

## 五、不能删除

| 对象 | 理由（证据） |
|---|---|
| `framework-common` / `framework-autoconfig` / `framework-ai` | 全局契约、Controller 扫描、API 注册表、智能体运行时 |
| `agent-auth`（含 `SysUser` / `ps_sys_user`） | 登录唯一实现，本期「只要登录」全靠它 |
| `agent-memory-core` | 被 `framework-ai` 11 处编译期 import；`ChatTurnMemoryRecorder` 是对话 Controller 的必需构造器注入 |
| `module-datasource-storage` | `agent-memory` 编译期依赖；删主体时须保留 |
| `agent-harness-core` / `module-tools-core` | 智能体核心（对话/Agent/模型/技能/图/工具治理），虽是依赖图叶子但靠 Spring 装配生效 |
| `frontend/modules/ai` | 核心页面；且 `ai/menus.js` 是**全平台唯一菜单聚合点**（6 个一级分组），删了整个导航消失 |
| `frontend/modules/workflow` | `AiAgentManage.vue:616` **直接 import** `WorkflowManage.vue`，`:219-223` 作为 tab 内嵌渲染 → 删目录会让 ai 模块编译失败 |
| `module-sys`（**需先解依赖，但不能整个删**） | ① `DataInitializer`、`ShortTermMemorySettingsConfiguration` 硬 import → 直接删会启动失败；② `SysApiRegistryController` `/api/biz/sys/api-registry` 被 `ai/src/api/api-registry.js:15` → `AiApiRegistrySelect.vue` / `AiSkillEditorDrawer.vue:177-178`（技能编辑器选 API）调用；③ `AgentSettingController` `/api/biz/sys/agent-setting` 被 `agentmemory/src/api/agent-settings.js` 调用。这三个智能体相关点必须先迁移 |
| `module-security` 的**内容过滤与审批**部分 | `ContentSafetyService`（敏感词/输出脱敏/危险 URL）经 `AiChatPreflight:123`、`AiChatController:74`、`AiChatStreamController:195`、`AgentRuntime:177`、`ToolGovernanceService:105` 实际接入；`ApprovalService` 是工具审批的载体。整模块删 = 这两项智能体能力静默归零 |
| `observ_*` 8 张表 / `api_registry` / `ps_sys_user` / 主库文件 | 表共享，删模块不能删库 |

---

## 六、保留全量后的实施顺序（每步可回滚，🚫 无删除步骤）

> 原「删除顺序」整节作废。现在的目标不是缩减代码，而是**让保留下来的东西真的能跑**。

1. **阶段 0 · 前置阻塞：建库可复现**（§3.1）
   Flyway + 全量双 dialect DDL（约 55 张缺表）、收编 7 处 `@PostConstruct` 与 3 个孤儿脚本、补非权限类种子。
   **不做这步，换机器 clone 后启动即崩**，后面所有联调都无从谈起。
2. **阶段 1 · 修「点了就报错」**（§3.5，全部恢复为必修）
   `agent-trace-ui/fork.js` 双 `/api` 前缀 · `MemoryDashboard` 缺 `element-plus` 导入 · 两处裸 `fetch` ·
   `AiAgentManage` MCP 响应层级判错 · `DataSourceManage` 新建态 `/datasources/null/test` 400。
3. **阶段 2 · 消除保留带来的冲突与空转**
   - **端口**：`agent-harness-ui/vite.config.js` 与 `web-shell/vite.config.js` 均为 `16600 + strictPort`，
     两个工程都保留就必须错开（改端口，或明确不同时启动）。
   - **插件清单**：`sys` 前端模块本就不存在，`/api/plugins` 持续下发 → `plugin-loader.js:16` 每次刷新告警，需补模块或下线该登记。
   - **凭据**：飞书凭据缺失时启动日志有 ERROR；OTel exporter 需可用的 endpoint。
   - **落盘**：`MemoryFlushManager` 在 Windows 上因 sessionKey 含 `:` 报 `Illegal char <:>`。
4. **阶段 3 · 数据真实性清理（仅展示层，不删页面/功能）**（§3.6）
   `Home.vue` 一键部署死链 / `plugins.length*3` / `'在线'` 字面量 / 死图标映射、`MainLayout.vue` 角色标签、
   `MemoryDashboard` 的 `trend: 72` 与硬编码模型清单、18 个列表页的分页与空态。
5. **阶段 4 · 补齐「保留项」的可用性**（本次决策直接带来的新增工作）
   - **飞书渠道**：凭据与网关开关、`ai` 模块「智能体-飞书绑定」三件套联调、`framework.api-registry` 的 `afterName` 指向有效配置类。
   - **数据源主体**：`DataSourceManage` 新建态、`query_datasource` 技能可用、`module-datasource-storage` 与 core 边界不回归。
   - **OTel 导出**：exporter 接通，与自研 `TraceCollector` 双通道共存；`agent-trace-ui` 链路分叉/续跑一并对齐。
   - **API 文档管理 / 通用告警**：前端 7 + 4 个调用与后端端点联调；确认 `ai_api_doc` / `observ_alert_*` 表在新 DDL 中。
   - **2 个遗留工程**：`agent-harness-ui` 评测工作台（`HarnessWorkbench`/`CaseTree`/`CaseAssertEditor`/`ExecutionGraph`）明确「接入主壳」还是「独立工具运行」；`agent-memory-ui` 与主壳 `agentmemory` 6 个同名页面明确以哪套为准。
6. **阶段 5 · 智能体能力补齐**（§3.2 / 3.3 / 3.4）
   工具审批闭环（当前只 `return "需审批"`，参数丢弃）、L2 场景层、图 `cancel`/`timeoutMs`、
   OLAP(Arrow) 只写不读、RAG embedding 永久熔断、`kbId` 恒 null、死配置 7 组。
7. **阶段 6 · 运行态验收**
   全模块启用下逐页实测登录、对话、以及上述每个保留模块的主流程；本文此前所有结论均为静态证据，需在此阶段闭环。

> 🚫 **数据红线**：主库文件、全部表（含 `ps_feishu_*`、`ai_api_doc`、`observ_alert_*`、`observ_*`）、
> `ps_sys_user`、`api_registry` 一律**不动**。原「阶段 4 清理可删表」已删除。

---

## 七、决策记录（2026-11-06 16:26）

| 原待拍板点 | 拍板结果 |
|---|---|
| 1. 数据源管理删不删 | 🚫 **保留**。`query_datasource` 技能保留；C5（`DataSourceManage` 新建态 400）**恢复为必修** |
| 2. 飞书是否彻底删 | 🚫 **完整保留**，含 `ai` 模块内「智能体-飞书绑定」三件套 |
| 3. `agent-trace` 保不保留 | 🚫 **保留**。OTel GenAI span 导出与自研 `TraceCollector` 双通道并存 |
| 4. `module-security` 内容过滤与工具审批 | 🚫 按全量保留口径**保留**；§3.2 的工具审批闭环仍是要补的项 |
| 5. API 文档管理 / 通用告警 / 提示词 A/B | 🚫 **三者全部保留** |

**仍待确认（4 项，均为实施细节，不涉及功能存废）**：

1. **主壳区块**：本次否决清单未包含 `Home.vue` / `MainLayout.vue` / `permission-guard.js`，
   当前按「**允许清理假数据与死链，但不删页面、不删功能**」处理；若也要求原样保留，请指出。
2. **16600 端口**：两个遗留工程都保留，是改 `agent-harness-ui` 端口，还是约定不同时启动？
3. **评测工作台定位**：接入 `web-shell` 成为正式菜单，还是保持独立工具？`agent-memory-ui` 与主壳 `agentmemory` 的同名页面以哪套为准？
4. **外部凭据来源**：飞书 app-id/secret、OTel exporter endpoint 由谁提供、放在哪套环境配置里。

---

## 八、置信度与未闭环

**置信度 85%**。全部结论为静态证据 + 本地库只读查询，**未在运行态逐页实测**；未执行任何删除，
且已由用户决策**不再执行任何删除**。第 1~5 节为依赖事实档案，**第六节是当前唯一有效的行动清单**。

已亲自复核 11 项：`SysUser` 归属、`agent-application` 对 sys 的硬 import、`framework-ai → agent-memory`、`agent-memory → ds.storage`、`ai/menus.js` 聚合、`AiAgentManage.vue:616` 跨模块 import、三个前端工程端口与 `AGENTS.md` 主壳定义、`module-security` 服务类清单、对话/工具链路对内容过滤与审批的实际调用、两个 D 类端点前缀、跨模块 import 全量统计。

仍需确认：
1. `ApiRegistryStartupRunner` 是否清理已下线模块的历史端点行。
2. `afterName` 悬空字符串在 Spring Boot 3.4.5 的容忍度（建议清理，未实测）。
3. `agent-harness → agent-rag-core` 的 Java 层依赖（只确认到 pom）。
4. `plugin-loader.test.mjs:173,205,259-407` 有指向已删除目录（`modules/sys`、`modules/manufacturing-pm`）的 stale 引用，当前测试是否已红未运行验证。
5. `agent-memory` 是嵌套独立 git 仓库却仍在 `modules/pom.xml` 反应堆中，调整模块或清理构建时须一并处理。

> **顺带纠正一条长期口径**：项目长期记忆里写「前端是 `agent-harness-ui`（NaiveUI，16600）」，与代码现状不符。实测 `frontend/AGENTS.md:16-18` 明确 `web-shell` 是主壳（Element Plus，16600），`agent-harness-ui` 是带独立 `.git`、同样占用 16600 的遗留工程，`agent-memory-ui` 是 6060 的同源遗留工程。三者的 `package.json`/`vite.config.js` 互不引用。
