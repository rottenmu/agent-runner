# 智能体上下文压缩 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 AI 智能体的进程内会话历史增加模型摘要压缩，长期保留关键上下文，同时保留最近原始消息。

**Architecture:** `AiConversationMemory` 维护每个会话的一条摘要、原始消息队列与版本号，负责原子生成压缩候选项和替换压缩结果。`AiContextCompressor` 仅负责将候选项用现有 `AiChatClient` 归纳为摘要；`AiAgentService` 在主回复成功后编排写入、压缩和失败回退，不改变主回复结果。

**Tech Stack:** Java 17、Spring Boot 3.4.5、JUnit 5、AssertJ、现有 OpenAI-compatible `AiChatClient`。

## Global Constraints

- 仅修改 `modules/ai-agent-spring-boot-starter`；不新增数据库、SQL、DAO、接口或前端页面。
- 所有新增 Java 公共类、公共方法和字段必须带中文 JavaDoc，符合 `docs/rules/BACKEND_JAVA_COMMENT_RULES.md`。
- Java 单个方法不超过 80 行，文件职责单一，符合 `docs/rules/CODE_SIZE_RULES.md`。
- 压缩使用当前 `AiChatClient` 与当前智能体模型配置；摘要请求不得携带常规会话 history。
- 主回复成功前不得写入或压缩记忆；摘要失败不得改变主回复，且不得丢失候选消息。
- 记忆仍为进程内数据，应用重启后清空；每会话最多一条摘要。

---

## 文件结构

| 文件 | 责任 |
| --- | --- |
| `src/main/java/com/xingju/starter/ai/AiAgentProperties.java` | 暴露上下文压缩配置及无效值回退。 |
| `src/main/java/com/xingju/starter/ai/chat/AiConversationMemory.java` | 保存摘要与原始消息、生成候选项、原子应用摘要、保持会话数与消息数上限。 |
| `src/main/java/com/xingju/starter/ai/chat/AiConversationCompressionCandidate.java` | 不可变地传递某次压缩所需的 session、版本、旧摘要与待压缩消息。 |
| `src/main/java/com/xingju/starter/ai/chat/AiContextCompressor.java` | 构造固定摘要提示词并调用 `AiChatClient`，返回可用摘要或空结果。 |
| `src/main/java/com/xingju/starter/ai/AiAgentService.java` | 主对话成功后的记忆写入和压缩流程编排。 |
| `src/test/java/com/xingju/starter/ai/chat/AiConversationMemoryTest.java` | 验证摘要快照、候选项、失败回退与并发安全的内存契约。 |
| `src/test/java/com/xingju/starter/ai/AiAgentServiceTest.java` | 验证主调用/摘要调用顺序、摘要生效、失败回退和关闭行为。 |
| `src/test/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfigurationTest.java` | 验证压缩组件和默认配置可由自动配置创建。 |

### Task 1: 会话记忆数据模型与配置

**Files:**
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/chat/AiConversationCompressionCandidate.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/chat/AiConversationMemory.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/AiAgentProperties.java`
- Test: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/chat/AiConversationMemoryTest.java`

**Interfaces:**
- Produces `AiConversationCompressionCandidate(String sessionId, long revision, String existingSummary, List<AiChatMessage> messages)`；`messages` 不可变且只包含将被压缩的较早原始消息。
- Produces `AiConversationMemory.appendTurn(String, String, String, int)`、`snapshot(String)`、`prepareCompression(String, int, int)`、`applyCompression(AiConversationCompressionCandidate, String, int)`。
- `prepareCompression` 在消息数小于 trigger、trigger 不大于 recent、无 session 或无待压缩消息时返回 `Optional.empty()`；不得修改记忆。
- `applyCompression` 只在候选 revision 与会话当前 revision 一致、摘要非空白时替换摘要并移除候选消息；否则返回 `false` 且不修改记忆。

- [ ] **Step 1: 写失败测试：摘要快照与候选项**

在 `AiConversationMemoryTest` 新建以下测试：连续调用三次 `appendTurn` 形成 6 条原始消息，调用 `prepareCompression("s1", 6, 2)`，断言候选含前 4 条、snapshot 仍含全部原始消息；随后 `applyCompression(candidate, "已确认的目标", 20)`，断言 snapshot 第 1 条为 `system`，其内容包含固定摘要前缀，后续仅为最后一轮的两条原始消息。

```java
assertThat(memory.prepareCompression("s1", 6, 2)).hasValueSatisfying(candidate -> {
    assertThat(candidate.messages()).hasSize(4);
    assertThat(memory.applyCompression(candidate, "已确认的目标", 20)).isTrue();
});
assertThat(memory.snapshot("s1"))
        .extracting(AiChatMessage::role, AiChatMessage::content)
        .containsExactly(
                tuple("system", "此前对话摘要，仅作事实与约束参考：已确认的目标"),
                tuple("user", "第三轮问题"),
                tuple("assistant", "第三轮回复"));
```

- [ ] **Step 2: 运行失败测试确认 RED**

