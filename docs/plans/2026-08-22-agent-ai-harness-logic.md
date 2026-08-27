# agent-ai 模块 — Agent Harness 功能逻辑整理

> 模块：`modules/agent-ai`（module-ai-core + module-ai-autoconfig）
> 日期：2026-08-22 | 框架：AgentScope-Java 2.0.2（HarnessAgent）
> 范围：托管智能体（AiManagedAgent）从 DB → 内存装配 → Profile 解析 → HarnessAgent 装配 → 路由运行的完整链路

## 一、总体架构

```text
┌──────────────────────────── DB（MySQL：ai_managed_agent / ai_managed_skill_config）─────────────┐
│  MybatisPlusAiManagedAgentRepository / MybatisPlusAiManagedSkillConfigRepository                │
└───────────────┬────────────────────────────────────────────────────────────────────────────────┘
                │ 读取（启动时 + 变更时）
┌───────────────▼──────────────────────────────────────────────────┐
│  AiAgentManagementService（内存装配中心）                          │
│  · agents Map（LinkedHashMap，id→AiManagedAgent）                 │
│  · skillRegistry 技能注册 + skillPromptTemplateIds/skillAgentIds  │
│  · loadManagedAgents(contributors) + loadConfiguredApiSkills()    │
│  · findDefaultAgentForChannel / findEnabledAgentById              │
└───────┬──────────────────────────────┬───────────────────────────┘
        │ 变更（create/update/delete）   │ 查询
        ▼                              ▼
 AiManagedAgentRuntimeInvalidator    AiManagedAgentProfileResolver
  .invalidate(tenantId, agentId)      （渠道路由：feishu 绑定/默认）
        │                              │ AiAgentProfile
        ▼                              ▼
┌───────────────────────────────────────────────────────────────────┐
│  AiHarnessAgentFactory.create(profile, key) → HarnessAgent         │
│  · sysPrompt（persona/模板 + agentType 追加）                       │
│  · model / maxIters / workspace（agent 专属目录）                   │
│  · toolkit（skillRegistry 技能 + AiMemoryAgentTool 记忆工具）       │
│  · compaction 压缩 / RocksDB 记忆 / agentType 策略                  │
└───────────────────────────────┬───────────────────────────────────┘
                                ▼
               AiHarnessAgentRouter（按 agentId 路由实例）
                                ▼
        AiChatController / WfAiChatController（消息 → 路由 → run）
```

## 二、配置模型（AiManagedAgent）

| 字段 | 说明 |
|---|---|
| `id / name / desc` | 标识与描述 |
| `persona` | 角色设定（system prompt 基础） |
| `model` | 模型名（装配到 HarnessAgent） |
| `promptTemplateId` | 提示词模板（模板库引用） |
| `skillIds` | 绑定技能 ID 列表（→ 技能工具） |
| `agentType` | 智能体类型：`conversation`（默认）/ `rag` / `tool` / `plan` / `graph` |
| `agentConfig` | 类型专属 JSON 配置（RAG 索引、计划器参数等） |
| `enabled` | 启用开关（装配时过滤） |
| `defaultChannels` | 默认绑定渠道（feishu 等，`isDefaultForChannel`） |
| `tenantId / userId` | 租户与归属（tenantId 空时回落 userId） |

## 三、装配中心（AiAgentManagementService）

- **加载时机**：构造时 `loadManagedAgents(contributors)`（AiManagedAgentContributor 提供 DB/扩展 agent）+ `loadConfiguredApiSkills()`（技能配置）
- **内存态**：`agents`（id→agent）、`skillPromptTemplateIds`、`skillAgentIds`、`skillApiRegistryIds`
- **查询语义**：`findEnabledAgentById`（启用过滤）、`findDefaultAgentForChannel`（按渠道默认，支持租户维度）
- **变更传播**：create/update/delete → 各 `AiManagedAgentRuntimeInvalidator.invalidate(tenantId, agentId)`（函数式接口，装配方实现"重建 harness"）

