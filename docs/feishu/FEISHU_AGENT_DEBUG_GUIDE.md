# 飞书 Agent 本地调试与交付指南

本文面向接入 `module-feishu` 的后端开发人员，覆盖凭据层、Channel SDK 交互层、CLI 执行层和统一管理接口的本地验证流程。

## 1. 单元测试 Demo

在项目根目录执行以下命令。

### 凭证创建测试

验证扫码初始化后是否创建启用状态的飞书应用凭据，并返回扫码地址。

```powershell
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentCredentialServiceTest test
```

重点测试类：

- `com.zimo.module.feishu.agent.FeishuAgentCredentialServiceTest`
- `com.zimo.module.feishu.agent.FeishuAgentCredentialControllerTest`
- `com.zimo.module.feishu.config.FeishuConfigServiceTest`

核心覆盖点：

- `FeishuAgentCredentialService#initTenantScan`
- `FeishuAppCreationClient#initScan`
- `FeishuConfigService#create`
- `POST /api/biz/feishu/agent/tenant-scan/init`
- `POST /api/biz/feishu/admin/tenant-scan/init`

### Channel 消息模拟测试

验证飞书 `im.message.receive_v1` 事件能被解析成内部指令消息，并能走调度网关链路。

```powershell
mvn -pl modules/module-feishu/module-feishu-core "-Dtest=FeishuChannelMessageParserTest,FeishuChannelMessageListenerTest,FeishuAgentDispatchServiceTest,FeishuAgentGatewayMessageHandlerTest" test
```

重点测试类：

- `com.zimo.module.feishu.channel.FeishuChannelMessageParserTest`
- `com.zimo.module.feishu.channel.FeishuChannelMessageListenerTest`
- `com.zimo.module.feishu.gateway.FeishuAgentDispatchServiceTest`
- `com.zimo.module.feishu.gateway.FeishuAgentGatewayMessageHandlerTest`

核心覆盖点：

- 只处理文本消息。
- 群聊里必须 `@机器人` 才会解析。
- 提取 `messageId`、`chatId`、`tenantKey`、发送人 `userId/openId/unionId`、指令文本。
- 调度网关支持限流、业务路由、多维表格归档、卡片回复。

### CLI 表格写入测试

验证 Java 层封装能构造飞书 CLI 调用请求，并覆盖多维表格、文档、日历、任务四类业务对象。

```powershell
mvn -pl modules/module-feishu/module-feishu-core "-Dtest=FeishuCliUsageDemoTest,FeishuCliTemplateTest,FeishuCliBusinessServiceTest" test
```

重点测试类：

- `com.zimo.module.feishu.cli.FeishuCliUsageDemoTest`
- `com.zimo.module.feishu.cli.FeishuCliTemplateTest`
- `com.zimo.module.feishu.cli.FeishuCliBusinessServiceTest`
- `com.zimo.module.feishu.admin.FeishuAgentAdminServiceTest`

管理接口调试入口：

```text
POST /api/biz/feishu/admin/cli/bitable/write-test
Header: X-Feishu-Admin-Token: <feishu.admin.api-token>
```

请求体示例：

```json
{
  "appToken": "base_xxx",
  "tableId": "tbl_xxx",
  "fields": {
    "名称": "本地调试记录",
    "来源": "production-studio"
  }
}
```

## 2. 本地开发调试步骤

### 2.1 基础配置

在本地 Spring 配置文件中准备以下配置。敏感值只放本地环境或部署环境，不提交到仓库。

```yaml
feishu:
  enabled: true
  app-id: cli_xxx
  app-secret: ${FEISHU_APP_SECRET}
  verification-token: ${FEISHU_VERIFICATION_TOKEN}
  encrypt-key: ${FEISHU_ENCRYPT_KEY}

  admin:
    enabled: true
    api-token: local-admin-token

  agent:
    credential:
      enabled: true
      validate-before-agent-call: true
    channel:
      enabled: true
      auto-start: true
      auto-reconnect: true
      await-ready-timeout-seconds: 10
      stream:
        chunk-size: 80
        interval-millis: 300
      log:
        enabled: true
        record-raw-payload: true
        record-reply-payload: true
    gateway:
      enabled: true
      rate-limit-enabled: true
      rate-limit-window-seconds: 60
      rate-limit-max-requests: 10
      archive-enabled: true
      progress-reply-enabled: true
      archive-app-token: base_xxx
      archive-table-id: tbl_xxx

  cli:
    enabled: true
    mode: NPX
    command: npx
    package-name: "@larksuite/cli@latest"
    timeout-seconds: 30
    retry-times: 1
    log-enabled: true
```

