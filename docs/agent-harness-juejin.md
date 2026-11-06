# 拆解一个生产级 AI Agent 运行时：配置驱动装配、双通道对话、可观测闭环

> 一个 171 个类的 Spring Boot 模块，如何把「数据库里的一行配置」变成「一个能跑、能观测、能治理、挂不掉的 Agent」。

## 引子：Agent 落地的难点，从来不是"把模型调通"

几乎所有团队都经历过这个阶段：三天写个 Demo，`curl` 一下模型，能聊天了，大家很兴奋。

然后进入生产，问题全来了：

- **配置散在代码里**：换个 persona 要改代码、打包、重启。运营想调个提示词，得排期等发版。
- **对话一挂就整个挂**：记忆服务抖动一下，用户看到的是"请求失败"，而不是"回复少了记忆增强"。
- **出问题查不到**：用户说"昨天那个回答是错的"，你翻遍日志只知道"调了一次模型"，不知道中间走了哪几步工具、超时了几秒、token 花了多少。
- **改一个接口，另一个接口偷偷裸奔**：新增了一条流式通道，忘了加权限校验，它就成了绕过安全管控的旁路。
- **新增接口莫名 404**：Bean 没注册，启动不报错，运行时才发现端点不存在。

`agent-harness` 这个模块，就是把这些"生产问题"逐个解决后的产物。它基于 **AgentScope Java v2（HarnessAgent）**，Java 17 + Spring Boot 3.4.5，一共 171 个源文件。

这篇文章不讲"怎么调通模型"，只讲**一个 Agent 运行时真正难的地方**——以及代码里那些看起来啰嗦、实则是踩坑换来的约束。

---

## 一、先看全景：四层结构

整个模块可以拆成四层，自下而上：

```text
┌──────────────────────────────────────────────────────────────────────┐
│  ④ 接入层  Controller                                                │
│     /api/biz/ai/chat（同步）  /api/biz/ai/chat/stream（SSE 流式）      │
│     /api/biz/ai/agent（管理） /api/biz/ai/workflow /observ ...        │
├──────────────────────────────────────────────────────────────────────┤
│  ③ 闸门层  AiChatPreflight（同步/流式共用同一份）                       │
│     权限校验 → 意图识别路由 → 敏感词拦截 → 技能准入 → 智能体装载         │
├──────────────────────────────────────────────────────────────────────┤
│  ② 装配层  AiAgentManagementService（内存装配中心）                    │
│     DB(ai_managed_agent) → 快照 → ProfileResolver → HarnessAgent      │
│     RuntimeInvalidator：配置变更 → 失效重建，不重启                    │
├──────────────────────────────────────────────────────────────────────┤
│  ① 能力层  技能 / 模型 / Prompt / 工作流 / 多智能体 / 插件 / 沙箱        │
└──────────────────────────────────────────────────────────────────────┘
```

一条消息从进来到出去的完整链路：

```text
HTTP 请求
   │
   ├─► AiChatPreflight.run(body)
   │      ├─ 校验参数（agentId / message 必填）
   │      ├─ security.canAccess("agent", agentId, "execute")   ← 权限
   │      ├─ intentService.parse(message)                      ← 意图识别
   │      │     ├─ reject      → 抛异常（已拒绝）
   │      │     ├─ needClarify → 闸门接管，不调模型，直接追问/二次确认
   │      │     └─ 否则继续
   │      ├─ security.interceptInput(message)                  ← 敏感词
   │      ├─ skillRouter.route(...)                            ← 技能准入
   │      │     ├─ NEED_CLARIFY / MULTI_CONFLICT → 闸门接管
   │      │     └─ SINGLE → 带 hint 引导模型优先调用该技能
   │      └─ 装载 Agent → 组装 AiAgentProfile
   │
   ├─► aiAgentService.chat(message, route)  ← 同步整段
   │   或 aiAgentService.stream(message, route) ← Flux，逐块 push
   │
   ├─► 输出脱敏 sanitizeOutput + 审计 audit
   │
   └─► memoryRecorder.record(...)  ← 旁路异步，失败不影响本次对话
```

注意最后一步：**记忆记录是旁路**。这是全文会反复出现的一个主题——**增强能力不能反噬主流程**。

---

## 二、装配：把数据库里的一行配置，变成能跑的 Agent

### 2.1 内存装配中心

`AiAgentManagementService` 是整个模块的心脏。它做的事很简单：**启动时把 DB 里的活跃 Agent 全量读进内存快照**，之后所有读取都走内存，写入同步落库。

```java
private final Map<String, AiManagedAgent> agents = new LinkedHashMap<>();

public synchronized Optional<AiManagedAgent> findEnabledAgentById(String id) {
    if (!StringUtils.hasText(id)) {
        return Optional.empty();
    }
    return Optional.ofNullable(agents.get(id.trim()))
            .filter(AiManagedAgent::enabled);
}
```

为什么要内存快照？因为**对话是高频读、低频写**的场景。每次对话都去查一次 `ai_managed_agent`，数据库会先扛不住。快照 + `synchronized` 就够了——管理操作的 QPS 极低，用一把锁换实现简单，是划算的。

### 2.2 五种智能体，共享一个装配骨架

平台支持 5 种 Agent 类型：

| 类型 | 说明 |
|---|---|
| `conversation` | 默认，通用多轮对话 |
| `rag` | 检索增强，注入知识检索工具 |
| `tool` | 工具密集型，优先工具调用 |
| `plan` | 规划型，任务拆解逐步执行 |
| `graph` | 图编排，多节点流程 |

关键设计：**它们共享同一套装配骨架**，差异只收敛到「系统提示词追加 + 策略注入」两处。

这不是偷懒，而是刻意的。如果每种类型各写一条装配链路，那么"新增一种类型"就要复制一遍「sysPrompt 拼装 + 模型绑定 + toolkit 注入 + 记忆存储 + workspace 配置」。复制出来的代码，半年后必然互相漂移——A 类型修了 bug，B 类型没修。

**能共享的骨架必须共享，差异必须显式表达。** 这是装配层的第一原则。

### 2.3 渠道路由：逐级降级

一个 Agent 可能同时服务控制台、飞书、协作会话等多个渠道。路由策略是**逐级降级**：

```text
显式绑定（消息里带的 binding）
    ↓ 没有则
渠道绑定（Agent 配置的 defaultChannels 命中该渠道）
    ↓ 没有则
渠道默认（该渠道的默认 Agent）
    ↓ 没有则
全局默认
```

`AiManagedAgentProfileResolver` 里的实现很克制——它只暴露**聊天与渠道路由所需字段**，不把整个管理实体透传给运行时：

