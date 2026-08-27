# 飞书配置管理页面设计

## 背景

`module-feishu` 已提供飞书 Client 初始化、文本消息发送和事件订阅 Webhook。本次扩展是在飞书模块内增加配置管理能力，让平台用户可以在主壳中维护飞书自建应用配置，包括新增、编辑、删除和查询。

飞书配置包含敏感信息，必须避免在列表、详情、日志和聊天回复中扩散真实密钥。页面允许录入和更新密钥，但查询结果只返回脱敏状态。

## 目标

- 在 `modules/module-feishu` 后端模块内新增飞书配置 CRUD。
- 新增数据库表保存多套飞书应用配置。
- 支持单条配置启用，启用一条时自动停用其他配置。
- 查询列表和详情时对 `appSecret`、`verificationToken`、`encryptKey` 脱敏。
- 编辑时密钥字段留空表示保留原值。
- 现有飞书 Client 和 Webhook 配置优先读取数据库启用配置，数据库没有启用配置时回退到 `application.yml`。
- 在 `frontend/modules/feishu` 新增“飞书配置”页面，提供查询、新增、编辑、删除和启用操作。

## 非目标

- 不实现密钥加密存储；本次先控制 API 回显和前端展示，后续可接入统一密钥管理或加密字段。
- 不实现飞书 OAuth、通讯录同步、审批、文档等其他开放平台能力。
- 不新增复杂权限体系；先沿用主壳插件菜单和后端接口的基础接入方式。
- 不在页面中展示真实密钥。

## 后端设计

### 表结构

新增模块启动时表结构初始化：

```text
module-feishu 启动时检查并创建配置表
```

表名：`ps_feishu_config`

核心字段：

- `id`
- `config_name`
- `app_id`
- `app_secret`
- `verification_token`
- `encrypt_key`
- `enabled`
- `remark`
- `deleted`
- `create_time`
- `update_time`

`enabled` 使用 `0/1`，服务层保证同一时间最多一条记录启用。`deleted` 使用 MyBatis-Plus 逻辑删除。

### 后端分层

```text
com.zimo.module.feishu.config
├─ FeishuConfigEntity
├─ FeishuConfigMapper
├─ FeishuConfigService
├─ FeishuConfigServiceImpl
├─ FeishuConfigController
├─ FeishuConfigRequest
└─ FeishuConfigResponse
```

Controller 路径：

```text
GET    /api/feishu/config/page
GET    /api/feishu/config/{id}
POST   /api/feishu/config
PUT    /api/feishu/config/{id}
DELETE /api/feishu/config/{id}
PUT    /api/feishu/config/{id}/enable
```

响应统一使用 `R`。

### 敏感信息策略

- 新增时必填 `appSecret`、`verificationToken`、`encryptKey`。
- 编辑时这三个字段如果为空字符串或 `null`，保留数据库原值。
- 查询列表和详情统一返回 `******` 或空值标记，不返回真实密钥。
- 服务内部读取启用配置时可以使用实体真实值。

### Client 配置来源

新增 `FeishuConfigProvider`：

```java
FeishuRuntimeConfig getActiveConfig();
```

默认实现优先查询数据库启用配置。没有启用配置时，回退到 `FeishuProperties` 中的 `application.yml` 配置。

`FeishuAutoConfiguration` 中 `Client`、事件 token、SDK 发送服务使用该 Provider 获取运行时配置。这样页面启用配置后，后续创建 Bean 或运行时读取时有统一入口。

本次不强制实现热重建飞书 SDK Client；如果配置变更后需要立即生效，可以通过后续任务加入动态 Client 工厂。本次验收重点是配置 CRUD 和运行时配置来源边界。

## 前端设计

`frontend/modules/feishu` 从空模块扩展为菜单和路由模块：

```text
frontend/modules/feishu
├─ index.js
├─ menus.js
├─ routes.js
└─ views/FeishuConfigManage.vue
```

菜单：

```text
飞书平台
└─ 飞书配置
```

页面布局遵循现有系统管理页面风格：

- 顶部查询区：配置名称、App ID、启用状态。
- 表格列：ID、配置名称、App ID、启用状态、备注、创建时间、更新时间、操作。
- 操作：新增、编辑、删除、启用。
- 弹窗表单：配置名称、App ID、App Secret、Verification Token、Encrypt Key、备注、启用状态。
- 编辑弹窗中密钥字段提示“留空则保持原值”。

前端不保存真实密钥，不在表格中展示真实密钥。

## 测试策略

后端 TDD：

- `FeishuConfigServiceTest`：验证新增、编辑留空保留密钥、删除、启用互斥、查询脱敏。
- `FeishuConfigControllerTest`：验证分页、新增、编辑、删除、启用接口。
- `FeishuAutoConfigurationTest`：验证 `FeishuConfigProvider` Bean 存在，并可回退到配置文件值。

前端测试：

- 扩展 `plugin-loader.test.mjs`，验证 `feishu` 模块注册菜单和路由。
- 使用源码断言验证页面包含查询、新增、编辑、删除、启用、密钥留空提示和正确 API 调用。

验证命令：

```powershell
mvn -pl modules/module-feishu/module-feishu-core -am test
mvn -pl modules/module-feishu/module-feishu-autoconfig -am test
mvn -pl admin-shell -am package
cd frontend/web-shell && npm run test:plugin-loader
cd frontend/web-shell && npm run build
```

## 验收标准

- 后端提供飞书配置新增、编辑、删除、查询和启用接口。
- 同一时间最多一条飞书配置启用。
- 查询接口不返回真实密钥。
- 编辑时密钥字段留空保留原值。
- 飞书运行时配置优先读取数据库启用配置，未配置时回退 `application.yml`。
- 前端主壳出现“飞书平台 / 飞书配置”菜单。
- 飞书配置页面可完成查询、新增、编辑、删除和启用操作。
- 相关后端、前端测试和主应用打包通过。
