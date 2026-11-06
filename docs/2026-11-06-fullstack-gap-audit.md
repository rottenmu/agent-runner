# agent_runner 前后端功能缺口审计（2026-11-06）

> 状态：审计结论，未进入实施
> 范围：后端 `framework/` + `modules/` + `agent-application/`，前端 `frontend/web-shell/` + `frontend/modules/`
> 方法：全仓静态扫描（无 TODO/FIXME 可供定位）+ 前端 254 条接口调用与后端 322 个端点做集合比对 + 本地 SQLite 只读查询
> 验证标记：**✅ 已亲自复核**（我读了源码/查了库）；**○ 待确认**（来自静态分析，未逐行复核）

---

## 零、一页结论

框架骨架很完整——插件装配、降级策略、埋点、可观测都写得很细，**没有一处 `TODO`**。缺口不在"没写完"，而在**"最后一公里"**：能力造出来了，但没接线、没权限、没进 UI、或者只在开发者的机器上能跑。

按"能否在干净环境跑起来 / 是否造成越权或资损"排序，最该先处理的六件事：

| # | 问题 | 本质 | 严重度 |
|---|---|---|---|
| 1 | 62 张 `@TableName` 表里约 55 张**全仓库无建表语句**，`data/` 被 `.gitignore` | 项目**不可复现**：换机器 clone 后启动即失败 | 阻断 |
| 2 | RBAC 拦截器注册被注释，**25 处 `@HasPerm` 全部失效** | 后端越权面：绕过前端就能调管理接口 | 阻断 |
| 3 | 审批"发起得了、执行不了"：参数被丢弃，通过后无人重放 | Agent 写操作闭环断裂，且文案在骗人 | 阻断 |
| 4 | 登录强校验 `department`，但登录页无部门选择器 | 非"行政"账号永远登不进去 | 阻断 |
| 5 | 图引擎**没有 cancel**、节点**没有超时** | 停不下来、单个慢节点可拖死线程池 → 成本失控 | 高 |
| 6 | 前端 `sys` 模块整体缺失 | 后端 30 个管理端点完全没有 UI 入口 | 高 |

---

## 一、阻断级（P0）

### 1.1 数据库不可复现：约 55/62 张表没有 DDL ✅

**实测数据**：

| 指标 | 值 |
|---|---|
| 后端 `@TableName` 注解数 | **62** |
| Java 内 `CREATE TABLE` 语句数 | **16**（其中 SQLite/MySQL 双份，去重后覆盖 7 张表） |
| 仓库内 `.sql` 文件 | **3**（2 个 MySQL 版、1 个 SQLite 版） |
| 本地库实际表数 | **68** |
| `data/` 是否被 git 忽略 | **是**（`.gitignore` 第 8、12 行） |

也就是说：**表是历史上手工建的，仓库里没有可复现的建库脚本**。

有 DDL 的 7 张全部硬编码在 Java 里（`@PostConstruct` 或构造器内执行）：

`ai_tool_meta`、`ai_graph_tool_invoke_log`（`ToolSchemaInitializer`）、`observ_session_event`（`SessionEventLogServiceImpl`）、`ps_feishu_config`、`ps_feishu_user_mapping`、`ps_feishu_cli_call_log`、`ps_feishu_message_log`。

另有 6 张非实体表由记忆模块自建：`l0_raw_log`、`l1_atomic_memory`、`l2_scene_block`、`l3_persona`、`memory_arch_config`、`memory_span`。

**没 DDL 的表（按模块）**：

```
module-sys        : ps_sys_user, ps_sys_role, ps_sys_user_role, ps_sys_menu, ps_sys_role_menu,
                    ps_sys_oper_log, ps_sys_agent_setting
module-security   : sec_resource_permission, sec_audit_log, sec_approval, sec_dept, sec_user_dept,
                    sec_data_scope, sec_sensitive_word, sec_dangerous_api
agent-harness     : agent_session, agent_session_member, agent_session_message, agent_task, agent_task_flow,
                    agent_role_template, ai_agent_capability, ai_agent_skill_config, ai_ab_test,
                    ai_api_doc, ai_managed_agent, ai_mcp_config, ai_model_config, ai_prompt_template,
                    ai_prompt_version, tool_definition, tool_plugin, tool_python_script,
                    tool_agent_permission, tool_invoke_log, wf_template, wf_workflow,
                    wf_workflow_version, wf_workflow_run, wf_workflow_run_log
agent-rag         : rag_knowledge_base, rag_document, rag_chunk, rag_version, rag_evaluation,
                    rag_kb_permission, rag_retrieve_log
agent-datasource  : ds_data_source
observ            : observ_trace, observ_trace_step, observ_test_case, observ_test_run,
                    observ_test_case_result, observ_alert_rule, observ_alert_event
```