```java
private AiAgentProfile toProfile(AiManagedAgent agent) {
    return new AiAgentProfile(
            agent.id(), agent.tenantId(), agent.name(), agent.model(),
            agent.persona(), agent.skillIds(), agent.agentType(),
            agent.agentConfig(), agent.enabled());
}
```

这一层适配器看着多余，实则是**依赖倒置**：starter 运行时不需要知道 `module-ai` 的管理实体长什么样。管理模型随便演进，只要这 9 个字段的映射不变，运行时就不受影响。

### 2.4 动态生效：改配置不重启

运营在后台把 persona 从"采购审批助手"改成"采购风控助手"，期望是**立即生效**。

实现靠 `AiManagedAgentRuntimeInvalidator` 这个 SPI：

```java
public synchronized AiManagedAgent update(String id, AiManagedAgentRequest request) {
    AiManagedAgent existing = agents.get(id);
    if (existing == null) {
        return null;
    }
    AiManagedAgent agent = toAgent(id, request, existing.createdAt());
    invalidateRuntime(existing);   // ① 先失效旧实例
    saveAgent(agent);              // ② 再落库
    agents.put(id, agent);         // ③ 最后换快照
    return agent;
}
```

**注意顺序：先失效、再落库、最后换快照。**

假设反过来（先落库再失效），中间存在一个时间窗口：库里已经是新配置，但内存里跑的还是旧 Agent。如果这时恰好落库成功、失效抛异常，系统就卡在"配置是新的、行为是旧的"这个不一致状态里，而且**下次重启才会自愈**——这种 bug 极难定位。

更微妙的是：为什么不是"先换快照再失效"？因为失效是一个可能失败的副作用（要通知运行时销毁实例）。把它放在最前面，失败就当整次更新没发生，快照不动，状态一致。

**凡是「有副作用的多步操作」，先做最可能失败的那一步。** 这是并发场景下保持一致性的通用心法。

还有一处细节值得看——技能变更要**级联失效**所有引用了该技能的 Agent：

```java
private void invalidateAgentsUsingSkill(String skillName) {
    agents.values().stream()
            .filter(agent -> agent.skillIds().contains(skillName))
            .forEach(this::invalidateRuntime);
}
```

因为技能更新可能改了权限（比如某个 API 技能从只读变成可写），**继续复用旧的 HarnessAgent 等于让它继续持有旧权限**。这不是性能问题，是安全问题。

---

## 三、对话双通道，和「一个闸门」

### 3.1 为什么必须有两条通道

同步端点返回完整回复，适合短问答和调试；流式端点逐块推送，适合长回复。

两条通道带来一个经典风险：**如果各写一份前置校验，流式那条一旦漏掉某道校验，它就成了绕过安全管控的旁路。**

而且这种缺失**很难在常规回归里被发现**——流式端点通常只在新前端里被调用，后端回归测试可能压根不覆盖它。

所以 `AiChatPreflight` 被单独抽了出来，两条通道共用：

```java
/**
 * <p><b>为什么要单独抽出来</b>：对话现在有两条入口通道 —— 同步的
 * {@code POST /api/biz/ai/chat} 与流式的 {@code POST /api/biz/ai/chat/stream}。
 * 若两条通道各写一份闸门，流式通道一旦漏掉某道校验（尤其是 {@code agent:execute}
 * 权限与敏感词拦截），就会成为一条<b>绕过安全管控的旁路</b>……抽成同一份实现，
 * 从结构上排除该风险。</p>
 */
```

注意这里的措辞：**"从结构上排除该风险"**。

这是安全设计里一个非常重要的思路。靠"开发者记得两条通道都要加校验"是**流程约束**，它会随时间腐坏；靠"只有一份实现，想绕过都没有第二个入口"是**结构约束**，它不依赖人的记性。

**能用结构解决的问题，不要用纪律解决。**

### 3.2 闸门接管：不调模型的那几种情况

闸门不只是"拦截"，它还负责**接管**——有些请求压根不该进模型：

```java
public record Result(
        Map<String, Object> shortCircuit,   // 非 null = 已被闸门接管
        AiManagedAgent agent,
        AiAgentProfile profile,
        String tenantId, String userId, String sessionId,
        String skillRouteHint,
        IntentParseResult intent) {

    public boolean shortCircuited() {
        return shortCircuit != null;
    }
}
```

哪些情况会被接管？

1. **权限不足** → 抛异常
2. **意图风险拒绝** → 抛异常（并审计）
3. **必填槽位缺失**（`PARAM_CLARIFY`）→ 直接追问补全，**禁止调用模型执行**
4. **写操作二次确认**（如订单操作）→ 先确认再执行
5. **技能意图歧义**（`NEED_CLARIFY`）→ 停止调度，向用户澄清
6. **多技能冲突**（`MULTI_CONFLICT`）→ 返回冲突提示

第 3、4 条尤其重要。一个"采购下单"的请求缺了数量参数，如果放模型进去，它很可能**编一个数量**然后真的把单下了。闸门的价值就在于：**在参数不全时，机器比模型更清楚"不该继续"**。

而第 7 种情况则相反——**单一技能命中时，闸门不接管，而是给一个 hint**：

```java
case SINGLE -> {
    // 单一命中：注入提示，引导模型优先调用该技能
    return new SkillRoute(null, decision.matchedSkills().get(0).name());
}
```

这里的分寸感很值得品味：**闸门负责"确定性"的拦截，模型负责"模糊性"的判断。** 技能 100% 确定命中的时候，闸门也不越权替模型做决定，只是引导一下。因为工具调用往往需要模型自己组织参数，硬接管反而会破坏它的推理链。

---

## 四、流式协议里的六个决定

流式端点 `AiChatStreamController` 是整个模块里注释最密集的类。几乎每个"看起来可以更简单"的地方，背后都有一个明确的取舍。

### 4.1 为什么是 POST + fetch，而不是 GET + EventSource

浏览器的 `EventSource` 不能自定义请求头。本项目的鉴权头是 `Authorization: <token>`，如果为了迁就 `EventSource` 把 token 挪到 query string 会怎样？

```java
/**
 * ...若为迁就 {@code EventSource} 把 token 挪到 query string，token 会进入
 * 访问日志、浏览器历史与 Referer，属于把凭据降级为「谁看到 URL 谁就能用」。
 * 因此这里用普通 POST 承载请求体与鉴权头，响应体是 SSE 分帧，
 * 前端用 fetch + ReadableStream 消费。
 */
```

一句话：**URL 是会被记录、被分享、被泄漏的，凭据不该出现在那里。** 为此宁可放弃 `EventSource` 的便利，自己在前端手写流解析。

