# 拆一个生产级 AI Agent 运行时的架构设计：我在 agent-harness 里看到的 9 个关键决策

> 本文不是源码逐行讲解（那篇另发），而是**架构设计分析**：为什么是这种结构、哪些决策值得借鉴、哪些坑是被刻意绕开的。所有结论都来自 `modules/agent-harness/agent-harness-core`（171 个源文件，包 `com.zimo.module.ai`）的真实代码。

---

## 0. 先给结论

agent-harness 不是一个"把大模型包一层"的 demo，而是一个**配置驱动、插件化、双通道、可观测闭环**的 Agent 运行时。它最值得学的不是某个类怎么写，而是三条贯穿全局的架构纪律：

1. **能力即配置** —— 智能体、模型、技能、工作流都是数据，不是硬编码分支。
2. **对话是第一公民，记忆/遥测是旁路** —— 任何旁路故障都不能让对话失败。
3. **装配是集中的、可审计的** —— 10 个 `*AutoConfiguration` 各自负责一块，但共享一个"内存快照装配中心"。

下面逐条展开。

---

## 1. 整体分层：一个模块，但内部是清晰的分层

工程只拆成 `agent-harness-core` 一个 Maven 模块（不是多模块），靠**包（package）做分层**，避免模块间编译耦合过重。包结构本身就暴露了架构意图：

```text
com.zimo.module.ai
├── autoconfig/      (12)  装配中心：把能力装进 Spring 容器
├── management/      (40)  领域核心：智能体/技能/模型/协作的编排逻辑
├── controller/      (17)  协议层：REST 出入参、参数校验
├── collab/          (16)  多智能体协作状态机
├── observ/          (24)  可观测：trace/step 双层埋点 + 告警
├── modelconfig/     (11)  模型配置与连通性
├── workflow/        (9)   工作流引擎
├── skillimport/     (5)   技能 ZIP 导入与安全校验
├── memory/          (3)   对话记忆旁路（MCP 记录器）
├── mapper/          (13)  MyBatis 持久层
├── plugin/          (1)   插件身份声明 AiPluginRegister
├── skill/ plan/     (各1) 占位/扩展点
```

**设计取舍**：为什么不分更多 Maven 子模块？因为这个工程的"插件边界"是**运行时**的（通过 `AiPluginRegister` 向框架声明 `pluginId="ai"`、`apiPrefix="/api/ai"`、`frontendRoute="/ai"`），而不是编译期的。编译期多模块只会增加 `*-core` / `*-autoconfig` 样板，对"一个 Agent 平台"这种内聚度高的系统反而拖累。框架层 `framework-common` / `framework-ai` / `agent-rag-core` 才是真正的跨模块复用点。

**可借鉴点**：包名即架构图。新接手的人看一眼目录就能知道"领域逻辑在 management、装配在 autoconfig、旁路在 observ/memory"，不需要读文档。

---

## 2. 插件身份：用接口契约取代"约定俗成"

`AiPluginRegister` 实现框架的 `PluginRegister`，一次性声明了插件的所有对外边界：

```java
public class AiPluginRegister implements PluginRegister {
    public String getPluginId()      { return "ai"; }
    public String getApiPrefix()     { return "/api/ai"; }
    public String getFrontendRoute() { return "/ai"; }
    public String getFrontendModule(){ return "ai"; }
    public String getAgentName()     { return "ai-agent"; }
    public int getOrder()            { return 4; }
}
```

**设计分析**：把"API 前缀 / 前端路由 / 前端模块 / 智能体名 / 排序"这些**跨前后端的耦合点**收敛到一个接口里，而不是散落在 Controller 注解、前端路由表、菜单配置三处。新增一个业务插件（比如 `module-sys`）只要实现同一接口，框架就自动把它接入菜单、路由、权限。

**为什么这是好架构**：耦合点显式化 = 可测试。单元测试 `AiPluginRegisterTest` 直接断言这些返回值，等于把"插件接入是否正确"变成了可回归的契约。

---

## 3. 装配哲学：内存快照装配中心，而非"到处 new"

