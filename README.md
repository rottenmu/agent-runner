# agent_runner

`agent_runner` 是一个面向 AI Agent 运行场景的插件化平台工程。后端采用 Java 17、Spring Boot 3.4.5 和 Maven 多模块组织，前端采用 Vue 3、Vite 和「单一主壳 + 源码级插件模块」架构。

当前工程的核心方向：

- 后端保留插件平台能力，不收敛为不可扩展的单体应用。
- 前端保留一个主壳应用，由主壳动态加载各插件的菜单、路由和页面模块。
- AI 智能体、对话、图编排、长期记忆、工具治理、RAG、意图识别、飞书集成等能力以插件模式接入主项目。

> ⚠️ **与历史文档的偏差（重要）**：早期 README / AGENTS.md 中提到的 `admin-shell`、`module-ai`、`module-demo`、`module-wms`、`xingju-project-mgmt-starter`、`ai-agent-spring-boot-starter` 等模块**均已不存在**，以本文档的模块清单为准。

## 工程结构

```text
agent_runner
├─ framework/          共享框架能力（common / autoconfig / ai）
├─ modules/            业务插件与独立模块
├─ agent-application/  后端主应用装配层（唯一 Spring Boot 主入口）
├─ frontend/
│  ├─ web-shell/       前端主壳应用
│  └─ modules/         前端插件模块（源码级）
├─ data/               本地运行数据（SQLite / H2 / RocksDB / Arrow OLAP）
├─ docs/               架构设计与实施计划
└─ AGENTS.md           仓库协作规范
```

## 后端架构

根 Maven 工程聚合三个层次：`framework` / `modules` / `agent-application`。

### framework（平台级共享能力）

| 模块 | 职责 |
|---|---|
| `framework-common` | 统一返回体 `R`/`ApiResponse`、分页、错误码、通用异常、`PluginRegister` 插件契约、`PluginRegistry`（`GET /api/plugins`）、校验工具、AI skill 抽象、文件存储、AI 事件发布 |
| `framework-autoconfig` | 全局自动配置：MyBatis-Plus、Web、API 注册表、存储（RocksDB / 文件） |
| `framework-ai` | AI 运行时 starter：agent 工厂/路由/运行时、`AiChatClient`/`OpenAiCompatibleChatClient`、skill、`ToolBridge`、MCP、A2A、可观测、记忆中间件 |

### modules（业务插件）

| 模块 | 定位 | 后端 pluginId / 前缀 |
|---|---|---|
| `module-sys` | 系统管理：用户/角色/RBAC/菜单/智能体设置/API 注册表 | `sys` / `/api/biz/sys`（order 6） |
| `agent-auth` | 登录/注册/Sa-Token 会话（强依赖 `module-sys` 的 `SysUser` 表） | — |
| `agent-harness` | 最大模块：对话/图引擎/工作流/技能/工具/模型配置/协作审批/MCP/A2A | `ai` / `/api/ai`（order 4） |
| `agent-datasource` | 数据源管理 + 通用存储 SPI（被 `agent-memory` 复用） | — |
| `agent-rag` | 检索增强 | — |
| `agent-intent` | 意图识别（规则引擎 + 可选 LLM 解析器） | — |
| `module-tools` | 工具治理（权限/限流/熔断/审计） | — |
| `module-security` | 安全治理 | — |
| `agent-trace` | 链路追踪（OTel / observer） | — |
| `agent-memory` | 四层记忆（L0~L3）+ H2(OLTP)/Arrow(OLAP) 双存储；另含独立可运行子应用 `agent-memory-application` | — |
| `module-feishu` | 飞书机器人/事件/凭证 | `feishu` / `/api/feishu`（order 5） |

> 模块实际只拆成 `*-core`（自动配置以 `…autoconfig` 子包内嵌，并非独立 `*-autoconfig` 子模块）。`agent-application` 的 `pom.xml` 直接依赖各业务模块的 `-core`。

### agent-application（主应用装配层）

- 唯一 `@SpringBootApplication` 主类：`com.zimo.agentapplication.AgentApplication`。
- 主类**不自定义 `@ComponentScan`**，完全靠各 jar 的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 装配。
- 全局配置集中点：`CorsConfig`、`GlobalExceptionHandler`、`DataInitializer`、`application.yml`。
- 默认端口：**9900**。

## 插件发现机制（前后端契约）

后端通过 `PluginRegister` 声明插件身份（pluginId / 名称 / apiPrefix / frontendRoute / frontendModule / agentName / order）。`PluginRegistry` 暴露：

