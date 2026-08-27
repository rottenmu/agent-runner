# 飞书 Agent 凭据层模块 Starter 化设计

## 背景

当前 `module-feishu` 已具备飞书配置管理、消息发送、事件回调和插件注册能力，但飞书 Agent 场景还缺少“扫码创建应用、自动开通权限、自动订阅事件、凭据校验、凭据刷新”的统一凭据层。

本设计采用“方案 A：在 `module-feishu` 内新增 Agent Credential 子域”，保持现有 `module-feishu-core + module-feishu-autoconfig` 结构不变，以 Spring Boot Starter 风格扩展模块能力。

## 设计目标

- 封装飞书官方一键创建应用 Web 扫码 SDK 调用入口。
- 支持扫码创建应用后，自动批量开通 IM、表格、文档租户权限。
- 支持自动订阅消息事件，并预留回调事件扩展。
- 基于现有飞书凭据表实现 AppID、AppSecret 的新增、查询、删除、校验和密钥刷新。
- 对外提供租户扫码初始化、凭据查询、凭据删除等 REST 接口。
- 增加凭据合法性校验逻辑，避免无效飞书连接继续进入业务调用链。
- 保持模块边界清晰，符合 Spring Boot Starter 自动装配模式。

## 模块结构

```text
modules/module-feishu/
  module-feishu-core/
    src/main/java/com/xingju/module/feishu/
      agent/
        FeishuAgentCredentialController.java
        FeishuAgentCredentialService.java
        FeishuAgentCredentialServiceImpl.java
        FeishuAgentCredentialValidator.java
        FeishuAgentCredentialInterceptor.java
        FeishuAppCreationClient.java
        OfficialFeishuAppCreationClient.java
        dto/
          FeishuTenantScanInitRequest.java
          FeishuTenantScanInitResponse.java
          FeishuTenantCredentialResponse.java
          FeishuCredentialRefreshRequest.java
          FeishuCredentialValidateResponse.java

      config/
        FeishuConfigEntity.java
        FeishuConfigMapper.java
        FeishuConfigService.java

  module-feishu-autoconfig/
    src/main/java/com/xingju/module/feishu/autoconfig/
      FeishuAutoConfiguration.java
      FeishuAgentCredentialProperties.java
      FeishuAgentCredentialWebConfig.java
```

## 核心组件

### FeishuAppCreationClient

`FeishuAppCreationClient` 是飞书官方一键创建应用 SDK 的隔离适配层，负责屏蔽 SDK 版本差异。

主要职责：

- 构造 Web 扫码创建应用请求。
- 传入应用名称、描述、回调地址、事件订阅地址。
- 传入需要开通的租户权限范围。
- 返回扫码 URL、扫码状态标识、过期时间和临时票据。

实现类 `OfficialFeishuAppCreationClient` 负责调用飞书官方 SDK。若当前 `oapi-sdk` 版本缺少对应类，只需要调整该适配器或升级 SDK，不影响业务 Service。

### FeishuAgentCredentialService

`FeishuAgentCredentialService` 是凭据层业务编排服务。

主要职责：

- 发起租户扫码初始化。
- 接收或转换扫码创建应用后的 AppID、AppSecret。
- 保存或更新飞书租户凭据。
- 查询凭据列表和凭据详情。
- 删除凭据。
- 校验凭据合法性。
- 刷新密钥。
- 创建成功后自动启用有效凭据，并禁用其他启用凭据。

### FeishuAgentCredentialValidator

`FeishuAgentCredentialValidator` 负责凭据合法性校验。

校验规则：

- AppID 不能为空。
- AppSecret 不能为空。
- 启用状态必须有效。
- 可通过飞书 token 获取接口做远端轻量校验。
- 校验失败时返回明确的错误原因。

### FeishuAgentCredentialInterceptor

`FeishuAgentCredentialInterceptor` 负责请求进入飞书 Agent 能力前的连接校验。

拦截范围默认为：

```text
/api/biz/feishu/agent/**
```

放行范围默认为：

```text
/api/biz/feishu/agent/tenant-scan/init
```

这样扫码初始化可以在没有有效凭据时访问，其他 Agent 能力必须先通过凭据校验。

## REST 接口设计

### 租户扫码初始化

```text
POST /api/biz/feishu/agent/tenant-scan/init
```

请求 DTO：`FeishuTenantScanInitRequest`

字段：