**用户可见后果**：新机器 `git clone` → 启动 → `DataInitializer` 查 `ps_sys_user` 抛 `BadSqlGrammarException`，进程退出。即使绕过它，登录、会话、RAG、观测、工作流全部首查即崩。

**同一问题今天已实锤一次**：从错误工作目录启动 → SQLite 落到空库 → `no such table: ai_managed_agent` 启动失败。因为该表恰恰属于"无 DDL"这一类，**空库永远补不回来**。

**DDL 双轨且右轨不执行** ○：`resources/db/**` 的 3 个脚本**运行时从不加载**（全仓无 Flyway/Liquibase/`ResourceDatabasePopulator`），只有两个测试在读（`HarnessMysqlDdlParityTest`、`ObservMysqlDdlTest`）。运行时真正建表的是 Java 常量，两套手抄，靠测试锁住——`-DskipTests` 即可漂移。且 `api_registry.sql` 只有 SQLite 方言，切 MySQL 会因 `AUTOINCREMENT` / `datetime('now','localtime')` 报语法错。

**补齐方向**：引入 Flyway，按 MySQL + SQLite 双 dialect 出全量 migration；把 7 处 `@PostConstruct` DDL 迁进去；`resources/db` 三个脚本收编。**这是唯一"不做则其他改动都无法在干净环境验证"的前置项。**

---

### 1.2 RBAC 拦截器被注释，25 处 `@HasPerm` 形同虚设 ✅

`modules/module-sys/.../autoconfig/config/WebInterceptorConfig.java:57` 起：

```java
// Temporarily disable RBAC permission interceptor registration.
// permissionInterceptor.ifAvailable(interceptor -> registry.addInterceptor(interceptor)
//         .addPathPatterns("/**")
//         .excludePathPatterns(whitelistPaths(properties)));
```

`PermissionInterceptor` Bean 照常创建（`:40`），只是**没注册进 MVC**。业务侧标了 **25 处** `@HasPerm`（`SysUserController`、`SysRoleController`、`SysMenuController`、`SysRbacController`、`SysApiRegistryController`）。

**后果**：任何登录用户直接 `curl /api/biz/sys/users` 就能拉全量用户，甚至调用 `sys:api:update` 级写接口。前端隐藏菜单不等于鉴权。

**注意**：不能只把注释放开。`DataInitializer` 只创建 admin 用户 + admin 角色并绑定，**不写 `sec_resource_permission`**；本地库现存仅 2 行授权（`role 1 → agent execute`、`user 2 → knowledge_base 1 view`）。放开拦截器 + 不补种子 = 全站 403。这三件事必须一起做：

1. 补 `sec_resource_permission` 种子（或改 `PermissionService` 的默认策略）
2. `PermissionCache` 默认是 `NoopBackend`（`save()` 空体、`load()` 恒 `empty()`）→ 放开后每请求多 5~10 次 SQL，需先换 Caffeine/Redis 实现
3. 再放开拦截器注册

**前端侧同样空转** ✅：`utils/permissions.js` + `filterMenusByPermissions` 实现完整，但 `frontend/modules/**` 里 **0 处** `permission:` 声明，`permission-guard.js` 无 `meta.permission` 直接放行。两端都没生效。

---

### 1.3 审批"发起得了、执行不了" ✅

三处断点叠加：

```java
// ① modules/module-tools/.../ToolGovernanceService.java:116-123
ApprovalRequest approval = security.get().requireApproval(riskAction, "tool", toolName, arguments, agentId);
if (approval.required()) {
    String msg = "高危操作已转人工审批（" + approval.message() + "），审批通过后自动执行";
    ...
    return "需审批: " + msg;      // ← toolName + arguments 到此丢弃
}
```

```java
// ② module-security/.../SecurityAutoConfiguration.java:102-106 —— 通过后只写审计
approvalService.setCallback((approval, approved) ->
        auditLogService.record("system", "approval-executor", "approval.execute", ...));
```

```java
// ③ module-security/.../SecurityFacadeImpl.java:82-84 —— 门面通知是空实现
public void notifyApprovalResult(String approvalId, boolean approved) {
    // 由前端审批接口直接驱动；此处保留兼容入口
}
```

