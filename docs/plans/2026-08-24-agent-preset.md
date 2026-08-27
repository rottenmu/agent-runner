# P2-2 agent preset / 模式（对齐 dsh A6：modes = 能力 + 配置组合）

> 日期：2026-08-24
> 模块：`modules/agent-spring-boot-starter`
> 目标：把原先硬编码在 `AiHarnessAgentFactory` 里的 agentType 分支策略，收口为可注册/可替换的「模式预设」注册表，对齐 DeepSeek Harness (dsh) A6 中 modes 的语义。

## 1. 背景与动机

P2-1 之前，工厂按 `profile.agentType()` 走 `switch` 硬编码策略：每种类型固定一套提示词、能力开关与超参，业务无法在不改源码的前提下扩展新「模式」或调整既有模式的组合。dsh 将 **mode 定义为 plugin + config 的组合（modes = 配置组合）**，运行时按 preset 名称解析并应用，覆盖面包括提示词片段、能力开关与超参。

本次新增 `AiAgentPreset` 作为「模式」模型，`AiAgentPresetRegistry` 作为注册表，`AiHarnessAgentFactory` 改为 preset 驱动装配。

## 2. 模型设计

### 2.1 AiAgentPreset（record，`preset/` 包）

| 字段 | 含义 |
| --- | --- |
| `id` | 预设标识，与 agentType 同值空间（conversation/rag/tool/plan/graph），业务可扩展 |
| `description` | 预设说明 |
| `promptSuffix` | 类型专属系统提示词片段，追加在基础 prompt 之后；rag 支持 `{knowledge}` 占位符注入知识库内容 |
| `abilities` | 能力开关集合：`plan`/`graph`/`shell`/`taskList`/`rag`/`tool` |
| `overrides` | 超参覆盖：`maxIters`/`temperature`/`maxTokens`，未覆盖项沿用全局配置 |

辅助方法：`hasAbility`、`overrideInt` / `overrideDouble`（key 缺省回退 fallback）。

### 2.2 AiAgentPresetRegistry

- 无参构造即注册 5 个内建预设（与 `AiAgentProfile.TYPE_*` 一致）。
- `resolve(agentType)`：未知类型 / 空值回退 conversation，不抛异常。
- `register(preset)`：注册或覆盖，空 id 抛 `IllegalArgumentException`。
- `find(id)` / `all()`：查询与枚举。
- 静态 `builtin(agentType)`：无注册表场景（旧构造器）的等价兜底。

### 2.3 Factory 装配流程（preset 驱动）

```
create(profile, key)
  └─ resolvePreset(profile.agentType())      # 注册表优先，缺失兜底 builtin
     ├─ sysPrompt  = base + applyTypePrompt(preset.promptSuffix, rag 注入 {knowledge})
     ├─ model      = resolvedModel(profile, preset)   # temperature/maxTokens 覆盖
     ├─ maxIters   = preset.overrideInt(maxIters, 全局)
     └─ applyTypeStrategy(builder, agentType, config, workspace, preset)
          ├─ plan  ability → enablePlanMode + planFileDirectory + shell/taskList
          ├─ graph ability → subagents 声明（Windows 下 disableSubagents 退化为提示词引导）
          └─ rag/tool/conversation → 仅 sysPrompt + toolkit，无额外策略
```

## 3. 装配（AutoConfiguration）

`AiAgentAutoConfiguration` 新增：

```java
@Bean
@ConditionalOnMissingBean
public AiAgentPresetRegistry aiAgentPresetRegistry() {
    return new AiAgentPresetRegistry();
}
```

`aiHarnessAgentFactory(...)` 增加 `AiAgentPresetRegistry` 参数并传入 9 参构造器（`properties, skillRegistry, storageService, objectMapper, memoryService, toolListeners, capabilities, pluginManager, presetRegistry`）。

## 4. 兼容性与缺陷修复

- **构造器链**：既有的 2 参 / 3 参 / 5 参 / 6 参 / 7 参 / 8 参构造器全部保留；6 参构造器此前漏赋值 final 字段 `presetRegistry`，本次补齐。
- 无注册表注入时（旧构造器场景）`resolvePreset` 走 `AiAgentPresetRegistry.builtin(agentType)`，行为与改造前等价。

## 5. 测试

| 测试类 | 用例数 | 覆盖点 |
| --- | --- | --- |
| `AiAgentPresetRegistryTest` | 6 | 5 内置解析 / 未知与空回退 / 自定义注册与覆盖 / 空 id 拒绝 / find / builtin 兜底 |
| `AiHarnessAgentFactoryPresetTest` | 6 | plan 提示词+plan 模式+maxIters 覆盖 / rag 知识库注入 / tool 提示词 / graph / conversation / 自定义 preset 应用 |

回归：`agent-spring-boot-starter` 全量单测 **129 通过，0 失败 / 0 错误 / 0 跳过**（含 Spring 上下文、沙箱、fork/resume 等既有用例）。

## 6. 后续

- P3 候选：AGENTS.md/hooks 互操作、subagent provider（图编排的非 Windows 完整能力）。
- 业务方可在装配层 `register(...)` 自定义 preset 覆盖内建模式，无需改工厂代码。