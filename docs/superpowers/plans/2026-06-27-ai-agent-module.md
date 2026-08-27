# AI 智能体模块实施计划

> **给执行代理的要求：** 实施本计划时必须按任务逐步执行。推荐使用 `superpowers:subagent-driven-development`；如果在当前会话连续执行，则使用 `superpowers:executing-plans`。任务使用复选框语法跟踪进度。

**目标：** 新建一个可复用的 Spring Boot starter，用 Java AgentScope 2.0 提供智能体、技能、MCP 和 A2A 基础能力，并通过当前插件平台新增 `ai` 模块对外暴露。

**架构：** 采用“starter + 插件模块”双层结构。`starters/ai-agent-spring-boot-starter` 承载通用 AI 能力；`modules/module-ai` 作为平台插件接入 `/api/plugins`；`admin-shell` 只负责引入插件并提供运行配置。

**技术栈：** Java 17、Spring Boot 3.4.5、Maven 多模块、AgentScope Java `agentscope-harness` 2.0.0-RC3、JUnit 5、Spring MVC Test。

## 全局约束

- 沿用现有插件模式：`module-*-core` 放 `PluginRegister`，`module-*-autoconfig` 放 Spring Boot 自动配置。
- AI 通用能力必须放在 `starters/ai-agent-spring-boot-starter`，starter 不依赖 `module-ai`。
- AI 插件 API 前缀固定为 `/api/ai`。
- MCP 初版采用 HTTP 接口：`GET /api/ai/mcp/tools`、`POST /api/ai/mcp/tools/call`。
- A2A 初版采用 HTTP JSON-RPC 接口：`GET /api/ai/a2a/agent-card`、`POST /api/ai/a2a/tasks/send`。
- A2A 初版不做流式输出。
- 大模型配置从用户指定 `.env` 的字段同步。
- 按用户确认，`DASHSCOPE_API_KEY` 需要写入当前项目配置文件。
- Markdown 文档中不重复展示 API Key 明文，避免在说明文档、日志或回复里扩散密钥。
- 后端验证命令：`mvn -pl admin-shell -am package`。

---

## 文件结构

新增：

- `starters/pom.xml`：starter 聚合模块。
- `starters/ai-agent-spring-boot-starter/pom.xml`：AI starter Maven 模块。
- `starters/ai-agent-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：starter 自动配置入口。
- `starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/AiAgentProperties.java`：`ai.agent` 配置绑定。
- `starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/skill/AiSkill.java`：技能接口。
- `starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/skill/AiSkillDescriptor.java`：技能描述模型。
- `starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/skill/AiSkillResult.java`：技能调用结果。
- `starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/skill/AiSkillRegistry.java`：技能注册表。
- `starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/skill/DefaultAiSkills.java`：默认技能。
- `starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/agent/AiAgentReply.java`：智能体回复模型。
- `starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/agent/AiAgentService.java`：AgentScope 智能体服务。
- `starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/mcp/McpController.java`：MCP HTTP 适配器。
- `starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/a2a/A2aController.java`：A2A HTTP 适配器。
- `starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfiguration.java`：starter 自动配置。
- `starters/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/skill/AiSkillRegistryTest.java`
- `starters/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfigurationTest.java`
- `starters/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/mcp/McpControllerTest.java`
- `starters/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/a2a/A2aControllerTest.java`
- `modules/module-ai/pom.xml`
- `modules/module-ai/module-ai-core/pom.xml`
- `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/AiPluginRegister.java`
- `modules/module-ai/module-ai-autoconfig/pom.xml`
- `modules/module-ai/module-ai-autoconfig/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiModuleProperties.java`
- `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfiguration.java`

修改：

- `pom.xml`：增加 `starters` 模块、AgentScope 版本、starter 和 `module-ai` 依赖管理。
- `modules/pom.xml`：增加 `module-ai`。
- `admin-shell/pom.xml`：增加 `module-ai-autoconfig`。
- `admin-shell/src/main/resources/application.yml`：增加 `ai.agent` 和 `plugin.ai` 配置，并按用户确认写入 `DASHSCOPE_API_KEY`。

---

## 任务 1：创建 Maven 模块骨架

**文件：**

- 新增：`starters/pom.xml`
- 新增：`starters/ai-agent-spring-boot-starter/pom.xml`
- 修改：`pom.xml`

**产出接口：**

- 产出 Maven 模块 `com.zimo:ai-agent-spring-boot-starter:1.0.0`。
- 后续任务可以通过根 Maven reactor 编译 starter。

- [ ] **步骤 1：修改根 `pom.xml`**

在 `<modules>` 中加入 `starters`：

```xml
<modules>
    <module>framework</module>
    <module>starters</module>
    <module>modules</module>
    <module>admin-shell</module>