**后果**：Agent 要删数据 / 发邮件 / 执行 DDL 时，对话里出现"已转人工审批"，审批人点通过后**什么都不会发生**，模型也收不到"已批准"的后续信号。文案承诺的"审批通过后自动执行"在代码里不存在。

**补齐方向**：`sec_approval` 落 `payload`（toolName + arguments + agentId），`handle()` 后由回调拿到 payload 真正重放工具，再把结果回注 Agent 会话（`notifyApprovalResult`）。

---

### 1.4 登录强制部门匹配，但登录页没有部门选择器 ✅

```java
// modules/agent-auth/.../AuthController.java:53-55
if (!dto.getDepartment().trim().equals(user.getDepartment())) {
    throw new BizException(401, "账户、密码或部门错误");
}
```

```vue
<!-- frontend/web-shell/src/views/Login.vue:25,55 -->
<el-form-item prop="department" v-if="activeMode === 'register'">   <!-- 仅注册模式渲染 -->
const loginForm = reactive({ username: 'admin', password: 'admin', department: '行政' })  <!-- 登录恒为行政 -->
```

**后果**：采购部 / 设计部 / 生产部的账号，刷新页面后登录框里的部门是"行政"，且改不了 → 永远提示"账户、密码或部门错误"。注册成功后因 `Login.vue:112` 回填了部门才显得正常，掩盖了缺陷。

当前库里只有 1 个 admin（行政），所以这个坑**尚未暴露**——一旦有第二个部门的账号就会撞上。

---

### 1.5 图引擎缺 cancel、缺节点超时 ○

| 能力 | 现状 |
|---|---|
| `STATUS_CANCELED` | 常量定义在 `GraphEngine.java:49`、`isTerminal()` 里判一次，**全文件再无第三处引用**；`GraphController` 无 `cancel` 端点 |
| 中断分支 | `advance()` 的中断走 `fail(...,"执行被中断")`，落 `failed` 而非 `canceled` |
| 对比 | 旧引擎 `WfWorkflowController` 有 `/stop`、驳回时 `setStatus("canceled")` —— **新图引擎把这块丢了** |
| 节点超时 | `GraphNodeExecutor` 里 `RestClient.create()` 未设 connect/read timeout，无 `timeoutMs` 配置 |
| 并行池 | 固定 4 线程、不可配 |
| 重试 | 仅 `task` / `async_task` 支持，`llm`/`http`/`code`/`agent`/`tool` 无 |

**后果**：用户点"停止"后图继续跑、继续调 LLM、继续花钱；一个慢 HTTP 节点能占满 4 线程池把整图拖死。

`conflict` 终态同样**只标记不裁决** ○：supervisor 汇总出 `conflict` 时若未配 `conflictTarget` 就沿普通出边继续（"带伤继续"），没有"停下来等人选"的语义，也没有 `resolve` 接口——只能翻 trace 的 `conflictedNodes`。

---

### 1.6 前端 `sys` 模块整体缺失 ✅

后端 `SysPluginRegister`（`pluginId=sys`, `frontendModule=sys`, `frontendRoute=/biz/sys`, `order=6`）已随 `/api/plugins` 下发，但：

- `frontend/web-shell/src/plugin-loader/local-modules.js` 里**没有 `sys` key**（只有一行注释提到 sys）
- `frontend/modules/sys/` 目录**不存在**

`plugin-loader` 命中"模块找不到"分支时只 `console.warn`。**后果**：用户、角色、菜单、API 注册表、智能体参数这 5 组共约 30 个端点完全没有 UI，侧边栏也没有任何提示。

参照上一轮已修的 `agenttrace` → `agent-trace-ui` 命名坑：这次不是命名不一致，是**模块压根没写**。

---

## 二、功能半成品（P1）

### 2.1 四层记忆：L2 场景层零生产零消费 ✅（模型与仓储存在，无人调用）

`L2SceneBlock` 有模型、有建表、有仓储的 3 个方法（`saveSceneBlock` / `recallSceneBySession` / `listScenesBySession`），但 `MemoryManager`、`AiMemoryService`、`MemoryAwarePromptBuilder`、`MemoryMcpToolkit`、`AiMemoryController` **全部不引用**。

**后果**：所谓"四层记忆"实际只有三层在跑。长会话（几十轮后）无法用场景骨架做压缩召回，只能靠 L1 逐条命中。