### 4.2 事件协议

| 事件 | 载荷 | 语义 |
|---|---|---|
| `meta` | `{agentId, agentName, agentType, sessionId}` | 已受理 |
| `delta` | `{text}` | 增量文本块，消费方应**追加** |
| `replace` | `{reply, ...}` | 整段替换（闸门接管 / 脱敏校正） |
| `error` | `{message}` | 失败，message 已含归因与处置 |
| `done` | `{reply, elapsedMs}` | 收尾，reply 为**权威全文** |

注意 `delta` / `replace` / `done` 三者的分工。前端不能只认 `delta` 拼接，因为：

- **脱敏可能需要回头改**（见 4.3）
- **闸门接管时压根没有 delta**，直接一个 `replace` + `done`

所以协议里明确了 **`done` 的 `reply` 是权威全文**。前端正确的做法是：用 `delta` 做打字机效果，用 `done` 做最终对齐。

### 4.3 为什么逐块净化不够，还要整段再净化一次

这是全文最精彩的一个细节：

```java
case DONE -> {
    // 收尾以**整段净化**为准：逐块净化无法覆盖「敏感词跨块拼接」的情况，
    // 这里把权威全文再净化一次，若与已推送内容不同则以 replace 校正
    String full = sanitize(event.text());
    if (!full.equals(event.text())) {
        send(emitter, closed, EVENT_REPLACE, payload("reply", full));
    }
    send(emitter, closed, EVENT_DONE,
            payload("reply", full, "elapsedMs", event.elapsedMs()));
    ...
}
```

**"敏感词跨块拼接"**——模型逐字输出 `<敏感词>`，如果敏感词是"内部资料"，而流式分块恰好切成 `内` + `部资料`，那么每一块单独看都可能不命中敏感词规则，拼起来却是完整的。

逐块净化在这种情况面前是**结构性失效**的：不是规则写得不够好，而是"分块"这个动作本身破坏了"词"的边界。

所以流式脱敏必须做两道：**逐块净化（保实时）+ 整段净化（保正确）**，后者发现差异时用 `replace` 校正。这是流式场景下"实时性"与"正确性"矛盾的标准解法。

### 4.4 失败为什么走 `error` 事件，而不是 HTTP 非 2xx

```java
/**
 * <p><b>失败为什么走 {@code error} 事件而不是 HTTP 非 2xx</b>：前端拿到 200 之后再遭遇
 * 传输层错误会被当成「网络故障、可重试」，而模型侧失败是业务失败（要按归因处置）。
 * 闸门失败（校验/权限/敏感词）仍按 HTTP 语义抛出，与同步端点完全一致。</p>
 */
```

这背后是一套清晰的**归因模型**：

- **闸门失败**（参数、权限、敏感词）→ HTTP 语义抛出。因为这类失败在建立流之前就发生了，前端的处理方式和其他接口一致。
- **模型侧失败** → 走 `error` 事件。因为这是**业务失败**，需要前端展示归因（比如"模型无权限，请检查配置"），而不是提示"网络错误，请重试"。

如果把模型失败也做成 HTTP 500，前端只会看到一个笼统的失败，用户反复重试也没用——因为**根因不是网络**。

注意代码里那条同样重要的约束：

```java
// 闸门刻意放在建立 SSE 通道之前：校验失败时让异常按既有 HTTP 语义抛出
AiChatPreflight.Result pre = preflight.run(body);
SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
```

闸门在 `SseEmitter` 创建**之前**就跑完了。顺序一颠倒，闸门异常就得想办法塞进流里，两套语义立刻混在一起。

### 4.5 客户端断开，必须取消上游订阅

```java
// 客户端断开（Stop / 关页面）必须取消上游订阅，否则模型会继续生成到结束 ——
// 既浪费额度，也会让「停止」按钮在观感上失灵
emitter.onCompletion(() -> dispose(subscription, closed));
emitter.onError(error -> dispose(subscription, closed));
emitter.onTimeout(() -> {
    dispose(subscription, closed);
    finish(emitter, closed);
});
```

用户点了"停止"，如果后端不取消订阅，模型会继续把整段生成完。**用户看到的是"停不下来"，账单上却是实实在在的 token。** 观感问题和成本问题，本质是同一个 bug。

### 4.6 关闭通道时静默失败

```java
private void send(SseEmitter emitter, AtomicBoolean closed, String name, Map<String, Object> data) {
    if (closed.get()) {
        return;
    }
    try {
        emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
    } catch (Exception exception) {
        // 客户端断开最常见的表现就是这里抛 IOException：标记关闭并停发，
        // 不往上抛 —— 否则一次正常关页会在服务端留下异常噪音
        closed.set(true);
    }
}
```

**"客户端断开"是正常事件，不是异常。** 用户关个页面就在服务端刷一屏 ERROR 日志，会把真正的故障淹没在噪音里。这类"预期内的失败"必须被静默吸收。

### 4.7 收尾时机的选择

```java
case DONE -> {
    ...
    // 只在**真正收尾**时记录：delta 阶段记会记到半截回复，而 error 分支
    // 压根不该进记忆（失败回复不是对话内容）
    memoryRecorder.record(turn.tenantId(), turn.userId(), turn.sessionId(),
            turn.message(), full);
}
```

只在 `DONE` 记录，不在 `delta` 记录，也不在 `error` 记录。理由写得很明白：

- `delta` 阶段记 → 记到半截
- `error` 阶段记 → 把失败回复当成对话内容存进记忆，**后续会被注入上下文**，等于把错误教给了模型

---

## 五、记忆：如何优雅地做一条旁路

`agent-harness` 与 `agent-memory` 是**解耦的兄弟模块**，没有编译依赖。前者通过 **MCP（Model Context Protocol）** 把每轮对话写进后者。

这个集成有三层设计值得讲。

### 5.1 记录器：有界队列 + 单线程 + 立即返回

```java
public void record(String tenantId, String userId, String sessionId,
                   String userMessage, String assistantReply) {
    if (!properties.isEnabled()) {
        return;
    }
    if (isBlank(sessionId)) {
        return;   // 无会话维度就无处归集
    }
    String content = compose(userMessage, assistantReply);
    if (content.isEmpty()) {
        return;
    }
    long seq = turnSeq.incrementAndGet();
    try {
        executor.execute(() -> write(tenantId, userId, sessionId, seq, content));
    } catch (Exception e) {
        // 队列满（AbortPolicy）或已关闭：丢弃并告警。丢一条记忆远好过拖慢或中断对话
        log.warn("[对话记忆] 入队失败，本轮不记录 (sessionId={}): {}", sessionId, e.toString());
    }
}
```

四道防线，层层收紧：

