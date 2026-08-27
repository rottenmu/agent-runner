# 智能体记忆系统与内存管理分析报告

> 状态：分析报告（待评审）
> 日期：2026-08-18
> 范围：ai-agent-spring-boot-starter + module-channel 中智能体记忆与内存管理实现
> 说明：无 codegraph 工具连接，基于静态代码分析（读码 + 调用关系追踪）

## 1. 记忆系统架构总览（三层记忆）

```text
┌─────────────────────────────────────────────────────────────┐
│ Layer 1  进程内会话记忆（AiConversationMemory，内存）           │
│          LinkedHashMap 有界（512 会话）+ 每会话消息队列截断      │
├─────────────────────────────────────────────────────────────┤
│ Layer 2  平台自有持久记忆（AiMemoryService → RocksDB）          │
│          mem/{domain}/{tenant}/{scope}/{subKey}               │
│          ├─ sv 会话变量    ├─ um 用户长期记忆    └─ gm 全局记忆  │
├─────────────────────────────────────────────────────────────┤
│ Layer 3  AgentScope 框架记忆（HarnessAgent + DistributedStore）│
│          astate/{agentId}/{sessionId}/{key}  →  RocksDB        │
│          flush / consolidation / retention 由框架管理          │
└─────────────────────────────────────────────────────────────┘
```

### 各层职责

| 层 | 类 | 存储 | 生命周期 |
|---|---|---|---|
| L1 进程内对话 | `AiConversationMemory` | 内存 LinkedHashMap | 512 会话上限，进程重启即失 |
| L2 平台持久记忆 | `AiMemoryService` + `AiMemoryAgentTool` + `AiMemoryController` + `AiMemorySensitiveFilter` | RocksDB（`mem/` 前缀） | 手动删（无自动过期） |
| L3 AgentScope 状态 | `RocksdbAgentStateStore` + `RocksdbBaseStore`（实现 `AgentStateStore`） | RocksDB（`astate/` 前缀） | flush 600s / retention 90-180 天 |
| 渠道会话（旁路） | `ChanConversation` / `ConversationBridge`（module-channel） | SQLite/MySQL | 会话维度持久化 |

## 2. 各层机制详解

### 2.1 L1 进程内会话记忆（AiConversationMemory）

- **容量控制**：`LinkedHashMap` 有界 512 会话（`trimSessionCount` 队首淘汰）；每会话 `ArrayDeque` 按 `chat-history-limit`（`AiAgentService.appendTurn` 传入）从队首截断；
- **摘要机制**：`snapshot()` 把会话摘要作为首条 system 消息注入；`prepareCompression()` 生成压缩候选（含 `revision` 版本号），`applyCompression()` 原子应用摘要并移除候选消息（带 revision 校验防并发竞态）；
- **同步控制**：全部方法 `synchronized`，线程安全。

### 2.2 L2 平台自有持久记忆（AiMemoryService）

- **三级域**：`sv`（会话变量）、`um`（用户长期记忆，类别 persona/preference/history/custom）、`gm`（全局记忆）；
- **键格式**：`mem/{domain}/{tenant}/{scope}/{subKey}`；记录含 id/content/timestamp/ts/附加元数据，JSON 序列化存 RocksDB；
- **安全**：类别白名单（`memory-whitelist-categories`，空=全放行）；`AiMemorySensitiveFilter` 7 条规则（手机号/身份证/银行卡/邮箱/API 密钥/Bearer/内网 IP）写入前自动脱敏；
- **Agent 工具**：`memory_write`（target=session/user/global + JSON Schema 强约束）、`memory_read` 注册进 HarnessAgent Toolkit；
- **管理端**：`/api/ai/memory`（session/user/global 的 CRUD + 类别过滤 + 敏感规则名展示）。

### 2.3 L3 AgentScope 框架记忆（AiHarnessAgentFactory 装配）

```java
MemoryConfig.builder()
    .flushTrigger(throttled(600s))            // 记忆刷新节流
    .consolidationMinGap(120min)              // 记忆整合最小间隔
    .consolidationMaxTokens(4000)             // 整合 token 上限
    .dailyFileRetentionDays(90)               // 每日文件保留
    .sessionRetentionDays(180)                // 会话状态保留
```

- `DistributedStore`（agentStateStore + baseStore 均基于 RocksDB）实现跨会话/跨实例状态共享；`AiHarnessAgentFactory` 仅在 `memory.enabled && storageService != null` 时装配。

## 3. 内存管理机制总结

| 机制 | 实现 | 阈值 | 可配置 |
|---|---|---|---|
| 会话数上限 | LinkedHashMap 淘汰 | 512 | ❌ 硬编码 |
| 单会话消息数 | 队首截断 | `chat-history-limit` | ✅ |
| 摘要压缩 | revision 版本化候选 | `triggerMessages > recentMessages` | ❌ 未接线 |
| 记忆整合 | AgentScope consolidation | 120min / 4000 tokens | ✅ |
| 记忆刷新 | throttled flush | 600s | ✅ |
| 保留期 | daily 90d / session 180d | — | ✅ |

