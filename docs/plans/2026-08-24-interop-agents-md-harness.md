# P3 互操作（对齐 dsh A8：AGENTS.md/hooks + meta-harness 子 agent）

> 日期：2026-08-24
> 模块：`modules/agent-spring-boot-starter`
> 目标：补齐 dsh A8 互操作两块能力——AGENTS.md/CLAUDE.md 规则文件原生读取，以及外部 harness 子 agent provider（meta-harness）。

## 1. 背景

差距分析（2026-08-24-agent-harness-vs-deepseek-harness.md）指出 A8 互操作完全缺失：
- 无 AGENTS.md/CLAUDE.md 读取
- 无 subagent provider 抽象（collab 是平台内多智能体，非「驱动外部 harness」）

P0-P2（事件日志/中间件/工具流水线/fork-resume/远程沙箱/preset 模式）已落地，P3 收尾补齐互操作。

## 2. Stage1：AGENTS.md / CLAUDE.md 互操作读取

### 2.1 AgentInstructionReader（`interop/` 包）

从应用工作目录向上最多 8 层发现规则文件，文件名按配置顺序优先（默认 `AGENTS.md` > `CLAUDE.md`），只读文本文件、单文件 256KB 上限。方法：
- `findFirst()` / `findAll()`：按优先级发现首个/全部规则
- 解析为 `InteropInstruction(name, instructions, file)`，指令行 trim 后非空才保留

### 2.2 AgentInteropService

渲染规则为提示词片段（`【规则文件 X】\n- 指令…`），支持 maxChars 截断；`discovered()` 供排查。

### 2.3 InstructionFileSkill（内存技能）

技能名 `interop_instructions`：无参数返回全部规则；`topic` 参数按关键字过滤行。Agent 可在需要时主动检索仓库规则，不常驻系统提示词。

### 2.4 注入通道（双通道）

1. **技能通道**：`aiSkillRegistry` 汇总时若启用则注入 `InstructionFileSkill`
2. **提示词通道**：`aiHarnessAgentRouter` 装配时把规则块拼进默认 profile 的 systemPrompt（`【仓库规则（AGENTS.md/CLAUDE.md）】` 前缀）

### 2.5 配置（`ai.agent.*`）

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `interop-instruction-files-enabled` | true | 总开关 |
| `interop-instruction-files` | `AGENTS.md,CLAUDE.md` | 文件名列表（逗号分隔） |
| `interop-instruction-max-chars` | 8000 | 最大注入字符数 |
| `interop-instruction-skill` | true | 是否提供技能通道 |

## 3. Stage2：外部 harness 子 agent provider（meta-harness）

### 3.1 ExternalHarnessSubagent（record）

声明外部 harness 节点：`name/description/url/headers/model/maxIters/remoteStreaming`；`fromJson(JsonNode)` 从 `agentConfig.externalHarness.tasks[]` 解析。

### 3.2 ExternalHarnessSubagentProvider（接口）

- `extract(JsonNode config)`：解析 `externalHarness.tasks[]`，跳过非法节点
- `toDeclaration(ExternalHarnessSubagent)`：转换为 AgentScope 2.0.2 `SubagentDeclaration`（remote url/headers 形态），description 空时自动补默认（**AgentScope 要求非空 description**，否则构建抛异常）
- `defaults()` / `localDefault()`：默认实现；Spring 装配 `@ConditionalOnMissingBean` 可被业务替换

### 3.3 Factory 集成

`AiHarnessAgentFactory` 新增 10 参构造（+`ExternalHarnessSubagentProvider`），`applyGraph` 合并本地 `tasks` 与远端 `externalHarness.tasks` 声明，统一 `builder.subagents(...)`。

配置示例（agentConfig）：
```json
{
  "externalHarness": {
    "tasks": [
      {
        "name": "claude-code",
        "description": "交由 Claude Code 评审",
        "url": "https://harness.example.com/run",
        "headers": { "Authorization": "Bearer xxx" },
        "model": "claude-sonnet-4",
        "maxIters": 10,
        "remoteStreaming": true
      }
    ]
  }
}
```