```text
GET /api/plugins
```

当前后端实际注册的插件只有三个：`sys`、`ai`、`feishu`（其余模块为后端能力，未独立声明 PluginRegister）。

前端主壳的加载流程：

```text
用户登录
  ↓
web-shell 调用 GET /api/plugins
  ↓
mergeShellDefaultPlugins(后端插件, 前端 shellDefaultPlugins)   ← 按 pluginId/frontendModule 去重合并
  ↓
按 frontendModule 从 localPluginModules 找到本地前端模块并 import()
  ↓
合并 menus / 动态注册 routes
```

前端 `frontend/web-shell/src/plugin-loader/` 下：

- `local-modules.js`：`localPluginModules`（本地前端模块导入表）+ `shellDefaultPlugins`（前端侧默认插件清单，覆盖 workflow / datasource / rag / tools / agentmemory / agent-trace-ui）。
- `plugin-loader.js`：`loadFrontendPlugins` / `resolvePluginModules` / `mergeShellDefaultPlugins`。

**契约约束**：`shellDefaultPlugins` 中每个条目的 `frontendModule` 必须能在 `localPluginModules` 中找到对应 key；后端插件若没有对应前端模块（如 `sys` 的 UI 内置于主壳），加载器会跳过并给出提示，不影响其余插件。

## 前端架构

```text
frontend
├─ web-shell/          唯一主前端应用（dev 端口 16600 / 预览端口 18000）
└─ modules/            各子平台前端模块
    ├─ ai/  feishu/  workflow/  datasource/  rag/  tools/  agentmemory/  agent-trace-ui/
```

每个前端插件模块导出 `index.js` / `menus.js` / `routes.js` / `src/views/`，由主壳动态合并菜单与路由。前端插件模块不应重复创建登录、全局布局、全局请求封装或主路由实例。

## 数据库与存储（与 AGENTS.md 规范的偏差说明）

- **平台主库当前使用 SQLite**（`application.yml` 中 `jdbc:sqlite:./data/agent_runner.db`，`MybatisPlusConfig` 用 `DbType.SQLITE`）。AGENTS.md 规定「禁用 SQLite/H2，只能 MySQL」——这是已知偏差：本地开发以 SQLite 跑通，生产切换 MySQL 仍需补做迁移与验证（本仓库无 Flyway/Liquibase，表结构由 `@PostConstruct` 内联 DDL 与 DAO `CREATE TABLE IF NOT EXISTS` 自举）。
- **`agent-memory` 四层记忆**经用户明确批准使用嵌入式 H2（OLTP）+ Arrow/Calcite（OLAP），属进程内记忆存储的独立例外，其他模块不得复用。
- **无数据库迁移工具**：全仓无 Flyway/Liquibase 依赖，`modules/agent-harness/.../db/V*__*.sql` 为尚未被加载器消费的孤儿脚本。

## 常用命令

后端完整验证：

```powershell
mvn clean verify
```

后端主应用打包：

```powershell
mvn -pl agent-application -am package
```

后端主应用运行（端口 9900）：

先打出可执行 jar，再用仓库自带脚本启动：

```powershell
mvn -pl agent-application -am package -DskipTests
python start-backend.py
```

> **不要用 `mvn -pl agent-application -am spring-boot:run`**：`-am` 会把根聚合工程
> `agent-runner`（`packaging=pom`）一起拉进 reactor，而 CLI 直接触发的 goal 会在**每个**
> reactor 项目上执行，执行到根工程时必然报
> `Unable to find a suitable main class`。
>
> 若确实想用 `spring-boot:run`，必须先 `install` 到本地仓库，再只对单一模块执行：
> `mvn -pl agent-application -am install -DskipTests` → `mvn -pl agent-application spring-boot:run`。
>
> `start-backend.py` 里带 `--add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED`，
> agent-memory 的 Arrow(OLAP) 存储依赖这个参数才能启动，直接 `java -jar` 时别漏掉。

前端主壳构建：

```powershell
cd frontend/web-shell
npm install
npm run build
```

前端开发服务（端口 16600）：

```powershell
cd frontend/web-shell
npm run dev
```

## 工程约束

- 共享能力放在 `framework/`；业务插件放在 `modules/`；应用组装放在 `agent-application/`。
- 不编辑 `target/`、`dist/` 等生成产物。
- Markdown 文档默认使用中文。
- 敏感配置通过环境变量注入，不要扩散到文档、日志、提交信息或聊天回复中。
