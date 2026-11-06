# 为 6 个 feature 模块补齐 PluginRegister，让后端 /api/plugins 成为插件清单唯一真相源

> 状态：实施中（代码已落盘，待构建与真机验证）
> 起草日：2026-11-06
> 关联模块：`modules/agent-harness`、`modules/agent-datasource`、`modules/agent-rag`、`modules/module-tools`、`modules/agent-memory`、`modules/agent-trace`、`frontend/web-shell`
> 规范依据：`docs/rules/PLAN_DOC_RULES.md`

---

## 一、置信度与剩余风险

- 当前置信度：87%
- 主要剩余风险：新增 6 个 PluginRegister 会让 `GET /api/plugins` 从 3 条变 9 条，并让 API 注册表（`api_registry`）首次登记 `/api/biz/{wf,ds,rag,ai/tools}` 与 `/api/agent-memory/*` 下的端点；若前端或权限侧存在「按插件数量 / 前缀」的隐式假设，会以「菜单重复 / 接口 403 / 注册表多出一批行」的形式暴露。

---

## 二、目标

本次要达成：

- 为 `workflow` / `datasource` / `rag` / `tools` / `agentmemory` / `agent-trace-ui` 六个前端 feature 模块各补一个后端 `PluginRegister`。
- 让 `GET /api/plugins` 返回 9 条，与前端 `localPluginModules` 可加载模块一一对应；`frontendModule` 与前端模块 key、目录名三者一致。
- 前端 `shellDefaultPlugins` 退化为「后端不可用时的兜底」，后端已提供时由去重逻辑合并，不重复加载。

本次明确不做（延后项见第九节）：

- 不改动前端 `shellDefaultPlugins` 的兜底逻辑本身（保留可用性兜底）。
- 不新增任何业务接口 / 数据表。
- 不做 SQLite→MySQL 迁移。
- 不把 `ObservController` 从 agent-harness 迁到 agent-trace（属重构）。

---

## 三、成功标准

本方案完成后，以下每一条都应能在真机上直接判定为真：

1. 启动后端后执行 `curl --noproxy '*' -s http://127.0.0.1:9900/api/plugins` → 返回 `data` 数组长度 = **9**，`pluginId` 集合 = `{sys, ai, feishu, workflow, datasource, rag, tools, agentmemory, agent-trace-ui}`，且无重复 `pluginId`。
2. 同一响应中 `order` 升序为 `ai(4) → feishu(5) → sys(6) → workflow(7) → datasource(8) → rag(9) → tools(10) → agentmemory(13) → agent-trace-ui(14)`。
3. 登录前端主壳 → 左侧菜单出现「工作流编排 / 数据源管理 / RAG 知识库 / 工具中心 / 智能体记忆 / 链路观测」6 项。
4. 打开浏览器 Console → `Frontend module not found for plugin:` 的条数 = **1**（仅 `sys`，属已知项）；除 `sys` 外无任何模块加载失败。
5. 依次访问 `/wf/workflows`、`/ds/datasources`、`/rag/knowledge`、`/tools/center`、`/agent-memory/dashboard`、`/agent-trace` → 每个页面均渲染出对应标题/表格，无白屏、无未捕获异常。
6. 打开 API 注册表页面，按模块筛选 `workflow` / `datasource` / `rag` / `tools` / `agentmemory` → 每个模块记录数 ≥ **1**；`/api/biz/ai/tools` 下的端点归属 `tools`，`/api/agent-memory/*` 下的端点归属 `agentmemory`。

> 前置条件（不算成功标准）：`mvn -pl agent-application -am test` 中 `PluggableArchitectureTest`、`ApiRegistryOwnershipTest`、`LoadedModuleLoggerTest` 全绿。

---

## 四、参照行为（对标）

无（本方案不引入外部对标，仅沿用仓库内 `SysPluginRegister` / `AiPluginRegister` / `FeishuPluginRegister` 的既有模式）。

---

## 五、现状差距

### 能力缺失

- 后端 `PluginRegister` 只有 3 个实现（`sys` / `ai` / `feishu`），而 6 个 feature 模块已有前端 UI 与真实 Controller，却没有后端插件身份 → `GET /api/plugins` 与实际 UI 能力不对齐。
- 前端因此只能靠硬编码 `shellDefaultPlugins` 加载这 6 个模块，形成「前后端两份清单」。
- `ApiEndpointScanner` 只登记路径命中某个插件 `apiPrefix` 的 Controller；这 6 个模块没有 PluginRegister，其端点从不进入 `api_registry`。

