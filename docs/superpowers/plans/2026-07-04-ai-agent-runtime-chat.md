# AI 智能体自动创建与大模型调用 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `modules/ai-agent-spring-boot-starter` 中实现启动时自动创建智能体运行时、自动注册技能，并通过 OpenAI-compatible 接口调用真实大模型回复。

**Architecture:** 在 starter 内增加 `AiAgentRuntime` 运行时模型和 `AiAgentRuntimeFactory`，由自动配置启动时创建运行时并记录状态；增加 `AiChatClient` 抽象和默认 `OpenAiCompatibleChatClient`；改造 `AiAgentService` 统一处理运行时状态、参数校验和模型调用。现有 MCP 与 A2A Controller 保持路径兼容，继续通过 `AiSkillRegistry` 和 `AiAgentService` 提供能力。

**Tech Stack:** Java 17, Spring Boot 3.4.5, Maven, JUnit 5, AssertJ, MockMvc, Spring `RestClient`, AgentScope Harness 2.0.0-RC3。

## Global Constraints

- 共享能力放在 `framework/`，业务插件放在 `modules/`，应用组装放在 `admin-shell/`。
- 不编辑 `target/`、`dist/` 等生成产物。
- 本仓库 `.md` 文档默认使用中文。
- 禁止使用 SQLite、H2 等本地数据库；本次不新增数据库结构。
- Service 层、Controller 层中所有需要注入 Bean 的类，均采用普通 public 构造器注入。
- Service 层、Controller 层 Bean 每个类只保留一个 public 构造器。
- 敏感配置不能复制到文档、日志、提交信息或聊天回复中。
- 新功能和行为变更按 TDD 实施：先写失败测试，再写最小实现。

---

## File Structure

- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/runtime/AiAgentRuntimeStatus.java`
  - 运行时状态枚举。
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/runtime/AiAgentRuntime.java`
  - 不可变运行时快照。
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/runtime/AiAgentRuntimeFactory.java`
  - 根据配置、技能注册表和 AgentScope Harness 创建运行时。
- Create: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/runtime/AiAgentRuntimeFactoryTest.java`
  - 运行时创建规则测试。
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/chat/AiChatClient.java`
  - 大模型调用接口。
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/chat/AiChatRequest.java`
  - 聊天请求模型。
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/chat/AiChatResponse.java`
  - 聊天响应模型。
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/chat/OpenAiCompatibleChatClient.java`
  - DashScope/OpenAI-compatible 默认实现。
- Create: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/chat/OpenAiCompatibleChatClientTest.java`
  - HTTP 请求和错误脱敏测试。
- Create: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/AiAgentServiceTest.java`
  - Agent 服务编排行为测试。
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/AiAgentService.java`
  - 从回显改为运行时校验和模型调用。
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfiguration.java`
  - 注册运行时、聊天客户端、RestClient Builder，并更新 `AiAgentService` Bean。
- Modify: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfigurationTest.java`
  - 验证新 Bean 和自定义覆盖规则。
- Modify: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/a2a/A2aControllerTest.java`
  - 更新 `/message` 预期为模型回复或未配置错误。
- Modify: `modules/ai-agent-spring-boot-starter/src/main/resources/application.yaml`
  - 移除真实敏感示例，改为环境变量占位。

---

### Task 1: Agent 运行时快照与创建工厂

**Files:**
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/runtime/AiAgentRuntimeStatus.java`
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/runtime/AiAgentRuntime.java`
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/runtime/AiAgentRuntimeFactory.java`
- Create: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/runtime/AiAgentRuntimeFactoryTest.java`

**Interfaces:**
- Consumes: `AiAgentProperties`, `AiSkillRegistry`
- Produces:
  - `AiAgentRuntimeStatus`
  - `AiAgentRuntime`
  - `AiAgentRuntimeFactory#create(AiAgentProperties properties, AiSkillRegistry skillRegistry): AiAgentRuntime`

- [ ] **Step 1: Write the failing test**

