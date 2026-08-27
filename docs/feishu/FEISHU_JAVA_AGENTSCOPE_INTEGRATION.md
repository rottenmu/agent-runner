# 飞书模块对接 Java AgentScope 智能体框架指南

本文面向 `module-feishu` 后端开发人员，说明如何基于当前飞书插件能力接入 Java AgentScope 智能体框架，让飞书群聊或单聊里的 `@机器人` 消息进入 AgentScope 智能体，再由智能体调用飞书工具、业务工具，并将结果通过飞书消息返回给用户。

## 1. 当前模块能力

`module-feishu` 已经具备以下基础能力：

- 插件注册：`FeishuPluginRegister` 声明插件 ID 为 `feishu`，智能体名称为 `feishu-agent`。
- 凭据层：支持租户扫码初始化、飞书应用凭据保存、启用配置、凭据校验。
- Channel SDK 交互层：通过飞书 WebSocket Channel 接收 `im.message.receive_v1` 事件，本地调试无需公网回调域名。
- 用户映射层：支持飞书 UserId 与内部用户上下文绑定。
- 调度网关层：`FeishuAgentDispatchService` 已串联指令解析、限流、业务路由、多维表格归档和飞书卡片回复。
- CLI 执行层：封装 `npx @larksuite/cli` 或自研 Wrapper CLI，支持多维表格、文档、日历、任务四类飞书对象。
- 管理接口：提供机器人状态、CLI 写入测试、消息日志分页、机器人启停等调试入口。

因此，AgentScope 接入不需要重写飞书连接层，只需要把“业务处理器”从固定指令处理扩展为“智能体处理器”。

## 2. 推荐架构

推荐保持当前插件边界，将 AgentScope 作为 `module-feishu` 的可选智能体能力接入。

```text
飞书用户 @机器人
        |
        v
Feishu Channel SDK WebSocket
        |
        v
FeishuChannelMessageListener
        |
        v
FeishuUserPermissionBinder 绑定内部用户权限上下文
        |
        v
FeishuAgentMessageHandler
        |
        v
FeishuAgentDispatchService
        |
        v
FeishuAgentBusinessHandler
        |
        v
FeishuAgentScopeService
        |
        v
AgentScope HarnessAgent + Toolkit
        |
        +-- FeishuAgentScopeTools 调用飞书 CLI 能力
        +-- 业务模块 Tools 调用项目、WMS、系统等内部服务
        |
        v
FeishuAgentReplyService 返回文本、流式文本或交互卡片
```

### 2.1 分层职责

| 层级 | 建议类 | 职责 |
| --- | --- | --- |
| 消息入口 | `FeishuChannelMessageListener` | 接收飞书事件、解析 `@机器人` 文本、记录日志 |
| 权限绑定 | `FeishuUserPermissionBinder` | 把飞书用户映射成内部用户权限上下文 |
| 智能体路由 | `FeishuAgentBusinessHandler` | 将自然语言指令路由到 AgentScope 智能体 |
| 智能体服务 | `FeishuAgentScopeService` | 初始化 AgentScope、注册工具、执行对话 |
| 飞书工具 | `FeishuAgentScopeTools` | 把多维表格、文档、日历、任务封装成 AgentScope Tool |
| 自动装配 | `FeishuAgentScopeAutoConfiguration` 或并入 `FeishuAutoConfiguration` | 根据配置开关注入 AgentScope 相关 Bean |

## 3. Maven 依赖建议

根 `pom.xml` 已维护 AgentScope 版本：

```xml
<agentscope.version>2.0.0-RC3</agentscope.version>
```

若要在 `module-feishu-core` 中直接声明 AgentScope Tool，建议增加依赖：

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-harness</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

如果希望 `module-feishu-core` 不强依赖 AgentScope，可以参考项目管理模块做法：在智能体服务里通过反射初始化 `io.agentscope.harness.agent.HarnessAgent`，工具注解仍建议放在独立 `agent` 包中，便于后续拆分。

## 4. 推荐包结构

