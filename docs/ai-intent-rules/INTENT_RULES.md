# 大模型意图识别规则集（生产可用）

> 生成引擎：企业业务大模型意图规则解析与生成引擎 v1.1
> 生成日期：2026-08-14 ｜ 版本：1.0.0（v1.1 引擎实现映射见第六节，2026-08-15）
> 交付物：`intent-rules.json`（可直接入库）+ 本说明 + 实时解析提示词

---

## 一、规则总览

| # | intentCode | 意图名称 | 路由策略 | 置信度阈值 |
|---|-----------|---------|---------|-----------|
| 1 | DATA_QUERY | 业务数据查询 | 工具调用 | 0.8 |
| 2 | DATA_EXPORT | 数据导出 | 工具调用 | 0.8 |
| 3 | ORDER_OPERATE | 单据操作 | 工具调用+二次确认 | 0.9 |
| 4 | FAQ_ANSWER | 业务知识问答 | 知识库检索 | 0.8 |
| 5 | GENERAL_CHAT | 通用闲聊 | 闲聊回复 | 0.6 |
| 6 | PARAM_CLARIFY | 参数澄清追问 | 追问 | —（路由层触发） |
| 7 | UNSUPPORTED | 超出能力范围 | 兜底说明 | 0.7 |
| 8 | RISK_REJECT | 风险违规拒绝 | 拒绝 | 0.75 |

**核心判定顺序（强制执行）**：
`RISK_REJECT 命中 → 高优先级拒绝` → `精准业务意图（DATA_QUERY/DATA_EXPORT/ORDER_OPERATE/FAQ_ANSWER）` → `必填槽位缺失 → PARAM_CLARIFY` → `闲聊 GENERAL_CHAT` → `兜底 UNSUPPORTED`。杜绝随意归类。

---

## 二、规则明细（10 字段标准结构）

### 1. DATA_QUERY 业务数据查询

| 字段 | 内容 |
|------|------|
| intentCode | DATA_QUERY |
| intentName | 业务数据查询 |
| intentDesc | 用户查询业务数据：报表、工单、生产批次、设备状态、产量、订单、库存、质量等；适用所有数据查看类诉求 |
| triggerKeywords | 查询、查一下、看看、看下、报表、数据、统计、汇总、明细、多少、工单、订单、产量、库存、设备状态、进度、趋势、排行；口语句式：今天/本周/本月 XX 是多少、帮我查 XX、XX 的单子有哪些、最近 XX 情况 |
| requiredSlots | `dataType`（查询对象）、`timeRange`（时间范围，默认当日/近7天） |
| optionalSlots | `filter`（筛选条件）、`dimension`（分组维度：日/周/月/部门/产线） |
| supportTool | report_query、order_query、production_board、inventory_query |
| confidenceThreshold | 0.8 |
| routeStrategy | TOOL_CALL |
| rejectRule | 跨租户/无权限范围数据、敏感经营数据（成本、毛利明细）拒绝；时间跨度超 365 天需人工确认 |

### 2. DATA_EXPORT 数据导出

| 字段 | 内容 |
|------|------|
| intentCode | DATA_EXPORT |
| intentName | 数据导出 |
| intentDesc | 用户要求导出/下载报表或数据文件（Excel/CSV/PDF），区别于纯查看 |
| triggerKeywords | 导出、下载、保存表格、生成报表、拉个表、发我份、导出来、下载数据、把 XX 导成 excel、XX 报表下载、导出 XX 明细 |
| requiredSlots | `dataType`（导出对象）、`exportFormat`（导出格式，默认 excel） |
| optionalSlots | `timeRange`（时间区间）、`columns`（导出字段，缺省全列） |
| supportTool | export_service、report_generator |
| confidenceThreshold | 0.8 |
| routeStrategy | TOOL_CALL |
| rejectRule | 单次导出行数超限（默认 10 万）拒绝并提示分批；涉密/成本数据无权限拒绝 |

### 3. ORDER_OPERATE 单据操作

| 字段 | 内容 |
|------|------|
| intentCode | ORDER_OPERATE |
| intentName | 单据操作 |
| intentDesc | 对业务单据执行新增/修改/提交/撤销等写操作，涉及状态变更，需高置信度 + 二次确认 |
| triggerKeywords | 新增、创建、新建、修改、编辑、提交、撤销、作废、退回、加一张 XX 单、把 XX 单改一下、提交 XX、撤销 XX 单、补录 XX |
| requiredSlots | `operationType`（操作类型）、`orderType`（单据类型）、`orderId`（单据标识，修改/撤销必填） |
| optionalSlots | `remark`（备注）、`itemData`（单据明细） |
| supportTool | order_service、production_order_service |
| confidenceThreshold | 0.9 |
| routeStrategy | TOOL_CALL_WITH_CONFIRM（执行前二次确认） |
| rejectRule | 无操作权限拒绝；撤销/作废已审批、已过账单据拒绝；修改他人单据无授权拒绝；**删除/清空等破坏性操作一律拒绝并转人工** |

