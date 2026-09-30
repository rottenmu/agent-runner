# 给 AI Agent 造一个「记忆」：四层金字塔 + 双存储 + 可解释打分，全拆给你看

> 关键词：Agent Memory · 记忆系统 · 可解释召回 · 双存储 · MCP · 架构设计复盘

## 一、为什么 Agent 需要「记忆」，以及为什么这很难

大模型本身是无状态的。一次对话里它「记得」你，靠的是把历史消息塞进上下文窗口；对话一结束，或者上下文一超长，它就「失忆」了。

要让 Agent 真正用起来，至少要解决三类记忆问题：

1. **跨会话的长期记忆**：用户偏好、人设、历史约定，换一轮对话还能用。
2. **推理时的主动召回**：模型该「想起」什么，不该等模型自己来问。
3. **记忆会腐坏**：记错了、记重复了、记了敏感信息、记了一堆永远用不上的垃圾——这些都要有治理手段。

市面上的记忆方案，要么只做了「存」（一个 vector store 塞进去完事），要么把「召回」完全甩给模型自己（Pull 模式，模型爱读不读）。我这次做的 `agent-memory` 模块，定位是一个**带强制隔离、可解释打分、失败不反噬、可溯源可审计、读写分离的独立子系统**。

核心不是「存得下」，而是「召回得准、治理得动、出错不崩、出了错能查」。

下面把它从架构到关键代码拆开讲。

---

## 二、整体架构：一个分层、解耦的记忆子系统

```
┌──────────────────────────────────────────────────────────────────┐
│  调用方                                                          │
│  WorkBuddy / OpenClaw (MCP) · agent-harness 对话链路 · 管理 UI · 治理 API │
└───────────────────────────────┬──────────────────────────────────┘
                                │
            ┌───────────────────▼────────────────────┐
            │         agent-memory 模块 (core)         │
            │                                          │
            │  MemoryMcpEndpoint    AiMemoryService    │
            │  (接入层 + 准入)      (唯一写入出口)       │
            │                                          │
            │  MemoryManager         MemoryAwarePromptBuilder │
            │  (治理门面)            (Push 召回注入)     │
            │                                          │
            │  MemoryWritebackQueue  MemoryScorer       │
            │  (异步回写)            (S1 打分)           │
            │                                          │
            │  MemorySpanRecorder (审计 + 埋点)          │
            └───────┬───────────────────────┬──────────┘
                    │                       │
        ┌───────────▼─────────┐   ┌─────────▼──────────┐
        │  H2 OLTP 主库         │   │  Arrow OLAP 副库    │
        │  L0/L1/L2/L3 + span  │   │  L0 宽表            │
        └───────────┬─────────┘   └─────────┬──────────┘
                    │ 游标增量 ETL (每分钟)    │
                    └────────────────────────┘
                            ▼
                   MemoryAnalyticsService (分析)
```

读图要点：

- **运行时读写只走 H2 OLTP**；Arrow OLAP 副库只被后台分析和 ETL 触碰，读写负载严格分离。
- 业务层只依赖 `MemoryStorageFacade` 这个门面接口，底层是 H2 还是 MySQL/RocksDB，业务零感知。
- 所有落库都经过 `AiMemoryService` 这一个写入出口——脱敏、白名单、重要性评估只在此处实现一次。

---

## 三、四层记忆模型：L0~L3 金字塔

记忆不是「一条条平铺的记录」，而是一个分层的金字塔。从底到顶信息密度递增、召回优先级递增：

```
L0 RawLog    原始对话日志
   │         - traceId 全局溯源键，可回放
   │         - 不入召回，只做留痕与溯源
   ▲ traceId
L1 Atomic    原子记忆（不可再分）
   │         - 类型：session_var / user / global
   │         - 参与召回打分与遗忘淘汰
   ▲ l1Ids
L2 Scene     场景块
   │         - summary 摘要 + 关联 l1Ids
   │         - 会话启动优先加载做「骨架」
   ▲ traceId
L3 Persona   用户画像
             - persona / preference / habit / history
             - 跨会话稳定，高重要性来源
```