这是整个工程最反直觉但最关键的一点。智能体、技能、模型配置这些"运行时能力"不是启动时一次性 `new` 好就完事——它们会被**后台编辑、删除、热更新**。如果每次请求都查库，数据库扛不住；如果只启动时装载，改了不生效。

agent-harness 的解法是**内存快照 + 顺序化变更**：

```text
                    ┌─────────────┐
   后台编辑/删除 ──▶ │ 先失效快照   │  (1) invalidate()
                    └──────┬──────┘
                           ▼
                    ┌─────────────┐
                    │ 再落库持久化 │  (2) repository.save()
                    └──────┬──────┘
                           ▼
                    ┌─────────────┐
                    │ 最后换快照   │  (3) snapshot = reload()
                    └─────────────┘
                           │
                           ▼
   前台对话请求 ──▶ 读内存快照（零 DB 访问）
```

**三个动作的顺序是铁律**：先失效 → 再落库 → 最后换快照。如果反过来（先换快照再落库），会有一瞬间"内存有、库里没有"，进程重启就丢数据。这个顺序在 `AiModuleAutoConfiguration` 与各 `ManagementService` 中被严格保持。

**可借鉴点**：配置类数据用"内存快照 + 顺序化写"是性价比极高的模式——读路径零 DB、写路径强一致、重启可恢复。比引入 Redis 这类外部缓存轻得多，且不存在缓存与 DB 双写不一致。

---

## 4. 对话架构：双通道，但只有一个闸门

流式（SSE）和同步（一问一答）是两条代码路径，但 agent-harness 强制它们**共用一个前置闸门 `AiChatPreflight`**：

```text
          用户消息
             │
             ▼
    ┌──────────────────┐
    │  AiChatPreflight │ ◀── 唯一闸门：鉴权、限流、参数、智能体可用性
    └────────┬─────────┘
             │ 通过
        ┌────┴─────┐
        ▼          ▼
  同步通道      流式通道
  AiChat-      AiChatStream-
  Controller   Controller
```

**设计取舍**：为什么不让每个 Controller 各自做校验？因为"校验逻辑写两遍"必然会在某次迭代中只改了一处，导致流式绕过限流或同步漏了鉴权。`AiChatPreflight` 是同步/流式**唯一**入口，新增对话通道不得绕过它。

**踩过的坑（已写入记忆）**：`AiControllerScanAutoConfiguration` 只扫 `com.zimo.module.ai.controller` 包，所以 `AiChatPreflight` 这种 `@Component` **必须显式 `@Bean` 注册**在 `AiChatMemoryAutoConfiguration`（实际在装配类里），漏了的表现是"对话接口整个 404"——和"抽了个闸门"表面毫无关联。这正是集中装配的价值：把这种隐蔽依赖收口到少数几个 autoconfig 里。

**闸门只拦"确定性"，模型负责"模糊性"**：preflight 判断的是"是否允许对话"（鉴权/限流/智能体存在），而不是"回答对不对"。把确定性问题与模型模糊性彻底分开，使得闸门可以独立测试、独立限流。

---

## 5. 流式协议：为什么是 POST + fetch，而不是 EventSource

前端流式用的是 `fetch` + `ReadableStream` 手动解析 SSE，而不是浏览器原生的 `EventSource`：

```text
POST /api/ai/chat/stream
   │
   ▼ 服务端逐块吐 SSE: data: {...}\n\n
   │
前端 fetch().body.getReader() 逐块读 → 解析 data: 行 → 上屏
```

**架构决策点**：`EventSource` 只支持 GET、不能带自定义请求体。Agent 对话需要把完整上下文（多轮 history、技能上下文、会话参数）作为请求体发出，GET 的 URL 长度不够且语义别扭。所以选 POST + fetch 流式读取。代价是前端要自己写 SSE 解析（这个复杂度是不可避免的）。

**流式脱敏是结构性难点**：敏感词可能跨块拼接（"内"+"部资料"单块都不命中）。agent-harness 的解法是**两道净化**——逐块净化（防大部分）+ 整段净化（防拼接），且整段净化后用 `replace` 把结果校正回正确位置。这是"流式 + 合规"绕不开的代价，被显式建模而不是忽略。

