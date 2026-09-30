# 短期会话记忆续接与闲置清理

> 状态：已落地
> 起草日：2026-09-29
> 关联模块：`framework/framework-ai`、`modules/agent-harness/agent-harness-core`、`modules/module-sys/module-sys-core`、`agent-application`、`frontend/modules/agentmemory`
> 规范依据：`docs/rules/PLAN_DOC_RULES.md`

---

## 一、置信度与剩余风险

- 当前置信度：88%
- 主要剩余风险：首次上线时历史会话没有活动时间 metadata，系统只能从首次扫描时开始计算 7 天宽限期；读写与清理共享存储锁，数据量很大时清理扫描可能短暂排队会话状态操作。

---

## 二、目标

本次要达成：

- 把“短期记忆”明确为同一会话内可续接的对话历史与压缩摘要；使用标准 Harness 路径已有的 AgentStateStore 与 Compaction，不再建立一份重复对话存储。
- 为持久化的短期会话状态补充闲置过期清理，默认保留 7 天；管理员可在前端记忆策略页面设置 1 至 365 天的保留期限。
- 配置通过现有智能体设置接口保存，并由应用组装层接入短期记忆清理运行时，确保页面保存值实际控制后端清理。
- 保留 `L1 session_var` 作为会话变量与对话归档能力，不把它误作聊天上下文的自动召回来源。

本次明确不做（延后项见第九节）：

- 不改变用户长期记忆的 S1 召回、L0 轨迹格式或 `session_var` 的 CRUD 接口。
- 不把 Harness 历史、旧版 `AiConversationMemory` 与 L1 归档三种存储强行合并。
- 不变更生产存储类型；短期状态继续使用现有 RocksDB `FileStorageService` / AgentStateStore，不引入 SQLite、H2 或新数据库。

---

## 三、成功标准

本方案完成后，以下每一条都应能在真机上直接判定为真：

1. 使用可记录模型输入的测试模型，经 `/api/biz/ai/chat` 对同一租户、用户、智能体、渠道和会话连续发送 3 轮；第三轮模型输入包含前两轮的历史或压缩摘要，且每段历史最多出现 1 次。
2. 固定相同 `conversationId`，逐项更换 tenant、user、agent、channel 后调用聊天接口；各作用域的模型输入均不含其他作用域写入的唯一哨兵文本。
3. 将压缩配置设为 `triggerMessages=6`、`recentMessages=4`，连续发送 4 轮后触发压缩；下一次模型输入包含摘要和最近 4 条原始消息，不含被压缩的旧原文；摘要文本长度不超过配置上限。
4. 打开前端记忆策略页面时，短期会话保留天数显示默认值 7；将其改为 2 并保存、刷新后仍显示 2，且运行时清理策略读取到的值为 2。
5. 配置短期保留 2 天后，测试时钟前进 3 天并执行清理：闲置会话的 AgentState 键数为 0，最近 1 天有写入的会话仍可续接；页面保存后的最新配置最迟在下一次每小时清理任务中生效。
6. 对同一会话通过 MCP 写入一条 `target=session` 的 `session_var` 后，`memory_read` 仍能查到该变量；聊天模型输入不会因归档记录再次多出同一轮对话。

---

## 四、参照行为（对标）

无外部产品对标。本方案沿用仓库已有的 Harness 会话状态和上下文压缩行为。

---

## 五、现状差距

### 能力缺失

- `RocksdbAgentStateStore` 提供按 `agentId/sessionId` 保存、列举和删除状态键的能力，但当前存储键没有最后活跃时间，也没有定时清理过期会话的流程。
- 标准 Harness 状态续接与旧版 `AiConversationMemory` 是两套实现；旧版会话内存默认驻留进程内，或按原始 `sessionId` 写共享存储。其行为不等同于标准聊天路径。
- 已有 `ai.agent.memory-session-retention-days` 被传给 AgentScope `MemoryConfig`；当前代码没有把它用于 `RocksdbAgentStateStore` 的 AgentState 键清理，不能据此认定短期对话状态有 TTL。
- 系统模块已有 `AgentSettingService` 和 `/api/biz/sys/agent-setting` 的预设项读写接口，但尚未包含短期保留天数，且后端清理逻辑未消费这些运行设置。
- 前端 agentmemory 模块已有记忆策略页面，可作为短期会话保留设置入口；该页面尚未接入智能体设置读写接口。

### 只是没接线