</modules>
```

增加版本属性：

```xml
<agentscope.version>2.0.0-RC3</agentscope.version>
```

在 `dependencyManagement` 中增加：

```xml
<dependency>
    <groupId>com.zimo</groupId>
    <artifactId>ai-agent-spring-boot-starter</artifactId>
    <version>${project.version}</version>
</dependency>
<dependency>
    <groupId>com.zimo</groupId>
    <artifactId>module-ai-autoconfig</artifactId>
    <version>${project.version}</version>
</dependency>
<dependency>
    <groupId>com.zimo</groupId>
    <artifactId>module-ai-core</artifactId>
    <version>${project.version}</version>
</dependency>
```

- [ ] **步骤 2：创建 `starters/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.zimo</groupId>
        <artifactId>production-studio</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>starters</artifactId>
    <packaging>pom</packaging>

    <modules>
        <module>ai-agent-spring-boot-starter</module>
    </modules>
</project>
```

- [ ] **步骤 3：创建 starter `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.zimo</groupId>
        <artifactId>starters</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>ai-agent-spring-boot-starter</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.zimo</groupId>
            <artifactId>framework-common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>io.agentscope</groupId>
            <artifactId>agentscope-harness</artifactId>
            <version>${agentscope.version}</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **步骤 4：验证 Maven 能识别 starter**

运行：

```powershell
mvn -pl starters/ai-agent-spring-boot-starter -am test
```

预期：Maven 能进入 starter 模块。如果失败原因是模块无法解析，先修正 Maven 模块声明。

---

## 任务 2：实现技能模型和技能注册表

**文件：**

- 新增：`starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/skill/AiSkill.java`
- 新增：`starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/skill/AiSkillDescriptor.java`
- 新增：`starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/skill/AiSkillResult.java`
- 新增：`starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/skill/AiSkillRegistry.java`
- 新增：`starters/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/skill/AiSkillRegistryTest.java`

**产出接口：**

- `AiSkillRegistry#list()` 返回 `List<AiSkillDescriptor>`。
- `AiSkillRegistry#call(String, Map<String, Object>)` 返回 `AiSkillResult`。
- MCP 和 A2A 都通过这个注册表调用技能。

- [ ] **步骤 1：先写失败测试**

测试覆盖：

- 注册技能后能按名称列出。
- 能按技能名调用。
- 未知技能返回失败结果，不抛 500。

测试文件核心内容：

```java
class AiSkillRegistryTest {

    @Test
    void listsRegisteredSkillsAndCallsByName() {
        AiSkillRegistry registry = new AiSkillRegistry(java.util.List.of(new TestSkill()));

        assertThat(registry.list()).extracting(AiSkillDescriptor::name).containsExactly("test_echo");

        AiSkillResult result = registry.call("test_echo", Map.of("text", "hello"));

        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("hello");
    }

    @Test
    void unknownSkillReturnsFailureResult() {
        AiSkillRegistry registry = new AiSkillRegistry(java.util.List.of());

        AiSkillResult result = registry.call("missing", Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.content()).contains("Unknown AI skill: missing");
    }
}
```

- [ ] **步骤 2：运行测试确认 RED**

```powershell
mvn -pl starters/ai-agent-spring-boot-starter -Dtest=AiSkillRegistryTest test
```

预期：测试失败，原因是相关类还不存在。

- [ ] **步骤 3：实现技能接口和注册表**

实现：

- `AiSkill`
- `AiSkillDescriptor`
- `AiSkillResult`
- `AiSkillRegistry`

`AiSkillRegistry` 要使用 `LinkedHashMap` 保存技能，保证工具列表顺序稳定。

- [ ] **步骤 4：运行测试确认 GREEN**

