# 意图识别引擎接入渠道链路方案

> 日期：2026-08-15
> 状态：待审核
> 关联：`docs/ai-intent-rules/INTENT_RULES.md` 第六节、`AiChannelHandler`（ai-agent-spring-boot-starter）

---

## 一、背景与现状

- `ai-agent-spring-boot-starter` 的 `AiChannelHandler.handle()` 已内置意图扩展点：
  普通渠道消息先依次执行 `List<AiChannelIntentHandler>`，任一处理器返回非空回复即直接下发、
  不再调用 Agent；全部返回 null 才进入 HarnessAgent 分层路由。
- 全仓库当前 **没有任何 `AiChannelIntentHandler` 实现**。
- `module-intent` 已具备完整规则引擎（风险硬拦截、缺参追问、三级置信度路由、LLM 混合识别），
  但仅以 `/api/biz/intent/*` HTTP 接口暴露，未参与飞书等真实对话链路。

结论：意图识别能力与渠道链路之间存在最后一公里断点。

## 二、目标

飞书（及其他复用 `AiChannelHandler` 的渠道）消息在进入 Agent 前经过意图引擎：

- 违规内容直接拒绝，不调用 LLM。
- 必填参数缺失时直接追问，不调用 LLM。
- 其余意图（查询/导出/FAQ/闲聊等）照常交给 Agent，不打断现有能力。

## 三、方案设计

### 3.1 新增适配器 `IntentChannelIntentHandler`

位置：`modules/module-intent/module-intent-core`，包 `com.zimo.intent.channel`。

实现 `AiChannelIntentHandler`，处理语义：

| 解析结果 | 动作 |
|---|---|
| `RISK_REJECT`（isReject） | 返回拒绝回复（rejectReason），拦截，不调用 Agent |
| `needClarify=true` 且 `requiredSlotMissing` 非空 | 返回追问话术（clarifyPrompt），拦截 |
| 其余（含模糊档、TOOL_CALL_WITH_CONFIRM 确认态） | 返回 null，放行给 Agent |

设计约束：

1. `AiChannelMessage.attributes()` 为不可变副本，适配器**不修改消息**，只做拦截或放行。
2. 不做多轮会话状态：追问后的下一轮输入按新消息重新解析（与无状态 HTTP 接口一致）。
3. `TOOL_CALL_WITH_CONFIRM` 的二次确认、多轮澄清轮次管理属于会话层职责，本期不实现，
   避免无状态拦截造成死循环。
4. 解析异常（规则加载失败等）一律放行（fail-open），保证渠道可用性优先，异常写日志。

构造依赖：`IntentRecognitionService`；拦截开关来自 `IntentProperties`。

### 3.2 配置项（`plugin.intent.channel.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | true | 渠道意图拦截总开关（关闭后处理器返回 null 放行） |
| `intercept-clarify` | true | 是否拦截缺参追问（关闭则仅拦截风险） |

风险拦截不设开关（命中即拒绝，与引擎 RISK_REJECT 语义一致）。

### 3.3 依赖与装配

- `module-intent-core/pom.xml` 新增依赖 `ai-agent-spring-boot-starter`（compile）。
  已核实 starter 不依赖 module-intent，无循环；starter 仅额外引入 framework-common 已有依赖。
- `IntentAutoConfiguration` 新增 Bean：
  `@ConditionalOnClass(AiChannelIntentHandler.class)` + `@ConditionalOnMissingBean` +
  受 `plugin.intent.enabled` 总开关控制（复用现有注解）。
- `AiAgentAutoConfiguration.aiChannelHandler` 已自动收集容器中全部
  `AiChannelIntentHandler` Bean，无需改动 starter。

### 3.4 admin-shell 配置

`application.yml` 的 `plugin.intent` 下补充：

```yaml
    channel:
      enabled: true
      intercept-clarify: true
```

## 四、测试计划

`module-intent-core` 新增 `IntentChannelIntentHandlerTest`（不依赖 Spring 容器）：

1. 违规输入 → 返回拒绝回复，内容含 rejectReason；
2. 缺参输入（如「提交采购单」）→ 返回追问话术；
3. 正常查询（如「查询本周订单数据」）→ 返回 null 放行；
4. 闲聊问候 → 返回 null 放行（交给 Agent）；
5. 开关关闭（`channel.enabled=false`）→ 一律放行；
6. `intercept-clarify=false` → 缺参放行、风险仍拦截；
7. 解析异常 fail-open → 返回 null。

回归：`mvn -pl admin-shell -am compile` 与 module-intent 现有 35 个测试保持通过。

## 五、影响评估

- 编译安全：starter 与 module-intent 无循环依赖；其他模块不受影响。
- 运行时：仅当 `plugin.intent.enabled=true`（当前 admin-shell 已开启）时生效；
  飞书消息命中风险词/缺参时将先看到拦截/追问回复，不再消耗 LLM 调用。
- 风险：风险词表误杀（如业务文案含「删除」）——可通过编辑
  `data/intent-rules.json` 的 RISK_REJECT 触发词热更新修正。
- 不涉及数据库表、不新增 SQL。

## 六、审核确认

请审核以上方案，确认后按第三节实施。如需调整拦截范围（例如 FAQ 走知识库直答、
模糊档二次确认），请在确认时一并说明。