- `tenantName`：租户名称。
- `appName`：飞书应用名称。
- `appDescription`：飞书应用描述。
- `redirectUri`：扫码完成后的回跳地址。
- `eventCallbackUrl`：事件回调地址。
- `permissionScopes`：需要开通的权限列表。
- `eventSubscriptions`：需要订阅的事件列表。

响应 DTO：`FeishuTenantScanInitResponse`

字段：

- `scanUrl`：扫码 URL。
- `scanTicket`：扫码流程标识。
- `expireSeconds`：过期秒数。
- `permissionScopes`：本次申请权限。
- `eventSubscriptions`：本次订阅事件。

### 查询凭据列表

```text
GET /api/biz/feishu/agent/credentials
```

响应 DTO：`FeishuTenantCredentialResponse`

敏感字段规则：

- `appSecret` 不明文返回。
- `verificationToken` 不明文返回。
- `encryptKey` 不明文返回。

### 查询凭据详情

```text
GET /api/biz/feishu/agent/credentials/{id}
```

### 删除凭据

```text
DELETE /api/biz/feishu/agent/credentials/{id}
```

### 校验凭据

```text
POST /api/biz/feishu/agent/credentials/{id}/validate
```

响应 DTO：`FeishuCredentialValidateResponse`

字段：

- `valid`：是否有效。
- `message`：校验结果说明。
- `validateTime`：校验时间。

### 刷新密钥

```text
POST /api/biz/feishu/agent/credentials/{id}/refresh-secret
```

请求 DTO：`FeishuCredentialRefreshRequest`

字段：

- `appSecret`：新密钥。
- `verificationToken`：可选，新事件校验 Token。
- `encryptKey`：可选，新事件加密 Key。

## 数据模型

优先复用现有 `ps_feishu_config` 表，避免凭据分散。

现有字段继续使用：

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

建议新增字段：

- `tenant_key`：飞书租户标识。
- `tenant_name`：租户名称。
- `credential_status`：凭据状态，例如 `PENDING`、`VALID`、`INVALID`。
- `last_validate_time`：最后校验时间。
- `last_refresh_time`：最后密钥刷新时间。
- `scan_state`：扫码流程状态。
- `scan_ticket`：扫码流程标识。
- `permission_scopes`：已申请权限，JSON 字符串。
- `event_subscriptions`：已订阅事件，JSON 字符串。

## 自动装配设计

在 `module-feishu-autoconfig` 中扩展自动装配：

- 注册 `FeishuAppCreationClient`。
- 注册 `FeishuAgentCredentialService`。
- 注册 `FeishuAgentCredentialValidator`。
- 注册 `FeishuAgentCredentialController`。
- 注册 `FeishuAgentCredentialInterceptor`。
- 注册 `FeishuAgentCredentialWebConfig`。

启停配置：

```yaml
feishu:
  agent:
    credential:
      enabled: true
      validate-before-agent-call: true
      scan-expire-seconds: 600
      default-permission-scopes:
        - im:message
        - sheets:spreadsheet
        - docs:document
      default-event-subscriptions:
        - im.message.receive_v1
```

## 错误处理

- 请求参数缺失返回业务错误。
- 飞书 SDK 调用失败返回明确失败原因。
- 凭据不存在返回业务错误。
- 凭据无效时拦截器直接阻断请求。
- 删除凭据使用逻辑删除，避免历史配置丢失。
- 所有对外接口统一返回 `R<T>`。

## 测试策略

- Controller 测试：验证接口路径、入参、返回结构。
- Service 测试：验证扫码初始化、凭据保存、删除、校验、刷新流程。
- Validator 测试：验证空凭据、无效凭据、有效凭据判断。
- Interceptor 测试：验证无有效凭据时阻断 Agent 请求，扫码初始化接口放行。
- AutoConfiguration 测试：验证配置开启时 Bean 自动注册，关闭时不注册。

## 实施边界

本阶段实现完整工具类、Service、Controller、DTO、拦截器和自动装配代码。

若当前飞书 `oapi-sdk` 版本不包含一键创建应用所需 SDK 类，先通过 `FeishuAppCreationClient` 保持业务层稳定，再在适配器内升级或替换官方调用实现。

## 非目标

- 本阶段不实现飞书前端页面。
- 本阶段不实现完整飞书事件业务处理，只完成事件订阅配置和接收入口兼容。
- 本阶段不拆分新的 `module-feishu-agent` 独立插件。