1. **开关关闭** → 直接返回
2. **sessionId 空白** → 直接返回（没有会话维度，写进去也归不了集）
3. **内容为空** → 直接返回
4. **队列满** → 丢弃 + WARN（`AbortPolicy` 而非 `CallerRunsPolicy`）

第 4 条是关键。线程池用的是 `AbortPolicy`——队列满了**直接拒绝**，而不是让调用方线程（也就是对话线程）自己跑。

如果用 `CallerRunsPolicy`，队列一满，**对话线程就会被拉去执行写记忆的 HTTP 调用**，用户立刻感觉到响应变慢。用 `AbortPolicy`，最坏情况是丢一条记忆，对话体验完全不受影响。

**这不是权衡，这是明确的优先级排序：对话可用性 > 记忆完整性。**

线程也是守护线程：

```java
runnable -> {
    Thread thread = new Thread(runnable, "chat-memory-recorder");
    // 守护线程：记录是旁路，不应阻止 JVM 退出
    thread.setDaemon(true);
    return thread;
}
```

### 5.2 写什么：与既有语义对齐，不要发明新结构

```java
/** 组装记录正文：按侧截断，空侧不出行。 */
private String compose(String userMessage, String assistantReply) {
    StringBuilder sb = new StringBuilder();
    appendLine(sb, "[user] ", userMessage);
    appendLine(sb, "[assistant] ", assistantReply);
    return sb.toString();
}
```

注释里写得很清楚：

> 与 `AiConversationMemory.appendTurn(sessionId, user, assistant, max)` 的语义对齐 —— 项目里「一轮对话」的口径就是这一对消息，不额外发明结构。

还有一个容易踩的坑被显式标注了：

```java
/**
 * ⚠️ {@code saveSessionVar} 是**追加**语义（每次生成新 id），不会按 key 覆盖，
 * 所以每轮用**独立 key** 是正确用法，不要试图用固定 key 做「每会话一条」。
 */
```

key 生成方式是 `"chat-" + System.currentTimeMillis() + "-" + seq`——**时间戳 + 自增序号**，既保证唯一，又天然可排序。

截断也要显式标注：

```java
/** 按配置上限截断单侧内容，并显式标注 —— 无声截断会让人以为原始回复就这么短。 */
private String truncate(String text) {
    int max = properties.getMaxChars();
    if (max <= 0 || text.length() <= max) {
        return text;
    }
    return text.substring(0, max) + "…[已截断，原文 " + text.length() + " 字]";
}
```

**无声截断是数据可信度的敌人。** 半年后有人查看记忆，看到一段戛然而止的回复，无从判断是"模型就这么答的"还是"被截断了"。加一行标注，成本几乎为零。

### 5.3 MCP 客户端的三个坑

`MemoryMcpClient` 的类注释直接列出了三个"都会表现为「静默写不进去」"的坑：

```java
/**
 * <h2>三个必须处理的坑（都会表现为「静默写不进去」）</h2>
 * <ol>
 *   <li><b>工具级失败也是 HTTP 200</b>：{@code tools/call} 内部的参数/未知工具错误由
 *       {@code MemoryMcpToolkit} 以 {@code {"error": "..."}} 作为**正常结果**返回，
 *       外层 HTTP 状态码仍是 200。只看状态码会把失败当成功。</li>
 *   <li><b>结果被包了两层</b>：JSON-RPC 的 {@code result} 是 MCP 的
 *       {@code {content:[{type:"text",text:"<JSON 字符串>"}]}}，业务结果藏在
 *       {@code content[0].text} 这个**字符串**里，需要再解析一次。</li>
 *   <li><b>401 的错误体不是统一响应信封</b>：准入失败时返回的是 JSON-RPC error 对象
 *       （HTTP 401），按 {@code {code,msg,data}} 解析会拿到 null 而丢掉真实原因。</li>
 * </ol>
 */
```

对应的实现：

```java
if (response.statusCode() != 200) {
    throw new IllegalStateException("MCP 返回 HTTP " + response.statusCode()
            + "：" + jsonRpcError(root, response.body()));
}
if (root != null && root.hasNonNull("error")) {
    throw new IllegalStateException("MCP 返回错误：" + jsonRpcError(root, response.body()));
}
Map<String, Object> result = unwrapToolResult(root);
// 坑 1：工具级失败同样是 200，必须检查业务结果里的 error
if (result.containsKey("error")) {
    throw new IllegalStateException("MCP 工具 " + name + " 执行失败：" + result.get("error"));
}
return result;
```

**三层错误检查，缺一不可：**

| 层级 | 检查 | 漏掉的后果 |
|---|---|---|
| HTTP 状态码 | `!= 200` | 网络/准入层失败被忽略 |
| JSON-RPC 层 | `root.hasNonNull("error")` | 协议层失败被忽略 |
| **业务层** | `result.containsKey("error")` | **工具执行失败被当成成功** |

第三层是最容易漏的。因为前两层检查完你会觉得"都 200 了肯定成了"，但 MCP 的 `tools/call` 协议规定：**工具内部的错误作为正常结果返回**。只看状态码的代码，会把"参数不合法"当成"写入成功"。

这就是"静默写不进去"的典型成因——**没有报错，但什么也没发生**。

### 5.4 端点地址为什么必须惰性求值

```java
/**
 * <p>推导放在**首次调用**而非构造期：Web 容器端口在 Bean 构造时可能尚未绑定，
 * 过早取值会得到一个 0 或 -1，然后把整个进程生命周期的写入都指向无效端口。</p>
 */
String endpoint() {
    if (properties.getUrl() != null && !properties.getUrl().isBlank()) {
        return properties.getUrl().trim();
    }
    String cached = cachedUrl;
    if (cached != null) {
        return cached;
    }
    String derived = urlFallback == null ? null : urlFallback.get();
    String resolved = derived == null || derived.isBlank()
            ? "http://127.0.0.1:9900/api/agent-memory/mcp" : derived;
    cachedUrl = resolved;
    return resolved;
}
```

而且它取的是**容器实际监听的端口**，不是 `server.port` 配置值。因为本机环境变量 `SERVER__PORT` 可能覆盖 yml 里的配置——**读配置值会得到一个"正确但没用"的端口**。

这个坑很隐蔽：构造期读到 0，缓存下来，之后每次写都打到无效端口，表现为"记忆页一直是空的"。加一行 `volatile` + 惰性求值，成本极低，收益是避免一整类诡异问题。

### 5.5 脱敏后入记忆

同步端点里有一行注释点出了要害：