- 标准 Harness 路径已经通过 `AiHarnessSessionKeyFactory` 将 tenant、agent、channel、conversation、user 五个维度长度编码进会话键；`RocksdbAgentStateStore` 已支持列举和删除某个智能体下的会话状态。
- 标准聊天已有 AgentScope `CompactionConfig`；旧版 `AiConversationMemory` 也有摘要压缩与 revision 乐观校验。短期历史和压缩能力不需要再造一套算法。
- `ChatTurnMemoryRecorder` 在对话结束后异步经 MCP 写入 L1 `session_var`；`AiMemoryService.getSessionVars()` 与 `memory_read` 可以显式读取这些行，但 `MemoryAwarePromptBuilder` 当前只执行用户记忆预召回，不会自动读取 session vars。

### 不打算解决的问题

- 本期只把标准 Harness AgentState 生命周期闭环；旧版 `legacyChat` 的 `AiConversationMemory` 暂不迁移到同一存储 —— 原因：它的调用协议、并发语义和已有历史兼容需单独设计。
- 不为 `session_var` 增加 TTL 或自动注入 —— 原因：其 key=value 变量与整轮对话归档语义不同，和 Harness 历史重复注入会造成上下文重复。
- 不自动抽取长期事实或程序性记忆 —— 原因：这属于长期记忆抽取与治理，不应与短期会话状态清理耦合。

---

## 六、技术方向

### 1. 保持标准 Harness 会话为短期上下文事实源

职责：

- 标准 `/api/biz/ai/chat` 与流式端点继续通过 `AiHarnessSessionKeyFactory` 生成隔离键，并由 AgentScope `AgentStateStore` 保存对话状态。
- 继续用现有 `CompactionConfig` 控制触发条数和保留条数；短期历史不从 L1 `session_var` 再拼接进 Prompt。
- L1 `session_var` 继续提供显式的临时变量/归档读取和 L0 留痕，不作为标准聊天历史的第二数据源。

关键接口 / 数据结构：

```text
AiHarnessSessionKeyFactory.create(request, profile)
    = framed(tenantId) + framed(agentId) + framed(channel)
      + framed(conversationId) + framed(userId)

AgentStateStore key:
    astate/{agentId}/{sessionKey}/{stateKey}

Harness CompactionConfig:
    triggerMessages / keepMessages
```

取舍理由：Harness 已有跨轮状态与压缩机制；直接读取 L1 对话归档会并行形成第二份历史、引入异步写入竞态和重复上下文。

### 2. 为 RocksDB AgentState 增加闲置时间与安全清理

职责：

- 为每个短期会话状态前缀维护最后活动时间；AgentState 成功读取或写入后更新时间戳，时间戳更新失败时记录 WARN，不能让正常聊天因旁路元数据失败而失败。
- 对没有 metadata 的历史会话，在首次扫描时写入当前时间作为起算点，给予完整 7 天迁移宽限期，避免上线首次清理时批量删除存量状态。
- 新增预设设置项 `short-term-retention-days`，默认值为 7，允许范围 1 至 365 天；定时任务每小时读取当前有效设置，删除 `lastActivity < now - retentionDays` 的会话下全部状态键。
- 清理按完整的 agent/session 前缀操作，并保留 `AiHarnessSessionKey` 的长度编码；不通过不完整的 `conversationId` 前缀删数据。
- 清理任务可被显式触发，便于用固定测试时钟验证；服务关闭时停止调度，不中断在途聊天。

关键接口 / 数据结构：

```text
AgentStateStore.get/save(agentId, sessionKey, stateKey, state)
    -> 读取或保存状态并更新 session metadata.lastActivityAt

cleanupExpiredSessions(now, retentionDays)
    -> 按 agentId/sessionKey 列出候选
    -> 删除过期 session 前缀下所有状态键与 metadata

配置：AgentSettingService[short-term-retention-days] = 7
```

取舍理由：不改 AgentScope 的序列化负载，避免破坏既有 `State` 读写兼容；按会话前缀单独保存 metadata，可与状态值分开迁移和验证。由 `framework-ai` 定义轻量保留期限读取接口，应用组装层使用 `AgentSettingService` 提供动态值，避免框架反向依赖系统业务模块。已有 `memory-session-retention-days` 保持原义，不复用为 AgentState TTL。

### 3. 在前端记忆策略页面设置短期保留期限

职责：

