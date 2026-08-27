# 飞书配置管理页面 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `module-feishu` 中新增飞书配置的后端 CRUD、单条启用规则、脱敏查询和前端管理页面。

**Architecture:** 后端在 `module-feishu-core` 新增 `config` 领域包，使用 MyBatis-Plus 的 Entity、Mapper、Service、Controller 分层；`module-feishu-autoconfig` 提供自动装配，表结构由模块启动初始化器检查创建，运行时配置由 Provider 提供。前端把 `frontend/modules/feishu` 从空模块扩展为菜单、路由和 Element Plus 表格弹窗页面。

**Tech Stack:** Java 17、Spring Boot 3.4.5、MyBatis-Plus、启动时表结构初始化、Vue 3、Vite、Element Plus、Node test、JUnit 5。

## Global Constraints

- 新增 `.md` 文档默认使用中文。
- 不在列表、详情、日志、提交信息或聊天回复中扩散真实飞书密钥。
- 查询列表和详情时对 `appSecret`、`verificationToken`、`encryptKey` 脱敏。
- 编辑时密钥字段为空表示保留原值。
- 同一时间最多一条飞书配置启用。
- 飞书运行时配置优先读取数据库启用配置，没有启用配置时回退 `application.yml`。
- 前端必须提供查询、新增、编辑、删除、启用操作。

---

### Task 1: 后端配置领域模型与服务规则

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigEntity.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigMapper.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigResponse.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuRuntimeConfig.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigProvider.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigService.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigServiceImpl.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuConfigServiceTest.java`

**Interfaces:**
- Produces: `FeishuConfigService#create(FeishuConfigRequest)`
- Produces: `FeishuConfigService#update(Long, FeishuConfigRequest)`
- Produces: `FeishuConfigService#delete(Long)`
- Produces: `FeishuConfigService#enable(Long)`
- Produces: `FeishuConfigService#page(long, long, String, String, Integer)`
- Produces: `FeishuConfigProvider#getActiveConfig()`

- [ ] **Step 1: Write failing tests**

Cover create, update with blank secrets preserving old values, enable disabling other rows, soft delete, and response masking.

- [ ] **Step 2: Run tests to verify failure**

Run: `mvn -pl modules/module-feishu/module-feishu-core -am test`
Expected: FAIL because `config` classes do not exist.

- [ ] **Step 3: Implement minimal backend service**

Implement entity, request/response DTOs, service methods, masking, and active config provider.

- [ ] **Step 4: Run tests to verify pass**

Run: `mvn -pl modules/module-feishu/module-feishu-core -am test`
Expected: PASS.

### Task 2: 后端 Controller、自动配置和表结构初始化

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigController.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigSchemaInitializer.java`
- Modify: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAutoConfiguration.java`
- Modify: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuProperties.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuConfigControllerTest.java`
- Test: `modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAutoConfigurationTest.java`

**Interfaces:**
- Consumes: `FeishuConfigService`
- Produces: `/api/feishu/config/**`
- Produces: `ps_feishu_config`
- Produces: auto-configured `FeishuConfigProvider`

- [ ] **Step 1: Write failing Controller and auto-configuration tests**

Verify page/create/update/delete/enable endpoints and provider Bean registration.

- [ ] **Step 2: Run tests to verify failure**

Run: `mvn -pl modules/module-feishu/module-feishu-autoconfig -am test`
Expected: FAIL because Controller and auto-config beans are missing.

- [ ] **Step 3: Implement Controller, schema initialization and auto-configuration**

Register Mapper scan for `com.zimo.module.feishu.config`, service beans, and fallback runtime config.

- [ ] **Step 4: Run tests to verify pass**

Run: `mvn -pl modules/module-feishu/module-feishu-autoconfig -am test`
Expected: PASS.

### Task 3: 前端飞书配置模块

**Files:**
- Modify: `frontend/modules/feishu/index.js`
- Create: `frontend/modules/feishu/menus.js`
- Create: `frontend/modules/feishu/routes.js`
- Create: `frontend/modules/feishu/views/FeishuConfigManage.vue`
- Modify: `frontend/web-shell/src/plugin-loader/plugin-loader.test.mjs`

**Interfaces:**
- Produces: menu `/integration/feishu/config`
- Produces: route `FeishuConfigManage`
- Consumes APIs under `/feishu/config`

- [ ] **Step 1: Write failing frontend tests**

Extend plugin loader/source tests to verify feishu menu, route, CRUD API calls, and “留空则保持原值” copy.

- [ ] **Step 2: Run tests to verify failure**

Run: `npm run test:plugin-loader` in `frontend/web-shell`
Expected: FAIL because feishu menus/routes/page do not exist.

- [ ] **Step 3: Implement frontend module**

Add menus, routes, and Element Plus table/dialog page.

- [ ] **Step 4: Run tests to verify pass**

Run: `npm run test:plugin-loader` in `frontend/web-shell`
Expected: PASS.

### Task 4: Final verification

**Files:**
- No new files.

**Interfaces:**
- Verifies all prior deliverables.

- [ ] **Step 1: Run backend focused tests**

Run:

```powershell
mvn -pl modules/module-feishu/module-feishu-core -am test
mvn -pl modules/module-feishu/module-feishu-autoconfig -am test
```

- [ ] **Step 2: Run main app package**

Run: `mvn -pl admin-shell -am package`

- [ ] **Step 3: Run frontend verification**

Run in `frontend/web-shell`:

```powershell
npm run test:plugin-loader
npm run build
```
