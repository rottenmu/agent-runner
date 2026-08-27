# 工程精简方案：只保留 AI / 飞书 / Agent 功能

> 日期：2026-08-08
> 状态：待审核

---

## 一、目标

前后端只保留 `ai`、`feishu`、`agent` 相关功能，删除其他所有业务代码。

---

## 二、依赖分析结论

4 个待删除模块与保留模块之间 **零编译依赖**：

- `module-sys` / `module-auth` / `module-ai` / `module-feishu` / `ai-agent-spring-boot-starter` / `framework` 均不依赖 `module-wms`、`module-material`、`module-packaging-bom`、`xingju-project-mgmt-starter`
- 唯一跨边界引用是 `FrameworkApiRegistryAutoConfiguration` 中 `@AutoConfiguration(afterName)` 的字符串数组，需同步删除

---

## 三、保留清单

### 后端
| 模块 | 说明 |
|------|------|
| `framework/` | framework-common + framework-autoconfig（共享框架层） |
| `ai-agent-spring-boot-starter` | AI Agent 基础 Starter（Skill/MCP/A2A 协议） |
| `module-sys` | 系统管理（RBAC 权限、API 注册表，feishu 模块依赖 module-sys-core） |
| `module-auth` | 认证登录（Sa-Token） |
| `module-ai` | AI 智能体管理 |
| `module-feishu` | 飞书平台集成 |
| `admin-shell/` | 应用组装启动壳 |

### 前端
| 模块 | 说明 |
|------|------|
| `frontend/web-shell/` | 主壳应用 |
| `frontend/modules/sys/` | 系统管理（用户/角色/菜单/API管理） |
| `frontend/modules/ai/` | AI 智能体 |
| `frontend/modules/feishu/` | 飞书配置 |

### 其他
| 目录 | 说明 |
|------|------|
| `deploy/` | Docker Compose + Dockerfile 部署配置（保留，可能需要微调） |
| `docs/rules/` | 代码规范文档 |

---

## 四、删除清单

### 4.1 整个目录删除（文件系统）

| 目录 | 说明 |
|------|------|
| `modules/module-wms/` | 仓储管理 |
| `modules/module-material/` | 物料管理 |
| `modules/module-packaging-bom/` | 包装 BOM |
| `modules/xingju-project-mgmt-starter/` | 制造项目管理 |
| `frontend/modules/manufacturing-pm/` | 前端 PM 模块 |
| `frontend/modules/wms/` | 前端 WMS 模块 |
| `frontend/modules/material/` | 前端物料模块 |
| `frontend/modules/packaging-bom/` | 前端 BOM 模块 |
| `frontend/modules/demo/` | 前端示例模块 |

### 4.2 文件修改（引用清理）

#### A. `pom.xml`（根）
- 删除 `<dependencyManagement>` 中 8 个待删除模块的 `<dependency>` 声明（第 74-128 行）
- 可选：删除不再需要的 `<properties>`：`apache-poi.version`、`aliyun-oss-v2.version`、`aliyun-credentials.version`、`json-schema-validator.version`（如果只有 packaging-bom 在用）

#### B. `modules/pom.xml`
- 删除 `<modules>` 中 4 行：`xingju-project-mgmt-starter`、`module-wms`、`module-packaging-bom`、`module-material`

#### C. `admin-shell/pom.xml`
- 删除 4 个 `<dependency>`：`xingju-project-mgmt-autoconfig`、`module-wms-autoconfig`、`module-material-autoconfig`、`module-packaging-bom-autoconfig`

#### D. `admin-shell/src/main/resources/application.yml`
- 删除 `framework.api-registry.data-source-bean-name: materialDataSource` 配置行
- 删除 `plugin.manufacturing-pm` 整个配置块
- 删除 `plugin.wms` 配置块
- 删除 `plugin.packaging-bom` 配置块（含 OSS）
- 删除 `plugin.stock/accounts/selling/buying/demo` 开关（如果有）

#### E. `framework/framework-autoconfig/.../FrameworkApiRegistryAutoConfiguration.java`
- 从 `@AutoConfiguration(afterName)` 中删除 3 个字符串：
  - `"com.zimo.material.autoconfig.MaterialAutoConfiguration"`
  - `"com.zimo.module.wms.autoconfig.WmsAutoConfiguration"`
  - `"com.zimo.module.manufacturingpm.autoconfig.ManufacturingPmAutoConfiguration"`

#### F. `frontend/web-shell/vite.config.js`
- 删除 `wmsApiTarget` 和 `manufacturingPmApiTarget` 变量声明
- 删除 10+ 条代理规则（WMS 和 Manufacturing PM 相关）
- 修改 `@` 别名（当前指向 `modules/manufacturing-pm/src`）→ 改为指向通用路径或删除

#### G. `frontend/web-shell/src/plugin-loader/local-modules.js`
- 删除 4 个导入映射：`manufacturing-pm`、`wms`、`material`、`packaging-bom`、`demo`
- 从 `shellDefaultPlugins` 中删除 `manufacturing-pm` 条目

#### H. `admin-shell/src/test/` 测试文件
- 删除 `PackagingBomAdminShellContractTest.java`
- 修改 `AdminApplicationConfigTest.java`（移除 packaging-bom/wms 相关断言）
- 修改 `LoadedModuleLoggerTest.java`（移除 wms 相关断言）

#### I. `docs/packaging-bom/`（可选）
- 整个目录可删除（包装 BOM 的合同/迁移文档）

#### J. `scripts/`（可选）
- `scripts/packaging-bom-contracts/` 和 `scripts/packaging-bom-phase1/` 可删除

---

## 五、执行步骤

| 步骤 | 操作 | 风险 |
|------|------|------|
| 1 | 删除 5 个前端模块目录 | 低，仅文件系统 |
| 2 | 删除 4 个后端模块目录 | 低，仅文件系统 |
| 3 | 修改 `modules/pom.xml` | 低 |
| 4 | 修改根 `pom.xml` | 低 |
| 5 | 修改 `admin-shell/pom.xml` | 低 |
| 6 | 修改 `admin-shell/application.yml` | 需谨慎 |
| 7 | 修改 `FrameworkApiRegistryAutoConfiguration.java` | 低 |
| 8 | 修改前端 `vite.config.js` + `local-modules.js` | 低 |
| 9 | 清理 `admin-shell/src/test/` | 低 |
| 10 | 运行 `mvn clean compile` 验证 | 必须通过 |
| 11 | 运行前端 `npm run build` 验证 | 必须通过 |

---

## 六、影响评估

- **编译安全**：保留模块不依赖删除模块，编译不会因删除而失败
- **运行时安全**：重启后仅加载 ai/feishu/sys/auth 模块，不再初始化 wms/material/bom/pm
- **数据库**：删除模块的表结构将不再被启动脚本初始化，但已存在的表数据不受影响
- **API**：`GET /api/plugins` 返回列表将减少至 4 个插件（sys / ai / feishu，auth 不注册插件接口本身）

---

## 七、审核确认

请审核以上方案，确认后我将按步骤执行。如有需要调整的保留/删除项，请说明。