**要补**：写侧在 `afterReply` 达到轮次阈值时聚合 L1 → 生成场景摘要（当前**没有** LLM 汇总组件，`FactExtractor` 只做规则抽取）；读侧在会话首轮注入场景骨架；`MemoryManager` 加门面。

### 2.2 `habit` 画像类别恒空 ○

画像注入默认读 `persona, preference, habit` 三类（`PersonaInjection.DEFAULT_TYPES`），但 `FactExtractor` 只产出 `persona / preference / history / custom`，**没有任何代码写 `habit`**。每次构造系统提示词都会多发一次恒空的查询。

两种收敛方式：补 `habit` 抽取规则，或把 `DEFAULT_TYPES` 收敛为 `persona,preference` 如实反映能力。

### 2.3 RAG：两个"静默错误" ○

**（1）embedding 熔断是永久的**：`RagEmbedder.java:33` `volatile boolean remoteDown = false`，`:68-73` 一旦失败置 `true` 且**本进程内不再重试**，此后所有向量退化为本地 2-gram 哈希。更糟的是新文档用降级向量入库，与库中已有真向量**不同空间**，余弦相似度失去意义、排序静默劣化，不报错不告警。

**（2）Skill 路径绕过知识库权限**：`RagRetrieveSkill.java:62` 的 `kbId` **恒传 null**（全库检索），而权限校验只在 `RagController` 的 `/retrieve`、`/chat` 里做。Agent 直接调 Skill 不经 Controller → **完全无 `RagKbPermissionService` 校验**。

**（3）切片参数硬编码**：`RagPipelineService.effectiveProperties()` 写死 `splitBy=paragraph`、`chunkSize=800`、`overlap=100`，不读 `rag.chunk-size`；`RagProperties.getTopK()` 则是彻底零引用。

### 2.4 OLAP(Arrow) 名不副实：只持久化，不参与计算 ○

`AsyncLogSyncTask` 每分钟把 H2 增量写 `.arrow`，但 `ArrowOlapAnalyticsRepository` 的所有查询（`:251/:281/:303/:318`）都在 `volatile List<Object[]> rows` 上做 `for` 循环和 `stream()`，Calcite 的 `ScannableTable` 扫的也是这份内存 `rows`。

**后果**：数据量上来 `/api/agent-memory/analytics/*` 会 OOM；"下推计算"实际不存在。

### 2.5 字段脱敏整体未投入使用 ○

`FieldMaskService` 由 `PermissionAutoConfiguration:42-43` 以 `new FieldMaskService(true)` 硬编码创建，**全仓 0 个业务调用点**；注解 `@FieldDesensitize` 生产实体零使用；`PermissionProperties.field-mask.*` 整块配置**从未被读取**。

**后果**：手机号/邮箱在任何接口返回里都是明文。

### 2.6 图引擎之外的"死配置"清单 ○

| 配置 | 位置 | 现象 |
|---|---|---|
| `field-mask.*` 全部 7 项 | `PermissionProperties` | 0 读取 |
| `data-scope.*` 全部 6 项 | `PermissionProperties` | 0 读取，`DataScopeMybatisPlugin` 用无参构造 |
| `operation-log.*` 4 项 | `PermissionProperties` | 只有 `enabled`/`excludePaths` 被读 |
| `plugin.intent.channel.*` 整块 | `IntentProperties` | `getChannel()` 无调用方 |
| `rag.top-k` | `RagProperties` | 0 调用 |
| `ai.agent.memory-*` 4 项 | `AiAgentProperties` | 0 调用，且与 `agent-memory.*` **同名双份**——改错一个不生效 |
| `plugin.sys.agent-name` | `SysProperties` | 0 调用 |

其中 `ai.agent.memory-whitelist-categories` vs `agent-memory.whitelist-categories` 最危险：运维改了前者会以为白名单生效。建议给前者加 `@Deprecated` 并在启动日志提示迁移。

### 2.7 `@DataScope` 零使用 ○

`DataScopeMybatisPlugin` 注册了，但注解 `@DataScope` 在业务代码里 **0 次使用** → 行级数据权限（部门隔离）完全没启用，所有 Mapper 查询都不带 `dept_id`。

---

## 三、前端具体缺陷（P1）

> 均为源码级问题，**已用源码复核**（标 ✅）或静态分析（○）。

### 3.1 `agent-trace-ui` 的 fork/resume/context 三条必然 404 ✅

```js
// frontend/modules/agent-trace-ui/src/api/fork.js:12,20,28
url: '/api/biz/ai/observ/sessions/fork'
```

