# 会话轨迹（Trajectory）仅追加事件流 — 方案

## 背景与目标

**理念来源**（用户提供）：模型看到的一切都写入**仅追加设计的会话日志**——包括系统提示词、思维链、工具调用与结果、子 Agent 调度、每一次上下文注入；在 **Trajectory 视图**中按来源查看；**恢复、分叉、检索与回放共享同一份事件流**。

**当前差距**（agent-memory 模块）：

| 理念要求 | 现状 | 差距 |
|---|---|---|
| 全量记录模型看到的一切 | L0 仅记录 user/assistant 对话 + 少量 system 事件（SESSION_VAR/USER_MEMORY） | 系统提示词、思维链、工具调用与结果、子 Agent 调度、上下文注入均未落 L0 |
| 按来源查看 | L0 只有 `role`（user/assistant/tool/system）单维度 | 缺 `source` 分类维度（system_prompt / chain_of_thought / tool_call / tool_result / sub_agent / context_injection） |
| 仅追加设计 | H2 存储支持按 id 删除 | 缺 append-only 约束保障（审计可信度） |
| Trajectory 视图 | 溯源分析页仅按 traceId 查 role 时间线 | 无按来源筛选/分组、无事件详情展开 |
| 恢复/分叉/检索/回放共享事件流 | restore/fork 已基于 L0 ✓ | 补充检索能力（按 source 检索） |

## 目标

将 L0 升级为**仅追加 Trajectory 事件流**：全量采集模型可见输入输出，按来源（source）分类，前端提供 Trajectory 视图，恢复/分叉/检索/回放统一基于该事件流。

## 设计

### 1. L0 模型扩展（追加 source 维度，向后兼容）

`L0RawLog` 新增 `source` 字段（String，可空，兼容历史数据）：

```
source 枚举（建议）:
  user_message       用户消息
  assistant_message  助手回复
  system_prompt      系统提示词（会话初始化注入）
  chain_of_thought   思维链
  tool_call          工具调用（含参数）
  tool_result        工具执行结果
  sub_agent          子 Agent 调度（含任务/结果摘要）
  context_injection  上下文注入（历史摘要、检索片段、记忆注入）
  system_event       系统事件（SESSION_VAR/USER_MEMORY 等现有）
```

`role` 保留（user/assistant/tool/system），`source` 是其细化：role 表达"谁说的"，source 表达"这是什么"。

### 2. Append-only 保障

- L0 表**移除删除入口**：现有代码已无 L0 删除 API，新增审计校验（repository 层拒绝 deleteRawLog，抛 UnsupportedOperationException）
- 文档标注 L0 为仅追加（insert-only），ETL 同步游标基于自增 id 不受影响

### 3. 采集点（新增 TrajectoryRecorder 组件）

新组件 `TrajectoryRecorder`（agent-memory-core，注入 OltpMemoryRepository），提供批量落盘 API：

```java
recordSystemPrompt(sessionId, tenantId, traceId, prompt)
recordChainOfThought(sessionId, tenantId, traceId, content)
recordToolCall(sessionId, tenantId, traceId, toolName, args, result)
recordSubAgent(sessionId, tenantId, traceId, agentName, task, resultSummary)
recordContextInjection(sessionId, tenantId, traceId, segment)
```

接入点（starter 侧 Agent 调用链，通过事件/钩子采集，不侵入 AgentScope 底层）：
- **系统提示词**：Agent 会话创建/调用入口
- **思维链**：推理循环中的中间思考
- **工具调用与结果**：现有工具钩子（ToolHook）扩展
- **子 Agent 调度**：多智能体协作服务（collab）调用点
- **上下文注入**：记忆/检索注入点

采集失败不影响主流程（try-catch + 降级日志）。

### 4. Trajectory 视图（前端溯源分析页升级）

`TraceAnalysis.vue` 升级为 Trajectory 视图：
- **来源筛选 chips**：全部 / 系统提示词 / 思维链 / 工具调用 / 工具结果 / 子 Agent / 上下文注入 / 消息
- **时间线**：保留现有事件链，事件条目显示 source 徽标（mono，中性灰/语义色区分）
- **详情展开**：工具调用显示参数与结果（可折叠），子 Agent 显示任务摘要
- 兼容现有 `?traceId=xxx` 直达与 trace 查询

### 5. 统一事件流支撑

- 恢复：`restore(sessionId)` 已按 L0 回放 ✓（补充：只回放 user/assistant source，与现状一致）
- 分叉：`fork` 已复制消息并补记 L0 ✓
- 检索：新增按 source + sessionId + traceId 组合查询接口（`GET /agent-memory/analytics/trace?source=...`）

## 改动文件清单

| 文件 | 改动 |
|---|---|
| `model/L0RawLog.java` | 新增 source 字段 + forInsert 重载 |
| `storage/OltpMemoryRepository.java` | 声明 source 检索接口 |
| `storage/impl/H2OltpMemoryRepository.java` | 建表/迁移（source 列）+ 检索实现 + append-only 校验 |
| `memory/TrajectoryRecorder.java`（新增） | 六类事件采集组件 |
| `chat/AiConversationMemory.java` | appendTurn 记录 source（user_message/assistant_message） |
| `autoconfig/AgentMemoryAutoConfiguration.java` | 装配 TrajectoryRecorder |
| `analytics/*`（OLAP 同步） | L0 source 列同步 |
| 前端 `views/TraceAnalysis.vue` | Trajectory 视图（来源筛选 + 徽标 + 详情展开） |
| 前端 `api/memory.js` | 来源筛选参数 |
| SQL 迁移脚本 | `V*.sql`（L0 表加 source 列，MySQL 版本脚本） |

## 验证方式

1. 后端：单元测试覆盖 TrajectoryRecorder 六类事件落库 + append-only 拒绝删除 + source 检索
2. 前端：溯源分析页按来源筛选、详情展开、traceId 直达回归
3. 兼容性：历史 L0 数据（source 为 null）在 Trajectory 视图正常展示为"消息"类
4. `mvn clean verify` + 前端 build 通过