### 只是没接线

- 6 个模块均已有 `*AutoConfiguration`，且都已在各自 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中注册。
- 只差「一个 `PluginRegister` 类 + 一个 `@Bean`」这一步，不涉及任何业务逻辑改动。

### 不打算解决的问题

- `sys` 无独立前端模块导致的 loader 提示：本次不处理（见第九节）。
- `AiPluginRegister.apiPrefix=/api/ai` 覆盖不到实际在用的 `/api/biz/ai/*`：这是既有问题，本次不改（见第九节）。

---

## 六、技术方向

### 1. 每个模块新增一个 PluginRegister 类（放 core 包，不放 autoconfig）

职责：

- 仅声明插件身份元数据，零业务逻辑、零依赖注入。

关键接口：

```java
public interface PluginRegister {
    String getPluginId();
    String getPluginName();
    String getApiPrefix();
    String getFrontendRoute();
    default String getFrontendModule() { return getPluginId(); }
    String getAgentName();
    default int getOrder() { return 100; }
}
```

取值清单（均已对照真实 Controller 前缀与前端 `routes.js` 核实）：

| 模块 | 新类（core 包） | pluginId | pluginName | apiPrefix | frontendRoute | frontendModule | agentName | order |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| agent-harness | `com.zimo.module.ai.WorkflowPluginRegister` | `workflow` | 工作流编排 | `/api/biz/wf` | `/wf` | `workflow` | `workflow-agent` | 7 |
| agent-datasource | `com.zimo.module.ds.DsPluginRegister` | `datasource` | 数据源管理 | `/api/biz/ds` | `/ds` | `datasource` | `datasource-agent` | 8 |
| agent-rag | `com.zimo.module.rag.RagPluginRegister` | `rag` | RAG 知识库 | `/api/biz/rag` | `/rag` | `rag` | `rag-agent` | 9 |
| module-tools | `com.zimo.module.tools.ToolsPluginRegister` | `tools` | 工具中心 | `/api/biz/ai/tools` | `/tools` | `tools` | `tools-agent` | 10 |
| agent-memory | `com.zimo.agent.memory.AgentMemoryPluginRegister` | `agentmemory` | 智能体记忆 | `/api/agent-memory` | `/agent-memory` | `agentmemory` | `agentmemory-agent` | 13 |
| agent-trace | `com.zimo.module.trace.TracePluginRegister` | `agent-trace-ui` | 链路观测 | `/api/biz/ai/observ` | `/agent-trace` | `agent-trace-ui` | `trace-agent` | 14 |

取舍理由：

- 放 core 包而非 autoconfig 包：与既有 `SysPluginRegister` / `AiPluginRegister` / `FeishuPluginRegister` 一致；且 `PluggableArchitectureTest` 只禁止「core 依赖 autoconfig」，反过来（autoconfig `new` core 类）是允许的。
- `agent-trace` 的 `apiPrefix` 取 `/api/biz/ai/observ`：该 UI 的数据确实来自 ObservController（物理实现仍在 agent-harness），逻辑归属链路观测；`/api/agent-memory` 与 `/api/ai/memory` 并存时，`/api/ai/memory` 会按最长前缀归属到 `ai`，不归 `agentmemory`，这是可接受的语义。

### 2. 在既有 AutoConfiguration 追加 @Bean，不新增 imports

职责：

- 把 `PluginRegister` 注册成 Spring Bean，由 `PluginRegistry` 通过 `List<PluginRegister>` 收集。

挂载点：

| 目标 AutoConfiguration | 新增方法 |
| --- | --- |
| `com.zimo.module.ai.autoconfig.AiWorkflowAutoConfiguration` | `@Bean @ConditionalOnMissingBean WorkflowPluginRegister workflowPluginRegister()` |
| `com.zimo.module.ds.autoconfig.DsAutoConfiguration` | `@Bean @ConditionalOnMissingBean DsPluginRegister dsPluginRegister()` |
| `com.zimo.module.rag.autoconfig.RagAutoConfiguration` | `@Bean @ConditionalOnMissingBean RagPluginRegister ragPluginRegister()` |
| `com.zimo.module.tools.autoconfig.ToolsAutoConfiguration` | `@Bean @ConditionalOnMissingBean ToolsPluginRegister toolsPluginRegister()` |
| `autoconfig.com.zimo.agent.memory.AgentMemoryAutoConfiguration` | `@Bean @ConditionalOnMissingBean AgentMemoryPluginRegister agentMemoryPluginRegister()` |
| `com.zimo.module.trace.autoconfig.GenAiTraceAutoConfiguration` | `@Bean @ConditionalOnMissingBean TracePluginRegister tracePluginRegister()` |