```java
// 记到 agent-memory 的是**脱敏后**的正文：记忆会被注入后续对话上下文，
// 存原文等于把敏感信息绕开脱敏复制到另一个会进 prompt 的地方
memoryRecorder.record(pre.tenantId(), userId, pre.sessionId(), message, content);
```

**记忆是会被回灌进 prompt 的。** 如果把未脱敏的原文存进记忆，那么这次脱敏就白做了——敏感信息从"输出被过滤"变成"下次对话的输入"。

安全边界必须覆盖**数据的每一处落点**，而不只是"最显眼的那一处出口"。

---

## 六、可观测：把"黑盒"变成"时间线"

Agent 最大的运维难题是**不可见**。用户说"这个回答不对"，你只知道调了一次模型，中间发生了什么完全不知道。

### 6.1 trace + step 两层模型

```text
ObservTrace（一次完整执行）
  ├─ traceKey / sessionId / agentId / agentName / intent / triggerType
  ├─ status: running → success / failed
  ├─ prompt / response / tokens / latencyMs
  └─ 时间线
       ├─ step[1]  type=agent   name=begin
       ├─ step[2]  type=tool    name=tool_call   latency=xxx  status=ok
       ├─ step[3]  type=plan    name=plan_enter
       └─ step[4]  type=agent   name=end
```

`ObservTraceService` 实现了框架层的 `TraceObserver` 接口：

```java
public ObservTraceService(ObservTraceMapper traceMapper,
                          ObservTraceStepMapper stepMapper,
                          AlertService alertService) {
    this.traceMapper = traceMapper;
    this.stepMapper = stepMapper;
    this.alertService = alertService;
    TraceCollector.register(this);   // 构造期自注册
}
```

注意 `TraceCollector.register(this)`——**在构造器里把自己注册进去**。这样运行时埋点方（`TraceCollector`）完全不需要知道 `ObservTraceService` 的存在，只依赖 `TraceObserver` 这个接口。

### 6.2 埋点绝不能反噬主流程

`ObservTraceService` 里每一个回调都长这样：

```java
@Override
public void onBegin(String traceId, String sessionId, String agentId, String agentName,
                    String intent, String triggerType) {
    try {
        ObservTrace trace = new ObservTrace();
        ...
        traceMapper.insert(trace);
    } catch (Exception ignored) {
    }
}
```

```java
@Override
public void onStep(String traceId, int seq, String stepType, String name,
                   String inputJson, String outputJson, long latencyMs, String status) {
    try {
        ...
        stepMapper.insert(step);
    } catch (Exception ignored) {
    }
}
```

**`catch (Exception ignored)`——所有三个回调都是。**

这个写法在代码评审里通常会被打回："异常怎么能吞掉？"但在这里它是正确的，而且是必须的：

> **可观测性设施不允许影响业务。** 埋点写库失败（数据库连接池满、字段超长、表锁），不能让用户的对话失败。

代价是排查困难。所以这里的取舍是：**吞异常 + 通过别的手段保证可观测**（告警、监控慢查询、trace 表本身的健康检查）。

输入输出还要截断，防止把几万字的 prompt 塞进库里：

```java
step.setInputJson(truncate(inputJson, 2000));
step.setOutputJson(truncate(outputJson, 3000));
```

### 6.3 事件桥：业务代码与 trace 落库解耦

`ObservEventBridge` 订阅 Spring 事件，把三类领域事件翻译成 trace：

```java
/** Agent 步骤事件 → observ step。 */
@EventListener
public void onAgentStep(AgentStepEvent event) { ... }

/** 会话事件 → 建 trace（每会话一条，标题即首条用户消息）。 */
@EventListener
public void onConversation(ConversationTurnEvent event) { ... }

/** 工具事件 → observ tool step。 */
@EventListener
public void onTool(ToolCallEvent event) { ... }
```

这样带来的好处是：**业务代码只发事件，完全不知道 trace 的存在**。以后要换成 OpenTelemetry、或者同时落两套，业务侧一行都不用改。

事件桥里还有个很实用的分类细节——把 Plan Mode 的工具单独归类：

```java
// Plan Mode 工具（plan_enter/write/exit/todo_write）归类为 plan 事件，
// 便于终态判定（是否规划过/是否退出）
boolean planTool = isPlanTool(event.toolName());
String stepType = planTool ? "plan" : "tool";
```

把 `plan_*` 和 `todo_write` 归为 `plan` 类型，之后就能用一条 SQL 回答"这个会话到底规划过没有、有没有正常退出 plan"。

### 6.4 自动告警：四个判据

```java
private void detectAlerts(ObservTrace trace, String status, String response, int tokens, long latencyMs) {
    String agentId = trace.getAgentId();
    if ("failed".equals(status) || (response != null && response.startsWith("AI 智能体调用失败"))) {
        alertService.report("agent_error", "Agent 调用报错: " + truncate(response, 200), agentId);
    }
    if (latencyMs > 30_000) {
        alertService.report("timeout", "Agent 调用超时: " + latencyMs + "ms", agentId);
    }
    if (tokens > 20_000) {
        alertService.report("token_overload", "Token 消耗过载: " + tokens + " tokens", agentId);
    }
    String word = alertService.findSensitiveWord(checkText);
    if (word != null) {
        alertService.report("sensitive_word", "触发敏感词「" + word + "」", agentId);
    }
}
```

四类告警：**报错 / 超时 / Token 过载 / 敏感词**。

注意第一个判据的双条件——不仅看 `status`，还看响应内容是否以 `"AI 智能体调用失败"` 开头。因为有些失败路径状态可能是 `success`（模型正常返回了，只是内容是失败提示），只判状态会漏。

**告警判据要考虑"同一语义的多种表现形式"，不能只盯状态字段。**

---

## 七、多智能体协作：状态机 + 解耦

`MultiAgentCollaborationService` 实现了完整的多人（Agent + 人）协作：

```text
                    ┌──────────┐
                    │ pending  │ 创建
                    └────┬─────┘
                         │ dispatch
                    ┌────▼─────┐
                    │ assigned │ 已分发
                    └────┬─────┘
                         │ assign
                    ┌────▼──────┐
              ┌─────│in_progress│─────┐
              │     └────┬──────┘     │
       handoff│          │ submit     │ markConflict
       （转交）│          │            │
        回到 assigned ┌──▼───────┐  ┌─▼────────┐
                    │submitted │  │ conflict │
                    └────┬─────┘  └────┬─────┘
              approve ／  ＼ reject    │ resolve
           ┌────────┐  ┌──────────┐   │(approved/retry)
           │approved│  │ rejected │   ▼
           └───┬────┘  └──────────┘  done
               │
           ┌───▼──┐
           │ done │
           └──────┘
```

### 7.1 每一个状态变化都留痕