| 层 | 语义 | 召回 / 淘汰 |
|---|---|---|
| L0 | 原始日志，可溯源、可回放 | 不召回，只留痕 |
| L1 | 原子记忆（会话变量/用户/全局） | 参与召回打分与淘汰 |
| L2 | 按会话聚合的上下文块 | 会话启动优先加载 |
| L3 | 跨会话稳定画像，带 version 演进 | 参与召回，高重要性 |

**一个容易踩坑的点**：L1 的 `global` 类型，它的 `user_id` 列实际存的是 `tenantId`；`session_var` 与 `global` 的 `user_id` 语义和正常用户记忆不同。所以「按 userId 取全部」时必须显式排除这两类，否则会把租户级/会话级数据混入用户记忆，造成跨租户串数据。

---

## 四、双存储引擎：SPI 插拔 + 读写分离

```
业务层 (AiMemoryService / MemoryManager / Analytics)
        │ 只依赖
        ▼
MemoryStorageFacade
        ├── oltp() ──▶ OltpMemoryRepository ──▶ H2OltpStorageProvider ──▶ H2 MVStore 文件库
        └── olap() ──▶ OlapAnalyticsRepository ─▶ ArrowOlapStorageProvider ─▶ Arrow IPC 文件
```

几个设计亮点：

- **SPI 已通用化**：`StorageFacade / StorageProvider<T> / StorageRouter` 是通用存储抽象，能被任意模块复用。换 MySQL/DuckDB 只需实现 Provider 接口 + 注册 Bean + 改一个 `engine` 配置，业务零改动。
- **零 JNI 的 OLAP**：副库用 Arrow + Calcite 纯 Java 实现，没有 DuckDB 那种 JNI 依赖坑。
- **读写分离**：主库扛运行时 CRUD 和召回，副库只做离线的会话/时序/溯源分析，互不干扰。

---

## 五、写入主链路：唯一的出口，不能乱的次序

所有写记忆都走 `AiMemoryService`。下面这段是它的核心——也是整个模块「防坑」最密集的地方：

```java
public Map<String, Object> saveUserMemory(
        String tenantId, String userId, String category, String content, Double importance) {
    requireText(tenantId, "租户标识不能为空");
    requireText(userId, "用户标识不能为空");
    String normalizedCategory = hasText(category) ? category.trim() : "custom";
    if (!isCategoryAllowed(normalizedCategory)) {            // ① 白名单校验
        return Map.of("allowed", false, "reason", "记忆类别不在白名单内...");
    }
    ImportanceScore assessed = importanceScorer.score(content, normalizedCategory); // ② 重要性的评估【脱敏前】
    String safeValue = sanitizeIfEnabled(content);          // ③ 敏感脱敏
    if (!hasText(safeValue)) {
        return Map.of("allowed", false, "reason", "内容为空或全部为敏感信息...");
    }
    String id = IdUtil.fastSimpleUUID().substring(0, 12);
    String traceId = "trace-" + id;
    long ts = System.currentTimeMillis();
    double value = resolveImportance(importance, assessed);
    oltp.saveRawLog(L0RawLog.forInsert(traceId, "user-" + userId, tenantId, ts,
            "system", "USER_MEMORY:" + normalizedCategory + "=" + safeValue, null, null)); // ④ L0 溯源留痕
    if ("persona".equals(normalizedCategory) || "preference".equals(normalizedCategory)) {
        oltp.savePersona(L3Persona.create(id, userId, normalizedCategory, safeValue, 1, ts, tenantId, value));
    } else {
        oltp.saveAtomicMemory(L1AtomicMemory.create(id, traceId, "user-" + userId, userId,
                normalizedCategory, safeValue, ts, tenantId, value));
    }
    // 返回体诚实：importanceSource = scored | explicit
    Map<String, Object> recordMap = record(id, safeValue, ts, Map.of("userId", userId, "category", normalizedCategory));
    return result(applyImportance(recordMap, importance, assessed), content, safeValue);
}
```

**最不能颠倒的次序**：重要性评估必须在**脱敏前的原文**上做。如果先脱敏再打分，`138****8000` 已经不匹配手机号规则，敏感降权会静默失效——记忆照样被当成高权重存进去。

**返回体要诚实**：返回 `importanceSource = scored | explicit`。显式指定重要性时不回显自动信号，否则调用方会误以为存进去的分是「系统算的」，掩盖了「我的入参到底生效没有」这个关键问题。