### 4. FAQ_ANSWER 业务知识问答

| 字段 | 内容 |
|------|------|
| intentCode | FAQ_ANSWER |
| intentName | 业务知识问答 |
| intentDesc | 咨询业务流程、制度规则、操作指引、系统功能说明等知识类问题，不产生数据变更 |
| triggerKeywords | 怎么、如何、流程、步骤、规定、规则、要求、制度、可以吗、怎么办、需要什么材料、多久、标准、说明、什么是 |
| requiredSlots | `topic`（问题主题） |
| optionalSlots | — |
| supportTool | knowledge_base_retrieval、faq_service |
| confidenceThreshold | 0.8 |
| routeStrategy | KB_RETRIEVAL |
| rejectRule | 未公开制度仅给公开口径；知识库无答案**不得编造**，回退 UNSUPPORTED 或转人工 |

### 5. GENERAL_CHAT 通用闲聊

| 字段 | 内容 |
|------|------|
| intentCode | GENERAL_CHAT |
| intentName | 通用闲聊 |
| intentDesc | 无业务诉求的日常对话：问候、感谢、寒暄、情绪表达、非业务话题 |
| triggerKeywords | 你好、hi、hello、谢谢、辛苦了、再见、好的、在吗、早上好、随便聊聊、你是、讲个笑话 |
| requiredSlots | — |
| optionalSlots | — |
| supportTool | （不调用任何工具） |
| confidenceThreshold | 0.6 |
| routeStrategy | CHAT_REPLY |
| rejectRule | 闲聊夹带业务诉求时优先识别业务意图；内容命中敏感词仍按 RISK_REJECT |

### 6. PARAM_CLARIFY 参数澄清追问

| 字段 | 内容 |
|------|------|
| intentCode | PARAM_CLARIFY |
| intentName | 参数澄清追问 |
| intentDesc | 业务意图已识别但必填槽位缺失，系统主动追问补全；由路由层自动触发，不依赖用户直接表达 |
| triggerKeywords | — |
| requiredSlots | — |
| optionalSlots | — |
| supportTool | — |
| confidenceThreshold | — |
| routeStrategy | CLARIFY |
| rejectRule | 同一意图追问超过 3 轮仍缺失转人工或放弃；追问话术必须明确缺失字段与示例格式 |

### 7. UNSUPPORTED 超出能力范围

| 字段 | 内容 |
|------|------|
| intentCode | UNSUPPORTED |
| intentName | 超出能力范围 |
| intentDesc | 诉求超出系统预设业务能力：不存在的功能、无法执行的操作、业务范围外需求 |
| triggerKeywords | 炒股、投资建议、算命、天气、新闻、游戏、写代码、翻译文章 |
| requiredSlots | — |
| optionalSlots | — |
| supportTool | — |
| confidenceThreshold | 0.7 |
| routeStrategy | FALLBACK |
| rejectRule | 能力外诉求不拒绝但明确说明不支持范围，可引导人工 |

### 8. RISK_REJECT 风险违规拒绝

| 字段 | 内容 |
|------|------|
| intentCode | RISK_REJECT |
| intentName | 风险违规拒绝 |
| intentDesc | 指令含违规、越权、敏感、非法内容：删除篡改数据、绕过权限、套现刷单、攻击系统、涉政涉黄涉暴、获取他人隐私等，直接拒绝且不执行任何操作 |
| triggerKeywords | 删除数据库、清空、绕过、越权、冒充、套现、刷单、作弊、篡改、攻击、破解、提现别人、查他人信息、删除记录、改分数、隐瞒、违规操作 |
| requiredSlots | — |
| optionalSlots | — |
| supportTool | — |
| confidenceThreshold | 0.75 |
| routeStrategy | REJECT |
| rejectRule | 命中即拒绝并输出原因；**高危指令（删除/提现/越权）无论置信度高低一律拒绝**；拒绝后写审计日志 |

---

## 三、实时意图解析提示词（可直接作为 LLM System Prompt）