**失败归因分流**：流式连接断开时，必须区分"用户主动停止" / "上游模型崩了" / "网关超时"，分别给出不同提示（而非一律"连接未正常收尾"）。这是把"失败"当一等公民处理的体现。

---

## 6. 韧性架构：旁路失败绝不让对话崩

这是 agent-harness 最值得抄的设计。记忆记录、遥测埋点都是**旁路**，它们的故障必须被隔离：

```text
对话主链路:  收消息 → 校验 → 调模型 → 返回
                 │           │
        ┌────────┴────┐ ┌────┴─────────┐
        ▼             ▼ ▼              ▼
   记忆记录器      遥测埋点         (都失败也不影响主链路)
   (有界队列+      (try-catch
    独立线程,      全吞异常,
    满则丢弃)      只 WARN)
```

**记忆旁路**（`memory/` 包，3 个类）的设计要点：
- 有界队列 + 独立线程 `AbortPolicy`：**队列满直接丢**，绝不阻塞对话线程。
- 脱敏**之后**才入记忆：进入记忆的内容已经过净化，避免把用户原始敏感信息写进记忆库。
- 失败隔离：MCP 调用挂了只记日志，用户那边毫无感知。

**遥测旁路**（`observ/`，24 个类）的设计要点：`ObservTraceService` 的三个回调（开始/步骤/结束）全部 `catch (Exception ignored)`。埋点系统挂了、慢了、序列化失败了——**都不能让用户的对话失败**。这是很多系统会犯的错：把可观测性当成主链路的一环。

**架构原则提炼**：任何"增强型"能力（记忆、遥测、推荐）都必须满足——**它的故障域被严格限制在自身，且对主链路是 fail-silent 的**。用有界队列 + 独立线程 + 全吞异常三件套实现。

---

## 7. 可观测架构：trace / step 两层模型

`observ/` 不是堆 `log.info`，而是建了一套**两层结构化模型**：

```text
Trace (一次对话) ── 1:N ──▶ Step (对话内的一个阶段: 调模型/调技能/工作流节点)
   │                        │
   │  traceId 串联           │  stepId 串联子操作
   ▼                        ▼
 整体耗时/状态/错误信息    单步输入/输出/耗时/状态
```

**设计取舍**：为什么是两层而不是一层"详细日志"？因为排查时需要两种视角——"这次对话整体成功没、卡在哪"用 trace；"卡住的那步到底发生了什么"用 step。单层日志两种都查不利索。

**`catch (Exception ignored)` 为什么是对的**（再次强调）：可观测代码的异常被吞，是因为它的失败**不应**冒泡。但注意——吞之前它通常会 `markStepFailed()`，即把"埋点自身失败"也记录成 step 状态，而不是无声消失。这是"fail-silent 但可审计"。

---

## 8. 多智能体协作：状态机 + 委派回调，而非轮询

`collab/`（16 类）实现了多 Agent 协作。关键架构决策是**用状态机驱动 + 委派回调**，而不是"主线程 sleep 轮询子 Agent 状态"：

```text
  Coordinator ──委派任务──▶ SubAgent A
       │   ▲                     │
       │   └──── 回调结果 ───────┘
       │
       └──委派任务──▶ SubAgent B
                      │
                      └──── 回调结果 ──▶ 汇聚 → 状态机推进
```

状态机大致是：`idle → running → (部分失败→conflict) / (全成功→merged) → done`。

**关键决策**：部分失败进 `conflict` 状态，而不是 `failed`。因为多 Agent 协作里"有一个没回答好"不等于"整件事失败"——协调者可以带着冲突标记继续，让用户决定。把"部分失败"建模成独立状态，比二值成功/失败表达力强得多。

**为什么不用轮询**：轮询要维护定时任务、要处理并发状态读写、要处理 Agent 永远不回的情况。委派回调把状态推进的责任交给"结果回来"这个事件，天然无轮询、无空转。

---

## 9. 可扩展性：加一个新能力要动几处？

这是检验架构好坏的终极问题。在 agent-harness 里加一个"新能力类型"（比如新增一种对话后处理），标准路径是：