```powershell
mvn -pl starters/ai-agent-spring-boot-starter -Dtest=AiSkillRegistryTest test
```

预期：测试通过。

---

## 任务 3：实现 starter 自动配置、配置属性和默认技能

**文件：**

- 新增：`starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/AiAgentProperties.java`
- 新增：`starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/skill/DefaultAiSkills.java`
- 新增：`starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfiguration.java`
- 新增：`starters/ai-agent-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- 新增：`starters/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfigurationTest.java`

**产出接口：**

- `AiAgentProperties` 绑定 `ai.agent` 配置。
- 默认注册 `echo`、`summarize`、`generate_plan`、`route_plugin_task` 四个技能。
- 自动配置创建 `AiSkillRegistry` Bean。

- [ ] **步骤 1：先写自动配置失败测试**

测试要求：

- `ai.agent.enabled=true` 时创建 `AiAgentProperties`。
- 创建 `AiSkillRegistry`。
- 默认技能列表包含四个默认技能。

- [ ] **步骤 2：运行测试确认 RED**

```powershell
mvn -pl starters/ai-agent-spring-boot-starter -Dtest=AiAgentAutoConfigurationTest test
```

预期：测试失败，原因是自动配置类还不存在。

- [ ] **步骤 3：实现 `AiAgentProperties`**

字段：

- `enabled`
- `name`
- `modelName`
- `modelType`
- `baseUrl`
- `apiKey`
- `temperature`
- `maxTokens`
- `maxIters`
- `chatHistoryLimit`

配置前缀：

```java
@ConfigurationProperties(prefix = "ai.agent")
```

- [ ] **步骤 4：实现默认技能**

默认技能：

- `echo`：返回 `arguments.text`。
- `summarize`：先做轻量摘要，文本小于 120 字则原样返回，超过 120 字则截断并追加省略号。
- `generate_plan`：根据 `arguments.goal` 生成三步计划。
- `route_plugin_task`：根据任务文本返回建议插件：系统类返回 `sys`，制造项目类返回 `manufacturing-pm`，其他返回 `ai`。

- [ ] **步骤 5：实现自动配置**

`AiAgentAutoConfiguration` 要：

- 标注 `@AutoConfiguration`。
- 标注 `@EnableConfigurationProperties(AiAgentProperties.class)`。
- 在 `ai.agent.enabled=true` 或未配置时启用。
- 导入默认技能配置。
- 在缺少 `AiSkillRegistry` 时创建默认注册表。

自动配置导入文件内容：

```text
com.zimo.starter.ai.autoconfig.AiAgentAutoConfiguration
```

- [ ] **步骤 6：运行测试确认 GREEN**

```powershell
mvn -pl starters/ai-agent-spring-boot-starter -Dtest=AiAgentAutoConfigurationTest test
```

预期：测试通过。

---

## 任务 4：实现 MCP 和 A2A 控制器

**文件：**

- 新增：`starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/mcp/McpController.java`
- 新增：`starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/a2a/A2aController.java`
- 修改：`starters/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfiguration.java`
- 新增：`starters/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/mcp/McpControllerTest.java`
- 新增：`starters/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/a2a/A2aControllerTest.java`

**产出接口：**

- MCP：`/api/ai/mcp/tools`、`/api/ai/mcp/tools/call`
- A2A：`/api/ai/a2a/agent-card`、`/api/ai/a2a/tasks/send`

- [ ] **步骤 1：先写 MCP 控制器失败测试**

测试要求：

- `GET /api/ai/mcp/tools` 返回 `code=200`，并包含 `echo`。
- `POST /api/ai/mcp/tools/call` 调用 `echo` 返回 `success=true` 和输入内容。

- [ ] **步骤 2：先写 A2A 控制器失败测试**

测试要求：

- `GET /api/ai/a2a/agent-card` 返回智能体名称和 `capabilities.tools=true`。
- `POST /api/ai/a2a/tasks/send` 支持 `method=tasks/send`，返回 `status=completed`。
- 不支持的方法返回 JSON-RPC error。

- [ ] **步骤 3：运行测试确认 RED**

```powershell
mvn -pl starters/ai-agent-spring-boot-starter -Dtest=McpControllerTest,A2aControllerTest test
```

预期：测试失败，原因是控制器还不存在。

- [ ] **步骤 4：实现 `McpController`**

接口：

- `GET /api/ai/mcp/tools`：返回 `AiSkillRegistry#list()`。
- `POST /api/ai/mcp/tools/call`：读取 `name` 和 `arguments`，调用 `AiSkillRegistry#call()`。