```java
private void changeStatus(AgentTask task, String toStatus, String actionType,
                          String operatorType, String operatorId, String comment) {
    String from = task.getStatus();
    task.setStatus(toStatus);
    task.setUpdatedAt(LocalDateTime.now());
    taskMapper.updateById(task);
    recordFlow(task.getId(), from, toStatus, actionType, operatorType, operatorId, comment);
}
```

`changeStatus` 是**唯一**修改状态的入口，而且必然记录流水。这样 `AgentTaskFlow` 表天然是一份完整的审计日志：谁、什么时候、把任务从什么状态改成什么状态、为什么。

### 7.2 委派：不问"子任务完成了吗"，而是"父任务被通知"

`delegate` 创建子任务，父任务进入 `in_progress` 等待：

```java
/** 任务委派：创建子任务给被委派者，父任务等待子任务回调。 */
public AgentTask delegate(Long taskId, String delegateType, String delegateId, String instruction) {
    ...
    AgentTask subTask = createTask(subTaskParams);
    if (!STATUS_IN_PROGRESS.equals(task.getStatus()) && ...) {
        changeStatus(task, STATUS_IN_PROGRESS, "delegate", "user", task.getCreatorId(),
                "任务委派给 " + delegateId + "，等待子任务完成");
    }
    ...
}
```

关键在**回调**：

```java
/** 子任务回调：子任务完成后更新父任务。 */
private void subtaskCallback(Long parentTaskId) {
    ...
    List<AgentTask> children = taskMapper.selectList(...);
    boolean allDone = children.stream().allMatch(t -> STATUS_DONE.equals(t.getStatus())
            || STATUS_APPROVED.equals(t.getStatus()));
    boolean anyFailed = children.stream().anyMatch(t -> STATUS_FAILED.equals(t.getStatus())
            || STATUS_REJECTED.equals(t.getStatus()) || STATUS_CONFLICT.equals(t.getStatus()));
    ...
    if (allDone) {
        ...  // 全部完成 → 父任务 done 或 submitted
    } else if (anyFailed) {
        changeStatus(parent, STATUS_CONFLICT, "subtask_callback", "system", null,
                "存在失败/被驳回子任务，需人工处理");
    }
}
```

为什么用回调而不是轮询？

- **轮询**：需要一个定时任务扫全表，判断"哪些父任务的所有子任务都完成了"，写起来复杂，还有延迟。
- **回调**：每个子任务完成时主动通知父任务，一步到位。

而且注意 `anyFailed` 的处理——**有任何一个子任务失败，父任务进入 `conflict` 而不是 `failed`**。

这个区分很重要：

- `failed` = "确定失败了，不用管了"
- `conflict` = "结果有矛盾/不完整，需要人来看"

子任务部分失败时，父任务的结果是"部分可信"的，直接标记失败会丢掉已经成功那部分的成果；标记 conflict 让人来决定"是接受部分结果还是重做"，才是正确的语义。

### 7.3 外部回调失败不能影响任务

```java
private void afterComplete(AgentTask task) {
    if (task.getParentTaskId() != null) {
        subtaskCallback(task.getParentTaskId());
    }
    if (StringUtils.hasText(task.getCallbackUrl())) {
        CompletableFuture.runAsync(() -> {
            try {
                ...
                restTemplate.postForEntity(task.getCallbackUrl(),
                        new HttpEntity<>(body, headers), String.class);
            } catch (Exception e) {
                log.warn("任务回调失败: {} - {}", task.getCallbackUrl(), safeMessage(e));
            }
        });
    }
}
```

又是熟悉的模式：**异步 + 吞异常 + WARN**。任务本身已经完成了，外部系统没收到通知是它的问题，不该把已完成的业务回滚。

---

## 八、工作流引擎：节点、断点、重试、兜底

`WfWorkflowEngine` 是一个轻量但完整的流程引擎，支持 11 种节点：

| 节点 | 作用 |
|---|---|
| `start` / `end` | 起止 |
| `task` / `async_task` | 任务（支持重试） |
| `llm` | 调用大模型 |
| `http` | 发起 HTTP 请求 |
| `code` | 受限 JS 执行 |
| `template` | 模板转换 |
| `condition` | 条件分支 |
| `loop` | 循环 |
| `manual` | 人工介入 |
| `fallback` | 异常兜底 |

### 8.1 断点调试：状态要能序列化

```java
// 断点暂停：debug 模式下未执行过的断点节点
if (Boolean.TRUE.equals(run.getDebug())
        && isBreakpoint(node)
        && !ctx.executedNodes.contains(nodeId)) {
    run.setCurrentNodeId(nodeId);
    run.setStatus("paused");
    run.setContextJson(ctx.toJson());
    runMapper.updateById(run);
    ctx.record(nodeId, node.path("name").asText(nodeId), "断点暂停",
            "breakpoint", "waiting");
    return;
}
```

支持断点，意味着**执行上下文必须能完整持久化和恢复**。`WfContext` 提供了两套构造器：

```java
/** 初始执行。 */
WfContext(WfWorkflowRun run, Map<String, Object> input, WfWorkflowRunLogMapper logMapper) { ... }

/** 从暂停状态恢复（反序列化 contextJson）。 */
WfContext(WfWorkflowRun run, WfWorkflowRunLogMapper logMapper) {
    ...
    Map<String, Object> restored = readJson(run.getContextJson());
    Object outputObj = restored.get("input");
    this.input = outputObj instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : new LinkedHashMap<>();
    Object outputs = restored.get("nodeOutputs");
    if (outputs instanceof Map<?, ?> m) {
        nodeOutputs.putAll((Map<String, Object>) m);
    }
    Object exec = restored.get("executedNodes");
    if (exec instanceof List<?> l) {
        for (Object o : l) {
            executedNodes.add(String.valueOf(o));
        }
    }
    ...
}
```

**断点调试的本质是"执行上下文可序列化"**。做不到这一点，就只能做"暂停"，不能做"恢复"和"单步"。

`executedNodes` 这个字段尤其关键——它保证了"断点只命中一次"，否则恢复执行后会立刻又停在同一位置，形成死循环。

### 8.2 重试：失败信息必须被记录