```java
package com.zimo.starter.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.starter.ai.AiAgentProperties;
import com.zimo.starter.ai.skill.AiSkillRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiAgentRuntimeFactoryTest {
    @Test
    void returnsNotConfiguredWhenApiKeyIsBlank() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setName("ai-agent");
        properties.setModelName("qwen-plus");
        properties.setApiKey("");
        AiSkillRegistry registry = new AiSkillRegistry(List.of());

        AiAgentRuntime runtime = new AiAgentRuntimeFactory().create(properties, registry);

        assertThat(runtime.status()).isEqualTo(AiAgentRuntimeStatus.NOT_CONFIGURED);
        assertThat(runtime.agentName()).isEqualTo("ai-agent");
        assertThat(runtime.modelName()).isEqualTo("qwen-plus");
        assertThat(runtime.message()).contains("AI 服务未配置");
        assertThat(runtime.message()).doesNotContain("Bearer");
    }

    @Test
    void returnsInitializationFailedWhenHarnessAgentClassIsMissing() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setApiKey("dummy-api-key");
        AiSkillRegistry registry = new AiSkillRegistry(List.of());
        AiAgentRuntimeFactory factory = new AiAgentRuntimeFactory() {
            @Override
            protected String harnessAgentClassName() {
                return "missing.HarnessAgent";
            }
        };

        AiAgentRuntime runtime = factory.create(properties, registry);

        assertThat(runtime.status()).isEqualTo(AiAgentRuntimeStatus.INITIALIZATION_FAILED);
        assertThat(runtime.message()).contains("AgentScope Java 依赖未加载");
        assertThat(runtime.message()).doesNotContain("dummy-api-key");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiAgentRuntimeFactoryTest test
```

Expected: FAIL because `com.zimo.starter.ai.runtime` classes do not exist.

- [ ] **Step 3: Write minimal implementation**

Create `AiAgentRuntimeStatus.java`:

```java
package com.zimo.starter.ai.runtime;

public enum AiAgentRuntimeStatus {
    READY,
    NOT_CONFIGURED,
    INITIALIZATION_FAILED
}
```

Create `AiAgentRuntime.java`:

```java
package com.zimo.starter.ai.runtime;

import com.zimo.starter.ai.skill.AiSkillDescriptor;
import java.util.List;

public record AiAgentRuntime(
        String agentName,
        String modelName,
        String modelType,
        List<AiSkillDescriptor> skillDescriptors,
        AiAgentRuntimeStatus status,
        String message) {
}
```

Create `AiAgentRuntimeFactory.java`:

```java
package com.zimo.starter.ai.runtime;

import com.zimo.starter.ai.AiAgentProperties;
import com.zimo.starter.ai.skill.AiSkillRegistry;
import java.nio.file.Paths;

public class AiAgentRuntimeFactory {
    public AiAgentRuntime create(AiAgentProperties properties, AiSkillRegistry skillRegistry) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return runtime(properties, skillRegistry, AiAgentRuntimeStatus.NOT_CONFIGURED,
                    "AI 服务未配置，请配置 ai.agent.api-key 或 DASHSCOPE_API_KEY");
        }
        try {
            Class<?> agentClass = Class.forName(harnessAgentClassName());
            Object builder = agentClass.getMethod("builder").invoke(null);
            builder.getClass().getMethod("name", String.class).invoke(builder, properties.getName());
            builder.getClass().getMethod("sysPrompt", String.class)
                    .invoke(builder, "你是 Production Studio AI 智能体，请基于用户请求给出准确、简洁的回答。");
            builder.getClass().getMethod("model", String.class)
                    .invoke(builder, modelReference(properties));
            builder.getClass().getMethod("workspace", java.nio.file.Path.class)
                    .invoke(builder, Paths.get(".agentscope/workspace"));
            builder.getClass().getMethod("build").invoke(builder);
            return runtime(properties, skillRegistry, AiAgentRuntimeStatus.READY, "AI 智能体已初始化");
        } catch (ClassNotFoundException e) {
            return runtime(properties, skillRegistry, AiAgentRuntimeStatus.INITIALIZATION_FAILED,
                    "AgentScope Java 依赖未加载，请检查 agentscope-harness 依赖");
        } catch (Exception e) {
            return runtime(properties, skillRegistry, AiAgentRuntimeStatus.INITIALIZATION_FAILED,
                    "AgentScope Java 初始化失败: " + safeMessage(e));
        }
    }

    protected String harnessAgentClassName() {
        return "io.agentscope.harness.agent.HarnessAgent";
    }

    private AiAgentRuntime runtime(
            AiAgentProperties properties,
            AiSkillRegistry skillRegistry,
            AiAgentRuntimeStatus status,
            String message) {
        return new AiAgentRuntime(
                properties.getName(),
                properties.getModelName(),
                properties.getModelType(),
                skillRegistry.list(),
                status,
                message);
    }

    private String modelReference(AiAgentProperties properties) {
        if (properties.getModelType() == null || properties.getModelType().isBlank()) {
            return properties.getModelName();
        }
        if ("dashscope_chat".equals(properties.getModelType())) {
            return "dashscope:" + properties.getModelName();
        }
        return properties.getModelType() + ":" + properties.getModelName();
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiAgentRuntimeFactoryTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/runtime modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/runtime
git commit -m "feat: add ai agent runtime factory"
```