```text
modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/
  FeishuAgentScopeService.java
  FeishuAgentScopeReply.java
  FeishuAgentScopeBusinessHandler.java
  FeishuAgentScopeTools.java
  FeishuAgentScopeToolManifest.java

modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/
  FeishuAgentScopeProperties.java
```

说明：

- `FeishuAgentScopeBusinessHandler` 实现 `FeishuAgentBusinessHandler`，作为调度网关中的一条业务路由。
- `FeishuAgentScopeService` 负责创建 AgentScope 智能体并执行对话。
- `FeishuAgentScopeTools` 将现有飞书服务封装为 AgentScope 工具。
- `FeishuAgentScopeProperties` 使用 `feishu.agent.scope` 配置前缀，控制启停、模型、工作目录和系统提示词。

## 5. 配置示例

敏感配置只放本地环境或部署环境，不提交真实密钥。

```yaml
feishu:
  enabled: true
  agent:
    channel:
      enabled: true
      auto-start: true
    gateway:
      enabled: true
      archive-enabled: true
      archive-app-token: ${FEISHU_ARCHIVE_APP_TOKEN:}
      archive-table-id: ${FEISHU_ARCHIVE_TABLE_ID:}
    scope:
      enabled: true
      agent-name: feishu-agent
      model: dashscope:qwen-plus
      workspace: .agentscope/feishu
      route-prefixes:
        - 智能体
        - 助手
        - 飞书
      route-keywords:
        - 帮我
        - 查询
        - 创建
        - 总结
      system-prompt: >
        你是智能体运行平台的飞书智能体。必须优先调用已注册工具获取真实数据，
        不允许编造项目、库存、用户和飞书文档信息。

ai:
  agent:
    api-key: ${DASHSCOPE_API_KEY:}
    model-name: ${LLM_MODEL_NAME:qwen-plus}
```

建议规则：

- `feishu.agent.scope.enabled=false` 时，保留当前固定指令路由能力。
- `DASHSCOPE_API_KEY` 缺失时，不让应用启动失败，智能体调用返回“AI 服务未配置”。
- 工作目录使用 `.agentscope/feishu`，避免和其他业务智能体混用。

## 6. AgentScope 工具封装示例

`FeishuAgentScopeTools` 建议只做薄封装，不直接拼接 HTTP 或 CLI 命令，统一复用当前模块已有 Service。

```java
package com.zimo.module.zimo.agent;

import cli.com.zimo.module.feishu.FeishuCliCommandResult;
import bitable.cli.com.zimo.module.feishu.BitableRecordCreateRequest;
import bitable.cli.com.zimo.module.feishu.FeishuBitableCliService;
import document.cli.com.zimo.module.feishu.DocumentAppendRequest;
import document.cli.com.zimo.module.feishu.DocumentCreateRequest;
import document.cli.com.zimo.module.feishu.FeishuDocumentCliService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.util.Map;

public class FeishuAgentScopeTools {
    private final FeishuBitableCliService bitableCliService;
    private final FeishuDocumentCliService documentCliService;

    public FeishuAgentScopeTools(
            FeishuBitableCliService bitableCliService,
            FeishuDocumentCliService documentCliService) {
        this.bitableCliService = bitableCliService;
        this.documentCliService = documentCliService;
    }

    @Tool(name = "feishu_bitable_create_record",
            description = "向飞书多维表格新增一条记录",
            concurrencySafe = false)
    public String createBitableRecord(
            @ToolParam(name = "appToken", description = "多维表格 app_token") String appToken,
            @ToolParam(name = "tableId", description = "数据表 table_id") String tableId,
            @ToolParam(name = "fields", description = "字段键值对") Map<String, Object> fields) {
        FeishuCliCommandResult result = bitableCliService.createRecord(
                new BitableRecordCreateRequest(appToken, tableId, fields));
        return toText(result);
    }

    @Tool(name = "feishu_document_create",
            description = "创建飞书文档",
            concurrencySafe = false)
    public String createDocument(
            @ToolParam(name = "title", description = "文档标题") String title,
            @ToolParam(name = "folderToken", description = "目标文件夹 token，可为空") String folderToken) {
        FeishuCliCommandResult result = documentCliService.createDocument(
                new DocumentCreateRequest(title, folderToken));
        return toText(result);
    }

    @Tool(name = "feishu_document_append",
            description = "向飞书文档追加内容",
            concurrencySafe = false)
    public String appendDocument(
            @ToolParam(name = "documentToken", description = "文档 token") String documentToken,
            @ToolParam(name = "content", description = "追加内容") String content) {
        FeishuCliCommandResult result = documentCliService.appendContent(
                new DocumentAppendRequest(documentToken, content));
        return toText(result);
    }

    private String toText(FeishuCliCommandResult result) {
        if (result == null) {
            return "飞书 CLI 未返回结果";
        }
        if (result.isSuccess()) {
            return result.getStdout();
        }
        return "飞书工具调用失败：" + result.getErrorMessage();
    }
}
```

