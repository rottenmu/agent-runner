# AI 智能体插件模块设计

## 背景

当前工程已经形成后端插件平台：

- `PluginRegister` 负责声明插件 ID、名称、API 前缀、前端路由、前端模块、智能体名称和排序。
- `module-*-core` 放插件核心声明。
- `module-*-autoconfig` 通过 Spring Boot 自动配置把插件注册到主工程。
- `admin-shell` 聚合启用各插件。
- 前端通过 `/api/plugins` 动态加载插件菜单和路由。

这次新增 AI 模块时，应继续沿用插件平台能力，而不是创建一个完全独立的旁路应用。

## 目标

新增一个基于 Java AgentScope 2.0 的 AI 能力模块，具备：

- 平台插件能力：作为 `ai` 插件挂入当前主项目。
- Starter 能力：沉淀一个可复用的 Spring Boot starter，后续其他子平台可以引入 starter 快速获得智能体、技能、MCP 和 A2A 基础能力。
- 智能体能力：基于 AgentScope Java 2.0 创建智能体。
- 技能能力：提供可注册、可枚举、可调用的技能模型。
- MCP 接口：对外暴露工具列表和工具调用接口。
- A2A 接口：对外暴露 Agent Card 和任务发送接口。
- 大模型配置：从指定 `.env` 读取，并按用户确认将 API Key 写入当前项目仓库配置。

## 非目标

本阶段不做：

- 完整前端 AI 聊天工作台。
- 多智能体编排 UI。
- AgentScope Python sidecar 替换。
- 复杂 MCP SSE 长连接协议。
- 持久化会话历史。
- 对外发布 Maven 仓库。

## 推荐架构

采用“Starter + 插件模块”的双层结构。

### 1. AI Starter 层

新增可复用 starter：

```text
starters/
  ai-agent-spring-boot-starter/
```

职责：

- 提供 AgentScope Java 2.0 依赖封装。
- 提供 `AiAgentProperties` 配置绑定。
- 提供 `.env` 环境变量读取和映射。
- 提供 `AiSkill`、`AiSkillRegistry`、`AiSkillResult` 等技能抽象。
- 提供 `AiAgentService` 智能体服务抽象。
- 提供 MCP HTTP 适配器。
- 提供 A2A JSON-RPC 适配器。
- 通过自动配置暴露可选 Bean。

Starter 不绑定具体业务插件，不依赖 `module-ai`。

### 2. AI 插件层

新增平台插件模块：

```text
modules/
  agent-harness/
    agent-harness-core/
    agent-harness-autoconfig/
```

职责：

- 声明插件身份：`pluginId = ai`。
- 将 AI 能力挂入 `/api/ai`。
- 注册默认技能。
- 注册默认智能体名称：`ai-agent`。
- 通过 `module-ai-autoconfig` 引入 starter 并启用 AI 能力。
- 让 `/api/plugins` 能发现 AI 插件，未来前端可按 `frontendModule = ai` 加载菜单。

### 3. 主应用接入层

`admin-shell` 新增依赖：

```text
module-ai-autoconfig
```

主工程只负责启用模块，不直接承载 AI 业务代码。

## Maven 模块规划

根 `pom.xml` 增加 dependencyManagement：

- `ai-agent-spring-boot-starter`
- `agent-harness-core`
- `agent-harness-autoconfig`

根 `<modules>` 增加：

- `starters`

`starters/pom.xml` 增加：

- `ai-agent-spring-boot-starter`

`modules/pom.xml` 增加：

- `module-ai`

`admin-shell/pom.xml` 增加：

- `module-ai-autoconfig`

## 配置设计

AI starter 使用配置前缀：

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

`module-ai` 使用插件配置前缀：

```yaml
plugin:
  ai:
    enabled: true
    agent-name: ${AI_AGENT_NAME:ai-agent}
```

`.env` 中读取的变量包括：

- `DASHSCOPE_API_KEY`
- `LLM_MODEL_NAME`
- `LLM_BASE_URL`
- `LLM_MODEL_TYPE`
- `LLM_TEMPERATURE`
- `LLM_MAX_TOKENS`
- `AGENT_MAX_ITERS`
- `CHAT_HISTORY_LIMIT`

API Key 按用户确认写入当前项目仓库配置文件，starter 仍保留从环境变量覆盖的能力。

## 技能模型

定义统一技能接口：

```java
public interface AiSkill {
    String name();
    String description();
    boolean readOnly();
    AiSkillResult call(Map<String, Object> arguments);
}
```

默认内置技能：

- `echo`：返回输入内容，用于联通性测试。
- `summarize`：对文本做摘要。
- `generate_plan`：根据目标生成步骤计划。
- `route_plugin_task`：根据任务描述返回建议插件。