取舍理由：

- 统一加 `@ConditionalOnMissingBean`，与既有风格一致，也便于单测用 `ApplicationContextRunner` 覆盖。
- 不新建 imports 文件：6 个目标 AutoConfiguration 均已在 imports 中，无需重复登记。

### 3. 前端兜底保留，去重逻辑不变

- `plugin-loader.js` 的 `mergeShellDefaultPlugins` 按 `pluginId` / `frontendModule` 去重；后端提供后，`shellDefaultPlugins` 同名条目不会再触发二次加载。
- 不改前端代码；依赖上一轮已修好的「`agenttrace` → `agent-trace-ui`」key 一致性。

---

## 七、里程碑

### M1. 6 个模块补齐 PluginRegister 与 @Bean

- 产出：6 个新类 + 6 处 `@Bean`。
- 验证：`GET /api/plugins` 返回 9 条（第三节标准 1、2）。

### M2. 前端菜单与页面可用性

- 产出：主壳菜单 6 项可点，路由可渲染。
- 验证：第三节标准 3~5。

### M3. API 注册表归属

- 产出：`api_registry` 新增对应模块记录。
- 验证：第三节标准 6。

### M4. 回填实施记录

- 产出：本文档第十一节。

---

## 八、自测计划

### 场景 1：后端插件清单

1. 停止后端进程。
2. 执行 `mvn -pl agent-application -am package`（前置条件，确认构建成功）。
3. 启动后端（端口 9900）。
4. 执行 `curl --noproxy '*' -s http://127.0.0.1:9900/api/plugins`。
5. 验证：
   - `data` 长度 = 9；
   - `pluginId` 集合 = `{sys, ai, feishu, workflow, datasource, rag, tools, agentmemory, agent-trace-ui}`；
   - `pluginId` 无重复；
   - `order` 升序与服务端排序一致（4,5,6,7,8,9,10,13,14）。

### 场景 2：前端菜单加载

1. 启动前端 dev（16600）并登录。
2. 打开浏览器 Console。
3. 验证：
   - 菜单出现 6 项，文本与 `pluginName` 一致；
   - `Frontend module not found for plugin:` 计数 = 1（仅 `sys`）；
   - 无动态 import 失败报错。

### 场景 3：页面可渲染

1. 依次访问 `/wf/workflows`、`/ds/datasources`、`/rag/knowledge`、`/tools/center`、`/agent-memory/dashboard`、`/agent-trace`。
2. 验证：每个页面渲染出对应标题/表格；无白屏；Console 无未捕获异常。

### 场景 4：API 注册表归属

1. 打开 API 注册表页面。
2. 分别按 `workflow` / `datasource` / `rag` / `tools` / `agentmemory` 筛选。
3. 验证：
   - 每个模块记录数 ≥ 1；
   - `/api/biz/ai/tools` 下端点 `moduleCode = tools`；
   - `/api/agent-memory/*` 下端点 `moduleCode = agentmemory`。

### 验证手段的语义缺陷声明

- 「`/api/plugins` 返回 9 条」只证明 Bean 注册成功，**不证明前端一定加载**（前端还依赖 import 路径与编译产物）。因此必须配合场景 2/3，不能只凭场景 1 判定完成。
- `sys` 的 loader 提示是既有现象，场景 2 的「计数 = 1」是预期值，不代表本方案有缺陷。

---

## 九、已知延后项

- **`sys` 的前端模块归属** —— 原因：`sys` 的 UI 是否内置于主壳、还是要单独建 `frontend/modules/sys`，需要单独确认，超出本次 6 模块范围。
- **`AiPluginRegister.apiPrefix=/api/ai` 覆盖不到 `/api/biz/ai/*`** —— 原因：这是既有不一致，改成 `/api/biz/ai` 会影响既有归属与权限判定，应单独评估。
- **`ObservController` 迁到 agent-trace** —— 原因：属模块重构，本次只做逻辑归属，不动物理位置。
- **回收前端 `shellDefaultPlugins` 兜底** —— 原因：后端不可用时兜底仍有价值，保留。

---

## 十、实施顺序