后续可继续补充：

- `feishu_calendar_create_event`
- `feishu_calendar_add_attendees`
- `feishu_task_create`
- `feishu_task_assign_owner`
- `feishu_message_send_text`
- `feishu_config_get_active`

## 7. 智能体服务示例

建议 `FeishuAgentScopeService` 负责以下事情：

- 校验大模型 API Key。
- 创建 `Toolkit` 并注册飞书工具。
- 初始化 `HarnessAgent`。
- 对外提供 `chat(commandText, sessionId)`。
- 返回稳定的 `FeishuAgentScopeReply`，不要把底层异常直接抛给飞书用户。

```java
package com.zimo.module.zimo.agent;

import io.agentscope.core.tool.Toolkit;

import java.nio.file.Paths;

import org.springframework.core.env.Environment;

public class FeishuAgentScopeService {
    private final FeishuAgentScopeTools tools;
    private final Environment environment;
    private final FeishuAgentScopeProperties properties;

    public FeishuAgentScopeService(
            FeishuAgentScopeTools tools,
            Environment environment,
            FeishuAgentScopeProperties properties) {
        this.tools = tools;
        this.environment = environment;
        this.properties = properties;
    }

    public FeishuAgentScopeReply chat(String message, String sessionId) {
        String apiKey = environment.getProperty("DASHSCOPE_API_KEY", System.getenv("DASHSCOPE_API_KEY"));
        if (apiKey == null || apiKey.isBlank()) {
            return FeishuAgentScopeReply.failure("AI 服务未配置，请联系管理员配置 DASHSCOPE_API_KEY。");
        }
        try {
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(tools);

            Class<?> agentClass = Class.forName("io.agentscope.harness.agent.HarnessAgent");
            Object builder = agentClass.getMethod("builder").invoke(null);
            builder.getClass().getMethod("name", String.class).invoke(builder, properties.getAgentName());
            builder.getClass().getMethod("sysPrompt", String.class).invoke(builder, properties.getSystemPrompt());
            builder.getClass().getMethod("model", String.class).invoke(builder, properties.getModel());
            builder.getClass().getMethod("workspace", java.nio.file.Path.class)
                    .invoke(builder, Paths.get(properties.getWorkspace()));
            builder.getClass().getMethod("build").invoke(builder);

            return FeishuAgentScopeReply.success(
                    "AgentScope Java 已初始化，已注册飞书工具：" + toolkit.getToolNames() + "。用户消息：" + message);
        } catch (ClassNotFoundException e) {
            return FeishuAgentScopeReply.failure("AgentScope Java 依赖未加载，请检查 agentscope-harness 依赖。");
        } catch (Exception e) {
            return FeishuAgentScopeReply.failure("AgentScope Java 初始化失败：" + e.getMessage());
        }
    }
}
```

当前项目管理模块已有类似的 AgentScope 初始化方式，可作为落地参考。

## 8. 接入调度网关

`FeishuAgentScopeBusinessHandler` 作为一条可路由业务处理器注入 Spring 容器即可被 `FeishuAgentCommandRouter` 自动收集。

