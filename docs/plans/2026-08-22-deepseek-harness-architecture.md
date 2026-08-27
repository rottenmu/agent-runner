# DeepSeek Harness 原理与架构整理

> 来源：https://deepseek-harness.github.io/deepseek-harness/reference/（架构 / Agent 生命周期）
> 日期：2026-08-22 | 定位：DeepSeek Harness（dsh）—— 可组合的智能体运行时框架

## 一、是什么与解决什么问题

**DeepSeek Harness（dsh）** 是一个以 **Cordis 插件框架**为底座的智能体运行时：**产品的每一部分都是插件**——模型适配器、工具注册表、会话日志、agent loop 本身均可从配置替换，**不存在需要打补丁的特权内核**。扩展方式是把插件挂载到其他插件旁边，注册是副作用、随插件卸载自动撤销。

## 二、基础框架：Cordis

| 概念 | 说明 |
|---|---|
| **插件树** | 运行中的 dsh 是一棵插件树，启动时按序叠加组合包 |
| **共享上下文（ctx）** | 插件向共享上下文贡献服务（`ctx.sessions` / `ctx.tools` / `ctx.llm` 等） |
| **类型化事件** | 会话事件 / Agent 事件 / 能力事件三类事件域 |
| **可逆副作用** | 注册都是副作用，插件卸载时撤销 |

## 三、启动组装：Profile × 组合包 × Patch

```
运行配置 = 空条目列表
  ← 按 profile 顺序叠加每个组合包（bundle）
  ← profile 的 cordis.patch.yml
  ← home 级 cordis.patch.yml
  ← 任意 --patch overlay
```

- **Profile**：Harness home 中的具名组装（列出组合包 + 树外插件 + 用户 patch）；`web` / `headless` 随发行版交付
- **组合包（bundle）**：Cordis 配置项 + 挂载代码的分发格式，可被上层 patch
- **分层**：`dsh-base`（模型/工具/持久化/沙箱/审批/设置/凭据/遥测）→ `dsh-web-app`（浏览器应用）或 `dsh-headless`（一次性运行器，无服务器）
- 查看实际配置树：`dsh --profile web --dump-config`

## 四、核心包（向 ctx 贡献内容）

| 包 | 职责 | ctx 键 |
|---|---|---|
| `core/session` | 仅追加的 `SessionEvent` 日志 + 内存存储 | `ctx.sessions` |
| `core/system-prompt` | 提示词片段与工具 schema 组装 | `ctx.systemPrompt` |
| `core/tools` | 作用域化工具注册表 + 把关执行流水线 | `ctx.tools` |
| `core/agent` | `Agent` 接口、活跃 agent 注册表、`agent/*` 事件 | `ctx.agents` |
| `core/agent-loop` | 默认 agent 驱动器（实现该接口） | `ctx.agentLoop` |
| `core/scope` | 按 agent 划分作用域的注册原语 | — |
| `llm/llm` | 消息/流式词汇表 + 适配器 seam | `ctx.llm` |

## 五、事件体系（三类事件域）

| 域 | 语义 | 用途 |
|---|---|---|
| **会话事件**（`session/*`、`turn/*`、`step/*`、`user/message`、`assistant/*`、`tool/*`） | 追加到日志的**持久事实**，经 `session/event` 广播 | 重载后仍需存在的事实（回放/审计） |
| **Agent 事件**（`agent/*`） | 携带活跃 `Agent`：inbox、步骤、状态、请求、验证、续跑 | 观察/拦截进行中的工作（实时协调） |
| **能力事件**（`fs/*`、`tools/*`、`telemetry/*`） | 向 seam 附加策略与适配器（免导入循环） | 能力扩展 |

**Waterfall（瀑布）事件**（`agent/pre-step`、`agent/request`、`llm/stream`、`tools/*` 三件套）：监听器必须调用 `next()` 委托下游；**Serial 事件**（`agent/turn-stopping`）无 `next()`。

## 六、轮次/步骤执行循环（核心原理）

```
turn/start
  claim 下一步输入 + 一条排队消息
  组装提示词片段 + 工具 schema
  → agent/pre-step             （拦截/改写/拒绝）
     step/start
     追加 entered 消息为 user/message
     从日志派生模型历史（deriveMessages）
     agent/request → llm/stream → assistant/chunk* → assistant/message
     tool/call* → tools/pre-execute → tools/execute → tools/post-execute → tool/result*
     step/end
     工具还欠请求 或 新输入到达 → claim → 下一步
  → agent/turn-stopping
turn/end
```

