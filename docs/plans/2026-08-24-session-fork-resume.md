# 会话键级 fork / resume 设计（基于追加式事件日志）

- 日期：2026-08-24
- 范围：`modules/agent-harness`（core + autoconfig）+ `agent-spring-boot-starter`（编排内核）+ 前端 `agent-trace-ui`
- 对齐目标：DeepSeek Harness（dsh）A3「Model-visible means logged」——模型可见的一切必须可由追加式会话日志重建；fork/resume 完全派生自单一事件日志流，不依赖进程内记忆

---

## 一、设计原则

| # | 原则 | 落地 |
|---|---|---|
| P1 | **上下文单一来源** | 对话历史只从 `observ_session_event`（append-only 事件日志，P0 阶段 1 产物）重建，不依赖 `AiConversationMemory` 等进程内/业务记忆 |
| P2 | **会话键级分叉** | fork = 派生**新 conversationId**（`{sourceKey}-fork-{uuid8}`），原会话不变，分叉链独立演进、独立打点 |
| P3 | **断点续跑不换键** | resume = 用**相同 conversationId** 重建历史后续跑，新回复与历史上下文连贯 |
| P4 | **可跨实例还原** | 事件日志落库，任何实例/重启后均可还原上下文 |
| P5 | **零侵入主链路** | 通过 `SessionEventLogService`（已实现 `TraceObserver`）旁路接入，fork/resume 服务不修改主执行链 |

---

## 二、模块与类

### 后端（agent-harness-core `collab` 包）

| 类 | 职责 |
|---|---|
| `SessionForkResumeController` | REST 入口：`POST /api/biz/ai/observ/sessions/fork`、`POST .../resume`、`GET .../{sessionId}/context` |
| `SessionForkResumeService` | 核心：历史重建（`rebuildHistory`）+ profile 解析（`resolveProfile`）+ 续跑执行 |
| `ReplayMessage(role, content)` | 重建消息记录（内部 record） |

### 装配（agent-harness-autoconfig `AiCollabAutoConfiguration`）

- `@Bean SessionForkResumeService`：注入 `SessionEventLogService` + starter `AiAgentService` + `AiAgentManagementService`
- `@Bean SessionForkResumeController`：注入服务
- 类级条件 `@ConditionalOnBean(AiAgentManagementService.class)`——管理服务缺失时整体跳过（与 collab 同模式）

### 前端（agent-trace-ui）

| 文件 | 职责 |
|---|---|
| `src/api/fork.js` | `forkSession` / `resumeSession` / `sessionContext` 三个 API 封装 |
| `src/views/TraceDetail.vue` | 链路详情页：头部「分叉」「续跑」按钮 + 模式对话框（消息输入）+ 结果卡（新会话 ID / 历史条数 / 成功态 / 回复） |

---

## 三、核心流程

### 3.1 历史重建（rebuildHistory）

```
sourceKey（conversationId 或 traceId）
  → listByTraceId(sourceKey)      # 优先精确取流
  → 空则 listBySessionId(sourceKey)   # 跨 trace 聚合
  → 遍历事件：
       END 事件（agent_reply）：inputText=prompt / outputText=response → user/assistant 对
       无 END 的链路退化：STEP(generation) 对兜底
       无 END 时 BEGIN 输入兜底首条 user
  → 去重（lastUser 游标防重复）+ trimHistory 截断（MAX_HISTORY=60）
```

### 3.2 fork

```
请求: {sourceKey, message, tenantId, userId, agentId}
 1. 校验 sourceKey/message/agentId 非空
 2. rebuildHistory(sourceKey) 重建历史
 3. newSessionId = sourceKey + "-fork-" + shortUuid()
 4. resolveProfile(tenantId, agentId) → 管理服务解析 AiAgentProfile（含 model/persona/skillIds/agentType）
 5. AiAgentService.chatWithHistory(message, route(newSessionId, profile), history)
 6. 返回 {mode:fork, sourceKey, conversationId:newSessionId, reply, success, historyCount}
```

### 3.3 resume

```
请求: {sessionId, message, tenantId, userId, agentId}
 1. 校验非空
 2. rebuildHistory(sessionId) 重建历史
 3. chatWithHistory(message, route(sessionId, profile), history)   # 会话键不变
 4. 返回 {mode:resume, sourceKey:sessionId, conversationId:sessionId, ...}
```

### 3.4 profile 解析（resolveProfile）

- 走 `AiAgentManagementService.findEnabledAgentById`，智能体不存在/未启用 → 抛异常拦截
- 组装 `AiAgentProfile(id, tenantId, name, model, persona, skillIds, agentType, agentConfig, true)` 9 参主构造
- 管理服务缺失（测试场景）退化轻量 profile

---

## 四、验证记录

| # | 验证项 | 结果 |
|---|---|---|
| 1 | 定点编译 `agent-harness-autoconfig -am` | ✅ 8s 增量通过 |
| 2 | 全量 `mvnw install -DskipTests` | ✅ 16s 通过 |
| 3 | `GET /sessions/{id}/context` | ✅ 200，历史重建 2 条（user/assistant 对），`userCount/assistantCount` 正确 |
| 4 | `POST /sessions/fork` | ✅ 200，派生新键 `c42255cc96d64ee9-fork-b146da9d`，`historyCount=2` 历史带入 |
| 5 | fork 新键事件独立打点 | ✅ `observ_session_event` 落 BEGIN（新 traceId），分叉链独立 |
| 6 | `POST /sessions/resume` | ✅ 200，`conversationId` 保持 `c42255cc96d64ee9` 不变（同键续跑） |
| 7 | 前端 `npm run build` | ✅ 12.6s，TraceDetail 含「分叉」产物打包成功 |
| 8 | 接口鉴权 | ✅ 未带 token 401 / 带 token 200（sa-token `Authorization` 头） |

> 注：模型调用返回 `AI 智能体调用失败：HTTP 404` 为本机模型 endpoint 无可用推理服务（环境问题），fork/resume 的**机制链路（键派生/历史重建/独立打点/上下文带入）全部验证通过**，与模型可用性解耦。

---

## 五、边界与约束

| 约束 | 说明 |
|---|---|
| 历史上限 | `MAX_HISTORY=60` 条，超长截断尾部（防止上下文爆炸） |
| 数据来源 | 仅事件日志；`listByTraceId` 优先、`listBySessionId` 兜底 |
| 会话键格式 | fork 键含 `-fork-` 标记，可从键名直接识别派生会话 |
| 无 END 退化 | BEGIN/STEP(generation) 兜底，保证最早链路可还原 |
| 管理服务依赖 | 非测试场景必须存在 `AiAgentManagementService`，否则装配跳过、接口 404 |

---

## 六、后续建议

1. fork 结果页支持「跳转到新会话」继续对话（当前仅展示结果卡）
2. context 预览接口可扩展为分页/时间过滤（大会话历史）
3. 模型 endpoint 可用时补一轮完整「fork → 新会话多轮 → 再 fork」链式验证