而 `web-shell/src/api/request.js:5` 已设 `baseURL: '/api'` → 实际请求 `/api/api/biz/ai/observ/sessions/fork`。

**后果**：链路详情的"分叉 / 续跑 / 上下文预览"三个按钮必失败。（`baseURL` 与路径双写 `/api` 已确认无误）

### 3.2 `MemoryDashboard` 缺 `ElMessage` 导入，画像配置功能必崩 ✅

`frontend/modules/agentmemory/src/views/MemoryDashboard.vue` 的 `<script setup>` 导入了 Vue、router、icons、子组件、api，**唯独没有 `element-plus`**；但 `:647/:688/:691/:696/:705/:708/:714/:720` 直接调用 `ElMessage.*` / `ElMessageBox.confirm`。

`web-shell/vite.config.js` 只挂了 `vue()`，**没有 `unplugin-auto-import`** → 运行时 `ReferenceError: ElMessage is not defined`。

**后果**：画像配置的加载失败提示、保存、提取、删除**全部中断**（点按钮直接抛错）。

### 3.3 内置工具库 / 工作台裸 `fetch` 不带 token ✅

```js
// frontend/modules/ai/src/views/AiToolLibrary.vue:78
const response = await fetch('/api/ai/mcp', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },   // ← 无 Authorization
  ...
})
} catch { tools.value = [] }                          // ← 401 静默吞掉
```

`/api/ai/mcp` 属 Sa-Token `includePaths: /api/**` 且不在 `excludePaths` → 401。`catch` 把错误吞成空数组。

**后果**：`/ai/tools` 内置工具库永远空列表；`AiWorkbench.vue:115/:149` 同样问题，聊天只显示"调用失败"。

### 3.4 MCP 配置增删恒提示失败（响应层级判错）○

```js
// AiAgentManage.vue:1176 / :1198
if (res.data?.code === 200) { ... } else { ElMessage.error('保存失败') }
```

`request.js` 响应拦截器已 `return data`（即 `{code,msg,data}` 信封），`res.data` 是业务对象 → 条件恒 false。**实际保存成功也弹"保存失败"**。

### 3.5 数据源新建态"测试连接"必 400 ○

`DataSourceManage.vue:246` 用 `editingId.value`，新建时该值为 `null` → `POST /api/biz/ds/datasources/null/test`，后端 `@PathVariable Long id` 类型转换失败。**后果**：新建向导里点"测试连接"永远 FAIL，无法在保存前验证连通性。

### 3.6 列表页普遍缺分页/空态 ○

| 统计 | 值 |
|---|---|
| 检查的列表页 | 18 |
| 有分页 | **2**（`AiFeishuBindingManage`、`FeishuConfigManage`） |
| 无分页 | **16+**（全量渲染） |
| 无空态（`el-empty` 为 0） | 6 个：`AiAgentManage`（4 张表）、`AiApiDocManage`、`AiModelConfigManage`、`AiPromptTemplateManage`、`ToolCenter`、`FeishuConfigManage` |
| 无 loading | 4 个：`AiWorkbench`、`AiChatPage`、`MultiAgentCollaboration`、`WorkflowDesigner` |

已具备分页/截断能力但前端没消费的接口：`ObservController`（`limit` 固定 50）、`agentmemory`（支持 `limit/offset`）、`feishu config/page`。

`agent-trace` 的 `TraceList.vue` 固定 `limit=50`，**第 51 条起永远看不到**，也没有"加载更多"。

### 3.7 前端调用了后端没有的接口（死调用）○

| 位置 | 调用 | 实况 |
|---|---|---|
| `agent-trace-ui/src/api/fork.js` | `/api/api/...` | 双前缀，404 ✅ |
| `agentmemory/src/api/memory.js:105/110/115` | `/agent-memory/analytics/personas*` | 后端无此映射（且这 3 个方法无页面引用） |
| `web-shell/src/views/Home.vue:13` | `router.push('/channel/deploy')` | 无 `/channel` 路由，点"一键部署"进空白页 |

### 3.8 假数据 / 占位 ○

