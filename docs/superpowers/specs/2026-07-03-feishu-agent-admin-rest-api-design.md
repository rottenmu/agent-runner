# 飞书 Agent 管理调试 Rest 接口设计

## 背景

`module-feishu` 已完成三层基础能力：

- 凭据层：支持租户飞书应用扫码初始化、凭据查询、校验、刷新和删除。
- Channel SDK 交互层：支持通过 WebSocket 监听飞书机器人消息，并暴露连接生命周期管理能力。
- CLI 执行层：支持通过 Java 封装调用飞书 CLI，向多维表格等对象写入数据。

当前还缺少一个面向前端调试和 Postman 测试的统一管理入口。已有接口分散在 `agent`、`config`、`event` 等 Controller 中，本次新增统一管理调试 Controller，复用已有三层服务能力，补齐连接状态、CLI 写入调试、消息日志分页和机器人启停能力。

## 设计目标

- 新增统一管理调试入口 `/api/biz/feishu/admin/**`。
- 复用现有 `R<T>` 统一返回体。
- 提供配套入参 DTO 和响应 DTO，便于前端和 Postman 调试。
- 提供简单接口权限校验，避免管理调试接口裸奔。
- 不重复实现飞书官方 SDK、Channel、CLI 的底层逻辑。
- 不破坏现有 `FeishuAgentCredentialController`、`FeishuConfigController`、`FeishuEventController`。

## 非目标

- 不开发前端页面。
- 不实现完整 RBAC 权限体系，仅提供本模块管理接口的轻量校验。
- 不新增数据库表。
- 不改变已有凭据、Channel、CLI、网关对外契约。
- 不在接口响应中泄露 AppSecret、EncryptKey 等敏感明文。

## 采用方案

采用方案 A：新增统一管理调试 Controller。

新增 `FeishuAgentAdminController` 作为统一入口，路径为 `/api/biz/feishu/admin`。Controller 只做请求接收、权限校验和统一返回；业务编排放在 `FeishuAgentAdminService` 中；权限校验放在 `FeishuAdminPermissionGuard` 中。

## 接口清单

### 1. 租户飞书应用扫码初始化

```text
POST /api/biz/feishu/admin/tenant-scan/init
```

用途：

- 面向管理端统一入口，复用凭据层扫码初始化能力。
- 入参复用或包裹现有 `FeishuTenantScanInitRequest`。
- 响应复用 `FeishuTenantScanInitResponse`。

处理逻辑：

- 执行管理权限校验。
- 调用 `FeishuAgentCredentialService#initTenantScan`。
- 返回 `R<FeishuTenantScanInitResponse>`。

### 2. 当前租户机器人连接状态查询

```text
GET /api/biz/feishu/admin/channel/status
```

响应 DTO：`FeishuChannelStatusResponse`

字段：

- `configured`：是否存在当前有效应用配置。
- `enabled`：当前应用配置是否启用。
- `running`：Channel WebSocket 是否正在运行。
- `appId`：当前应用 AppID。
- `tenantKey`：租户 key。
- `tenantName`：租户名称。
- `credentialStatus`：凭据状态。
- `message`：状态说明。

处理逻辑：

- 执行管理权限校验。
- 读取 `FeishuConfigService#getActiveConfig` 和当前启用配置摘要。
- 调用 `FeishuChannelClientManager#isRunning`。
- 不返回 AppSecret、VerificationToken、EncryptKey 明文。

### 3. 手动测试飞书 CLI 表格写入

```text
POST /api/biz/feishu/admin/cli/bitable/write-test
```

入参 DTO：`FeishuBitableWriteTestRequest`

字段：

- `appToken`：多维表格 app token。
- `tableId`：数据表 ID。
- `fields`：待写入字段 Map。

响应 DTO：`FeishuCliDebugResponse`

字段：

- `success`：CLI 调用是否成功。
- `exitCode`：CLI 退出码。
- `costMillis`：耗时。
- `attempts`：尝试次数。
- `stdout`：标准输出摘要。
- `stderr`：错误输出摘要。
- `errorMessage`：错误信息。

处理逻辑：

- 执行管理权限校验。
- 校验 `appToken`、`tableId` 非空。
- `fields` 为空时写入默认调试字段，例如 `调试时间`、`来源`。
- 调用 `FeishuBitableCliService#createRecord`。
- 截断 stdout/stderr，避免响应过大。

### 4. 消息日志分页查询

```text
GET /api/biz/feishu/admin/message-logs/page
```

查询参数：

- `current`：页码，默认 1。
- `size`：每页数量，默认 10。
- `tenantKey`：租户 key，可选。
- `senderUserId`：发送人 UserID，可选。
- `stage`：日志阶段，可选。
- `success`：是否成功，可选。
- `commandText`：指令文本模糊查询，可选。

响应：

- `R<Page<FeishuMessageLogEntity>>`

处理逻辑：