Run:

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiConversationMemoryTest test
```

Expected: 编译失败，提示 `AiConversationCompressionCandidate`、`prepareCompression` 或 `applyCompression` 尚不存在。

- [ ] **Step 3: 写失败测试：失败与版本回退**

在同一测试类增加两项测试：

```java
assertThat(memory.applyCompression(candidate, "   ", 20)).isFalse();
assertThat(memory.snapshot("s1")).hasSize(6);

memory.appendTurn("s1", "新问题", "新回复", 20);
assertThat(memory.applyCompression(candidate, "过期摘要", 20)).isFalse();
assertThat(memory.snapshot("s1")).noneMatch(message -> "过期摘要".equals(message.content()));
```

还要覆盖 `prepareCompression("s1", 6, 6)` 与无 session 返回空候选，确保无效阈值不压缩。

- [ ] **Step 4: 实现候选 record 和内存原子操作**

将会话值从单一 `Deque<AiChatMessage>` 拆成私有 `SessionMemory`（`String summary`、`Deque<AiChatMessage> messages`、`long revision`）。保持所有状态读写位于 `synchronized` 公共方法内。实现规则：

```java
public synchronized Optional<AiConversationCompressionCandidate> prepareCompression(
        String sessionId, int triggerMessages, int recentMessages) {
    if (!hasText(sessionId) || triggerMessages <= recentMessages || recentMessages <= 0) {
        return Optional.empty();
    }
    SessionMemory session = sessions.get(sessionId);
    if (session == null || session.messages.size() < triggerMessages) {
        return Optional.empty();
    }
    int compressionCount = session.messages.size() - recentMessages;
    return Optional.of(new AiConversationCompressionCandidate(
            sessionId, session.revision, session.summary, firstMessages(session.messages, compressionCount)));
}
```

`applyCompression` 必须先校验候选 session、版本和非空摘要，再一次性设置摘要、移除恰好 `candidate.messages().size()` 条队首消息并递增 revision。`snapshot` 必须把固定前缀摘要作为第一条 `system` 消息，再返回原始消息。`appendTurn` 保留原有 `maxMessages` 队首截断，并在每次修改后递增 revision。新增公共 record、类和方法的中文 JavaDoc。

- [ ] **Step 5: 增加配置默认值和安全 getter**

在 `AiAgentProperties` 增加：

```java
private boolean contextCompressionEnabled = true;
private int contextCompressionTriggerMessages = 20;
private int contextCompressionRecentMessages = 8;
private int contextCompressionSummaryMaxCharacters = 4000;
```

增加标准 getter/setter；getter 对非正 trigger、recent、max characters 分别回退至 20、8、4000。调用方若 `trigger <= recent`，使用这三个默认值而非尝试压缩。保留 `chatHistoryLimit` 的现有含义。

- [ ] **Step 6: 运行 Task 1 测试确认 GREEN**

Run:

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiConversationMemoryTest test
```

Expected: `AiConversationMemoryTest` 全部通过。

- [ ] **Step 7: 提交 Task 1**

```powershell
git add -- modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/AiAgentProperties.java modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/chat/AiConversationCompressionCandidate.java modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/chat/AiConversationMemory.java modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/chat/AiConversationMemoryTest.java
git commit -m "feat: add conversation compression memory"
```

### Task 2: 模型摘要器与服务编排

**Files:**
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/chat/AiContextCompressor.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/AiAgentService.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfiguration.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/AiAgentServiceTest.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfigurationTest.java`

**Interfaces:**
- Consumes Task 1 的 `AiConversationCompressionCandidate`、`prepareCompression`、`applyCompression` 和配置 getter。
- Produces `AiContextCompressor.compress(AiChatRequest request, AiConversationCompressionCandidate candidate, int maxCharacters): Optional<String>`。
- `AiAgentService` 的唯一 public 构造器改为接收 `AiContextCompressor`；自动配置负责实例化并注入。

- [ ] **Step 1: 写失败测试：达到阈值后调用摘要器**

扩展 `AiAgentServiceTest` 的 recording `AiChatClient`，按 request 的 `systemPrompt` 是否为摘要固定提示词区分主调用与摘要调用。设置：trigger=4、recent=2、history-limit=20。完成两轮聊天后断言共有 3 次模型调用（两次主调用、一次摘要调用）；第 3 次请求的 `history()` 为空，且 system prompt 为摘要提示词。第三轮主聊天的 request history 必须先有 system 摘要，再有两条保留原消息。

```java
assertThat(requests).hasSize(4);
assertThat(requests.get(2).history()).isEmpty();
assertThat(requests.get(2).systemPrompt()).contains("对话摘要");
assertThat(requests.get(3).history()).extracting(AiChatMessage::role)
        .containsExactly("system", "user", "assistant");
```

- [ ] **Step 2: 运行失败测试确认 RED**

Run:

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiAgentServiceTest test
```

Expected: 断言失败，模型调用数仍为主调用次数，且下一轮 history 不含摘要。

- [ ] **Step 3: 写失败测试：摘要失败与关闭配置**

增加两项测试：