---

### Task 2: OpenAI-compatible 大模型客户端

**Files:**
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/chat/AiChatClient.java`
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/chat/AiChatRequest.java`
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/chat/AiChatResponse.java`
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/chat/OpenAiCompatibleChatClient.java`
- Create: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/chat/OpenAiCompatibleChatClientTest.java`

**Interfaces:**
- Consumes: `AiAgentProperties`, Spring `RestClient.Builder`
- Produces:
  - `AiChatClient#chat(AiChatRequest request): AiChatResponse`
  - `AiChatRequest`
  - `AiChatResponse`

- [ ] **Step 1: Write the failing test**

```java
package com.zimo.starter.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.zimo.starter.ai.AiAgentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleChatClientTest {
    @Test
    void postsOpenAiCompatibleChatRequestAndExtractsContent() {
        AiAgentProperties properties = properties("dummy-api-key");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(properties, builder);

        server.expect(requestTo("https://dashscope.example/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer dummy-api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("qwen-plus"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("你好"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"模型回复"}}]}
                        """, MediaType.APPLICATION_JSON));

        AiChatResponse response = client.chat(new AiChatRequest(
                "ai-agent", "你好", "s1", "qwen-plus", 0.7, 2000));

        assertThat(response.success()).isTrue();
        assertThat(response.content()).isEqualTo("模型回复");
        server.verify();
    }

    @Test
    void returnsSanitizedFailureWhenHttpCallFails() {
        AiAgentProperties properties = properties("dummy-api-key");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(properties, builder);

        server.expect(requestTo("https://dashscope.example/v1/chat/completions"))
                .andRespond(withServerError());

        AiChatResponse response = client.chat(new AiChatRequest(
                "ai-agent", "你好", "s1", "qwen-plus", 0.7, 2000));

        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("大模型调用失败");
        assertThat(response.errorMessage()).doesNotContain("dummy-api-key");
        server.verify();
    }

    private AiAgentProperties properties(String apiKey) {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setBaseUrl("https://dashscope.example/v1");
        properties.setApiKey(apiKey);
        properties.setModelName("qwen-plus");
        return properties;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=OpenAiCompatibleChatClientTest test
```

Expected: FAIL because `com.zimo.starter.ai.chat` classes do not exist.

- [ ] **Step 3: Write minimal implementation**

Create `AiChatClient.java`:

```java
package com.zimo.starter.ai.chat;

@FunctionalInterface
public interface AiChatClient {
    AiChatResponse chat(AiChatRequest request);
}
```

Create `AiChatRequest.java`:

```java
package com.zimo.starter.ai.chat;

public record AiChatRequest(
        String agentName,
        String message,
        String sessionId,
        String modelName,
        double temperature,
        int maxTokens) {
}
```

Create `AiChatResponse.java`:

```java
package com.zimo.starter.ai.chat;

public record AiChatResponse(boolean success, String content, String errorMessage) {
    public static AiChatResponse ok(String content) {
        return new AiChatResponse(true, content, null);
    }

    public static AiChatResponse fail(String errorMessage) {
        return new AiChatResponse(false, null, errorMessage);
    }
}
```

Create `OpenAiCompatibleChatClient.java`:

```java
package com.zimo.starter.ai.chat;

import com.zimo.starter.ai.AiAgentProperties;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class OpenAiCompatibleChatClient implements AiChatClient {
    private final AiAgentProperties properties;
    private final RestClient restClient;

    public OpenAiCompatibleChatClient(AiAgentProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(trimTrailingSlash(properties.getBaseUrl())).build();
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        try {
            Map<String, Object> body = Map.of(
                    "model", request.modelName(),
                    "messages", List.of(Map.of("role", "user", "content", request.message())),
                    "temperature", request.temperature(),
                    "max_tokens", request.maxTokens());
            Map<String, Object> response = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            String content = extractContent(response);
            if (content == null || content.isBlank()) {
                return AiChatResponse.fail("大模型调用失败：响应内容为空");
            }
            return AiChatResponse.ok(content);
        } catch (RestClientException e) {
            return AiChatResponse.fail("大模型调用失败：" + safeMessage(e));
        } catch (Exception e) {
            return AiChatResponse.fail("大模型响应解析失败：" + safeMessage(e));
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object choicesValue = response.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            return null;
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choice)) {
            return null;
        }
        Object messageValue = choice.get("message");
        if (!(messageValue instanceof Map<?, ?> message)) {
            return null;
        }
        Object content = message.get("content");
        return content == null ? null : String.valueOf(content);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=OpenAiCompatibleChatClientTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/chat modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/chat
git commit -m "feat: add ai chat client"
```

---

### Task 3: Agent 服务编排真实聊天

**Files:**
- Create: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/AiAgentServiceTest.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/AiAgentService.java`

**Interfaces:**
- Consumes: `AiAgentRuntime`, `AiChatClient`, `AiSkillRegistry`, `AiAgentProperties`
- Produces:
  - `AiAgentService#chat(String message, String sessionId): AiAgentReply`
  - `AiAgentService#reply(String message): AiAgentReply`
  - `AiAgentService#skillRegistry(): AiSkillRegistry`

- [ ] **Step 1: Write the failing test**

```java
package com.zimo.starter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.starter.ai.chat.AiChatClient;
import com.zimo.starter.ai.chat.AiChatResponse;
import com.zimo.starter.ai.runtime.AiAgentRuntime;
import com.zimo.starter.ai.runtime.AiAgentRuntimeStatus;
import com.zimo.starter.ai.skill.AiSkillRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AiAgentServiceTest {
    @Test
    void returnsNotConfiguredMessageWithoutCallingChatClient() {
        AtomicInteger calls = new AtomicInteger();
        AiAgentService service = service(runtime(AiAgentRuntimeStatus.NOT_CONFIGURED, "AI 服务未配置"),
                request -> {
                    calls.incrementAndGet();
                    return AiChatResponse.ok("should not call");
                });

        AiAgentReply reply = service.chat("hello", "s1");

        assertThat(reply.agent()).isEqualTo("ai-agent");
        assertThat(reply.content()).contains("AI 服务未配置");
        assertThat(calls).hasValue(0);
    }

    @Test
    void rejectsBlankMessageWithoutCallingChatClient() {
        AtomicInteger calls = new AtomicInteger();
        AiAgentService service = service(runtime(AiAgentRuntimeStatus.READY, "ready"),
                request -> {
                    calls.incrementAndGet();
                    return AiChatResponse.ok("should not call");
                });

        AiAgentReply reply = service.chat("   ", "s1");

        assertThat(reply.content()).contains("消息内容不能为空");
        assertThat(calls).hasValue(0);
    }

    @Test
    void returnsModelContentWhenRuntimeIsReady() {
        AiAgentService service = service(runtime(AiAgentRuntimeStatus.READY, "ready"),
                request -> AiChatResponse.ok("模型回复"));

        AiAgentReply reply = service.reply("hello");

        assertThat(reply.agent()).isEqualTo("ai-agent");
        assertThat(reply.content()).isEqualTo("模型回复");
    }

    @Test
    void returnsReadableErrorWhenChatClientFails() {
        AiAgentService service = service(runtime(AiAgentRuntimeStatus.READY, "ready"),
                request -> AiChatResponse.fail("大模型调用失败：500"));

        AiAgentReply reply = service.chat("hello", "s1");

        assertThat(reply.content()).contains("大模型调用失败");
    }

    private AiAgentService service(AiAgentRuntime runtime, AiChatClient chatClient) {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setName("ai-agent");
        properties.setModelName("qwen-plus");
        properties.setTemperature(0.7);
        properties.setMaxTokens(2000);
        return new AiAgentService(properties, new AiSkillRegistry(List.of()), runtime, chatClient);
    }

    private AiAgentRuntime runtime(AiAgentRuntimeStatus status, String message) {
        return new AiAgentRuntime("ai-agent", "qwen-plus", "dashscope_chat", List.of(), status, message);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiAgentServiceTest test
```

Expected: FAIL because `AiAgentService` does not have the new constructor and `chat` method.

- [ ] **Step 3: Write minimal implementation**

Replace `AiAgentService.java` with:

```java
package com.zimo.starter.ai;

import com.zimo.starter.ai.chat.AiChatClient;
import com.zimo.starter.ai.chat.AiChatRequest;
import com.zimo.starter.ai.chat.AiChatResponse;
import com.zimo.starter.ai.runtime.AiAgentRuntime;
import com.zimo.starter.ai.runtime.AiAgentRuntimeStatus;
import com.zimo.starter.ai.skill.AiSkillRegistry;

public class AiAgentService {
    private final AiAgentProperties properties;
    private final AiSkillRegistry skillRegistry;
    private final AiAgentRuntime runtime;
    private final AiChatClient chatClient;

    public AiAgentService(
            AiAgentProperties properties,
            AiSkillRegistry skillRegistry,
            AiAgentRuntime runtime,
            AiChatClient chatClient) {
        this.properties = properties;
        this.skillRegistry = skillRegistry;
        this.runtime = runtime;
        this.chatClient = chatClient;
    }

    public AiAgentReply reply(String message) {
        return chat(message, null);
    }

    public AiAgentReply chat(String message, String sessionId) {
        if (message == null || message.isBlank()) {
            return new AiAgentReply(properties.getName(), "消息内容不能为空");
        }
        if (runtime.status() == AiAgentRuntimeStatus.NOT_CONFIGURED) {
            return new AiAgentReply(properties.getName(), runtime.message());
        }
        if (runtime.status() == AiAgentRuntimeStatus.INITIALIZATION_FAILED) {
            return new AiAgentReply(properties.getName(), "AI 智能体初始化失败：" + runtime.message());
        }
        AiChatResponse response = chatClient.chat(new AiChatRequest(
                properties.getName(),
                message,
                sessionId,
                properties.getModelName(),
                properties.getTemperature(),
                properties.getMaxTokens()));
        if (!response.success()) {
            return new AiAgentReply(properties.getName(), response.errorMessage());
        }
        return new AiAgentReply(properties.getName(), response.content());
    }

    public AiSkillRegistry skillRegistry() {
        return skillRegistry;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiAgentServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/AiAgentService.java modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/AiAgentServiceTest.java
git commit -m "feat: route ai agent chat through runtime"
```

---

### Task 4: Spring Boot 自动配置接入运行时和聊天客户端

**Files:**
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfiguration.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfigurationTest.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/resources/application.yaml`

**Interfaces:**
- Consumes: Task 1-3 classes
- Produces:
  - `AiAgentRuntimeFactory` Bean
  - `AiAgentRuntime` Bean
  - `RestClient.Builder` Bean when missing
  - `AiChatClient` Bean when missing
  - Updated `AiAgentService` Bean

- [ ] **Step 1: Write the failing auto-configuration tests**

Append these tests to `AiAgentAutoConfigurationTest.java`:

```java
@Test
void createsRuntimeChatClientAndAgentService() {
    contextRunner
            .withPropertyValues(
                    "ai.agent.enabled=true",
                    "ai.agent.api-key=")
            .run(context -> {
                assertThat(context).hasSingleBean(com.zimo.starter.ai.runtime.AiAgentRuntimeFactory.class);
                assertThat(context).hasSingleBean(com.zimo.starter.ai.runtime.AiAgentRuntime.class);
                assertThat(context).hasSingleBean(com.zimo.starter.ai.chat.AiChatClient.class);
                assertThat(context).hasSingleBean(com.zimo.starter.ai.AiAgentService.class);
                assertThat(context.getBean(com.zimo.starter.ai.runtime.AiAgentRuntime.class).status())
                        .isEqualTo(com.zimo.starter.ai.runtime.AiAgentRuntimeStatus.NOT_CONFIGURED);
            });
}