```java
private void executeTask(WfWorkflowRun run, WfContext ctx, JsonNode node,
        String id, String name, boolean async) {
    JsonNode config = node.path("config");
    int retry = config.path("retry").asInt(0);
    long retryInterval = config.path("retryInterval").asLong(0);
    int attempt = 0;
    while (true) {
        try {
            Map<String, Object> output = doTask(config, ctx);
            ctx.nodeOutputs.put(id, output);
            ctx.record(id, name, (async ? "异步任务" : "任务") + "执行成功"
                    + (attempt > 0 ? "（第" + (attempt + 1) + "次重试成功）" : ""),
                    "exit", "success");
            return;
        } catch (Exception e) {
            attempt++;
            if (attempt <= retry) {
                ctx.record(id, name, "执行失败，自动重试 " + attempt + "/" + retry
                        + "：" + e.getMessage(), "retry", "retrying");
                ...
            } else {
                throw new IllegalStateException(name + " 执行失败：" + e.getMessage(), e);
            }
        }
    }
}
```

两个细节：

1. **每次重试都记日志**，带失败原因。事后排查时，你能看到"第 1 次超时、第 2 次超时、第 3 次成功"。
2. **成功日志里标注重试次数**。`（第3次重试成功）`——如果某个节点经常需要重试才成功，这是**需要优化的信号**，而如果只看"成功"两个字，这个信号就消失了。

**日志要能反映"过程"，而不只是"结果"。**

### 8.3 兜底：只兜一次

```java
private String findFallbackNode(String definitionJson, WfContext ctx) {
    JsonNode def = parse(definitionJson);
    JsonNode nodes = def.path("nodes");
    if (nodes.isArray()) {
        for (JsonNode node : nodes) {
            if ("fallback".equals(node.path("type").asText())
                    && !ctx.fallbackUsed) {      // ← 关键
                ctx.fallbackUsed = true;
                return node.path("id").asText();
            }
        }
    }
    return null;
}
```

`ctx.fallbackUsed` 这个标志位防的是**兜底节点自己也失败，再触发一次兜底，无限循环**。

这是兜底机制的经典陷阱：兜底逻辑本身也是一个可能失败的节点，如果不加"只用一次"的约束，一个持续失败的流程会在兜底和异常之间反复横跳。

### 8.4 代码节点：IIFE 包装

```java
/** Nashorn 受限 JS 执行（JDK17 + nashorn-core 独立版）。 */
private Object evalJs(String code, Map<String, Object> input) {
    try {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        if (engine == null) {
            throw new IllegalStateException("JS 执行引擎不可用（缺少 nashorn-core 依赖）");
        }
        engine.getContext().setAttribute("input", input, ScriptContext.ENGINE_SCOPE);
        // 包装为 IIFE：顶层 return 在 ScriptEngine 中非法
        return engine.eval("(function(){" + code + "})()");
    } catch (Exception e) {
        throw new IllegalStateException("代码执行失败: " + e.getMessage(), e);
    }
}
```

`(function(){ ... })()` 这个包装看起来奇怪，但必须——因为 `ScriptEngine.eval` 不接受顶层 `return`。用户写的代码天然会带 `return xxx`，包装成 IIFE 就能正常返回了。

**引擎为 null 时给出明确的错误信息**（"缺少 nashorn-core 依赖"），而不是抛一个 `NullPointerException`。JDK 15 之后移除了 Nashorn，这是 JDK 17 环境的必踩坑。

---

## 九、技能导入：不解压就能校验 ZIP

`AiSkillZipStructureValidator` 是全文里最"硬核"的一个类——**手写 ZIP 结构校验，在解压之前就把非法包拦下来**。

```java
void validate(byte[] archive) {
    Eocd eocd = readEocd(archive);
    if (eocd.entryCount() > MAX_ENTRY_COUNT) {
        throw AiSkillImportException.tooLarge("ZIP 条目数不能超过 16");
    }
    CentralEntry[] entries = readCentralDirectory(archive, eocd);
    if (entries.length != 1 || !Arrays.equals(entries[0].name(), MANIFEST_NAME)) {
        throw invalidArchive();
    }
    validateLocalEntry(archive, entries[0], eocd.centralOffset());
}
```

校验顺序遵循 ZIP 规范的实际布局：

```text
[本地文件头 + 数据]
[中央目录项]
[EOCD 结束记录]
```

1. **从尾部找 EOCD**（要处理 64KB 注释的向后扫描）
2. **读中央目录**，检查条目数与文件名
3. **回验本地条目**，交叉比对中央目录与本地头的字段

为什么值得手写？因为**在解压前校验，可以避免解压恶意包**。一个精心构造的 ZIP 可能有上百万个条目或超大解压比（Zip Bomb），如果先解压再校验，防御就已经晚了。

校验的严格程度可以从常量里看出来：

```java
private static final int MAX_ENTRY_COUNT = 16;
private static final long ZIP64_MARKER = 0xFFFFFFFFL;
private static final int ZIP64_ENTRY_COUNT = 0xFFFF;
private static final int DATA_DESCRIPTOR_FLAG = 1 << 3;
private static final int ENCRYPTED_FLAG = 1;
private static final int STRONG_ENCRYPTION_FLAG = 1 << 6;
```

明确拒绝：**ZIP64、加密包、多磁盘、超过 16 个条目、非法压缩方法**：

```java
private void rejectUnsupportedEntry(int flags, int method, long compressedSize,
        long uncompressedSize, long localOffset, int startDisk) {
    if ((flags & (ENCRYPTED_FLAG | STRONG_ENCRYPTION_FLAG)) != 0
            || (method != 0 && method != 8)
            || compressedSize == ZIP64_MARKER
            || uncompressedSize == ZIP64_MARKER
            || localOffset == ZIP64_MARKER
            || startDisk != 0) {
        throw corruptArchive();
    }
    if (method == 0 && compressedSize != uncompressedSize) {
        throw corruptArchive();
    }
}
```

**"只支持我认识的那一种"**——这是安全校验的正确姿势。白名单式校验（只放行已知合法形态）永远比黑名单式（枚举所有非法形态）安全，因为攻击者的构造手法是无穷的。

而且**中央目录和本地头要交叉验证**：

```java
if (flags != entry.flags()
        || method != entry.method()
        || !Arrays.equals(localName, entry.name())) {
    throw corruptArchive();
}
```

这是防"中央目录与本地条目不一致"的经典攻击手法——某些解析器信任中央目录，另一些信任本地头，攻击者构造两者不一致的包，让不同组件看到不同的文件名。

---

## 十、装配治理：让"新增接口 404"不再发生

### 10.1 组件扫描取代手写 Bean

老写法是在每个 `AutoConfiguration` 里手写 `@Bean` 注册 Controller。问题很明确：**新增一个 Controller 忘了注册，启动不报错，运行时才发现接口 404。**

```java
@AutoConfiguration
@ConditionalOnExpression("${plugin.ai.enabled:true} && ${ai.agent.enabled:true}")
@ModuleControllerScan(basePackage = "com.zimo.module.ai.controller")
public class AiControllerScanAutoConfiguration {
}
```