---

## 六、记忆感知引擎：从「被动库」到「主动记忆」

这是模块从「被动记忆库」变成「主动记忆」的核心。和 `memory_read/search` 的 **Pull**（模型自己决定读）不同，这里是 **Push**（推理前主动注入）。

### 6.1 召回流水线

```
recallFragment(scope, query, traceId)
   └─▶ MemoryManager.recall
          └─▶ pageUserMemory (L3 ∪ L1 UNION, 租户+用户 SQL 下推)
                 └─▶ 逐条 MemoryScorer.score
                        └─▶ 稳定排序: score → lastAccess → ts → id
                               └─▶ 截取 topK
                                      └─▶ RecallResult (返回全量 topK, 阈值留给注入层)
                                             └─▶ composeFragment: 仅 ≥ minScore 注入
                                                    └─▶ 写 L0 注入留痕 (source=context_injection)
```

三条注入铁律（都是踩出来的）：

1. **冲突声明是硬约束**：片段首行必须是「仅供参考，若与用户当前陈述冲突，以用户当前陈述为准」。否则用户改了偏好仍被反复按旧偏好处理，还不知道为什么改不掉。
2. **空召回不注入任何东西**：连标题都不加。注入「暂无记忆」占位文本会污染上下文、破坏 Prompt 缓存命中。
3. **注入进系统提示词，不插 messages**：早期版本自己构造 SYSTEM 消息插进 messages，被框架拒绝（「Hooks must not inject SYSTEM messages」），表现成「每次命中记忆的对话都失败」。

### 6.2 异步回写队列

回复返回之后，链路已经收尾，这时候再做的事实抽取回写，绝不能再向链路发 span。所以它走一个独立的异步队列，只写审计表：

```java
public class MemoryWritebackQueue implements AutoCloseable {
    public static final int MAX_PENDING = 1000;          // 队满即拒绝新任务

    private final BlockingQueue<Task> queue = new LinkedBlockingQueue<>(MAX_PENDING);
    private final ExecutorService worker;

    public MemoryWritebackQueue(MemoryManager memoryManager, MemorySpanRecorder recorder) {
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "agent-memory-writeback");
            t.setDaemon(true);                           // 守护线程：退出不卡进程
            return t;
        });
        this.worker.submit(this::consume);
    }

    public int submit(Task task) {
        boolean accepted = queue.offer(task);
        if (!accepted) {
            dropped.incrementAndGet();
            log.warn("[memory-writeback] 队列积压已达上限 {}, 丢弃本次回写 traceId={}", MAX_PENDING, task.traceId());
            recorder.recordDroppedWriteback(...);        // 丢弃也必须可归因
            return -1;
        }
        return queue.size();
    }
    // 单消费者：handle() 内 extractFromMessages → 落库 → 写审计；异常只 WARN 不反噬
}
```

几个取舍：

- **带消息体而非只带 traceId**：会话侧 L0 用的是 `convo-` 前缀自生成 traceId，和链路 traceId 不是同一个值。若异步侧按链路 traceId 回查 L0 会一条都查不到——「回写静默空转」。所以任务自带本轮消息（内存构造，不重复落 L0）。
- **背压取舍**：队满时**拒绝新任务**而非挤掉最老的。老任务已等很久，丢弃它等于白付等待成本；而「宁可漏记最近几轮，也不让队列无界增长」是可解释的。
- **失败不反噬**：抽取失败只 WARN，绝不影响已经返回给用户的回复。

---

## 七、S1 可解释混合打分：为什么叫 match 不叫 similarity

召回排谁的优先级高，靠一个纯函数打分器。三项都已归一化到 0~1，加权和天然落在 0~1：

```
score = 0.5 × match      + 0.3 × decay        + 0.2 × importance
match  = 0.6 × bigramOverlap(query, content) + 0.4 × (content 含 query ? 1 : 0)
decay  = 0.5 ^ (Δt / halfLife)          // Δt = now - 记忆最近被访问时间, 量化到整小时
```