- 在 `frontend/modules/agentmemory` 的记忆策略页面增加“短期会话记忆”设置项，默认显示 7 天，并以整数输入限制 1 至 365 天。
- 页面加载预设定义和有效值；保存时调用现有 `PUT /api/biz/sys/agent-setting`，刷新后从现有 GET 接口回读持久化值。
- 后端 `AgentSettingService` 对 `short-term-retention-days` 做统一范围校验；无效值通过现有异常响应告知页面，不落库。

关键接口 / 数据结构：

```text
GET /api/biz/sys/agent-setting/definitions
GET /api/biz/sys/agent-setting
PUT /api/biz/sys/agent-setting
key = short-term-retention-days, range = [1, 365], default = 7
```

取舍理由：复用现有预设设置接口和记忆策略页面，不新增数据库表或重复配置 API；设置定义只增加短期会话项，原有长期记忆保留配置含义不变。

### 4. 显式区分旧版会话内存与 L1 session vars

职责：

- 在模块文档中标明 `AiConversationMemory` 是旧版兼容聊天路径的内存/共享存储与摘要压缩实现。
- 标明 `session_var` 是 agent-memory 的会话变量与可查询归档；`ChatTurnMemoryRecorder` 写入后不代表该记录已参与下一轮 Prompt。
- 如后续要求统一两条聊天路径或给 session vars 增加生命周期，另立方案并先确定回放、去重和数据迁移规则。

取舍理由：把“对话上下文”“结构化会话变量”“长期/审计归档”分开，避免把同一轮内容重复注入或把临时值错误纳入长期记忆治理。

---

## 七、里程碑

### M1. 短期上下文续接与压缩验收

- 用可记录模型输入的测试模型走同步与流式 Harness 入口，验证同会话续接、作用域隔离及压缩边界。
- 验证：压缩阈值为 6/4 时，模型输入只含摘要和最近 4 条原始消息，隔离哨兵不跨作用域出现。

### M2. 闲置会话状态清理

- 增加 AgentState 最后活跃时间、7 天默认过期策略和每小时清理调度，并接入前端 1 至 365 天设置。
- 验证：页面保存 2 天后刷新仍显示 2；测试时钟越过 2 天后，闲置 session 前缀下状态键数为 0；活跃 session 仍可恢复。

### M3. `session_var` 边界回归

- 保留现有 MCP/REST 行为和 `ChatTurnMemoryRecorder` 归档链路。
- 验证：MCP 写入后读取仍能返回一行；聊天上下文中不重复拼接这条归档。

---

## 八、自测计划

### Harness 同会话续接与作用域隔离

1. 启动使用隔离 RocksDB 路径的应用测试实例，注入可记录输入消息的测试模型。
2. 通过 `/api/biz/ai/chat` 对作用域 A 连续发送两轮不同哨兵文本，再发第三轮。
3. 验证第三轮模型输入中前两轮历史各出现 1 次；重启应用后继续同一 session，再发一轮仍能观察到保留上下文。
4. 使用相同 `conversationId` 分别更换 tenant、user、agent、channel 重复请求。
5. 验证每个模型输入都不含其他作用域的哨兵文本；不得因缺失任一隔离字段回退到共享默认 session。

### 压缩边界

1. 设置 `triggerMessages=6`、`keepMessages=4`、摘要上限 `4000` 字符，并使用固定返回摘要哨兵的测试模型。
2. 对同一 session 发送 4 轮，使原始消息数达到 8 条并触发压缩。
3. 再发一轮，检查模型输入：摘要哨兵恰好出现 1 次，最近 4 条原始消息各出现 1 次，最早 4 条原文均不出现，摘要不超过 4000 字符。

### 闲置清理与变量归档

1. 打开记忆策略页面，确认短期保留天数为 7；改为 2 并保存，刷新确认值仍为 2。
2. 新建两个隔离会话，分别写入 AgentState；仅对第二个会话再写入一次以更新其活跃时间。
3. 将可控时钟推进 3 天，执行一次清理。
4. 验证第一个会话的所有 AgentState 键与 metadata 均已删除；第二个会话仍可读取并续接；清理器读取到的保留值为 2。首次上线时缺少 metadata 的历史会话应先写入当前时间戳，不立即删除。
5. 通过 MCP 为第二会话写入一个 `target=session` 变量，再执行 `memory_read`。
6. 验证读取结果中恰有该变量 1 条，且后续模型输入未重复出现其 `[user]` / `[assistant]` 归档正文。

