# 飞书 Agent 统一消息调度网关设计

## 背景

`module-feishu` 已具备三层基础能力：

- 凭据层：维护飞书应用配置，能够读取当前启用的应用凭据。
- Channel SDK 交互层：通过 WebSocket 接收飞书群聊或单聊中的 `@机器人` 消息，并解析为内部命令消息。
- CLI 执行层：封装飞书 CLI 调用，支持向多维表格、文档、日历、任务等飞书对象写入或查询数据。

本次在此基础上新增统一消息调度网关。网关不新增独立应用，也不替换 Channel 监听器，而是作为 `FeishuAgentMessageHandler` 的默认业务实现接入现有消息链路。

## 设计目标

- Channel 监听器接收到飞书消息后，统一交给调度网关处理。
- 根据用户文本匹配业务指令，并路由到可扩展的业务处理器。
- 自动读取当前租户有效飞书凭据，避免无效连接继续执行业务链路。
- 串联业务接口调用、飞书 CLI 多维表格归档、飞书消息回复三层能力。
- 支持全链路参数校验、限流控制、异常兜底回复。
- 统一组装标准业务结果卡片，卡片可携带多维表格、文档等跳转链接。
- 保持 Spring Boot starter 风格，默认自动装配，业务模块可通过 Bean 扩展指令处理能力。

## 非目标

- 不在本次实现真实大模型推理。
- 不在本次新增前端页面。
- 不在本次引入 MQ、Redis 或分布式限流组件。
- 不在网关中硬编码项目管理、WMS 等具体业务逻辑；具体业务查询通过可插拔处理器扩展。
- 不改变已完成的凭据层、Channel SDK 层、CLI 执行层对外契约。

## 采用方案

采用方案 A：调度网关作为默认 `FeishuAgentMessageHandler` 接入。

现有链路已经在 `FeishuChannelMessageListener` 中完成消息解析、用户映射和权限上下文绑定，最后调用 `FeishuAgentMessageHandler`。因此网关只需要实现该接口即可进入主链路，既能复用已有能力，又避免监听器承担过多业务编排职责。

## 模块结构

新增核心包：

```text
com.zimo.module.feishu.gateway
  FeishuAgentGatewayMessageHandler.java
  FeishuAgentDispatchService.java
  FeishuAgentCommandRouter.java
  FeishuAgentCommandRoute.java
  FeishuAgentBusinessHandler.java
  FeishuAgentBusinessRequest.java
  FeishuAgentBusinessResult.java
  FeishuAgentResultCardFactory.java
  FeishuAgentRateLimiter.java
  FeishuAgentGatewayException.java
```

新增自动装配包内配置：

```text
com.zimo.module.feishu.autoconfig
  FeishuAgentGatewayProperties.java
```

## 核心组件

### FeishuAgentGatewayMessageHandler

实现 `FeishuAgentMessageHandler`，作为 Channel 层进入调度网关的桥接器。

职责：

- 接收 `FeishuAgentCommandMessage` 和 `FeishuAgentReplyService`。
- 调用 `FeishuAgentDispatchService` 执行完整调度链路。
- 捕获未处理异常，确保最终给用户兜底回复。

### FeishuAgentDispatchService

统一编排完整业务链路。

处理顺序：

1. 校验消息 ID、租户、发送人、指令文本等必要参数。
2. 通过 `FeishuConfigProvider` 读取当前启用飞书配置，并校验 AppID、AppSecret 是否存在。
3. 使用 `FeishuAgentRateLimiter` 执行本地限流。
4. 使用 `FeishuAgentCommandRouter` 匹配业务指令。
5. 调用匹配到的 `FeishuAgentBusinessHandler`。
6. 将业务结果通过 `FeishuBitableCliService` 归档到多维表格。
7. 先用 `FeishuAgentReplyService#streamText` 返回处理进度。
8. 再用 `FeishuAgentReplyService#replyCard` 返回标准业务结果卡片。

归档失败不阻断用户回复，但卡片中需要展示归档状态，便于排查。

### FeishuAgentCommandRouter

指令路由分发器，根据用户文本匹配不同业务查询接口。

匹配规则：

- 优先按命令前缀匹配，例如 `项目`、`库存`、`帮助`。
- 再按关键词匹配，例如 `项目进度`、`交期风险`、`实时库存`。
- 多个路由同时命中时，优先级数字更小者优先。
- 未命中时返回默认帮助路由。

路由来源：

- 自动收集 Spring 容器中的 `FeishuAgentBusinessHandler` Bean。
- 每个处理器声明自身支持的 `FeishuAgentCommandRoute`。

### FeishuAgentBusinessHandler

业务处理器接口，由具体业务模块扩展。

职责：

- 判断自己支持哪些指令。
- 根据 `FeishuAgentBusinessRequest` 调用自有业务接口。
- 返回统一的 `FeishuAgentBusinessResult`。

默认提供一个帮助处理器，用于展示当前可用指令。后续项目管理、WMS、系统管理等模块可以继续注册自己的处理器。