启动后先检查管理状态：

```powershell
curl -H "X-Feishu-Admin-Token: local-admin-token" http://localhost:9900/api/biz/feishu/admin/channel/status
```

### 2.2 WebSocket 本地调试

本模块使用飞书 Channel SDK 的 WebSocket 长连接能力，本地调试不需要公网回调域名。

调试流程：

1. 启动后端应用，确认日志中出现飞书 Channel 初始化信息。
2. 调用 `GET /api/biz/feishu/admin/channel/status`，确认：
   - `configured=true`
   - `enabled=true`
   - `running=true`
3. 若 `running=false`，先检查当前启用飞书应用凭据是否存在：
   - `GET /api/biz/feishu/config/page`
   - `PUT /api/biz/feishu/config/{id}/enable`
4. 需要手动启停机器人时调用：

```powershell
curl -X PUT http://localhost:9900/api/biz/feishu/admin/robot/enabled `
  -H "Content-Type: application/json" `
  -H "X-Feishu-Admin-Token: local-admin-token" `
  -d "{\"enabled\":true,\"configId\":1}"
```

### 2.3 飞书开放平台权限开通清单

在飞书开放平台确认应用至少开通以下能力。

IM 与机器人：

- 启用机器人能力。
- 允许机器人进入群聊。
- 订阅事件：`im.message.receive_v1`。
- 权限：读取用户发送给机器人的消息、发送消息、发送卡片消息。

多维表格：

- 读取多维表格。
- 写入多维表格记录。
- 更新多维表格记录或单元格。
- 确认机器人或应用已被目标多维表格授权。

文档：

- 新建文档。
- 追加文档内容。
- 获取文档链接。

日历与任务：

- 创建日程。
- 添加参会人。
- 新建任务。
- 指派任务负责人。

凭据与租户：

- App ID、App Secret 有效。
- Encrypt Key、Verification Token 与当前应用一致。
- 当前启用配置表中只有一个 `enabled=1` 的应用配置。

### 2.4 机器人添加群聊测试流程

1. 在飞书开放平台发布或启用机器人应用。
2. 将机器人添加到测试群。
3. 在群里发送：

```text
@机器人 查询项目风险
```

4. 预期表现：
   - 后端收到 `im.message.receive_v1`。
   - `FeishuChannelMessageParser` 提取指令文本。
   - `FeishuAgentDispatchService` 匹配业务路由。
   - 业务结果写入多维表格归档。
   - 群里收到文本或交互式卡片回复。

5. 若要查看处理日志：

```powershell
curl -H "X-Feishu-Admin-Token: local-admin-token" "http://localhost:9900/api/biz/feishu/admin/message-logs/page?pageNo=1&pageSize=10"
```

## 3. 部署注意事项

### Node 与飞书 CLI

生产环境需要安装 Node.js 和 npm，确保应用进程能执行 `npx`。

```bash
node -v
npm -v
npx @larksuite/cli@latest --help
```

建议：

- Node.js 使用 LTS 版本。
- 服务器首次运行 `npx @larksuite/cli@latest` 需要访问 npm registry。
- 如生产环境禁止动态拉包，将 `feishu.cli.mode` 改为 `WRAPPER`，并配置 `feishu.cli.wrapper-path` 指向已审核的本地脚本。
- 将 CLI 工作目录配置到应用可写目录，避免无权限写缓存。

### 网络出口权限

服务器需要能访问：

- 飞书开放平台 OpenAPI 域名。
- 飞书 Channel SDK WebSocket 服务。
- npm registry 或企业内部 npm 镜像。
- 业务系统内部接口。
- 多维表格、文档、日历、任务相关 API。

如果走代理，需要保证 Java 进程和 Node/npm 进程都能读取代理配置。

### 消息限流配置

默认限流配置：

```yaml
feishu:
  agent:
    gateway:
      rate-limit-enabled: true
      rate-limit-window-seconds: 60
      rate-limit-max-requests: 10