```text
1. 在 com.zimo.module.ai.<新包>/ 写领域逻辑
2. 写一个 <NewFeature>AutoConfiguration，@Bean 注册所需组件
3. 把它加进 META-INF/spring/...AutoConfiguration.imports（已在，10 个）
4. 若需后台管理，在 management/ 加 Service + controller/ 加 REST
5. （可选）若需旁路，按 memory/observ 的模式接有界队列或埋点
```

**架构红利**：因为装配被 `autoconfig/`（12 类）集中管理，新能力的接入点**可枚举、可审计**。你不会"忘了把 Bean 注册导致 404"（那是被踩过的坑，现在 autoconfig 就是防这个的）。`ObjectProvider` 用于软依赖——可选能力不装配时主链路不报错。

---

## 10. 与 agent-memory 的架构关系：解耦兄弟，靠 MCP 说话

agent-harness 和 agent-memory 是**兄弟模块，无编译依赖**。harness 通过 `memory/MemoryMcpClient` 走 **MCP 的 HTTP JSON-RPC** 把对话写进记忆库：

```text
agent-harness                         agent-memory
┌──────────────────┐   MCP/JSON-RPC   ┌──────────────────┐
│ ChatTurn-        │ ─── tools/call ─▶│ MemoryMcpEndpoint│
│ MemoryRecorder   │  (memory_write)  │ → L0~L3 存储      │
└──────────────────┘                  └──────────────────┘
        │                                        ▲
        │ 失败隔离（队列满/超时/HTTP 200 但工具级失败）│
        └──────── 绝不阻塞对话 ───────────────────┘
```

**设计亮点**：用 MCP 而非直接 JDBC/Feign 调用，把记忆系统变成**可替换的能力后端**。harness 不关心记忆存在 H2 还是别的，只认 MCP 协议。这也带来一个必须正视的坑（已写入记忆）：**MCP 工具级失败也是 HTTP 200**，只看状态码会把"参数不合法"当"写入成功"——所以 recorder 必须校验返回体内的 `isError` 字段。

---

## 11. 九条架构决策清单（划重点）

| # | 决策 | 解决的问题 | 替代方案（被否） |
|---|------|-----------|----------------|
| 1 | 能力即配置数据，非硬编码 | 智能体/技能/模型可后台增删改 | 代码里写 if-else 分支 |
| 2 | 内存快照 + 顺序化变更 | 读零 DB、写强一致、重启可恢复 | 每次查库 / 引 Redis 双写 |
| 3 | 双通道共用单一闸门 | 校验逻辑不会漏配 | 每个 Controller 各写校验 |
| 4 | 闸门只拦确定性 | 校验可独立测试/限流 | 闸门里塞业务判断 |
| 5 | POST+fetch 流式 | 对话上下文可随请求体传 | EventSource 只 GET |
| 6 | 旁路 fail-silent | 记忆/遥测挂了对话不崩 | 可观测当主链路 |
| 7 | 两层 trace/step | 整体与单步双视角排查 | 单层详细日志 |
| 8 | 协作用状态机+回调 | 部分失败可表达、无轮询 | 主线程 sleep 轮询 |
| 9 | 装配集中可枚举 | 新能力接入点审计得到 | 散落 @Component 各扫各的 |

---

## 12. 给想抄作业的同学的建议

1. **先把"主链路 vs 旁路"画清楚再写代码**。agent-harness 最大的优点就是旁路隔离做得彻底，这是稳定性来源。
2. **配置类数据用内存快照**，别一上来就上分布式缓存。
3. **流式脱敏要建模成两道**，别指望逐块过滤能挡住拼接攻击。
4. **集中装配 + AutoConfiguration.imports** 是防"漏注册导致 404"的 cheapest 防线。
5. **MCP 调用永远校验返回体里的错误字段**，HTTP 200 ≠ 成功。

---

*这篇是架构设计视角，侧重"为什么这么设计"。如果想看每个组件的具体代码怎么写（含源码片段），可以看配套的《拆解一个生产级 AI Agent 运行时》那篇。*

*欢迎在评论区聊聊你做 Agent 平台时踩过的架构坑 —— 尤其是"旁路故障反噬主链路"这种只有在线上才暴露的问题。*