1. 新建 6 个 `PluginRegister` 类（core 包）。
2. 在 6 个既有 `*AutoConfiguration` 追加 `@Bean`。
3. 打包并启动后端，验证 `/api/plugins` 9 条（场景 1）。
4. 启动前端，验证菜单与 6 个页面（场景 2、3）。
5. 验证 API 注册表归属（场景 4）。
6. 回填第十一节「实施记录」。

---

## 十一、实施记录

### 1. 实际改动清单（2026-11-06）

新增 6 个类（均为纯元数据，无业务逻辑、无注入）：

| 路径 | 类 |
| --- | --- |
| `modules/agent-harness/agent-harness-core/src/main/java/com/zimo/module/ai/WorkflowPluginRegister.java` | `WorkflowPluginRegister` |
| `modules/agent-datasource/module-datasource-core/src/main/java/com/zimo/module/ds/DsPluginRegister.java` | `DsPluginRegister` |
| `modules/agent-rag/agent-rag-core/src/main/java/com/zimo/module/rag/RagPluginRegister.java` | `RagPluginRegister` |
| `modules/module-tools/module-tools-core/src/main/java/com/zimo/module/tools/ToolsPluginRegister.java` | `ToolsPluginRegister` |
| `modules/agent-memory/agent-memory-core/src/main/java/com/zimo/module/agentmemory/AgentMemoryPluginRegister.java` | `AgentMemoryPluginRegister` |
| `modules/agent-trace/agent-trace-core/src/main/java/com/zimo/module/trace/TracePluginRegister.java` | `TracePluginRegister` |

修改 6 个既有 AutoConfiguration（各追加 1 个 `@Bean` + import）：

| 文件 | 新增方法 | 行号 |
| --- | --- | --- |
| `agent-harness-core/.../ai/autoconfig/AiWorkflowAutoConfiguration.java` | `workflowPluginRegister()` | 76 |
| `module-datasource-core/.../ds/autoconfig/DsAutoConfiguration.java` | `dsPluginRegister()` | 51 |
| `agent-rag-core/.../rag/autoconfig/RagAutoConfiguration.java` | `ragPluginRegister()` | 174 |
| `module-tools-core/.../tools/autoconfig/ToolsAutoConfiguration.java` | `toolsPluginRegister()` | 202 |
| `agent-memory-core/.../agentmemory/autoconfig/AgentMemoryAutoConfiguration.java` | `agentMemoryPluginRegister()` | 562 |
| `agent-trace-core/.../trace/autoconfig/GenAiTraceAutoConfiguration.java` | `tracePluginRegister()` | 67 |

所有 `@Bean` 均带 `@ConditionalOnMissingBean`；未新增/修改任何 `AutoConfiguration.imports`（6 个目标装配类原已在册）。

### 2. 与方案的偏差

- **无取值偏差**：6 个类的 `pluginId` / `pluginName` / `apiPrefix` / `frontendRoute` / `frontendModule` / `agentName` / `order` 与第六节表格逐字一致。
- **一处方案未提及的必要改动**：`AgentMemoryAutoConfiguration` 原本没有 `ConditionalOnMissingBean` 的 import，已补 `org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean`；其余 5 个类该 import 已存在。

### 3. 实施期核对到的事实（非新坑，但值得记）

- **6 个 `apiPrefix` 均有真实控制器命中**（不是拍脑袋取值）：
  - `/api/biz/wf` → `WfWorkflowController`、`WfTemplateController`、`WfAiChatController`（3 个）
  - `/api/biz/ds` → `DsDataSourceController`（1 个）
  - `/api/biz/rag` → `RagController`（1 个）
  - `/api/biz/ai/tools` → `ToolGovernanceController`（1 个）
  - `/api/agent-memory` → `MemoryArchController`、`MemoryFileController`、`AgentMemoryAnalyticsController`（3 个）
  - `/api/biz/ai/observ` → `ObservController`、`SessionForkResumeController`（2 个）
- **新增类名全局无重名**：6 个类名与 6 个 Bean 方法名在仓库内均为首次出现，规避了「跨模块同名 Mapper/Bean → `ConflictingBeanDefinitionException`」这一类启动失败。
- **`agent-trace-core` 无需改 pom**：其 `pom.xml` 已直接依赖 `framework-common`（尽管源码此前一次都没用到），本次新增类可直接编译。
- **`ai` 与 `workflow` 共处一个 jar**：`AiPluginRegister`（`/api/ai`）与 `WorkflowPluginRegister`（`/api/biz/wf`）同属 agent-harness，两个前缀互不包含，不存在最长前缀抢占。
- **两个守卫测试不受影响**：`PluggableArchitectureTest` 只禁「core → autoconfig」，新类只依赖 `com.zimo.framework.common.PluginRegister`；`LoadedModuleLoggerTest` 用的是自带桩插件，不统计真实 Bean 数量。