```

建议：

- 本地调试可临时调大 `rate-limit-max-requests`。
- 生产环境不要直接关闭限流，避免群聊刷屏或触发飞书接口限流。
- 高并发群聊建议按租户、群、用户维度增加外部限流或 Redis 限流。

### 敏感配置

- 不要把 App Secret、Encrypt Key、Verification Token 写入仓库。
- 不要在日志中打印明文密钥。
- `feishu.admin.api-token` 生产环境必须配置。
- 管理接口建议只对内网或 VPN 开放。

## 4. 常见报错排查清单

### Channel 连接失败

现象：

- `channel/status` 返回 `running=false`。
- 日志出现 `Feishu channel client start failed`。

排查：

1. 检查是否存在启用中的飞书配置。
2. 检查 App ID、App Secret 是否正确。
3. 检查服务器是否能访问飞书 WebSocket 出口。
4. 检查应用是否启用机器人能力。
5. 本地可先运行：

```powershell
mvn -pl modules/module-feishu/module-feishu-autoconfig -Dtest=OfficialFeishuChannelClientManagerTest test
```

### 权限不足

现象：

- 飞书接口返回无权限。
- CLI 写入多维表格失败。
- 群消息能收到，但回复失败。

排查：

1. 确认开放平台权限已申请并发布生效。
2. 确认机器人已加入目标群。
3. 确认多维表格已授权给应用或机器人。
4. 确认使用的是当前启用租户的 App ID 和 App Secret。
5. 重新执行凭证校验：

```text
POST /api/biz/feishu/agent/credentials/{id}/validate
```

### CLI 命令执行失败

现象：

- 返回 `feishu cli command timeout`。
- 日志出现 `failed to run feishu cli command`。
- `npx` 命令找不到。

排查：

1. 在服务器上执行 `node -v`、`npm -v`、`npx @larksuite/cli@latest --help`。
2. 检查应用运行用户是否能访问 Node 和 npm。
3. 检查服务器能否访问 npm registry。
4. 调大超时：

```yaml
feishu:
  cli:
    timeout-seconds: 60
    retry-times: 1
```

5. 使用管理接口做最小写入测试：

```text
POST /api/biz/feishu/admin/cli/bitable/write-test
```

### 消息收不到

现象：

- 群里 `@机器人` 后后端无日志。
- `message-logs/page` 没有新记录。

排查：

1. 确认群聊里真的 `@机器人`，普通文本会被忽略。
2. 确认消息类型是文本消息，图片、文件等会被忽略。
3. 确认订阅了 `im.message.receive_v1`。
4. 确认 Channel 连接状态为 `running=true`。
5. 检查机器人是否被移出群或应用未发布。
6. 运行解析单测确认事件结构：

```powershell
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuChannelMessageParserTest test
```

### 收到消息但没有业务结果

现象：

- 机器人回复“暂不支持该指令”或无业务卡片。
- 日志阶段停在网关调度。

排查：

1. 检查 `FeishuAgentCommandRouter` 是否注册了对应 `FeishuAgentBusinessHandler`。
2. 检查用户映射是否存在：飞书 UserId 是否能映射到内部账号。
3. 检查内部业务接口是否可用。
4. 检查限流是否触发。
5. 暂时关闭归档验证业务主链路：

```yaml
feishu:
  agent:
    gateway:
      archive-enabled: false
```

### 管理接口返回 403

现象：

- `/api/biz/feishu/admin/**` 返回 `forbidden`。

排查：

1. 检查请求头是否包含：

```text
X-Feishu-Admin-Token: <feishu.admin.api-token>
```

2. 本地若未配置 `feishu.admin.api-token`，默认放行。
3. 生产环境必须配置该 token，并限制访问来源。

## 5. 推荐验证顺序

首次接入建议按以下顺序验证：

```powershell
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentCredentialServiceTest test
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuChannelMessageParserTest test
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuCliUsageDemoTest test
mvn -pl modules/module-feishu/module-feishu-core "-Dtest=FeishuAgentAdminControllerTest,FeishuAgentAdminServiceTest" test
mvn -pl modules/module-feishu/module-feishu-autoconfig -Dtest=FeishuAdminAutoConfigurationTest test
```

全量验证：

```powershell
mvn -pl modules/module-feishu/module-feishu-core -am test
mvn -pl modules/module-feishu/module-feishu-autoconfig -am test
```