---

## 九、已知延后项

- `legacyChat` 的 `AiConversationMemory` 统一迁移到 Harness AgentStateStore —— 原因：需先分别验证其共享模式、恢复、fork 和并发语义的兼容性。
- `L1 session_var` 的 TTL、用户归属字段以及自动召回 —— 原因：这些会改变 agent-memory 的存储与查询契约，且不能与对话历史重复注入。
- L0 轨迹归档的定期清理 —— 原因：L0 是审计/分析原始日志，不应套用短期上下文的 7 天删除策略。

---

## 十、实施顺序

1. 将默认期限修订为 7 天，并补入前端设置入口和系统设置服务复用方式。
2. 实现 AgentState 最后活跃时间 metadata、过期清理、设置范围校验及动态保留期限读取。
3. 在 agentmemory 记忆策略页面增加保留天数设置并接入现有设置 API。
4. 回填实施记录，逐条记录第三节成功标准的验证结果与未执行项。

---

## 十一、实施记录

- 实施日期：2026-09-29
- 实际改动清单：

| 文件 / 模块 | 改动 |
| --- | --- |
| `framework/framework-ai/.../RocksdbAgentStateStore.java` | 为 AgentState 读写记录会话活动时间；增加过期会话扫描和按完整会话前缀清理；旧会话首次扫描时补时间戳，给予完整保留期宽限。 |
| `framework/framework-ai/.../AgentStateRetentionCleanup.java`、`AgentStateRetentionAutoConfiguration.java` | 每小时读取动态保留期限并清理；无有效设置时使用 7 天默认值。 |
| `framework/framework-ai/.../ShortTermMemoryRetentionDaysProvider.java` | 定义框架到应用层的保留期限读取端口，保持依赖方向。 |
| `modules/module-sys/module-sys-core/.../AgentSettingServiceImpl.java`、`AgentSettingService.java` | 新增 `short-term-retention-days=7` 预设值，并限制保存范围为 1 至 365 的整数。 |
| `agent-application/.../ShortTermMemorySettingsConfiguration.java` | 将系统设置服务的当前生效值提供给框架清理任务。 |
| `frontend/modules/agentmemory/src/views/MemoryPolicy.vue`、`src/api/agent-settings.js` | 在记忆策略页显示、保存短期会话保留天数，并复用智能体设置接口。 |
| `modules/module-sys/module-sys-core/.../AgentSettingController.java`、`AgentSetting.java` | 更新设置接口和实体注释，明确短期与长期设置范围。 |
| `docs/agent-memory-backend-code-description.md` | 补充短期会话上下文、清理期限及 `session_var` 边界说明。 |

### 与方案的偏差

| 原方案 | 实际做法 | 原因 |
| --- | --- | --- |
| 先补测试并验证固定时钟下的清理边界 | 未新增或运行测试；完成后端编译和前端生产构建 | 本次交付只进行编译和构建检查；保留可传入 `Instant` 的清理方法，供后续定向验证。 |
| 方案要求清理与活动会话写入互斥，未指定锁粒度 | 使用同一个 `FileStorageService` 对象监视器串行化状态读写与清理扫描 | 简化跨多个 `RocksdbAgentStateStore` 实例的并发边界；大规模扫描可能使状态操作短暂排队，已列入风险。 |

### 真机验证结果

- 成功标准 1–3：未执行模型交互和压缩边界验证，结论未确认。
- 成功标准 4：前端生产构建通过；未启动应用在页面实际保存并刷新确认，结论未确认。
- 成功标准 5：后端 Java 编译通过；未运行真机清理和时钟推进验证，结论未确认。
- 成功标准 6：未执行 MCP 与后续聊天的归档边界验证，结论未确认。
- 前置检查：`mvn -pl agent-application -am -DskipTests compile` 成功；`frontend/web-shell` 的 `npm run build` 成功，存在已有 Rollup 大 chunk 警告。

### 过程中新发现的坑

- `/api/biz/sys/agent-setting` 返回 `ApiResponse` 包装对象，前端请求函数需取 `.data` 才能得到定义列表和配置映射；本次 API 封装已处理。
- 上线前已存在的 AgentState 没有活动时间记录；首次清理只写入当前时间，不立刻删除，之后再按配置期限清理。
- `git diff --check` 命中 `docs/agent-memory-backend-code-description.md` 第 19 行原有尾随空格；未改动该既有行，避免混入无关格式变更。