未知技能返回 `AiSkillResult.fail(...)`，接口仍返回 `R.ok(result)`，避免非预期 500。

- [ ] **步骤 5：实现 `A2aController`**

接口：

- `GET /api/ai/a2a/agent-card`：返回 `name`、`description`、`url`、`capabilities`。
- `POST /api/ai/a2a/tasks/send`：解析 JSON-RPC 请求。

初版逻辑：

- `method` 不是 `tasks/send` 时返回 JSON-RPC `error`。
- 从 `params.message.parts[0].text` 提取文本。
- 初版调用 `echo` 技能，返回非流式 `completed` 结果。

- [ ] **步骤 6：把控制器纳入自动配置**

在 `AiAgentAutoConfiguration` 中创建：

- `McpController`
- `A2aController`

使用 `@ConditionalOnMissingBean`，允许业务系统覆盖。

- [ ] **步骤 7：运行测试确认 GREEN**

```powershell
mvn -pl starters/ai-agent-spring-boot-starter -Dtest=McpControllerTest,A2aControllerTest test
```

预期：测试通过。

---

## 任务 5：创建 AI 插件模块

**文件：**

- 新增：`modules/module-ai/pom.xml`
- 新增：`modules/module-ai/module-ai-core/pom.xml`
- 新增：`modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/AiPluginRegister.java`
- 新增：`modules/module-ai/module-ai-autoconfig/pom.xml`
- 新增：`modules/module-ai/module-ai-autoconfig/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- 新增：`modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiModuleProperties.java`
- 新增：`modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfiguration.java`
- 修改：`modules/pom.xml`
- 修改：`admin-shell/pom.xml`
- 修改：`pom.xml`

**产出接口：**

- `AiPluginRegister` 作为插件注册 Bean。
- `/api/plugins` 能返回 `ai` 插件。
- `admin-shell` 能通过 `module-ai-autoconfig` 启用 AI 插件。

- [ ] **步骤 1：增加 `module-ai` 聚合模块**

在 `modules/pom.xml` 增加：

```xml
<module>module-ai</module>
```

创建 `modules/module-ai/pom.xml`，子模块包括：

- `module-ai-core`
- `module-ai-autoconfig`

- [ ] **步骤 2：创建 `module-ai-core`**

`module-ai-core` 依赖 `framework-common`。

创建 `AiPluginRegister`：

```java
package com.zimo.module.ai;

import com.zimo.framework.common.PluginRegister;

public class AiPluginRegister implements PluginRegister {
    @Override public String getPluginId() { return "ai"; }
    @Override public String getPluginName() { return "AI 智能体"; }
    @Override public String getApiPrefix() { return "/api/ai"; }
    @Override public String getFrontendRoute() { return "/biz/ai"; }
    @Override public String getFrontendModule() { return "ai"; }
    @Override public String getAgentName() { return "ai-agent"; }
    @Override public int getOrder() { return 4; }
}
```

- [ ] **步骤 3：创建 `module-ai-autoconfig`**

`module-ai-autoconfig` 依赖：

- `framework-autoconfig`
- `module-ai-core`
- `ai-agent-spring-boot-starter`
- `spring-boot-autoconfigure`
- `spring-boot-configuration-processor`
- `lombok`

创建 `AiModuleProperties`：

```java
@ConfigurationProperties(prefix = "plugin.ai")
public class AiModuleProperties {
    private boolean enabled = true;
    private String agentName = "ai-agent";
}
```

创建 `AiModuleAutoConfiguration`：

- 当 `plugin.ai.enabled=true` 或未配置时启用。
- 创建 `AiPluginRegister` Bean。
- 如果存在 `AgentScopeClient`，启动时注册 `ai-agent`，销毁时注销。

自动配置导入文件内容：

```text
com.zimo.module.ai.autoconfig.AiModuleAutoConfiguration
```

- [ ] **步骤 4：接入 `admin-shell`**

在 `admin-shell/pom.xml` 增加：

```xml
<dependency>
    <groupId>com.zimo</groupId>
    <artifactId>module-ai-autoconfig</artifactId>