### FeishuAgentBusinessResult

统一业务结果模型。

核心字段：

- `title`：结果标题。
- `summary`：摘要说明。
- `fields`：卡片展示字段。
- `archiveFields`：写入多维表格的字段。
- `links`：表格、文档、业务页面等跳转链接。
- `success`：业务是否成功。
- `message`：失败或提示信息。

### FeishuAgentResultCardFactory

业务结果卡片组装工具。

职责：

- 将 `FeishuAgentBusinessResult` 转换为飞书卡片 Markdown。
- 自动追加归档状态。
- 自动将表格、文档、业务页面链接转换为按钮。
- 复用已有 `FeishuCardTemplateFactory` 生成最终卡片 JSON。

### FeishuAgentRateLimiter

本地限流组件。

默认规则：

- 维度：`tenantKey + senderUserId`。
- 窗口：60 秒。
- 默认最大请求数：10 次。

限流命中后立即回复用户，不继续调用业务接口或 CLI。

## 配置设计

新增配置前缀：`feishu.agent.gateway`

示例：

```yaml
feishu:
  agent:
    gateway:
      enabled: true
      rate-limit-enabled: true
      rate-limit-window-seconds: 60
      rate-limit-max-requests: 10
      archive-enabled: true
      archive-app-token:
      archive-table-id:
      progress-reply-enabled: true
```

配置说明：

- `enabled`：是否启用统一调度网关。
- `rate-limit-enabled`：是否启用本地限流。
- `rate-limit-window-seconds`：限流窗口秒数。
- `rate-limit-max-requests`：窗口内最大请求数。
- `archive-enabled`：是否启用多维表格归档。
- `archive-app-token`：归档多维表格 app token。
- `archive-table-id`：归档数据表 ID。
- `progress-reply-enabled`：是否发送流式处理进度。

## 自动装配规则

- `feishu.enabled=true` 且 `feishu.agent.gateway.enabled=true` 时启用。
- 如果业务方没有提供自己的 `FeishuAgentMessageHandler`，默认注册 `FeishuAgentGatewayMessageHandler`。
- 自动注册 `FeishuAgentDispatchService`、`FeishuAgentCommandRouter`、`FeishuAgentResultCardFactory`、`FeishuAgentRateLimiter`。
- 自动收集所有 `FeishuAgentBusinessHandler` Bean。
- 未配置归档表时，网关仍可处理消息和回复卡片，但跳过多维表格归档。

## 数据流

```mermaid
flowchart LR
  A["飞书群聊/单聊 @机器人"] --> B["Channel Message Listener"]
  B --> C["FeishuAgentGatewayMessageHandler"]
  C --> D["FeishuAgentDispatchService"]
  D --> E["校验当前有效飞书凭据"]
  E --> F["限流检查"]
  F --> G["FeishuAgentCommandRouter"]
  G --> H["FeishuAgentBusinessHandler"]
  H --> I["FeishuBitableCliService 归档"]
  I --> J["FeishuAgentResultCardFactory"]
  J --> K["FeishuAgentReplyService 流式文本 + 卡片回复"]
```

## 异常处理

- 空指令：回复可用指令帮助。
- 未命中指令：回复默认帮助卡片。
- 凭据无效：回复“飞书应用未完成配置”。
- 限流命中：回复“请求过于频繁，请稍后再试”。
- 业务处理失败：回复业务失败卡片，并记录错误摘要。
- CLI 归档失败：继续回复业务结果卡片，并展示归档失败状态。
- 飞书回复失败：由已有回复服务记录消息日志，网关不重复抛出。

## 测试策略

核心测试：

- 路由器按前缀、关键词、优先级匹配正确。
- 空指令、未知指令返回帮助路由。
- 调度服务在凭据缺失时返回兜底回复。
- 限流命中时不调用业务处理器和 CLI。
- 业务成功时调用多维表格归档并返回卡片。
- 归档失败时仍返回业务卡片。
- 业务异常时返回兜底失败卡片。

自动装配测试：

- `feishu.agent.gateway.enabled=true` 时注册网关相关 Bean。
- `feishu.agent.gateway.enabled=false` 时不注册网关处理器。
- 存在业务自定义 `FeishuAgentMessageHandler` 时不覆盖。

验证命令：

```text
mvn -pl modules/module-feishu/module-feishu-core -am test
mvn -pl modules/module-feishu/module-feishu-autoconfig -am test
```

## 风险与处理

- 业务指令范围未来会扩大：通过 `FeishuAgentBusinessHandler` 插件式扩展，避免网关硬编码业务。
- 多维表格归档配置缺失：默认跳过归档，不影响消息回复。
- 本地限流无法覆盖多实例部署：当前先实现轻量本地限流，后续可替换为 Redis 实现。
- 卡片内容过长：卡片工厂需要截断字段展示，完整结果通过表格或文档链接承载。
- 飞书凭据失效：网关入口统一校验，避免业务链路执行到一半才失败。