```java
public double match(String query, String content) {
    String q = normalize(query);
    String c = normalize(content);
    if (q.isEmpty() || c.isEmpty()) return 0.0;
    double bigram = bigramOverlap(normalizedSet(q), normalizedSet(c));  // |Q∩C| / |Q|
    double contains = c.contains(q) ? 1.0 : 0.0;
    return 0.6 * bigram + 0.4 * contains;
}

public double decay(long lastTouchedAt, long now) {
    if (lastTouchedAt <= 0 || now - lastTouchedAt <= 0) return 1.0;
    long quantized = (delta / DECAY_GRANULARITY_MS) * DECAY_GRANULARITY_MS;  // 量化到整小时
    return Math.pow(0.5, (double) quantized / (halfLifeDays * DAY_MS));
}
```

几个「看似啰嗦但很重要」的点：

- **为什么叫 `match` 不叫 `similarity`**：`bigramOverlap` 是字符级 2-gram 覆盖率，**不是语义相似度**。中文没有空格分词，单字命中会被「的/了/是」这类高频虚词主导（几乎任何句子都能和任意记忆「命中」）；bigram 对虚词贡献天然更小，且零依赖、可离线回归。命名诚实，是这套方案能被信任的前提。
- **纯函数保证可回归**：`MemoryScorer` 不持可变状态、不做 IO、不读时钟（`now` 由调用方传入）。同一输入必然逐位相同——这是「分数可回归」验收标准成立的基础。
- **Δt 量化到整小时**：不量化，`now` 的毫秒抖动会让同一条目两次召回分数在第 10 位小数漂移，使「连续 3 次逐位相同」永不成立。量化代价可忽略（半衰期 30 天，一小时衰减误差 ≈ 0.096%）。
- **权重和在启动期校验**：和 ≠ 1 直接抛 `IllegalStateException` 终止启动，不允许「服务起来了但分数恒为 0」。
- **单实例口径**：召回、合并近似判定、淘汰阈值共用**同一个 `MemoryScorer` 实例**，杜绝「低分不注入却长期留存」与「低分被淘汰」两套互不相容的标准。

```java
// AgentMemoryProperties.Recall —— 启动期就拦住配置错误
public Recall {
    if (weights.size() != 3) throw ...;
    double sum = 0; for (Double w : weights) sum += w;
    if (Math.abs(sum - 1.0) > 1e-6)
        throw new IllegalStateException(
            "weights 的权重之和必须等于 1，当前之和 = " + sum + "。请修正配置后重启；"
            + "服务不会带着无意义的打分继续运行。");
}
```

---

## 八、治理闭环：抽取 / 合并 / 遗忘

`MemoryManager` 在 CRUD 之上编排五类动作，构成记忆的「新陈代谢」：

| 动作 | 组件 | 说明 |
|---|---|---|
| 召回 | `MemoryScorer` | 见上 |
| 抽取 | `FactExtractor` | 规则档默认开，LLM 档默认关 |
| 合并 | `MemoryMerger` | 复用 `match` 做近似判定，同用户+同类别+内容近似归并 |
| 淘汰 | `MemoryEvictor` | 复用同一打分器，低分且超期淘汰，带安全阀 |
| 统计 | `AccessCounter` | 进程内计数，定时批量落库，不阻塞查询 |

**事务边界**：本类不做事务编排。召回是纯读；抽取/合并/淘汰逐条处理且允许部分成功——套事务只会把已成功条目一起撤销，放大数据损失面。

**保护机制**：`importance ≥ 0.9`（或显式标记）的记忆免于遗忘淘汰。

---

## 九、与对话链路集成：每轮对话都真实落记忆

和 `agent-harness`（对话模块）是**解耦的兄弟模块**，无编译依赖，通过 **MCP（HTTP JSON-RPC）** 集成。对话模块用一个 `MemoryMcpClient` 调 `memory_write/read`，把每轮对话真实写入记忆。

会话侧则通过 `AiConversationMemory` 在每轮成功后落 L0 事件流，支持跨重启回放：