## 4. 关键发现与风险

### 🔴 P0 风险

1. **L1 压缩机制未接线（死代码）**：`prepareCompression/applyCompression` 无任何 main 调用方（仅测试引用）。长对话超过 `chat-history-limit` 时**直接丢弃最老消息、无摘要保留**，事实与约束信息静默丢失——机制实现完整（revision 防竞态、摘要注入）但未启用。

2. **L2 持久记忆无自动清理**：`mem/` 前缀由平台自身管理，**不受 AgentScope retention 覆盖**。`memory-write` 工具无条数/频率/体积限制，LLM 可无限写入 → RocksDB 持续膨胀，无清理任务兜底（仅手动 delete）。

### 🟡 P1 关注

3. **进程内记忆多实例不一致**：`AiConversationMemory` 为进程内缓存，多副本部署下各实例会话记忆独立（L3 状态虽共享，L1 对话缓存不共享）——会话切换节点后上下文断裂。

4. **同步 IO + 全量遍历**：`listRecords` 全量列出 + JSON 反序列化，用户长期记忆量大时查询延迟线性增长；无分页/游标。

5. **敏感过滤误伤**：`api_key` 规则 `[A-Za-z0-9_-]{20,}` 会脱敏任意 20+ 位连续字母数字串（含正常业务文本）。

6. **AiAgentService 每次实例化新 AiConversationMemory**（`new AiConversationMemory()`）：依赖注入缺失，512 上限硬编码、不可配置，且并发场景下无全局共享。

## 5. 优化建议（按优先级）

### P0-1 接通上下文压缩（收益最高）
- `AiAgentService` 对话流程接入 `prepareCompression → LLM 摘要 → applyCompression`（条件：消息数 > `chat-history-limit` × 系数）；
- 阈值参数化：`ai.agent.conversation-compression-trigger / recent`，默认 trigger=2×limit、recent=limit 的 1/4。

### P0-2 L2 记忆治理
- 新增 `mem/` 前缀的保留策略：按 `memory-session-retention-days`（会话变量）与新增 `memory-user-retention-days`（用户记忆）定期清理；
- `memory_write` 增加条数上限（如用户记忆每类别 ≤ 200 条，超出淘汰最旧）+ 写入频率节流。

### P1-1 进程内记忆可配置 + 可观测
- `AiConversationMemory` 上限从硬编码 512 提取为配置（`ai.agent.conversation-max-sessions`）；
- 统计暴露（会话数/消息数/摘要数）接入现有指标。

### P1-2 性能
- `listRecords` 增加分页/限制参数（limit + offset 或游标）；RocksDB 前缀扫描改为 key 采样。

### P1-3 敏感过滤精度
- `api_key` 规则改为 `sk-[A-Za-z0-9]{16,}` 前缀强约束，移除裸 `{20,}` 误伤规则（或要求上下文前后缀）。

## 6. 结论

平台记忆体系三层分工清晰：L3 交给 AgentScope 框架（整合/保留完善）、L2 提供可控的持久记忆工具（安全过滤完备）、L1 提供低延迟会话缓存。**主要短板在 L1 压缩未启用（长对话丢信息）与 L2 无自动清理（存储膨胀）**，两者均为低风险增量改动，建议优先实施。

## 7. P0 实施结果（2026-08-18 已完成，用户确认）

### P0-1 上下文压缩接通（legacyChat 链路）

- `AiAgentService` 新增 `tryCompressConversation()`：appendTurn 后检查
  `prepareCompression`（触发=20 条、保留=8 条，配置已存在），满足时调用
  `summarize()` 生成摘要并 `applyCompression` 原子应用；
- 摘要为独立 LLM 调用（不携带会话历史，避免递归膨胀），失败/过期时保持会话
  记忆不变（revision 校验）；
- 复核修正：**HarnessAgent 主链路压缩本已启用**（AgentScope `CompactionConfig`
  trigger=20/keep=8，AiHarnessAgentFactory.applyCompaction）——缺口仅在旧版
  兼容链路 legacyChat，本次补齐。

### P0-2 L2 持久记忆治理

- `AiAgentProperties` 新增 `memoryUserRetentionDays`（180）与
  `memoryMaxRecordsPerCategory`（200）及 getter；
- `AiMemoryService`：
  - `saveUserMemory` 写入后执行 `enforceCategoryLimit`（每类别超 200 条淘汰最旧）
    + `purgeExpiredUser`（超 180 天惰性清理）；
  - `saveSessionVar` 写入后执行 `purgeExpiredSession`（会话变量超 180 天清理）；
- 配置项已写入 `application.yml`。

### 验证

- `mvnw verify` 全量 BUILD SUCCESS（含 AiConversationMemoryTest）；
- 后端重启（9900）后记忆接口回归：写入 `13812345678` → 读取 `138****5678`
  （脱敏生效、sensitiveMasked=true）、删除正常；测试数据已清理。
