# Production Studio

`agent_runner` 是一个面向生产管理场景的插件化平台工程。后端采用 Java 17、Spring Boot 3 和 Maven 多模块组织，前端采用 Vue 3、Vite 和主壳插件模块架构。

当前工程的核心方向是：

- 后端保留插件平台能力，不收敛为不可扩展的单体应用。
- 前端保留一个主壳应用，由主壳动态加载各子平台的菜单、路由和页面模块。
- AI、制造项目管理、系统管理、示例模块等能力以插件或 starter 模式接入主项目。

## 工程结构

```text
agent_runner
├─ framework/          共享框架能力
├─ modules/            业务插件、独立模块和可复用 starter
├─ admin-shell/        后端主应用装配层
├─ frontend/
│  ├─ web-shell/       前端主壳应用
│  └─ modules/         前端插件模块
├─ agents/             可选 AgentScope Python sidecar
├─ docs/               架构设计和实施计划
└─ AGENTS.md           仓库协作规范
```

## 后端架构

根 Maven 工程聚合三个主要层次：

```text
framework
modules
admin-shell
```

### framework

`framework/` 放置平台级共享能力：

- `framework-common`：通用响应、分页、异常、插件注册接口等基础模型。
- `framework-autoconfig`：插件发现接口、MyBatis-Plus 配置、Web 通用配置等。
- `module-auth`：用户身份模型、登录注册、Sa-Token 会话、登录拦截和认证异常处理。

插件核心契约是 `PluginRegister`，每个插件通过它声明：

```text
pluginId
pluginName
apiPrefix
frontendRoute
frontendModule
agentName
order
```

`PluginRegistry` 对外提供插件发现接口：

```text
GET /api/plugins
```

前端主壳通过该接口获取已启用插件，再根据 `frontendModule` 加载对应前端模块。

### modules

`modules/` 承载业务插件、starter 和部分独立服务模块。

当前主要模块包括：

| 模块 | 定位 |
|---|---|
| `module-sys` | 系统管理插件 |
| `module-ai` | AI 智能体平台插件 |
| `ai-agent-spring-boot-starter` | AI 智能体、技能、MCP、A2A 通用 starter 能力 |
| `xingju-project-mgmt-starter` | 制造业项目管理平台插件聚合模块，内部包含 `xingju-project-mgmt-core` 和 `xingju-project-mgmt-autoconfig` |
| `module-wms` | 仓储管理平台插件，承接 `inventory-mgmt` 的非 RFID Java 后端能力 |

标准平台插件通常采用：

```text
module-xxx
├─ module-xxx-core
└─ module-xxx-autoconfig
```

其中：

- `core` 放插件身份、实体、控制器、服务等核心业务代码。
- `autoconfig` 放 Spring Boot 自动配置、插件注册 Bean 和配置开关。

### admin-shell

`admin-shell/` 是当前主后端应用入口。

它负责：

- 启动 Spring Boot 主应用。
- 聚合框架自动配置。
- 引入已启用业务插件。
- 暴露统一认证、插件发现和业务接口。

默认端口：

```text
18080
```

主应用当前通过 Maven 依赖接入：

```text
framework-autoconfig
module-sys-autoconfig
module-demo-autoconfig
xingju-project-mgmt-autoconfig
module-ai-autoconfig
module-wms-autoconfig
```

## 前端架构

前端采用“单一主壳 + 源码级插件模块”的组织方式。

```text
frontend
├─ web-shell/          唯一主前端应用
└─ modules/            各子平台前端模块
```

### web-shell

`frontend/web-shell/` 是唯一主应用，负责：

- 登录和退出。
- token 存储。
- 全局 Axios 请求封装。
- 主布局。
- Vue Router 实例和路由守卫。
- 插件发现。
- 菜单合并。
- 动态路由注册。

插件加载入口：

```text
frontend/web-shell/src/plugin-loader/plugin-loader.js
frontend/web-shell/src/plugin-loader/local-modules.js
```

加载流程：

```text
用户登录
  ↓
web-shell 调用 GET /api/plugins
  ↓
读取后端插件元数据中的 frontendModule
  ↓
从 localPluginModules 找到本地前端模块
  ↓
合并模块 menus
  ↓
动态注册模块 routes
  ↓
在主布局 router-view 中渲染插件页面
```

当前本地前端模块注册包括：

```text
sys
demo
manufacturing-pm
ai
wms
```

### frontend/modules

每个前端插件模块负责导出自己的菜单、路由和页面组件。