- 执行管理权限校验。
- 通过 `FeishuMessageLogService#page` 查询。
- 按 `createTime` 倒序返回。
- 如果没有 Mapper 或日志未启用，返回空分页。

### 5. 机器人启用/停用开关

```text
PUT /api/biz/feishu/admin/robot/enabled
```

入参 DTO：`FeishuRobotSwitchRequest`

字段：

- `enabled`：是否启用。
- `configId`：启用时指定配置 ID，可选。

响应 DTO：`FeishuRobotSwitchResponse`

字段：

- `enabled`：配置是否启用。
- `running`：Channel 是否运行。
- `configId`：当前配置 ID。
- `message`：处理结果说明。

处理逻辑：

- 执行管理权限校验。
- 启用时：
  - 如果传入 `configId`，调用 `FeishuConfigService#enable(configId)`。
  - 调用 `FeishuChannelClientManager#start()`。
- 停用时：
  - 将当前启用配置关闭。
  - 调用 `FeishuChannelClientManager#stop()`。
- 返回配置启用状态和 Channel 运行状态。

## 权限校验设计

新增 `FeishuAdminPermissionGuard`。

校验规则：

- 请求头：`X-Feishu-Admin-Token`。
- 配置项：`feishu.admin.api-token`。
- 当 `feishu.admin.api-token` 有值时，请求头必须完全匹配。
- 当配置为空时，默认允许访问，方便本地开发调试。
- 校验失败由 Controller 统一返回 `R.fail(403, "forbidden")`。

配置类：

```yaml
feishu:
  admin:
    api-token:
```

## 模块结构

新增核心包：

```text
com.zimo.module.feishu.admin
  FeishuAgentAdminController.java
  FeishuAgentAdminService.java
  FeishuAdminPermissionGuard.java
  dto/
    FeishuBitableWriteTestRequest.java
    FeishuChannelStatusResponse.java
    FeishuCliDebugResponse.java
    FeishuMessageLogPageRequest.java
    FeishuRobotSwitchRequest.java
    FeishuRobotSwitchResponse.java
```

修改：

```text
com.zimo.module.feishu.log
  FeishuMessageLogService.java

com.zimo.module.feishu.config
  FeishuConfigService.java
  FeishuConfigServiceImpl.java

com.zimo.module.feishu.autoconfig
  FeishuAutoConfiguration.java
  FeishuAdminProperties.java
```

## 服务职责

### FeishuAgentAdminController

- 统一接收 `/api/biz/feishu/admin/**` 请求。
- 调用 `FeishuAdminPermissionGuard` 做权限校验。
- 调用 `FeishuAgentAdminService`。
- 返回 `R<T>`。

### FeishuAgentAdminService

- 编排凭据、Channel、CLI、日志和配置服务。
- 负责参数校验。
- 负责响应 DTO 转换。
- 负责启停机器人时同步配置状态和 Channel 状态。

### FeishuAdminPermissionGuard

- 只负责轻量管理权限校验。
- 不依赖 `module-sys` 权限上下文，避免管理调试接口和业务权限强耦合。

## 自动装配

新增 `FeishuAdminProperties`：

```java
@ConfigurationProperties(prefix = "feishu.admin")
public class FeishuAdminProperties {
    private boolean enabled = true;
    private String apiToken;
}
```

自动装配规则：

- `feishu.enabled=true` 且 `feishu.admin.enabled=true` 时注册管理接口。
- 存在必要服务 Bean 时注册 `FeishuAgentAdminService`。
- 默认注册 `FeishuAdminPermissionGuard`。
- 默认注册 `FeishuAgentAdminController`。

## 测试策略

核心测试：

- 权限 guard：未配置 token 放行，配置 token 时匹配放行，不匹配拒绝。
- Controller：每个接口调用 service 并返回 `R<T>`。
- Service：
  - 状态查询能返回 configured/running。
  - CLI 写入调试能构造 `BitableRecordCreateRequest`。
  - 日志分页能按条件调用 mapper。
  - 启用机器人时调用 config enable 和 channel start。
  - 停用机器人时关闭配置并调用 channel stop。

自动装配测试：

- `feishu.admin.enabled=true` 注册管理接口相关 Bean。
- `feishu.admin.enabled=false` 不注册管理接口。

验证命令：

```text
mvn -pl modules/module-feishu/module-feishu-core -am test
mvn -pl modules/module-feishu/module-feishu-autoconfig -am test
```

## 风险与处理

- 管理 token 未配置导致接口开放：仅用于本地调试，生产环境需要配置 `feishu.admin.api-token`。
- 停用配置需要更新数据库：复用 `FeishuConfigService`，新增显式 disable 方法，避免 Controller 直接操作 Mapper。
- 日志 Mapper 不存在时分页失败：服务层检测 Mapper 为空并返回空分页。
- CLI 输出过大：响应中对 stdout/stderr/errorMessage 做长度截断。
- Channel start 失败：`FeishuChannelClientManager` 内部已有失败兜底，接口返回 running=false 和说明信息。
