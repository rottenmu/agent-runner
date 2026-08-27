# agent-harness — 智能体 Harness 运行时模块

> 基于 AgentScope Java v2（HarnessAgent）的智能体运行时：配置驱动的装配中心、渠道路由、执行链路、遥测观测、技能/模型/工作流管理与动态插件扩展。
> 技术栈：Java 17 / Spring Boot 3.4.5 / AgentScope Java v2 / MyBatis-Plus。

## 一、介绍

### 1.1 定位

`agent-harness` 提供智能体的**全生命周期运行时**：从数据库中的智能体配置（persona/模型/技能/类型）出发，装配出可运行的 `HarnessAgent`，接收消息执行（含工具调用、上下文压缩、记忆读写），并输出遥测追踪（trace/step）与告警。

### 1.2 核心能力

- **配置驱动装配**：DB（`ai_managed_agent`）→ `AiAgentManagementService`（内存装配中心）→ `AiManagedAgentProfileResolver`（渠道解析）→ `AiHarnessAgentFactory.create(profile)` → `HarnessAgent`；create/update/delete 通过 `AiManagedAgentRuntimeInvalidator` 失效重建，**无需重启**
- **5 种智能体类型**：`conversation`（默认）/ `rag` / `tool` / `plan` / `graph`——共享装配骨架，差异收敛到系统提示词追加 + 策略注入
- **渠道路由**：`AiHarnessAgentRouter` 按显式绑定 → 渠道绑定 → 渠道默认 → 全局默认 逐级路由；支持运行时 Profile 补丁（`AiProfilePatchProvider`）
- **技能体系**：`AiSkillRegistry` 技能注册 + 运行时增删（DB 配置驱动），技能以 Toolkit 注入 harness
- **模型管理**：`AiModelConfig` 模型配置（OpenAI 兼容 DashScope 等）导入/测试/切换
- **Prompt 体系**：`AiPromptTemplate` 模板 + `AiPromptVersion` 版本化
- **工作流引擎**：`WfWorkflowEngine` 流程模板/版本/运行/日志；`MultiAgentCollaborationService` 多智能体协作
- **遥测观测**：`ObservTraceService`（trace + step + 告警），`ObservEventBridge` 订阅事件总线自动落库
- **事件体系**：会话/Agent/工具三类事件域（`ConversationTurnEvent` / `AgentStepEvent` / `ToolCallEvent`），Spring 事件桥
- **工具把关钩子**：`ToolExecutionListener`（pre 可拒绝 / post / error / registered），链式组合
- **沙箱抽象**：`SandboxBackend` SPI（默认本地直通，可替换远端/容器沙箱）
- **能力 Seam**：`AiCapabilityProvider` 能力提供方抽象（技能/记忆工具即既有实例）
- **动态插件**：`AiPlugin` SPI + `DynamicPluginManager`（URLClassLoader 热加载 `data/plugins/*.jar`，onLoad/onUnload 生命周期）

### 1.3 装配链路

```text
DB(ai_managed_agent)
  → AiAgentManagementService（内存装配中心，启动/变更时加载）
  → AiManagedAgentProfileResolver（渠道解析：feishu 绑定 / 默认渠道）
  → AiHarnessAgentFactory.create(profile) → HarnessAgent 装配
      ├─ sysPrompt：persona + 模板 + 类型化追加
      ├─ model：按 profile 模型配置
      ├─ toolkit：技能 + 记忆工具 + 动态插件能力
      └─ workspace / maxIters / compaction / 记忆存储
  → AiHarnessAgentRouter（按 agentId 路由实例）
  → AiChatController / WfAiChatController（消息 → run）
```

## 二、使用说明

### 2.1 Maven 依赖

```xml
<dependency>
    <groupId>com.zimo</groupId>
    <artifactId>agent-harness-autoconfig</artifactId>
    <version>1.0.0</version>
</dependency>
<!-- 或直接依赖 core -->
<dependency>
    <groupId>com.zimo</groupId>
    <artifactId>agent-harness-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

> 注意：Maven parent 为 agent_runner 根 pom（`com.zimo:agent_runner`）。独立构建本仓库需先安装父 pom 或改用独立 parent；本仓库为 agent_runner 平台代码拆分。

### 2.2 配置（application.yml）

```yaml
ai:
  agent:
    conversation-max-sessions: 512
    conversation-shared-store: true
    harness-workspace-root: data/harness
    plugin-dir: data/plugins      # 动态插件目录（jar 放入自动装载）