推荐结构：

```text
frontend/modules/xxx
├─ index.js
├─ menus.js
├─ routes.js
└─ src/views/
```

模块描述对象示例：

```js
export default {
  pluginId: 'xxx',
  name: '模块名称',
  baseRoute: '/biz/xxx',
  menus,
  routes,
  setup() {}
}
```

前端插件模块不应该重复创建独立登录、全局布局、全局请求封装或主路由实例。

## AI 模块

AI 能力分为两层：

```text
modules/ai-agent-spring-boot-starter
modules/module-ai
```

`ai-agent-spring-boot-starter` 提供通用能力：

- `AiAgentProperties`
- `AiSkill`
- `AiSkillRegistry`
- 默认技能
- `AiAgentService`
- MCP HTTP 适配
- A2A HTTP 适配

`module-ai` 负责作为平台插件挂入主项目：

```text
pluginId: ai
apiPrefix: /api/ai
frontendModule: ai
agentName: ai-agent
```

当前实际接口包括：

```text
POST /api/ai/mcp
GET  /api/ai/a2a/agent-card
POST /api/ai/a2a/message
```

注意：早期设计文档中曾规划 `/api/ai/mcp/tools`、`/api/ai/a2a/tasks/send` 等路径，当前代码实现与该设计存在差异，后续如需对齐协议规范，应统一接口路径。

## WMS 模块边界

`modules/module-wms` 是仓储管理平台插件，同时承接 `inventory-mgmt` 的非 RFID Java 后端能力。

当前结构：

```text
modules/module-wms
├─ module-wms-core          WMS 业务接口、服务、Mapper、测试
└─ module-wms-autoconfig    平台插件自动配置和插件注册
```

插件身份：

```text
pluginId: wms
apiPrefix: /api/wms
frontendRoute: /biz/wms
frontendModule: wms
agentName: wms-agent
```

它的长期边界是：

- 只实现非 RFID 的库存、WMS、移动端、MRP 预览和 Agent 巡检预览。
- 不新增 RFID Controller、Service、Mapper、路由、表或兼容空壳。
- API 响应统一使用 `code/message/data`。
- 作为平台插件运行时由 `admin-shell` 承载。
- 独立运行配置保留在 `module-wms-core/src/main/resources/wms-standalone.yml`，不会作为依赖污染主应用配置。

`frontend/modules/wms` 已提供主壳插件入口 `index.js`、`menus.js`、`routes.js`，当前主壳只接入非 RFID 菜单。目录中仍保留历史独立 Vite 应用文件和 RFID 页面，后续迁移时不要把 RFID 页面接入主壳菜单。

## 常用命令

后端完整验证：

```powershell
mvn clean verify
```

后端主应用打包：

```powershell
mvn -pl admin-shell -am package
```

后端主应用运行：

```powershell
mvn -pl admin-shell -am spring-boot:run
```

前端主壳构建：

```powershell
cd frontend/web-shell
npm install
npm run build
```

前端插件加载器测试：

```powershell
cd frontend/web-shell
npm run test:plugin-loader
```

WMS 后端验证：

```powershell
mvn -pl modules/module-wms/module-wms-core -am test
mvn -pl modules/module-wms/module-wms-autoconfig -am test
```

WMS 前端验证：

```powershell
cd frontend/modules/wms
npm run test
npm run build
```

## 扩展新插件的推荐方式

新增后端插件：

1. 在 `modules/` 下创建业务模块。
2. 定义 `PluginRegister`。
3. 在 `*-autoconfig` 中注册插件 Bean。
4. 通过配置项控制插件启用。
5. 在 `admin-shell` 中引入对应 autoconfig 依赖。

新增前端插件：

1. 在 `frontend/modules/xxx` 下创建模块。
2. 导出 `index.js`、`menus.js`、`routes.js`。
3. 在 `frontend/web-shell/src/plugin-loader/local-modules.js` 中注册模块 key。
4. 确保后端插件的 `frontendModule` 与前端模块 key 一致。

## 工程约束

- 共享能力放在 `framework/`。
- 业务插件放在 `modules/`。
- 应用组装放在 `admin-shell/`。
- 前端主能力放在 `frontend/web-shell/`。
- 前端子平台放在 `frontend/modules/`。
- 不编辑 `target/`、`dist/` 等生成产物。
- Markdown 文档默认使用中文。
- 敏感配置不要扩散到文档、日志、提交信息或聊天回复中。