| 位置 | 内容 | 应有来源 |
|---|---|---|
| `Home.vue:99-104` | "API 接口数 = 插件数 × 3"（后端 `PluginInfo` 无 `apiCount`）、"运行状态：在线"为字面量 | `/api/biz/sys/api-registry`、健康检查端点 |
| `MainLayout.vue:233` | 角色标签恒"普通用户"（`/api/auth/info` 不返回 `roles`） | 后端补 `roles` 字段 |
| `MemoryDashboard.vue:464` | `trend: 72` 硬编码 | 后端无趋势字段 |
| `bailianModels.js:8-58` | 全量模型清单硬编码（`qwen3.8-max-preview` 等） | 后端无模型目录接口，无法随百炼变化更新 |
| `AiToolLibrary.vue:63-71` | 8 条工具参数模板，含 `http://localhost:9900/api/plugins` | MCP `tools/list` 的 inputSchema |

---

## 四、已核实"正常"的项（避免误判，排优先级时可跳过）

| 项 | 结论 |
|---|---|
| 记忆召回链路 | **端到端接通** `AiAgentService:638 → MemoryPromptMiddleware:104 → personaFragment/recallFragment → MemoryAwarePromptBuilder`，中间件 Bean 确实注入 `AiHarnessAgentFactory` |
| L3 画像注入 | `persona` 与 `preference` 都写进 L3 且被读，只有 `habit` 恒空 |
| 插件 key / 目录 / 后端三者 | 8 个模块中 **7 个一致**；`agenttrace → agent-trace-ui` 命名坑已在上轮修复 |
| 记忆模块配置 | `AgentMemoryProperties` 全部字段均有消费方 |
| agent-trace 注册 | 真注册、`trace-middleware-enabled: true` 默认开 |
| agent-intent | 真在用（`AiChatPreflight` 每轮 `parse`），但 `llm-enabled: false` 走的是规则匹配 |
| `plugin.auth.enabled` / `plugin.sys.enabled` | 通过 `@ConditionalOnProperty` 真实生效，不是死配置 |
| 前端插件 loader 去重 | 按 `pluginId`/`frontendModule` 去重逻辑正确 |

---

## 五、建议实施批次

### 第 1 批（不做则一切改进无法验证）

1. **Flyway + 全量 DDL**（MySQL/SQLite 双 dialect），收编 3 个孤儿脚本与 7 处 `@PostConstruct` DDL
2. **建库数据种子**：`sec_resource_permission` 基础授权 + admin 角色绑定
3. 验证方式：删掉 `data/agent_runner.db` 后从零启动，登录 + 发起一次对话 + 查一次记忆

### 第 2 批（安全与闭环）

4. 恢复 `PermissionInterceptor` 注册；先补 `PermissionCache` 真实实现，避免放开后每请求多 5~10 次 SQL
5. 修审批闭环：`sec_approval` 存 payload → 通过后真重放 → 结果回注会话
6. 补 `sys` 前端模块（至少用户/角色/菜单三块），否则 30 个端点持续无入口

### 第 3 批（可控性与正确性）

7. 图引擎：`POST /runs/{id}/cancel` + 节点级 `timeoutMs` + `RestClient` 超时 + 并行度可配
8. RAG：embedding 半开熔断 + 入库/查询校验 `embedding_model` 一致 + `RagRetrieveSkill` 补权限
9. 前端 4 个必修 bug：`fork.js` 双前缀、`MemoryDashboard` 缺导入、两个 `fetch` 补 token、MCP 保存判错层级
10. 登录页补部门选择器（或后端放宽为只校验账号密码）

### 第 4 批（补全与体验）

11. L2 场景层接入（需先有 LLM 摘要组件）
12. 清理死配置（`ai.agent.memory-*` 标 `@Deprecated`）、接 `@FieldDesensitize`、接 `@DataScope`
13. 列表页补分页/空态（16+ 页）、`Home.vue` 假数据换真实来源
14. 处理遗留工程 `modules/agent-memory/frontend/agent-memory-ui`（2026-11-06 由 `frontend/` 归位；与 `modules/agentmemory` 逐行同源）与 `frontend/agent-harness-ui`，明确唯一生产前端

---

## 六、置信度与未闭环项

**置信度：82%**。降低置信度的原因：本次为静态审计 + 本地库抽查，未在运行态逐页实测。

**我亲自复核过的关键结论**（可直接采信）：DDL 覆盖率、RBAC 注释、`@HasPerm` 数量、登录部门强校验、审批返回字符串、`baseURL=/api` 与 fork 双前缀、`MemoryDashboard` 缺 `element-plus` 导入、`fetch` 无 token、`local-modules` 无 `sys`、`@TableName`=62 / `CREATE TABLE`=16。

**仍需确认**：