</dependency>
```

- [ ] **步骤 5：验证后端构建**

```powershell
mvn -pl admin-shell -am package
```

预期：构建通过。

---

## 任务 6：同步配置并做端到端验证

**文件：**

- 修改：`admin-shell/src/main/resources/application.yml`

**产出接口：**

- `ai.agent` 配置可被 starter 读取。
- `plugin.ai` 配置可被插件模块读取。
- 后端启动后 MCP 和 A2A 接口可访问。

- [ ] **步骤 1：增加 AI 配置**

在 `application.yml` 增加：

```yaml
ai:
  agent:
    enabled: true
    name: ${AI_AGENT_NAME:ai-agent}
    model-name: ${LLM_MODEL_NAME:qwen3.7-max}
    model-type: ${LLM_MODEL_TYPE:dashscope_chat}
    base-url: ${LLM_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
    api-key: "<从指定 .env 读取的 DASHSCOPE_API_KEY 明文值>"
    temperature: ${LLM_TEMPERATURE:0.7}
    max-tokens: ${LLM_MAX_TOKENS:2000}
    max-iters: ${AGENT_MAX_ITERS:5}
    chat-history-limit: ${CHAT_HISTORY_LIMIT:20}
```

在现有 `plugin:` 下增加：

```yaml
  ai:
    enabled: true
    agent-name: ${AI_AGENT_NAME:ai-agent}
```

- [ ] **步骤 2：运行后端构建**

```powershell
mvn -pl admin-shell -am package
```

预期：`BUILD SUCCESS`。

- [ ] **步骤 3：用 local profile 启动后端做安全冒烟测试**

```powershell
java -jar admin-shell\target\admin-shell-1.0.0.jar --spring.profiles.active=local
```

预期：服务在 `18080` 端口启动。

- [ ] **步骤 4：验证插件发现**

```powershell
Invoke-RestMethod -Uri 'http://localhost:18080/api/plugins' -Method Get
```

预期：返回数据包含插件 ID `ai`。

- [ ] **步骤 5：验证 MCP 工具列表**

```powershell
Invoke-RestMethod -Uri 'http://localhost:18080/api/ai/mcp/tools' -Method Get
```

预期：返回数据包含 `echo`、`summarize`、`generate_plan`、`route_plugin_task`。

- [ ] **步骤 6：验证 MCP 工具调用**

```powershell
$body = @{ name='echo'; arguments=@{ text='hello' } } | ConvertTo-Json -Depth 5
Invoke-RestMethod -Uri 'http://localhost:18080/api/ai/mcp/tools/call' -Method Post -ContentType 'application/json' -Body $body
```

预期：返回 `success=true`，`content=hello`。

- [ ] **步骤 7：验证 A2A Agent Card**

```powershell
Invoke-RestMethod -Uri 'http://localhost:18080/api/ai/a2a/agent-card' -Method Get
```

预期：返回 `name=ai-agent`，并且 `capabilities.tools=true`。

- [ ] **步骤 8：验证 A2A tasks/send**

```powershell
$body = @{
  jsonrpc='2.0'
  id='task-1'
  method='tasks/send'
  params=@{ message=@{ role='user'; parts=@(@{ kind='text'; text='hello' }) } }
} | ConvertTo-Json -Depth 8
Invoke-RestMethod -Uri 'http://localhost:18080/api/ai/a2a/tasks/send' -Method Post -ContentType 'application/json' -Body $body
```

预期：返回 `jsonrpc=2.0`、`id=task-1`、`result.status=completed`。

---

## 自检

- 规格覆盖：starter 模块、AI 插件模块、MCP、A2A、技能注册、AgentScope 依赖、`.env` 模型配置和 API Key 写入配置均已覆盖。
- 占位检查：没有未定义的实现步骤。配置示例中的 API Key 位置说明为“从指定 `.env` 读取的明文值”，实际实现阶段按用户确认写入配置文件。
- 类型一致性：`AiSkill`、`AiSkillResult`、`AiSkillDescriptor`、`AiSkillRegistry`、`AiAgentProperties`、`McpController`、`A2aController` 命名在各任务中保持一致。
