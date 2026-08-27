# 飞书平台接入模块设计

## 背景

`production-studio` 当前采用 Java 17、Spring Boot 3.4.5 和 Maven 多模块组织，业务能力通过 `modules/` 下的插件模块接入 `admin-shell`。本次新增飞书平台接入能力，用于初始化飞书开放平台 Client、发送机器人文本消息，并接收飞书事件订阅中的机器人被 @ 消息事件。

模块目录采用仓库现有命名风格：`modules/module-feishu`。该模块只提供后端能力，不新增前端插件页面。

## 目标

- 新增 `module-feishu` Maven 聚合模块。
- 使用飞书官方 Java SDK `com.larksuite.oapi:oapi-sdk:2.7.3`。
- 初始化 `com.lark.oapi.Client`，注册为 Spring Bean。
- 使用飞书官方 BaseUrl，并开启 SDK debug 日志。
- 提供文本消息发送服务，支持 `user_id`、`open_id`、`chat_id` 三种接收人类型。
- 提供飞书事件订阅 Webhook，接收并识别机器人被 @ 的消息事件。
- 使用 `application.yml` 管理飞书配置。
- 代码保持可运行、可测试、可扩展，遵循仓库现有插件边界。

## 非目标

- 不在本次实现前端管理页面。
- 不在本次实现飞书 OAuth 登录、通讯录同步、审批、文档等能力。
- 不在本次真实调用飞书服务做集成测试，单元测试通过接口适配层验证请求组装和分发行为。
- 不在聊天回复、文档或提交信息中写入真实飞书密钥。

## 模块结构

```text
modules/module-feishu
├─ pom.xml
├─ module-feishu-core
│  ├─ pom.xml
│  └─ src/main/java/com/xingju/module/feishu
│     ├─ event
│     ├─ message
│     └─ FeishuPluginRegister.java
└─ module-feishu-autoconfig
   ├─ pom.xml
   └─ src/main/java/com/xingju/module/feishu/autoconfig
```

`module-feishu-core` 负责业务契约、消息发送服务、事件订阅 Controller 和插件身份声明。`module-feishu-autoconfig` 负责配置属性绑定、飞书 Client Bean、SDK 适配器 Bean 和插件开关。

## Maven 接入

根工程的依赖管理增加：

```xml
<dependency>
    <groupId>com.zimo</groupId>
    <artifactId>module-feishu-core</artifactId>
    <version>${project.version}</version>
</dependency>
<dependency>
    <groupId>com.zimo</groupId>
    <artifactId>module-feishu-autoconfig</artifactId>
    <version>${project.version}</version>
</dependency>
```

`modules/pom.xml` 增加 `module-feishu`。`admin-shell` 增加 `module-feishu-autoconfig` 依赖，让主应用自动装配飞书能力。

## 配置设计

主应用 `application.yml` 增加以下配置项：

```yaml
feishu:
  app-id: ${FEISHU_APP_ID:}
  app-secret: ${FEISHU_APP_SECRET:}
  verification-token: ${FEISHU_VERIFICATION_TOKEN:}
  encrypt-key: ${FEISHU_ENCRYPT_KEY:}
  enabled: true
```

配置类为 `FeishuProperties`，绑定前缀 `feishu`。`app-id`、`app-secret`、`verification-token`、`encrypt-key` 均作为必备字段纳入配置模型；真实值通过环境变量注入，避免进入仓库。

## Client 初始化

`module-feishu-autoconfig` 注册 `com.lark.oapi.Client` Bean：

- 使用 `app-id` 和 `app-secret` 初始化。
- BaseUrl 使用飞书开放平台地址：`https://open.feishu.cn`。
- 开启 SDK debug 日志。
- 通过 `@ConditionalOnProperty(prefix = "feishu", name = "enabled", havingValue = "true", matchIfMissing = true)` 控制启用。

为了便于测试和后续扩展，核心服务不直接散落调用 SDK，而是通过 `FeishuMessageClient` 适配接口发送消息。默认实现使用官方 SDK。

## 消息发送服务

对外服务类为 `FeishuMessageService`，核心方法：

```java
FeishuMessageResponse sendTextMessage(FeishuReceiveIdType receiveIdType, String receiveId, String text);
```

`FeishuReceiveIdType` 支持：

- `USER_ID`
- `OPEN_ID`
- `CHAT_ID`

服务职责：

- 校验接收人类型、接收人 ID 和文本内容。
- 将枚举转换为飞书 API 需要的 `receive_id_type`。
- 构造文本消息内容。
- 调用 `FeishuMessageClient`。
- 返回内部统一响应对象 `FeishuMessageResponse`，包含成功状态、消息 ID、飞书响应码和错误信息。

## 事件订阅 Webhook

Webhook 路径：

```text
POST /api/feishu/events
```

支持两类请求：

1. URL verification：
   - 请求体包含 `type: url_verification`、`challenge`、`token`。
   - 校验 token 等于 `feishu.verification-token`。
   - 返回 `{ "challenge": "..." }`。

2. 事件回调：
   - 校验请求体中的 token。
   - 识别 `event_type` 为机器人被 @ 的消息事件。
   - 将事件转换为内部 `FeishuBotMentionEvent`。
   - 调用 `FeishuEventHandler` 处理。
   - 返回飞书要求的成功响应。

`encrypt-key` 本次作为必备配置保留在模型中，事件解密能力预留在 `FeishuEventParser` 边界内；当前实现优先支持未加密事件体。

## 插件身份

新增 `FeishuPluginRegister`：

```text
pluginId: feishu
pluginName: 飞书平台
apiPrefix: /api/feishu
frontendRoute: /integration/feishu
frontendModule: feishu
agentName: feishu-agent
order: 6
```

虽然本次不新增前端模块，插件元数据仍便于平台发现和后续扩展。

## 错误处理

- 配置缺失时，飞书 Client 创建失败应给出明确异常信息。
- 消息发送入参为空时抛出 `IllegalArgumentException`。
- Webhook token 校验失败返回 403。
- 不识别的事件类型返回成功空处理，避免飞书重复重试无关事件。
- SDK 调用异常由适配器捕获并转换为 `FeishuMessageResponse` 中的失败状态。

## 测试策略

使用 TDD 分层验证：

- `FeishuPropertiesTest`：验证配置绑定包含四个必备字段。
- `FeishuAutoConfigurationTest`：验证启用配置下能创建 `Client`、消息服务和插件注册 Bean。
- `FeishuMessageServiceTest`：验证 `user_id`、`open_id`、`chat_id` 三种发送类型的请求组装。
- `FeishuEventControllerTest`：验证 challenge 响应、token 校验失败、机器人被 @ 事件分发。
- `FeishuPluginRegisterTest`：验证插件元数据稳定。

验证命令优先使用窄范围：

```powershell
mvn -pl modules/module-feishu/module-feishu-core -am test
mvn -pl modules/module-feishu/module-feishu-autoconfig -am test
mvn -pl admin-shell -am package
```

## 验收标准

- `module-feishu` 被纳入 Maven 聚合。
- `admin-shell` 能通过自动配置加载飞书模块。
- `application.yml` 包含 `feishu.app-id`、`feishu.app-secret`、`feishu.verification-token`、`feishu.encrypt-key`。
- `com.lark.oapi.Client` 被注册为 Spring Bean，使用飞书 BaseUrl 并开启 debug。
- 文本消息发送服务支持 `user_id`、`open_id`、`chat_id`。
- `/api/feishu/events` 支持 URL verification 和机器人被 @ 消息事件分发。
- 新增测试通过，且不引入真实密钥。