1. 图引擎 `maxIterations` 超限后是抛错还是静默结束
2. `TraceDetail` fork 的 `sourceKey` 契约（`conversationId` 还是 `traceId`）
3. `bailianModels.js` 里 `qwen3.8-max-preview` 等型号在百炼是否真实存在
4. `MemoryDashboard` 的 `el-tabs` 与 `dsh-pane` 双切换在真实数据下的渲染正确性（需浏览器实测）
5. `ChatTurnMemoryRecorder` 与 `MemoryWritebackQueue` 双写路径的重复率（需跑起来对比 `l0_raw_log` 与 `l1_atomic_memory` 行数）
6. `sec_resource_permission` 在生产库是否另有手工种子（本地仅 2 行）

---

## 七、本期待办清单（2026-11-06）

> 范围：本期只做登录。以下清单替代第五节的四批划分。
> A1 需覆盖全部 62 张 `@TableName` 对应表（含 `sec_*`、`rag_kb_permission` 等运行时仍会被访问的表）。

### 7.0 批次总览

| 批次 | 内容 | 项数 |
|---|---|---|
| A | 建库可复现：Flyway + 全量 DDL + 种子 | 4 |
| B | 后端功能闭环与正确性 | 9 |
| C | 前端「点了就报错」的必修 bug | 5 |
| D | 体验、假数据与清理 | 6 |

### 7.1 A 批：前置项（不做则后面任何改动都无法在干净环境验证）

| # | 任务 | 现状证据 | 完成判据 |
|---|---|---|---|
| A1 | 引入 Flyway，全量 DDL 覆盖 **55 张缺表** | `@TableName`=62，Java `CREATE TABLE` 去重仅覆盖 7 张 | 删除 `data/agent_runner.db` 后从零启动成功，登录 + 一次对话 + 一次记忆查询全通 |
| A2 | 收编 7 处 `@PostConstruct` DDL 与 `resources/db/**` 3 个孤儿脚本 | 运行时从不加载，仅 2 个测试在读 | migration 成为唯一真相源，`HarnessMysqlDdlParityTest` 改为校验 migration |
| A3 | 建库种子：admin 用户 / 角色、菜单、模型配置、内置工具、插件行 | `DataInitializer` 只建 admin 与角色 | 空库启动后菜单可见、可登录、可发对话 |
| A4 | `api_registry.sql` 方言修正 | 只有 SQLite 方言，`AUTOINCREMENT` / `datetime('now','localtime')` 切 MySQL 报语法错 | MySQL 分支可执行（本机无 MySQL，需标注为未真机验证） |

### 7.2 B 批：后端功能闭环与正确性

| # | 任务 | 现状证据 | 影响 |
|---|---|---|---|
| B1 | 审批闭环：`sec_approval` 存 payload（toolName+arguments+agentId）→ 审批通过后真重放 → 结果回注会话 | `ToolGovernanceService.java:116-123` 只 `return "需审批: ..."`；`SecurityAutoConfiguration.java:102-106` 回调只写审计；`SecurityFacadeImpl.java:82-84` 空实现 | Agent 写操作闭环断裂，文案「审批通过后自动执行」与实际不符 |
| B2 | 图引擎可控性：`POST /runs/{id}/cancel`、节点级 `timeoutMs`、`RestClient` 连接/读超时、并行池可配、`conflict` 裁决接口 | `STATUS_CANCELED` 定义后无第三处引用；`GraphNodeExecutor` 的 `RestClient.create()` 无超时；并行池固定 4 | 点「停止」后继续跑继续烧钱；单慢节点拖死整图 |
| B3 | RAG：embedding 改**半开熔断**（可恢复）+ 入库/查询校验 `embedding_model` 一致 + 切片参数读 `rag.chunk-size` + `RagRetrieveSkill` 传真实 `kbId` | `RagEmbedder.java:33,68-73` 永久熔断；`RagPipelineService.effectiveProperties()` 写死 `800/100`；`RagRetrieveSkill.java:62` `kbId` 恒 null；`RagProperties.getTopK()` 零引用 | 一次失败后全进程向量降级且与库中真向量不同空间 → 排序静默劣化；Agent 调 Skill 变成全库检索 |
| B4 | OLAP 真下推，或如实降级命名 | `ArrowOlapAnalyticsRepository:251/281/303/318` 全在内存 `List<Object[]> rows` 上循环，Calcite 扫的也是这份 rows | 数据量上来 `/analytics/*` OOM |
| B5 | L2 场景层接入（需先补 LLM 摘要组件，`FactExtractor` 只做规则抽取） | `L2SceneBlock` 有模型/建表/仓储 3 方法，但 `MemoryManager`/`AiMemoryService`/`MemoryAwarePromptBuilder`/`MemoryMcpToolkit`/`AiMemoryController` 全不引用 | 四层记忆实际只有三层在跑 |
| B6 | `habit` 恒空收敛：补抽取规则，或把 `PersonaInjection.DEFAULT_TYPES` 收敛为 `persona,preference` | `FactExtractor` 从不产出 `habit` | 每次构造提示词多发一次恒空查询 |
| B7 | `FieldMaskService` 接线：`@FieldDesensitize` 落到手机号/邮箱字段 | 全仓 0 个业务调用点；`field-mask.*` 7 项配置 0 读取 | 个人信息接口返回全明文 |
| B8 | `MemoryFlushManager` Windows 文件名 bug | 日志实测 6 次 `Failed to offload to JSONL ... Illegal char <:> at index 1`：sessionKey 被直接当文件名 | Windows 上记忆落盘持续失败（不影响主链路，但丢归档） |
| B9 | 启动守卫：库里缺关键表时翻译成「你可能从错误目录启动，当前 CWD=xxx」 | 现抛裸 `SQLITE_ERROR no such table: ai_managed_agent` | 诊断成本高（今天已撞一次） |