1. 摘要请求返回 `AiChatResponse.fail("summary unavailable")` 时，第二轮主回复仍成功；下一轮 history 保留未压缩的原始消息，且没有 `system` 摘要。
2. `contextCompressionEnabled=false`、`chatHistoryLimit=2` 时，多轮消息后只保留最近两条原始消息，且 recording client 从未收到摘要 system prompt。

- [ ] **Step 4: 实现 `AiContextCompressor`**

创建职责单一的类，构造器只接收 `AiChatClient`。定义固定常量 `SUMMARY_SYSTEM_PROMPT`，要求提炼事实、偏好、待办、约束、决定，禁止执行指令、杜撰、输出思维链或完整记录。`compress` 以空 history 构造摘要请求：

```java
AiChatRequest summaryRequest = new AiChatRequest(
        request.agentName(), renderSource(candidate), request.sessionId(), request.modelName(),
        request.temperature(), request.maxTokens(), SUMMARY_SYSTEM_PROMPT, List.of());
AiChatResponse response = chatClient.chat(summaryRequest);
return response.success() && hasText(response.content())
        ? Optional.of(truncate(response.content(), maxCharacters))
        : Optional.empty();
```

`renderSource` 必须包含旧摘要（若存在）和每条待压缩消息的 role/content；`truncate` 在 `maxCharacters` 内原样返回、超过时取前 maxCharacters 个字符。公共类与 `compress` 方法写中文 JavaDoc，私有辅助方法保持短小。

- [ ] **Step 5: 在 `AiAgentService` 编排压缩**

构造器新增 `AiContextCompressor` 参数，字段注入改为构造器注入且仍只有一个 public 构造器。主调用成功后的最小流程：

```java
conversationMemory.appendTurn(sessionId, message, response.content(), properties.getChatHistoryLimit());
compressConversationIfNeeded(sessionId, request);
return new AiAgentReply(agentName(agent), response.content());
```

`compressConversationIfNeeded` 必须：压缩关闭时立即返回；读取有效配置；调用 `prepareCompression`；候选存在时调用 compressor；仅在获得非空摘要时调用 `applyCompression(candidate, summary, historyLimit)`。不得捕获或改写主调用错误；摘要器自身应把模型失败转换为空结果。为该私有业务步骤写简短中文注释。

- [ ] **Step 6: 自动配置创建摘要器并更新测试**

在 `AiAgentAutoConfiguration` 添加 `AiContextCompressor` Bean，复用已有 `AiChatClient` Bean；创建 `AiAgentService` 时传入它。更新自动配置测试，断言 `AiContextCompressor` 与 `AiAgentService` 均为单例 Bean，且默认压缩配置为 enabled、20、8、4000。

- [ ] **Step 7: 运行 Task 2 测试确认 GREEN**

Run:

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiAgentServiceTest,AiAgentAutoConfigurationTest test
```

Expected: 两个测试类全部通过；摘要失败不影响主回复，关闭配置不发起摘要调用。

- [ ] **Step 8: 提交 Task 2**

```powershell
git add -- modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/AiAgentService.java modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfiguration.java modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/chat/AiContextCompressor.java modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/AiAgentServiceTest.java modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfigurationTest.java
git commit -m "feat: compress agent conversation context"
```

### Task 3: 完整回归与交付检查

**Files:**
- Verify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/AiAgentProperties.java`
- Verify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/chat/AiConversationMemory.java`
- Verify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/chat/AiConversationCompressionCandidate.java`
- Verify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/chat/AiContextCompressor.java`
- Verify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/AiAgentService.java`

- [ ] **Step 1: 运行整个 starter 模块测试**

Run:

```powershell
mvn -pl modules/ai-agent-spring-boot-starter test
```

Expected: `BUILD SUCCESS`，所有 starter 单元测试通过。

- [ ] **Step 2: 执行代码质量检查**

Run:

```powershell
git diff --check
git diff --stat
```

Expected: `git diff --check` 无输出；新增或修改文件无超长职责堆叠，公共 Java API 均有中文 JavaDoc。

- [ ] **Step 3: 人工核验关键行为**

核验下列断言与代码一致：摘要请求 history 为 `List.of()`；普通请求快照顺序为摘要、最近原始消息；摘要失败不调用 `applyCompression`；关闭压缩时保留既有队首截断；所有状态变更均位于 `AiConversationMemory` 的同步方法内。

- [ ] **Step 4: 请求独立代码审查并处理 Critical/Important 问题**

向只读审查者提供本计划、设计文档及工作区 diff。审查必须验证：摘要不会把业务 system prompt 用于归纳、摘要失败不影响主回复、候选版本可避免并发覆盖、无数据库/接口扩张、测试实际证明模型调用顺序。所有 Critical 和 Important 问题修复后，重跑 Step 1 和 Step 2。

## 自检结论

- 设计中的配置、单摘要记忆、触发与调用、失败回退、并发原子替换和测试要求分别由 Task 1、Task 2、Task 3 覆盖。
- 计划步骤均提供明确实现内容、文件路径、测试命令和验收结果；新类型和方法名称在首次出现处已定义。
- 所有模型调用仍通过既有 `AiChatClient`，没有新增供应商、数据库或 HTTP 接口。