### 4. 真机验证结果

**未执行**（按当前工作约定：代码改动后不代跑构建与服务重启，交由使用方执行）。

待执行命令与判据：

```bat
rem 0) Windows 下必须先让 python3 指向真实可执行文件，否则 framework-ai 的
rem    沙箱测试（LocalSandboxBackendTest / SandboxSmokeTest）会以退出码 9009 全灭。
rem    根因：系统 PATH 里只有 python.exe，python3 只解析到
rem    %LOCALAPPDATA%\Microsoft\WindowsApps\python3.exe —— 那是 0 字节的应用商店别名桩。
rem    下面这行只影响当前 cmd 窗口，不修改系统环境。
set "PATH=C:\Users\Lenovo\.workbuddy\binaries\python\versions\3.13.12;%PATH%"

rem 1) 构建 + 守卫测试（前置条件；Windows 下用 mvnw.cmd，不要写 ./mvnw 或 /mvnw）
mvnw -pl agent-application -am test

rem 2) 起后端后验证插件清单
curl --noproxy "*" -s http://127.0.0.1:9900/api/plugins
rem   判据：data 长度 = 9；pluginId 集合不含重复；
rem   order 升序 = ai4, feishu5, sys6, workflow7, datasource8, rag9, tools10, agentmemory13, agent-trace-ui14
```

前端菜单/页面（第三节标准 3~5）与 API 注册表归属（标准 6）同样待真机确认。

> 验证语义提醒（沿第三节声明）：`/api/plugins` 返回 9 条**只证明 Bean 注册成功**，不证明前端能加载；必须配合菜单与 6 个页面的实访，否则「清单对了但页面空白」会被误判为完成。

### 5. 构建期发现的既有失败（**非本次改动引入**）

首次 `mvnw -pl agent-application -am test` 在 `framework-ai` 阶段失败，失败用例仅两个类、共 8 个：

| 测试类 | 失败数 | 断言位置 |
| --- | --- | --- |
| `LocalSandboxBackendTest` | 5/6 | expected `0`/`3` but was **9009**；`timedOut` 期望 true 实为 false；env 注入输出为空 |
| `SandboxSmokeTest` | 3/3 | expected `0`/`7` but was **9009** |

**根因是环境，不是代码**：

- 两个测试硬编码以 `python3` 起子进程（`List.of("python3", "-c", ...)`）。
- 本机用户 PATH 中只有 `C:\Users\Lenovo\AppData\Local\Programs\Python\Python311\`（仅 `python.exe`）。
- 唯一名为 `python3.exe` 的是 `%LOCALAPPDATA%\Microsoft\WindowsApps\python3.exe`，**0 字节的应用商店别名桩**，执行返回 **9009**（`cmd` 的「命令未找到」）。
- 实证：还原真实 PATH 后 `Get-Command python3` → WindowsApps 桩；`python3 -V` → `exit=9009`；`python -V` → `3.11.9`，正常。

**结论**：这 8 个失败在本次改动前就存在（`git log` 显示两个测试文件最后改动为 2026-09-10，本次未触碰 `framework/framework-ai` 任何源码）。判定本次改动是否安全的办法是让 `python3` 可用后重跑（见第 4 节命令块第 0 步），或只跑三个守卫测试：

```bat
mvnw -pl agent-application -am test -Dtest=PluggableArchitectureTest,ApiRegistryOwnershipTest,LoadedModuleLoggerTest -DfailIfNoTests=false
```

> 该问题已按「方案 A」在**环境侧**修复（2026-11-06，经用户确认）：在 `C:\Users\Lenovo\AppData\Local\Programs\Python\Python311\` 下为 `python.exe` 建立了硬链接 `python3.exe`（硬链接，非复制，103192 字节，不额外占空间）。因 PATH 中 Python311 位次 2 早于 WindowsApps 位次 4，已成功盖住商店别名桩。
>
> 验证：真实 PATH 下 `Get-Command python3` → `...\Python311\python3.exe`；`python3 -V` → `Python 3.11.9`，`exit=0`；`python3 -c "import sys; sys.exit(3)"` → `exit=3`。
>
> 回滚方式：删除 `C:\Users\Lenovo\AppData\Local\Programs\Python\Python311\python3.exe` 即可（删除硬链接不影响 `python.exe` 本体）。