### 7.3 C 批：前端「点了就报错」的必修 bug

| # | 位置 | 问题 |
|---|---|---|
| C1 | `agent-trace-ui/src/api/fork.js:12,20,28` | 路径写 `/api/...` 而 `request.js:5` 已设 `baseURL='/api'` → 实际 `/api/api/...` **404** |
| C2 | `agentmemory/src/views/MemoryDashboard.vue` | 用 `ElMessage/ElMessageBox` 但 `<script setup>` 未 import，vite 无 auto-import → **ReferenceError**，画像配置增删改全崩 |
| C3 | `ai/src/views/AiToolLibrary.vue:78`、`AiWorkbench.vue:115` | 裸 `fetch` 不带 Authorization → 401 被 `catch` 吞成空列表；改用统一 `request.js` 封装 |
| C4 | `AiAgentManage.vue:1176,1198` | `res.data?.code === 200` 判错层级（拦截器已返回信封）→ **保存成功也弹「保存失败」** |
| C5 | `DataSourceManage.vue:246` | 新建时 `editingId=null` → `POST /datasources/null/test` 路径变量类型转换失败，测试连接永远 FAIL |

### 7.4 D 批：体验、假数据与清理

| # | 任务 | 证据 |
|---|---|---|
| D1 | 列表页补分页 / 空态 / loading | 18 页仅 2 页有分页；6 页无 `el-empty`；4 页无 loading；`TraceList.vue` 固定 `limit=50`，第 51 条永远看不到 |
| D2 | 假数据换真实来源 | `Home.vue:99-104`（插件数×3、状态字面量）、`MainLayout.vue:233`（角色恒「普通用户」）、`MemoryDashboard.vue:464`（`trend: 72`）、`bailianModels.js:8-58`（模型清单硬编码、无后端目录接口） |
| D3 | 清理死调用 | `agentmemory/src/api/memory.js:105/110/115` 指向不存在的 `/analytics/personas*`；`Home.vue:13` `router.push('/channel/deploy')` 无此路由 |
| D4 | 死配置收敛（7 项） | `field-mask.*`、`data-scope.*`、`operation-log.*` 部分、`plugin.intent.channel.*`、`rag.top-k`、`ai.agent.memory-*`（与 `agent-memory.*` **同名双份，改错一个不生效**）、`plugin.sys.agent-name` |
| D5 | 明确唯一生产前端 | `modules/agent-memory/frontend/agent-memory-ui` 与 `modules/agentmemory` 逐行同源；仍有 `frontend/agent-harness-ui` 遗留工程 |
| D6 | 补 `sys` 前端模块的 API 注册表、智能体参数两组页面 | 后端 `SysPluginRegister` 已下发 `frontendModule=sys`，但 `local-modules.js` 无此 key、目录不存在，loader 仅 `console.warn` |

### 7.5 建议起手

**只做一件事：A 批的 A1 + A2 + A3。** 理由：`data/` 被 gitignore、55/62 张表无 DDL —— 当前任何改动都只能在你这台机器的那份库上验证，无法复现也无法回滚，这是所有其他项的前置阻塞。A4 可与 A1 合并做，但 MySQL 分支本机无实例，只能静态校验。