注释点明了收益：

> 通过 `@ComponentScan` 自动注册 `com.zimo.module.ai.controller` 包下全部 `@RestController`（14 个），替代在各 AutoConfiguration 中手写 `@Bean` 注册——**新控制器零配置自动生效，杜绝"新增接口 404"**。

但扫描范围的划定很克制：

> 注意：**仅扫描 controller 子包**（该包除 `AiAdminControllerSupport` 辅助类外均为控制器，无其他组件），**collab/observ 包保持手动注册**以避免与手动 Bean 冲突。

**"只扫描我能确定是纯控制器的那一个包"**——这是引入组件扫描时最容易翻车的地方：扫得太宽，会把手动注册的 Bean 重复注册（`@ConditionalOnMissingBean` 失效、Bean 定义冲突），启动直接失败。

### 10.2 条件装配：两个开关 + Bean 存在性

```java
@AutoConfiguration(after = AiAgentAutoConfiguration.class)
@ConditionalOnProperty(prefix = "plugin.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(AiSkillRegistry.class)
public class AiSkillAdminAutoConfiguration {
```

三层条件，逐层收紧：

| 条件 | 含义 |
|---|---|
| `plugin.ai.enabled` | 模块总开关 |
| `ai.agent.enabled` | Agent 运行时开关 |
| `@ConditionalOnBean(AiSkillRegistry.class)` | 运行时能力已就绪 |

### 10.3 软依赖：`ObjectProvider` 而不是硬注入

`AiChatPreflight` 依赖三个协作者：安全、意图、技能路由。它们分别来自可裁剪的模块。如果用构造器硬注入：

```java
// ❌ 硬依赖：任一模块缺失 → 启动失败
public AiChatPreflight(AiAgentManagementService m, SecurityFacade s,
                       IntentRecognitionService i, IntentAwareSkillRouter r) { ... }
```

单独启用 `plugin.ai` 时就会因为找不到 `SecurityFacade` 而起不来。所以用 `ObjectProvider`：

```java
@Bean
@ConditionalOnBean(AiAgentManagementService.class)
@ConditionalOnMissingBean
public AiChatPreflight aiChatPreflight(
        AiAgentManagementService agentManagement,
        ObjectProvider<SecurityFacade> security,
        ObjectProvider<IntentRecognitionService> intentService,
        ObjectProvider<IntentAwareSkillRouter> skillRouter) {
    return new AiChatPreflight(
            agentManagement,
            Optional.ofNullable(security.getIfAvailable()),
            Optional.ofNullable(intentService.getIfAvailable()),
            Optional.ofNullable(skillRouter.getIfAvailable()));
}
```

**注解上的注释直接说了为什么这个 Bean 必须显式注册：**

> 本模块只扫描 `controller` 子包，`@Component` 不会被拾取，因此必须在此显式注册 —— 否则 `AiChatController` 因缺依赖而注册失败，表现是「**对话接口整个 404**」，与本次改动毫无表面关联，极难定位。

这是一个非常典型的"**改动 A 导致 B 挂了**"的陷阱：把闸门抽出来加了 `@Component`，但因为扫描范围不包含它所在的包，它压根没被注册；然后 `AiChatController` 因为找不到这个依赖也注册失败；**用户看到的现象是"对话接口 404"，和"抽了个闸门"看起来毫无关系。**

**理解了组件扫描的边界，才能理解"为什么这个类要写 `@Component` 却还得手动 `@Bean`"。**

---

## 十一、十二条设计取舍

把上面所有细节收敛一下，这个模块真正在坚持的东西是这些：

| # | 取舍 | 体现 |
|---|---|---|
| 1 | **配置驱动，DB 是唯一事实源** | Agent 从 `ai_managed_agent` 装载，不写死在代码里 |
| 2 | **内存快照换读取性能** | 启动全量加载 + `synchronized`，管理写 QPS 低，不值得上分布式缓存 |
| 3 | **装配骨架共享，类型差异显式化** | 5 种 Agent 类型只差"提示词追加 + 策略注入" |
| 4 | **变更先失效、再落库、最后换快照** | 最可能失败的操作放最前，避免中间态不一致 |
| 5 | **权限变更必须级联失效运行时** | 技能改了权限，旧 HarnessAgent 不能继续持有 |
| 6 | **双通道共用一个闸门** | 用结构约束代替流程纪律，杜绝安全旁路 |
| 7 | **闸门只拦"确定性"，模型负责"模糊性"** | 技能单一命中时给 hint 而非硬接管 |
| 8 | **流式脱敏必须逐块 + 整段两道** | 分块会破坏词边界，单靠逐块是结构性失效 |
| 9 | **失败按归因分流** | 闸门失败走 HTTP 语义，模型失败走 `error` 事件 |
| 10 | **增强能力是旁路，永不可反噬主流程** | 记忆/遥测/回调全部异步 + 吞异常 + WARN |
| 11 | **有界队列 + 拒绝策略保护对话线程** | `AbortPolicy` 而非 `CallerRunsPolicy` |
| 12 | **错误检查必须穿透到业务层** | MCP 工具失败也是 200，只查状态码会"静默失败" |

如果要提炼成一句话：

> **这个模块的核心不是「让模型答得更好」，而是「让系统在配置乱改、依赖抖动、用户乱点、模型乱答的情况下，依然可解释、可恢复、挂不掉」。**

---

## 结语

回头看那些"看起来啰嗦"的代码：

- 为什么一个 `record()` 方法要有 4 道提前返回？—— 因为每一道都对应过一种"记忆页是空的"的排查经历。
- 为什么 `endpoint()` 要 `volatile` + 惰性求值？—— 因为有人真的在构造期读到了端口 0。
- 为什么 `ObservTraceService` 敢写 `catch (Exception ignored)`？—— 因为埋点挂了不该让用户对话失败。
- 为什么 ZIP 校验要手写两三百行？—— 因为解压之后再校验就晚了。

**生产级 Agent 系统与 Demo 的差距，不在模型调用那几十行，而在这些"看起来可以省略"的地方。**

这些约束不是过度设计，而是把"线上出过的问题"固化成"代码结构"的结果。每一条背后，都有一个曾经在凌晨排查过的 bug。

---

*技术栈：Java 17 / Spring Boot 3.4.5 / AgentScope Java v2 (HarnessAgent) / MyBatis-Plus / SSE*
*模块规模：171 个源文件，覆盖装配、路由、对话、技能、模型、Prompt、工作流、协作、遥测、插件、沙箱*

如果这篇文章对你有帮助，欢迎点赞收藏。评论区聊聊：你们在生产环境跑 Agent，踩得最深的坑是什么？