```text
【系统角色】
你是企业业务大模型意图解析引擎。根据用户输入与对话上下文，精准识别意图、抽取业务实体、判定缺参/工具/追问/拒绝，输出严格 JSON。禁止输出 JSON 以外的任何内容。

【预设意图枚举】（编码全局唯一，固定）
DATA_QUERY 业务数据查询｜DATA_EXPORT 数据导出｜ORDER_OPERATE 单据操作｜FAQ_ANSWER 业务知识问答｜GENERAL_CHAT 通用闲聊｜PARAM_CLARIFY 参数澄清追问｜UNSUPPORTED 超出能力范围｜RISK_REJECT 风险违规拒绝

【核心判定规则（强制执行）】
1. 优先匹配精准业务意图，再兜底闲聊/未知，杜绝随意归类；
2. 存在必填槽位缺失 → needClarify=true 并列出缺失槽位，禁止直接执行业务；
3. 语句含违规、越权、删除篡改、敏感内容 → 直接 RISK_REJECT；
4. 纯闲聊无业务诉求 → GENERAL_CHAT，不调用任何工具；
5. 超出预设能力 → UNSUPPORTED；
6. 置信度 < 0.8 降级为 UNSUPPORTED 或触发人工二次确认（ORDER_OPERATE 阈值 0.9）；
7. 结合历史对话做上下文指代消解，补全省略的参数。

【输出结构】（严格 JSON，无注释无多余字段）
{
  "query": "用户原始输入",
  "intentCode": "匹配的意图编码",
  "intentName": "意图名称",
  "confidence": 0.85,
  "entities": { "dataType": "订单", "timeRange": "本周" },
  "requiredSlotMissing": [],
  "needTool": true,
  "toolList": ["order_query"],
  "needClarify": false,
  "clarifyPrompt": "",
  "isReject": false,
  "rejectReason": "",
  "routeStrategy": "TOOL_CALL"
}
```

**routeStrategy 取值**：`TOOL_CALL` 工具调用 ｜ `TOOL_CALL_WITH_CONFIRM` 工具调用+确认 ｜ `KB_RETRIEVAL` 知识库检索 ｜ `CLARIFY` 追问 ｜ `CHAT_REPLY` 闲聊回复 ｜ `REJECT` 拒绝 ｜ `FALLBACK` 兜底。

---

## 四、对接与落地建议（agent_runner）

1. **能力配置对接**：智能体「能力配置 → 意图识别」当前为开关 + 置信度阈值（`AiAgentCapability.intentRecognition`）。建议扩展为「启用 + 阈值 + 规则集引用」，规则集指向 `intent-rules.json`（可按智能体覆盖）。
2. **执行链路**：对话入口（`/api/biz/ai/chat`）→ 意图解析（LLM + 本提示词）→ 判定：
   - `RISK_REJECT` → 直接拒绝并审计（对接安全中心敏感词/审计链路）
   - 缺参 → `PARAM_CLARIFY` 追问，3 轮内补全
   - `ORDER_OPERATE` → 二次确认后才允许写操作
   - 其余按 `routeStrategy` 路由到工具/知识库/闲聊
3. **规则扩展**：新业务场景在 `intent-rules.json` 的 `rules` 追加，编码全局唯一；同时可在 `triggerKeywords` 按行业补充（如制造：生产批次、设备工单、良率）。
4. **冲突校验**：新增规则前检查 triggerKeywords 与既有规则重叠度，重叠 > 40% 提示合并；requiredSlots 命名全局统一。

---

## 五、使用指令对照

| 指令 | 对应产物 |
|------|---------|
| 按业务场景生成全套规则 | `intent-rules.json` + 本表 |
| 实时意图解析 | 第三节提示词接入 LLM，输出固定 JSON |
| 优化/迭代规则 | 追加/修订 `rules[]` 项 |
| 冲突重复检测 | 第四节冲突校验规则 |

---

## 六、引擎实现映射（v1.1，2026-08-15）

规则引擎实现位于 `modules/module-intent`（包 `com.zimo.intent`），本节与上文提示词规范一一对应。

### 6.1 双模式提示词资源

| 模式 | 资源文件 | 用途 |
|------|---------|------|
| 模式一 | `ai-intent/prompt-generate.txt` | 业务场景 → 完整意图规则库（JSON 数组） |
| 模式二 | `ai-intent/prompt-parse.txt` | 实时意图解析，输出完整 JSON 契约 |
| 模式三 | `ai-intent/prompt-review.txt` | 规则冲突校验、补全与优化迭代 |

三个模板均支持 `{rules}` 占位符，运行时注入当前规则库 JSON。模板优先级：
`plugin.intent.llm-prompt`（仅模式二）显式配置 → 资源模板 → 内置简化提示词兜底。