```java
package com.zimo.module.zimo.agent;

import gateway.com.zimo.module.feishu.FeishuAgentBusinessHandler;
import gateway.com.zimo.module.feishu.FeishuAgentBusinessRequest;
import gateway.com.zimo.module.feishu.FeishuAgentBusinessResult;
import gateway.com.zimo.module.feishu.FeishuAgentCommandRoute;

import java.util.List;

public class FeishuAgentScopeBusinessHandler implements FeishuAgentBusinessHandler {
    private final FeishuAgentScopeService agentScopeService;
    private final FeishuAgentScopeProperties properties;

    public FeishuAgentScopeBusinessHandler(
            FeishuAgentScopeService agentScopeService,
            FeishuAgentScopeProperties properties) {
        this.agentScopeService = agentScopeService;
        this.properties = properties;
    }

    @Override
    public List<FeishuAgentCommandRoute> routes() {
        return List.of(new FeishuAgentCommandRoute(
                "飞书智能体",
                properties.getRoutePrefixes(),
                properties.getRouteKeywords(),
                100));
    }

    @Override
    public FeishuAgentBusinessResult handle(FeishuAgentBusinessRequest request) {
        FeishuAgentScopeReply reply = agentScopeService.chat(
                request.getCommandText(),
                request.getMessage().getChatId());
        if (!reply.isSuccess()) {
            return FeishuAgentBusinessResult.failure("飞书智能体执行失败", reply.getMessage());
        }
        return FeishuAgentBusinessResult.success("飞书智能体", reply.getMessage())
                .archiveField("智能体", properties.getAgentName())
                .archiveField("指令", request.getCommandText());
    }
}
```

这样飞书消息链路仍然沿用当前网关：

- 限流仍由 `FeishuAgentRateLimiter` 控制。
- 凭据仍由 `FeishuConfigProvider` 读取当前启用应用。
- 结果仍由 `FeishuAgentResultCardFactory` 组装成卡片。
- 处理记录仍写入消息日志和多维表格归档。

## 9. 自动装配建议

建议在 `FeishuAutoConfiguration` 中按条件注册以下 Bean：

```java
@Bean
@ConditionalOnMissingBean
@ConditionalOnProperty(prefix = "feishu.agent.scope", name = "enabled", havingValue = "true")
public FeishuAgentScopeTools feishuAgentScopeTools(
        FeishuBitableCliService bitableCliService,
        FeishuDocumentCliService documentCliService) {
    return new FeishuAgentScopeTools(bitableCliService, documentCliService);
}

@Bean
@ConditionalOnMissingBean
@ConditionalOnProperty(prefix = "feishu.agent.scope", name = "enabled", havingValue = "true")
public FeishuAgentScopeService feishuAgentScopeService(
        FeishuAgentScopeTools tools,
        Environment environment,
        FeishuAgentScopeProperties properties) {
    return new FeishuAgentScopeService(tools, environment, properties);
}

@Bean
@ConditionalOnMissingBean
@ConditionalOnProperty(prefix = "feishu.agent.scope", name = "enabled", havingValue = "true")
public FeishuAgentScopeBusinessHandler feishuAgentScopeBusinessHandler(
        FeishuAgentScopeService service,
        FeishuAgentScopeProperties properties) {
    return new FeishuAgentScopeBusinessHandler(service, properties);
}
```

注意：

- `FeishuAgentScopeBusinessHandler` 不要替换 `FeishuAgentMessageHandler`，它应该作为 `FeishuAgentBusinessHandler` 加入现有路由体系。
- 如果后续需要让所有指令都先进入智能体，可以把该路由优先级调小，或配置更宽的关键词。
- 不建议在 Controller 里直接调用 AgentScope，避免绕过飞书消息日志、用户权限绑定和限流。

## 10. 权限与安全要求

AgentScope 工具调用必须遵守以下规则：

- 只暴露已经经过后端封装的 Service，不让智能体直接拼接 SQL、HTTP 或 Shell 命令。
- 写操作工具必须设置 `concurrencySafe = false`，并在工具内部做参数校验。
- 调用飞书 CLI 前继续使用 `FeishuCliPolicy` 的业务域白名单。
- 飞书用户必须先通过 `FeishuUserMappingService` 映射到内部账号。
- 内部账号权限通过 `FeishuUserPermissionBinder` 绑定到当前线程，不在智能体提示词中暴露敏感权限细节。
- 不把 App Secret、Encrypt Key、Verification Token、DASHSCOPE_API_KEY 写入回复、日志或归档表格。