```java
public synchronized void appendTurn(String sessionId, String userMessage,
        String assistantMessage, int maxMessages) {
    if (!hasText(sessionId) || maxMessages <= 0) return;
    SessionMemory session = loadSession(sessionId);
    if (session == null) session = new SessionMemory();
    session.messages.addLast(new AiChatMessage("user", userMessage));
    session.messages.addLast(new AiChatMessage("assistant", assistantMessage));
    trimMessages(session.messages, maxMessages);
    session.revision++;
    storeSession(sessionId, session);
    recordTurnEvent(sessionId, userMessage, assistantMessage);  // 落 L0, 可回放
}

private void recordTurnEvent(String sessionId, String userMsg, String assistantMsg) {
    long ts = System.currentTimeMillis();
    String traceId = "convo-" + IdUtil.fastSimpleUUID().substring(0, 12);
    if (hasText(userMsg))
        eventLog.saveRawLog(L0RawLog.forInsert(traceId, sessionId, null, ts, "user", userMsg, null, null));
    if (hasText(assistantMsg))
        eventLog.saveRawLog(L0RawLog.forInsert(traceId, sessionId, null, ts, "assistant", assistantMsg, null, null));
}
```

**写读两侧必须同源**：会话记忆写入的 `sessionId`/`tenantId` 与读取端必须完全一致。两侧任一侧漂移都会造成「面板永久空」却无任何报错——这个坑我专门写过校验脚本来证明鉴别力。

---

## 十、MCP 接入层

```
MCP 客户端 ──POST /api/agent-memory/mcp──▶ MemoryMcpEndpoint
                                            │
                                   MemoryMcpAccessGuard (token / 仅回环)
                                            │
                                   MemoryMcpToolkit: tools/call
                                     memory_write / read / delete / search
                                            │
                                      AiMemoryService
```

- 协议：HTTP JSON-RPC 2.0（`initialize / tools/list / tools/call`）。
- 工具：`memory_write`（session/user/global）、`memory_read`、`memory_delete`、`memory_search`。
- 准入：配了 `agent-memory.mcp-token` 校验 `Authorization`；未配则仅放行回环来源（建议经环境变量注入）。

---

## 十一、12 条核心设计取舍（划重点）

以下是从代码反推出的真正起作用的设计原则，也是这个模块区别于「又一个记忆库」的地方：

1. **隔离维度强制成编译期约束**：`tenantId + userId` 在涉及用户/会话的仓储方法里都是必填，刻意不提供「只有 userId」的重载——漏传在编译期就暴露。
2. **单一写入出口**：所有落库走 `AiMemoryService`，脱敏/白名单/重要性评估只此处实现一次。
3. **记忆是增强能力，失败不反噬**：召回失败、L0 留痕失败、队列满都只 WARN，绝不阻断对话。
4. **阈值在启动期校验**：权重和 ≠ 1、半衰期 ≤ 0、topK ≤ 0 等直接终止启动。
5. **纯函数保证可回归**：`MemoryScorer` 不持状态、不读时钟，`now` 由调用方传入。
6. **同步/异步相位分工**：异步阶段（链路已收尾）绝不发链路 span，只写 append-only 审计表。
7. **审计 append-only**：`memory_span` 不提供 update/delete，「异步是否完成」由是否存在对应 `async` 记录表达。
8. **打分口径单一**：召回、合并、淘汰共用同一个 `MemoryScorer` 实例。
9. **注入层三条铁律**：必声明「以用户当前陈述为准」；空召回不注入任何内容；注入内容必进 L0。
10. **写读两侧必须同源**：`sessionId`/`tenantId` 写读一致。
11. **删除返回真实影响行数**：按 id 删除时 L3 与 L1 两表都尝试。
12. **计数更新不阻塞查询**：访问计数在请求线程只做进程内累加，定时批量落库。

---

## 十二、结语

做一个「能存记忆」的模块很简单，做一个「长期不腐坏」的记忆系统很难。这个模块把大量看似啰嗦的约束（启动期校验、append-only 审计、双维度必填、单实例打分、写读同源）都当成了**为「记忆系统长期不腐坏」付的架构债利息**。

如果只能记住一句话：

> **把「记忆」当成一个带强制隔离、可解释打分、失败不反噬、可溯源可审计、读写分离的独立子系统来建**——核心不是「存得下」，而是「召回得准、治理得动、出错不崩、出了错能查」。

---

*本文基于 `agent-memory` 模块的真实源码整理（四层模型、H2/Arrow 双存储、MCP 接入、S1 可解释打分、治理闭环），文中类名、方法、权重、默认值均来自代码。欢迎在评论区交流 Agent 记忆的工程实践。*