技能注册由 `AiSkillRegistry` 统一管理，MCP 和 A2A 都通过注册表调用技能，避免重复实现。

## 智能体模型

`AiAgentService` 负责：

- 初始化 AgentScope Java 智能体。
- 注册 `AiSkillRegistry` 中的技能。
- 根据配置选择模型。
- 在 API Key 缺失时返回明确错误，而不是抛出启动异常。
- 提供 `chat(message, sessionId)` 能力。

初版可保持轻量封装：

- 能初始化 AgentScope HarnessAgent。
- 能暴露已注册技能。
- 能返回可诊断的运行结果。

后续再补真实多轮记忆和工具自动调用。

## MCP 接口

对外路径：

```text
GET  /api/ai/mcp/tools
POST /api/ai/mcp/tools/call
```

`GET /tools` 返回：

```json
{
  "code": 200,
  "data": [
    {
      "name": "echo",
      "description": "返回输入内容",
      "readOnly": true
    }
  ]
}
```

`POST /tools/call` 请求：

```json
{
  "name": "echo",
  "arguments": {
    "text": "hello"
  }
}
```

响应：

```json
{
  "code": 200,
  "data": {
    "success": true,
    "content": "hello"
  }
}
```

未知技能返回业务错误，不返回 500。

## A2A 接口

对外路径：

```text
GET  /api/ai/a2a/agent-card
POST /api/ai/a2a/tasks/send
```

`GET /agent-card` 返回 AI 智能体能力描述：

```json
{
  "name": "ai-agent",
  "description": "Production Studio AI Agent",
  "url": "/api/ai/a2a/tasks/send",
  "capabilities": {
    "streaming": false,
    "tools": true
  }
}
```

`POST /tasks/send` 支持 JSON-RPC 风格：

```json
{
  "jsonrpc": "2.0",
  "id": "task-1",
  "method": "tasks/send",
  "params": {
    "message": {
      "role": "user",
      "parts": [
        {
          "kind": "text",
          "text": "帮我总结这段内容"
        }
      ]
    }
  }
}
```

响应：

```json
{
  "jsonrpc": "2.0",
  "id": "task-1",
  "result": {
    "status": "completed",
    "message": {
      "role": "agent",
      "parts": [
        {
          "kind": "text",
          "text": "处理结果"
        }
      ]
    }
  }
}
```

初版不做流式输出。

## 插件注册

`AiPluginRegister`：

- `getPluginId()` 返回 `ai`
- `getPluginName()` 返回 `AI 智能体`
- `getApiPrefix()` 返回 `/api/ai`
- `getFrontendRoute()` 返回 `/biz/ai`
- `getFrontendModule()` 返回 `ai`
- `getAgentName()` 返回 `ai-agent`
- `getOrder()` 返回 `4`

## 错误处理

- API Key 缺失：返回业务错误，提示 AI 服务未配置。
- 未知技能：返回业务错误，包含技能名称。
- AgentScope 初始化失败：返回业务错误，包含简短错误原因。
- A2A JSON-RPC 方法不支持：返回 JSON-RPC error。
- 请求体为空：返回明确参数错误。

## 测试策略

优先 TDD：

- `AiSkillRegistryTest`
  - 能注册并列出技能。
  - 未知技能返回失败结果。
- `McpControllerTest`
  - 能列出技能。
  - 能调用 `echo` 技能。
- `A2aControllerTest`
  - 能返回 Agent Card。
  - 能处理 `tasks/send`。
  - 不支持的方法返回 JSON-RPC error。
- `AiAgentPropertiesTest`
  - 能从环境变量映射模型配置。

构建验证：

```powershell
mvn -pl admin-shell -am package
```

## 实施顺序

1. 创建 `starters/ai-agent-spring-boot-starter`。
2. 创建 starter 的配置属性、技能接口、技能注册表和默认技能。
3. 创建 starter 的 MCP 和 A2A 控制器。
4. 创建 `modules/module-ai` 插件模块。
5. 将 `module-ai-autoconfig` 接入 `admin-shell`。
6. 在 `application.yml` 中加入非敏感默认配置。
7. 运行后端构建和测试。

## 待确认事项

本设计按以下假设执行：

- “starter 模式的 model” 理解为“Spring Boot starter 模式的模块”。
- MCP 初版采用 HTTP 工具接口，不做 SSE。
- A2A 初版采用 JSON-RPC `tasks/send` 风格，不做流式任务状态订阅。
- 按用户确认，将 `.env` 中的 `DASHSCOPE_API_KEY` 写入当前项目配置文件。
