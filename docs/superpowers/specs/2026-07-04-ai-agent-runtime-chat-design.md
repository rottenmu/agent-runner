# AI 智能体自动创建与大模型调用设计

## 背景

`modules/ai-agent-spring-boot-starter` 当前已经提供 `AiAgentProperties`、`AiSkill`、`AiSkillRegistry`、默认技能、MCP Controller 和 A2A Controller，并通过 Spring Boot 自动配置装配到主应用。它现在的问题是：配置里已有模型、API Key、上下文等参数，但 `AiAgentService` 只是回显用户消息，没有真正创建智能体运行时，也没有调用大模型。

本次设计目标是在现有 starter 内补齐“自动创建智能体 + 技能注册 + 真实大模型回复”的基础能力，同时保持模块边界清晰：starter 提供通用能力，`module-ai` 继续只负责平台插件身份和模块级技能。

## 目标

- 应用启动时自动创建一个 AI 智能体运行时，运行时包含智能体名称、模型配置、技能清单和初始化状态。
- 自动收集 Spring 容器中的全部 `AiSkill` Bean，并通过 `AiSkillRegistry` 对 MCP、A2A 和内部服务统一开放。
- 引入可替换的大模型调用抽象，默认支持 DashScope/OpenAI-compatible `/chat/completions` 调用。
- `AiAgentService` 提供真实 `chat(message, sessionId)` 能力，优先返回模型回复，失败时返回明确可诊断错误。
- API Key 缺失、AgentScope 初始化失败、大模型调用失败都不阻断 Spring Boot 启动。
- 保留当前接口兼容：`POST /api/ai/mcp`、`GET /api/ai/a2a/agent-card`、`POST /api/ai/a2a/message` 继续可用。

## 非目标

- 本次不实现多轮持久化会话历史。
- 本次不实现 LLM 自动选择技能、自动执行工具链、自动回填工具结果。
- 本次不新增数据库表，不使用 SQLite、H2 等本地数据库。
- 本次不重做前端 AI 工作台。
- 本次不强制切换到早期设计中的 `/api/ai/mcp/tools`、`/api/ai/a2a/tasks/send` 路径；这些路径可作为后续协议对齐任务。

## 推荐方案

采用“自动运行时 + 可插拔聊天客户端”的结构。

```text
AiAgentAutoConfiguration
  -> AiAgentProperties
  -> List<AiSkill>
  -> AiSkillRegistry
  -> AiAgentRuntimeFactory
  -> AiAgentRuntime
  -> AiChatClient
  -> AiAgentService
  -> McpController / A2aController
```

### 1. 智能体运行时

新增 `AiAgentRuntime`，保存运行时状态：

- `agentName`：来自 `ai.agent.name`。
- `modelName`：来自 `ai.agent.model-name`。
- `modelType`：来自 `ai.agent.model-type`。
- `skillDescriptors`：来自 `AiSkillRegistry.list()`。
- `status`：`READY`、`NOT_CONFIGURED`、`INITIALIZATION_FAILED`。
- `message`：用于说明当前状态，不能包含 API Key。

新增 `AiAgentRuntimeFactory`，负责启动时创建运行时：

- API Key 为空时返回 `NOT_CONFIGURED`，应用继续启动。
- AgentScope `HarnessAgent` 初始化成功时返回 `READY`。
- AgentScope 相关类不存在或初始化异常时返回 `INITIALIZATION_FAILED`。
- 初始化逻辑沿用项目管理 starter 的先例，优先通过反射调用 `io.agentscope.harness.agent.HarnessAgent`，降低 starter 对 AgentScope RC 版本 API 变化的编译耦合。

### 2. 大模型调用抽象

新增 `AiChatClient` 接口：

```java
public interface AiChatClient {
    AiChatResponse chat(AiChatRequest request);
}
```

新增默认实现 `OpenAiCompatibleChatClient`：

- 使用 Spring `RestClient` 调用 `${ai.agent.base-url}/chat/completions`。
- 请求体使用 OpenAI-compatible 格式：`model`、`messages`、`temperature`、`max_tokens`。
- `Authorization` 使用 Bearer token，但错误消息和日志不得输出 API Key。
- 返回第一个 `choices[0].message.content` 作为回复。
- HTTP 错误、响应格式异常、空回复统一转成失败响应。

新增 DTO：

- `AiChatRequest`：包含 `agentName`、`message`、`sessionId`、`modelName`、`temperature`、`maxTokens`。
- `AiChatResponse`：包含 `success`、`content`、`errorMessage`。

### 3. Agent 服务行为

调整 `AiAgentService`：

- 保留一个 public 构造器，使用构造器注入 `AiAgentProperties`、`AiSkillRegistry`、`AiAgentRuntime`、`AiChatClient`。
- 新增 `chat(String message, String sessionId)`。
- `reply(String message)` 继续保留，内部委托 `chat(message, null)`，保证当前 A2A Controller 兼容。
- 当运行时为 `NOT_CONFIGURED` 时，返回“AI 服务未配置，请配置 ai.agent.api-key 或 DASHSCOPE_API_KEY”。
- 当运行时为 `INITIALIZATION_FAILED` 时，返回“AI 智能体初始化失败：”加运行时状态中的错误摘要。
- 当消息为空时，返回参数错误，不调用大模型。
- 当大模型调用成功时，返回模型内容。
- 当大模型调用失败时，返回可读失败原因。