- **步骤（step）** = 一次模型请求 + 其调用的工具；**轮次（turn）** = 零或多个步骤，领取首条输入时打开、不再欠工作时关闭
- **inbox 唤醒**：输入到达立即唤醒驱动器；注入的上下文（`agent.inject()`）留在 inbox，直到另一条消息唤醒
- **`agent/pre-step` 权威**：决定模型看到什么；首轮被拒/改写为空仍关闭一个无步骤的持久轮次（留痕）

## 七、会话日志与"模型可见即已记录"不变量

- 会话日志是模型所见上下文的唯一来源：`deriveMessages()` 投影模型历史；原始 `assistant/chunk` 保证回放与 UI 保真
- **运行时不变式**：抵达模型请求的一切必须能从日志重建 → 新增模型可见输入 = 新增会话事件（扩展 `SessionEventMap` 并从日志渲染）
- 空内容/`max-tokens` 终止的调用仍记录 `assistant/message`（保留用量），但不入派生历史；`sourceEventSeqs` 精确关联 chunk

## 八、能力 Seam（可替换能力三角色）

```
Service Definition（声明接口） ← Service Provider（实现） ← Consumer（使用，通常是面向模型的工具）
```

替换 Provider 即改变整个产品：文件系统与进程共享执行世界 → 指向远程沙箱时 Bash/PTY/LSP 一并搬移，无需 Provider 专属 fork；subagent 同一接口可"新建子 agent"或"把轮次委派给另一产品"。

## 九、上下文压力与错误恢复（Compaction）

- 触发路径：`dsh-compaction-basic`（派生请求前，经 `agent/pre-step`）或 `agent/request-error`（上下文溢出）
- 流程：**工具结果剪枝（pruning）→ 摘要（summarization）**
- 恢复时机：失败步骤结束之后、失败轮次结束之前
- **重试规则**：仅当剪枝/摘要推进了 replacement generation 才开启全新重试轮次，否则以原始请求错误为准

## 十、子系统全景

| 域 | 子系统 |
|---|---|
| 内核与作用域 | core、scope、运行时不变式 |
| 会话与持久化 | session、session-query/reference/title/projection、persistence、spill、遥测 |
| 模型与上下文 | LLM 流式、Token 计量、系统提示词、上下文压缩 |
| 执行与工具 | tools、Bash、子进程、PTY、后台任务、文件系统、LSP、代码运行时、Web 访问、技能、工作流、子代理 |
| 策略与交互 | 审批、权限预设、沙箱、计划模式、用户提问、命令、目标、定时提醒 |
| 平台与接入 | HTTP 服务器、Typert、客户端模块、存储、工作区、用户设置、凭据 |

## 十一、新行为归属表（速查）

| 目标 | 机制 |
|---|---|
| 添加模型提供方 | `ctx.llm` 注册适配器 |
| 添加面向模型的能力 | `ctx.tools` 注册（schema 入提示词组装） |
| 添加 shell 执行 | `ctx.shell` 后端（本地经 `ctx.subprocess` spawn） |
| 添加用户命令 | `ctx.commands`（无需模型轮次即可分派） |
| 添加后台工作 | `ctx.jobs`（`job_*` 工具收集/停止） |
| 限制所启动的进程 | `ctx.sandbox` 后端（消费方启动前包装 argv） |
| 拦截请求/工具/轮次 | `agent/*`、`tools/*` 事件；`agent/turn-stopping` 停止轮次 |
| 添加模型可见上下文 | `agent.inject()` 落到下一次获准请求 |
| fork 活跃会话 | `ctx.sessions.fork(source, boundary?, childSessionId?)` |
| 生成会话标题 | 注册唯一 `ctx.sessionTitle` 提供方 |

## 十二、与项目对照（agent_runner 视角）

| 维度 | DeepSeek Harness | 本项目（AgentScope HarnessAgent） |
|---|---|---|
| 装配模型 | 插件树 + Profile/组合包 + patch 覆盖 | DB 托管 agent → `AiHarnessAgentFactory` 装配 |
| 扩展点 | 事件域 + ctx 服务 | `AiAgentProfileResolver` / skillRegistry / `AiMemoryAgentTool` |
| 持久化 | `SessionEvent` 追加日志（回放） | H2 `l0_raw_log` 四层 + Arrow OLAP |
| 上下文 | compaction 剪枝+摘要 | `applyCompaction`（HarnessAgent builder） |
| 工具 | `ctx.tools` 流水线 | Toolkit（技能 + 记忆工具） |