## 11. 本地验证流程

### 11.1 验证 AgentScope 依赖

```powershell
mvn -pl modules/module-feishu/module-feishu-core -DskipTests compile
```

若出现 `io.agentscope` 相关类找不到，检查 `module-feishu-core/pom.xml` 是否已加入 `agentscope-harness` 依赖。

### 11.2 验证飞书基础链路

```powershell
mvn -pl modules/module-feishu/module-feishu-core "-Dtest=FeishuChannelMessageParserTest,FeishuChannelMessageListenerTest,FeishuAgentDispatchServiceTest" test
```

### 11.3 验证 CLI 工具链路

```powershell
mvn -pl modules/module-feishu/module-feishu-core "-Dtest=FeishuCliUsageDemoTest,FeishuCliTemplateTest,FeishuCliBusinessServiceTest" test
```

### 11.4 验证自动装配

```powershell
mvn -pl modules/module-feishu/module-feishu-autoconfig -Dtest=FeishuAgentGatewayAutoConfigurationTest test
```

新增 AgentScope 自动配置后，建议补充：

```powershell
mvn -pl modules/module-feishu/module-feishu-autoconfig -Dtest=FeishuAgentScopeAutoConfigurationTest test
```

### 11.5 端到端调试

1. 启动后端应用。
2. 调用 `GET /api/biz/feishu/admin/channel/status`，确认 Channel 正常运行。
3. 在飞书群中发送：

```text
@机器人 智能体 帮我创建一份项目风险分析文档
```

4. 预期结果：
   - 后端记录收到飞书消息。
   - 用户映射成功。
   - `FeishuAgentScopeBusinessHandler` 命中路由。
   - `FeishuAgentScopeService` 初始化 AgentScope 并注册飞书工具。
   - 飞书群收到文本或卡片回复。
   - 若开启归档，多维表格中出现处理记录。

## 12. 常见问题

### 12.1 智能体返回“AI 服务未配置”

检查运行环境是否配置：

```text
DASHSCOPE_API_KEY
LLM_MODEL_NAME
```

不要把真实 API Key 写入文档、日志或提交信息。

### 12.2 飞书能收到消息，但没有进入智能体

检查：

- `feishu.agent.channel.enabled=true`
- `feishu.agent.gateway.enabled=true`
- `feishu.agent.scope.enabled=true`
- 指令是否命中 `route-prefixes` 或 `route-keywords`
- 是否存在其他更高优先级 `FeishuAgentBusinessHandler` 抢先匹配

### 12.3 智能体调用飞书工具失败

检查：

- Node.js、npm、npx 是否可用。
- `npx @larksuite/cli@latest --help` 是否可执行。
- `feishu.cli.allowed-business-types` 是否包含对应业务域。
- 当前启用飞书应用是否拥有多维表格、文档、日历、任务权限。
- 目标多维表格或文档是否授权给当前应用或机器人。

### 12.4 权限上下文丢失

AgentScope 工具应在当前请求线程内同步执行。如果后续引入异步执行或线程池，需要显式传递内部用户快照或重新绑定权限上下文。

## 13. 落地顺序建议

1. 为 `module-feishu-core` 增加 AgentScope 依赖或采用反射初始化方式。
2. 新增 `FeishuAgentScopeProperties` 配置类。
3. 新增 `FeishuAgentScopeTools`，先封装多维表格和文档两个工具。
4. 新增 `FeishuAgentScopeService`，完成 AgentScope 初始化与工具注册。
5. 新增 `FeishuAgentScopeBusinessHandler`，接入现有 `FeishuAgentCommandRouter`。
6. 在 `FeishuAutoConfiguration` 中增加条件 Bean。
7. 补充自动装配测试、工具测试和端到端消息模拟测试。
8. 再逐步扩展日历、任务、业务模块工具。

这个顺序可以最大限度复用当前飞书模块能力，同时保持插件式架构和后续扩展空间。