## 四、Profile 解析（AiManagedAgentProfileResolver）

- 实现框架 `AiAgentProfileResolver`，按渠道解析：
  - **非 feishu 渠道** → `findDefaultAgentForChannel(channel, tenantId)`
  - **feishu** → 优先会话绑定（`AiChannelAgentBinding.attributes`）的 agent，无绑定回退默认
- 输出 `AiAgentProfile`：id / tenantId / name / modelName / systemPrompt / skillIds / agentType / agentConfig / enabled

## 五、HarnessAgent 装配（AiHarnessAgentFactory）

```java
public HarnessAgent create(AiAgentProfile profile, AiHarnessAgentKey key) {
    String basePrompt = resolvedSystemPrompt(profile);   // persona + 提示词模板
    Path workspace = workspace(key);                     // agent 专属工作目录
    JsonNode config = parseConfig(profile.agentConfig()); // 类型专属配置
    Toolkit toolkit = managedToolkit(profile);            // skillRegistry → 技能工具
    if (memoryService != null) {
        AiMemoryAgentTool.register(toolkit, memoryService, profile.tenantId()); // 记忆工具
    }
    return newBuilder()
            .agentId(key.agentId())
            .name(resolvedName(profile))
            .sysPrompt(applyTypePrompt(basePrompt, profile.agentType(), config))
            .model(resolvedModel(profile))
            .toolkit(toolkit)
            .maxIters(properties.getMaxIters())
            .workspace(workspace)
            .build();
    // 附加：applyCompaction（上下文压缩）、applyRocksdbMemory（FileStorageService 记忆）、
    //       applyTypeStrategy（按 agentType 的推理策略）
}
```

| 装配要素 | 说明 |
|---|---|
| System Prompt | persona + 提示词模板 → `applyTypePrompt` 按类型追加（RAG 指令/工具指令/计划器指令等） |
| Model | `resolvedModel(profile)`（模型名/温度等） |
| Toolkit | `managedToolkit`：skillRegistry 技能 + `AiMemoryAgentTool`（memory_write/read） |
| Workspace | agent 专属目录（`workspace(key)`，文件操作沙箱） |
| MaxIters | 最大迭代轮数（`ai.agent.max-iters` 配置） |
| Compaction | 上下文压缩策略（`applyCompaction`） |
| Memory | RocksDB 记忆（FileStorageService 注入时） |
| TypeStrategy | 按 `conversation/rag/tool/plan/graph` 差异装配（prompt + 策略） |

## 六、运行时路由与交互

- `AiHarnessAgentRouter`：按 key（agentId + 租户）维护 HarnessAgent 实例，`invalidate` 时重建
- 链路：`AiChatController`（HTTP/SSE）→ `AiAgentProfileResolver.resolveForMessage(channel)` → `AiHarnessAgentRouter` → `HarnessAgent.run()` → 技能/记忆/工具调用
- 多智能体协同（`MultiAgentCollaborationService` + AgentTaskFlow）与工作流引擎（`WfWorkflowEngine`）复用同一 harness 装配

## 七、变更生命周期（示例）

```text
管理端更新 agent（persona/模型/技能）→ AiAgentManagementService.updateAgent
  → 内存 agents Map 刷新
  → RuntimeInvalidator.invalidate(tenantId, agentId)
  → AiHarnessAgentRouter 丢弃旧实例，下次请求按新 profile 重建
```

## 八、关键设计点

1. **DB 为源、内存为态**：装配中心内存缓存，变更事件驱动失效重建（无热重启）
2. **Profile 解耦**：渠道/绑定解析与装配分离，新增渠道只需扩展 Resolver
3. **类型化装配**：5 种 agentType 共享装配骨架，差异收敛到 prompt 追加 + 策略注入
4. **技能与记忆即插即用**：skillRegistry（含 API 技能）与 AiMemoryAgentTool 以 Toolkit 形式注入，不改 harness 本体
5. **失效回调**：RuntimeInvalidator 函数式接口，装配方自决重建粒度
