# agent-harness Plan Mode 支持检查报告

> 日期：2026-08-23 ｜ 依据：AgentScope Java v2 官方 plan-mode 文档 + agent-harness 代码核对
> 结论：✅ **已支持官方原生 Plan Mode，前后端闭环**

## 一、官方 Plan Mode 核心要求 vs 项目实现

| 官方能力 | 项目实现 | 状态 |
|---|---|---|
| `enablePlanMode()` 开启 | `AiHarnessAgentFactory.applyPlan()`：agentType=plan 时 `builder.enablePlanMode(true)` | ✅ |
| `planFileDirectory`（默认 plans/） | `config.planFileDir` 或回退 `workspace/plans` | ✅ |
| `allowShellInPlanMode`（可选） | `config.allowShell`（默认 false，放开则允许 plan 阶段只读 shell） | ✅ |
| plan_enter / plan_write / plan_exit 白名单工具 | AgentScope 2.0.2 原生提供（enablePlanMode 自动注册） | ✅ |
| todo_write 协作 | 原生可用（项目未开 `enableTaskList`，可选增强） | 🟡 |
| 只读阶段 + 拒绝提示 | 原生行为（非白名单工具被拒 + HITL 退出） | ✅ |
| AgentState 状态持久化（跨重启恢复 plan 阶段） | 原生行为（AgentScope 状态存储） | ✅ |
| 类型化提示词引导 | `applyTypePrompt` TYPE_PLAN：先写计划→逐步执行→更新进度→最终答案 | ✅ |

## 二、配置闭环（管理台 → 装配）

```text
AiAgentManage.vue（前端）
  agentType=plan（"规划执行"）
  agentConfig: {"planFileDir": "plans", "allowShell": false}
        ↓ DB(ai_managed_agent.agent_type / agent_config)
AiManagedAgentProfileResolver → AiAgentProfile(agentType=plan, agentConfig)
        ↓
AiHarnessAgentFactory.applyTypeStrategy → applyPlan(builder)
  ├─ enablePlanMode(true)
  ├─ planFileDirectory(workspace/plans 或 planFileDir)
  └─ allowShellInPlanMode(config.allowShell)
```

## 三、可补强点（对照官方，非阻塞）

| 项 | 说明 | 建议 |
|---|---|---|
| `enableTaskList()` | plan 阶段写的 todos 每次推理前提示 agent | 在 applyPlan 增加配置开关（如 `enableTaskList`） |
| 程序化进出 | `enterPlanMode/exitPlanMode/isPlanModeActive(ctx)` + admin REST | 业务侧可封装管理接口（当前依赖模型自主触发） |
| plan 终态判定 | 官方建议区分 4 种终态（未进入/进入已退出/起草未退/只说不做） | 可在 observ 事件桥捕获 ToolCallStartEvent（plan_enter/plan_write） |

## 四、其他发现

- plan 类型下 `disableSubagents()` 已调用——规避了官方"子 agent 不继承只读限制"的已知缺口（当前 plan 智能体不会 spawn 子 agent）
- graph 类型使用 SubagentDeclaration（子 agent 声明），与 plan 互不干扰