## 4. 测试

| 测试类 | 用例数 | 覆盖点 |
| --- | --- | --- |
| `AgentInteropServiceTest` | 7 | AGENTS.md 发现解析 / 优先级 / 父级目录遍历 / 无文件空结果 / 截断 / 技能调用 / 技能空报告 |
| `ExternalHarnessSubagentProviderTest` | 5 | tasks 解析 / 非法节点过滤 / toDeclaration / 非法拒绝 / factory 装配远端声明 |

回归：starter 全量 **141 通过，0 失败**（较 P2-2 的 136 新增 5 个 Stage2 用例；Stage1 的 7 个已含在 136 中）。

## 5. 关键坑

- **AgentScope `SubagentDeclaration` 要求非空 description**：空串构建抛 `SubagentDeclaration requires a non-blank description`，被 `externalHarnessDeclarations` 的 try/catch 吞掉后表现为「subagents 未调用」。修复：`toDeclaration` 中空 description 补默认值。
- 工厂异常被 catch 吞掉易误判：定位时用反射直调私有方法对比「provider 直接调用」与「factory 内部调用」差异，一次性锁定根因。

## 6. 后续（dsh 对标收尾）

- Claude Code 桥（驱动本机 Claude Code CLI 为外部 harness）——依赖具体 CLI 协议，可基于 `ExternalHarnessSubagentProvider` 自定义实现扩展
- `AGENTS.md hooks` 指令解析（`hook:` 语法）→ 映射到 ToolPipeline 钩子

## 7. P3-3 AGENTS.md hook 桥接（2026-08-24 追加，已完成）

### 7.1 AgentHook / AgentHookParser / AgentHookRegistry

- `AgentHook` record：`trigger`（PreToolUse/PostToolUse/Notification）+ `name` + `command` + `matcher`（`*` 前后缀通配）
- `AgentHookParser`：解析 AGENTS.md 中 `hook: <Trigger> name=... command=... matcher=...` 行（Claude Code 风格，引号内空格保留，未知属性忽略）
- `AgentHookRegistry`：按触发点分组注册，同名覆盖，`matching(trigger, toolName)` 支持工具过滤

### 7.2 AgentHookBridge（→ ToolHook）

- **PreToolUse**：执行 hook 命令，输出含 `REJECT[: 原因]` / JSON `{"decision":"reject"}` / `ask` → 拒绝短路
- **PostToolUse**：输出含 `REWRITE[: 内容]` / JSON `{"decision":"rewrite","updatedInput":...}` → 重写结果
- 命令经 `SandboxBackend.execute` 执行（本地/远程一致，超时 10s），工具入参 JSON 经 stdin 传入（`AGENT_HOOK_TOOL` 环境变量带工具名）
- 支持 Claude Code JSON 决策协议：`{"decision":"approve|reject|ask|rewrite","reason":...,"updatedInput":...}`
- 执行失败/无沙箱 → 放行 + 日志，不阻断流水线

### 7.3 装配

`agentHookRegistry`（从规则文件解析 hook 指令）+ `agentHookBridge`（ToolHook Bean 自动汇入 aiSkillRegistry 的 ToolPipeline）两个 Bean，受 `ai.agent.interop-instruction-files-enabled` 开关控制。

### 7.4 测试

`AgentHookBridgeTest` 16 用例：解析（风格/引号/非法跳过/多行）、注册（分组/匹配/覆盖）、桥接（REJECT/approve/REWRITE/无沙箱/不匹配）、JSON 决策（reject/approve/ask/rewrite）、stdin 传参。
回归：starter 全量 **157 通过，0 失败**（较 141 新增 16 个 hook 用例）。

### 7.5 配置示例（AGENTS.md）

```markdown
hook: PreToolUse name=guard-delete command=guard-cmd matcher=rm
hook: PostToolUse name=mask-secret command=mask-cmd matcher=*
```