### 4. 技能功能

`AiSkillRegistry` 继续作为技能入口：

- 自动收集所有 `AiSkill` Bean。
- 默认技能仍由 starter 自动配置提供。
- 业务模块可通过声明自己的 `AiSkill` Bean 自动加入注册表。
- `McpController` 继续通过 `tools/list` 和 `tools/call` 调用注册表。

本次默认技能的定位保持轻量：

- `echo`：连通性检查。
- `summarize`：轻量文本处理示例。
- `generate_plan`：轻量计划生成示例。
- `route_plugin_task`：插件任务路由示例。

真实模型回复由 `AiAgentService` 负责，不把默认技能伪装成大模型能力。

## 配置设计

保持现有配置前缀：

```yaml
ai:
  agent:
    enabled: true
    name: ${AI_AGENT_NAME:ai-agent}
    model-name: ${LLM_MODEL_NAME:qwen-plus}
    model-type: ${LLM_MODEL_TYPE:dashscope_chat}
    base-url: ${LLM_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
    api-key: ${DASHSCOPE_API_KEY:}
    temperature: ${LLM_TEMPERATURE:0.7}
    max-tokens: ${LLM_MAX_TOKENS:2000}
    max-iters: ${AGENT_MAX_ITERS:5}
    chat-history-limit: ${CHAT_HISTORY_LIMIT:20}
```

敏感值只通过环境变量或外部配置注入。starter 自带资源文件不得写入真实 API Key。

## 接口行为

### A2A

当前接口继续可用：

```text
GET  /api/ai/a2a/agent-card
POST /api/ai/a2a/message
```

`agent-card` 返回智能体名称和能力说明，能力中体现 `mcp-tools`、`skill-routing`、`plugin-assistant`。

`message` 接口调用 `AiAgentService.reply()`，返回：

```json
{
  "agent": "ai-agent",
  "content": "模型回复或明确错误"
}
```

### MCP

当前 JSON-RPC 风格接口继续可用：

```text
POST /api/ai/mcp
```

- `tools/list` 返回 `AiSkillRegistry.list()`。
- `tools/call` 调用 `AiSkillRegistry.call(name, arguments)`。
- 未知方法返回 JSON-RPC error。
- 未知技能返回 `AiSkillResult.fail(...)`，不返回 500。

## 错误处理

- API Key 缺失：接口返回业务错误内容，应用启动成功。
- AgentScope 初始化失败：接口返回初始化失败内容，应用启动成功。
- 大模型调用失败：接口返回调用失败内容，不泄露请求头、API Key 或完整敏感配置。
- 空消息：返回参数错误内容。
- 未知技能：返回失败结果，包含技能名称。

## 测试策略

按 TDD 实施，优先补窄范围测试：

- `AiAgentRuntimeFactoryTest`
  - API Key 为空时返回 `NOT_CONFIGURED`。
  - API Key 存在时尝试创建运行时，并在 AgentScope 不可用时返回 `INITIALIZATION_FAILED`。
- `AiAgentServiceTest`
  - 运行时未配置时不调用 `AiChatClient`。
  - 消息为空时返回参数错误。
  - 运行时就绪且模型调用成功时返回模型内容。
  - 模型调用失败时返回可读错误。
- `OpenAiCompatibleChatClientTest`
  - 请求体包含模型名、消息、温度和 max tokens。
  - 成功响应能提取 `choices[0].message.content`。
  - HTTP 错误不会泄露 API Key。
- `AiAgentAutoConfigurationTest`
  - 自动配置创建 `AiAgentRuntime`、`AiChatClient`、`AiAgentService`。
  - 用户自定义 `AiChatClient` 时不覆盖。
- `A2aControllerTest`
  - `/message` 返回模型回复。
  - 未配置时返回明确错误。

验证命令优先使用：

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -am test
```

影响扩大后再运行：

```powershell
mvn -pl admin-shell -am package
```

## 实施顺序

1. 写 `AiAgentRuntime`、运行时状态枚举和 `AiAgentRuntimeFactory` 的失败路径测试。
2. 实现运行时创建逻辑，保证未配置和初始化失败都不阻断启动。
3. 写 `AiAgentService` 的聊天行为测试。
4. 改造 `AiAgentService`，让 `reply()` 委托 `chat()`。
5. 写 `AiChatClient` 默认实现测试。
6. 实现 `OpenAiCompatibleChatClient`。
7. 更新自动配置测试，补齐 Bean 装配和自定义 Bean 覆盖规则。
8. 清理 starter 资源中的敏感示例配置，改为环境变量占位。
9. 运行 starter 测试；必要时扩大到 admin-shell 打包验证。

## 后续扩展

- 增加 `/api/ai/mcp/tools`、`/api/ai/mcp/tools/call`、`/api/ai/a2a/tasks/send`，与早期设计路径对齐。
- 引入内存或外部会话存储，支持 `sessionId` 多轮上下文。
- 支持 LLM tool calling，把 `AiSkill` 描述转换成模型工具定义，并执行工具调用闭环。
- 为不同业务插件增加独立 agent profile，例如 `wms-agent`、`manufacturing-pm-agent`。