### 6.2 混合架构实现

- `IntentRecognitionService`：主编排（风险硬拦截 → 规则匹配 → LLM 补强 → 指代消解 → 三级置信度路由 → 缺参追问）。
- `IntentRuleStore`：规则加载/热更新/持久化/CRUD/触发词冲突校验（重叠 > 40% 判冲突）。
- `IntentEntityExtractor`：触发词命中、槽位实体抽取、Schema 白名单过滤（防幻觉）、缺参判定。
- `IntentLlmParser`：LLM 解析层接口（可插拔）；`HttpIntentLlmParser` 走 OpenAI 兼容
  `/v1/chat/completions`（HTTP 裸调用），`HeuristicIntentLlmParser` 为无 LLM 环境兜底。
- `IntentEvaluator`：批量评估；`IntentRuleReviewer`：模式三校验器。

### 6.3 LLM 输出契约（模式二）

LLM 返回按完整契约解析：`intentCode / intentName / confidence / entities /
requiredSlotMissing / needTool / toolList / needClarify / clarifyPrompt /
isReject / rejectReason / routeStrategy`。引擎合并规则：

- LLM 返回 `isReject=true` → 引擎直接输出 RISK_REJECT（与规则硬拦截同级）。
- LLM 意图编码必须存在于规则库，否则忽略（防幻觉意图）。
- 实体按槽位 Schema 白名单过滤；最终路由与工具以规则库配置为准。
- 缺参追问话术优先采用 LLM `clarifyPrompt`，无则使用默认模板。

### 6.4 接口清单（前缀 `/api/biz/intent`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/parse` | 模式二实时解析（支持 context 指代消解） |
| GET | `/rules` | 查询已加载规则集 |
| POST | `/rules/reload` | 规则热更新 |
| POST | `/rules` | 新增规则（唯一性+冲突校验） |
| PUT | `/rules/{code}` | 编辑规则 |
| DELETE | `/rules/{code}` | 删除规则 |
| POST | `/rules/generate` | 模式一：启发式单条规则草稿 |
| POST | `/rules/generate/library` | 模式一：业务场景 → 完整规则库（LLM 开启时） |
| POST | `/rules/review` | 模式三：规则校验优化（静态 + 可选 LLM） |
| POST | `/evaluate` | 批量评估（准确率/风险拦截率/缺参召回率/实体 F1） |
| GET | `/stats` / POST `/stats/reset` | 路由统计 |
| GET | `/config` | 配置快照 |

### 6.5 评估指标口径

- 意图准确率：`expected` 与 `intentCode` 精确匹配占比。
- 风险拦截率：期望 `RISK_REJECT` 用例中被拒绝的比例。
- 缺参召回率：期望追问（`PARAM_CLARIFY` 或 `*_CLARIFY`）用例中触发追问的比例。
- 实体抽取 F1：用例声明 `expectedEntities`（JSON 对象）时统计，按槽位名+槽位值
  全等匹配，micro 平均，输出 `entityPrecision / entityRecall / entityF1`。

### 6.6 配置项（`plugin.intent.*`）

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `enabled` | true | 总开关 |
| `rules-location` | classpath:ai-intent/intent-rules.json | 规则来源（file: 可写） |
| `base-confidence` / `step-confidence` / `entity-confidence` / `max-confidence` | 0.8 / 0.06 / 0.06 / 0.99 | 规则置信度参数 |
| `ambiguous-low` / `ambiguous-high` | 0.5 / 0.8 | 三级置信度边界 |
| `risk-confidence` / `unsupported-confidence` / `chat-confidence` / `clarify-confidence` | 0.99 / 0.7 / 0.6 / 0.85 | 固定结果置信度档位 |
| `order-id-pattern` | `\d{6,}` | 单据号抽取正则，置空不抽取 |
| `exempt-slots` | timeRange | 必填槽位豁免名单 |
| `llm-enabled` | false | 是否启用 LLM 精准识别层（同时启用模式一/三 LLM） |
| `llm-parser-class` | （空） | LLM 解析器实现类全限定名 |
| `llm-prompt` | （空） | 模式二系统提示词覆盖 |
| `llm-base-url` / `llm-api-key` / `llm-model` / `llm-timeout-ms` | — | OpenAI 兼容端点配置 |
| `entity-words` | 内置词表 | 槽位词表（逗号分隔，支持 词=值） |
| `entity-intents` | 内置映射 | 槽位适用意图映射 |
| `history-window` / `reference-words` | 8 / 指代词表 | 多轮指代消解 |