```

### 2.3 REST API

| 分组 | 端点 | 说明 |
|---|---|---|
| 智能体管理 | `GET/POST/PUT/DELETE /api/ai/agent`、`/api/ai/agent/{id}/...` | 智能体 CRUD / 启停 / 渠道绑定（装配变更实时生效） |
| 对话 | `POST /api/ai/chat`、`/api/ai/workflow/chat` | 单智能体对话 / 工作流驱动对话 |
| 技能 | `GET/POST/PUT/DELETE /api/ai/skill`、`/api/ai/skill-call` | 技能管理 / 技能调用 |
| 模型 | `GET/POST /api/ai/model-config`、`/test` | 模型配置 / 连通性测试 |
| Prompt | `GET/POST/PUT/DELETE /api/ai/prompt-template`、`/prompt-version` | 模板与版本管理 |
| 工作流 | `GET/POST /api/ai/workflow`、`/run`、`/logs` | 流程模板/运行/日志 |
| 遥测 | `GET /api/ai/observ/traces`、`/traces/{id}/steps`、`/alert` | trace / step / 告警 |
| 插件 | `GET /api/ai/plugins`、`POST /upload`、`/load`、`/unload` | 动态插件管理 |

### 2.4 动态插件开发（三步）

```java
// 1. 实现 AiPlugin
public class MyPlugin implements AiPlugin {
    public String id() { return "my-plugin"; }
    public String version() { return "1.0.0"; }
    public void onLoad(PluginContext ctx) {
        ctx.registerCapability(new AiCapabilityProvider() { /* 注册工具 */ });
    }
    public void onUnload() { }   // 撤销注册由管理器自动处理
}
```

```properties
# 2. 清单 META-INF/ai-plugin.properties
plugin.class=com.example.MyPlugin
```

```bash
# 3. 打包放入插件目录（或 REST 上传）
jar cf my-plugin-1.0.0.jar com META-INF && cp my-plugin-1.0.0.jar data/plugins/
```

### 2.5 智能体类型说明

| 类型 | 说明 |
|---|---|
| `conversation` | 默认：通用多轮对话 |
| `rag` | 检索增强：注入知识检索工具与提示 |
| `tool` | 工具密集型：优先工具调用 |
| `plan` | 规划型：任务拆解 + 逐步执行 |
| `graph` | 图编排：多节点流程 |

## 三、模块结构

```text
agent-harness/
├── pom.xml
├── agent-harness-core/          # 领域模型 / 管理装配中心 / 遥测 / 工作流
│   ├── management/              # AiManagedAgent* / Contributor / ProfileResolver / RuntimeInvalidator
│   ├── observ/                  # ObservTraceService / ObservEventBridge / AlertService
│   ├── workflow/                # WfWorkflowEngine / WfTemplate / WfRun
│   ├── collab/                  # MultiAgentCollaborationService
│   ├── controller/              # AiChatController / WfAiChatController / 管理类控制器
│   ├── skillimport/             # 技能 zip 导入 / 结构校验
│   └── modelconfig/             # 模型配置
└── agent-harness-autoconfig/    # 自动装配 + REST 控制器 + Properties
```

## 四、构建与验证

```bash
# 模块构建（在 agent_runner 仓库内）
./mvnw install -pl modules/agent-harness -am -DskipTests

# 运行单测（装配路由/管理/模型配置/技能导入等）
./mvnw verify -pl modules/agent-harness
```

## 五、依赖关系

| 依赖 | 用途 |
|---|---|
| `agent-spring-boot-starter` | AiHarnessAgentFactory / HarnessAgent 装配、工具钩子、沙箱、动态插件 SPI |
| `agent-rag-core` | RAG 类型智能体检索能力 |
| `agent-intent-core` | 意图识别 |
| `framework-common` | 统一返回体、事件体系（ai/event） |
| `framework-autoconfig` | Web MVC / MyBatis-Plus 等全局配置 |

## 六、已知边界

- 事件总线为 Spring 事件（单机轻量），分布式场景需替换为消息中间件
- 沙箱默认本地直通（`LocalSandboxBackend`），生产环境建议实现远端/容器沙箱 SPI
- 动态插件为单一 ClassLoader 隔离，多版本共存需引入 PF4J 等框架（见演进方向）