@Test
void backsOffWhenCustomChatClientExists() {
    contextRunner
            .withBean(com.zimo.starter.ai.chat.AiChatClient.class,
                    () -> request -> com.zimo.starter.ai.chat.AiChatResponse.ok("custom"))
            .withPropertyValues("ai.agent.enabled=true")
            .run(context -> {
                com.zimo.starter.ai.AiAgentService service =
                        context.getBean(com.zimo.starter.ai.AiAgentService.class);
                assertThat(service.reply("hello").content()).contains("AI 服务未配置");
                assertThat(context).hasSingleBean(com.zimo.starter.ai.chat.AiChatClient.class);
            });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiAgentAutoConfigurationTest test
```

Expected: FAIL because auto-configuration does not create the new runtime and chat client beans, and `aiAgentService` uses the old constructor.

- [ ] **Step 3: Update auto-configuration**

Modify `AiAgentAutoConfiguration.java` imports and bean methods to include:

```java
import com.zimo.starter.ai.chat.AiChatClient;
import com.zimo.starter.ai.chat.OpenAiCompatibleChatClient;
import com.zimo.starter.ai.runtime.AiAgentRuntime;
import com.zimo.starter.ai.runtime.AiAgentRuntimeFactory;
import org.springframework.web.client.RestClient;
```

Add these beans before `aiAgentService`:

```java
@Bean
@ConditionalOnMissingBean
public AiAgentRuntimeFactory aiAgentRuntimeFactory() {
    return new AiAgentRuntimeFactory();
}

@Bean
@ConditionalOnMissingBean
public AiAgentRuntime aiAgentRuntime(
        AiAgentProperties properties,
        AiSkillRegistry skillRegistry,
        AiAgentRuntimeFactory runtimeFactory) {
    return runtimeFactory.create(properties, skillRegistry);
}

@Bean
@ConditionalOnMissingBean
public RestClient.Builder restClientBuilder() {
    return RestClient.builder();
}

@Bean
@ConditionalOnMissingBean
public AiChatClient aiChatClient(AiAgentProperties properties, RestClient.Builder restClientBuilder) {
    return new OpenAiCompatibleChatClient(properties, restClientBuilder);
}
```

Replace the existing `aiAgentService` method with:

```java
@Bean
@ConditionalOnMissingBean
public AiAgentService aiAgentService(
        AiAgentProperties properties,
        AiSkillRegistry skillRegistry,
        AiAgentRuntime runtime,
        AiChatClient chatClient) {
    return new AiAgentService(properties, skillRegistry, runtime, chatClient);
}
```

Update `application.yaml` so `api-key` uses an environment placeholder:

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

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiAgentAutoConfigurationTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfiguration.java modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfigurationTest.java modules/ai-agent-spring-boot-starter/src/main/resources/application.yaml
git commit -m "feat: wire ai runtime auto configuration"
```

---

### Task 5: A2A 兼容接口验收与窄范围验证

**Files:**
- Modify: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/a2a/A2aControllerTest.java`
- No production code expected unless this test exposes an integration issue.

**Interfaces:**
- Consumes: `A2aController`, `AiAgentService`, auto-configured beans
- Produces: verified current endpoint behavior

- [ ] **Step 1: Update A2A failing test for new behavior**

Replace `repliesToMessage` in `A2aControllerTest.java` with:

```java
@Test
void returnsNotConfiguredMessageWhenApiKeyIsMissing() throws Exception {
    mockMvc.perform(post("/api/ai/a2a/message")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"message":"hello"}
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.agent").value("ai-agent"))
            .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("AI 服务未配置")));
}
```

This controller test uses the not-configured path as the stable compatibility check because it does not make network calls and still verifies the endpoint delegates to the new `AiAgentService` behavior.

- [ ] **Step 2: Run controller test to verify it fails or exposes missing wiring**

Run:

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=A2aControllerTest test
```

Expected: PASS after Task 4.

- [ ] **Step 3: Run all starter tests**

Run:

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -am test
```

Expected: PASS.

- [ ] **Step 4: Run broader package verification**

Run:

```powershell
mvn -pl admin-shell -am package
```

Expected: PASS, unless unrelated dirty-worktree changes outside this task already break the build. If unrelated failures appear, record the failing module and test name without reverting other user changes.

- [ ] **Step 5: Commit**

```powershell
git add modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/a2a/A2aControllerTest.java
git commit -m "test: verify ai a2a runtime response"
```

---

## Self-Review

- Spec coverage: Tasks 1-4 implement automatic runtime creation, skill registration reuse, OpenAI-compatible model calls, service behavior, auto-configuration, and sensitive config cleanup. Task 5 verifies current A2A compatibility.
- Placeholder scan: This plan avoids undefined placeholders and gives concrete class names, method signatures, paths, and commands.
- Type consistency: `AiAgentRuntime`, `AiChatClient`, `AiChatRequest`, `AiChatResponse`, and the new `AiAgentService` constructor are defined before later tasks use them.
- Scope check: The plan stays within `modules/ai-agent-spring-boot-starter` and does not add database, frontend, multi-turn memory, or automatic tool-calling behavior.
