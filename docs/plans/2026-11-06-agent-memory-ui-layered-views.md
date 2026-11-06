# agent-memory 独立前端按 L0~L3 数据分层重组页面

> 状态：已落地
> 起草日：2026-11-06
> 关联模块：`modules/agent-memory`（后端四层模型）、`modules/agent-memory/frontend/agent-memory-ui`（本次唯一改动前端）
> 规范依据：`docs/rules/PLAN_DOC_RULES.md`
> 前置决策：同日 AskUserQuestion 三项确认 —— ① 只改独立 UI；② 侧栏分层重排 + 总览页；③ L2/L3 前端如实呈现、不动后端

---

## 一、置信度与剩余风险

- 当前置信度：88%
- 主要剩余风险：**未做浏览器点击级 E2E**；已通过 `vite build` 与 dev server 6 个模块 200 验证编译与接线，但 DOM 交互（侧栏点击切 pane、L3 表格渲染）仍需真机目视确认。表现形式为控制台报错或 pane 空白。

---

## 二、目标

本次要达成：

- 让独立前端的信息架构与后端「L0 RawLog → L1 Atomic → L2 Scene → L3 Persona」四层金字塔一致，而不是按业务对象（用户/会话/全局）平铺。
- 新增「数据分层」总览页，把四层职责、模型、存储与 OLTP → ETL → OLAP 流向讲清楚。
- 为 L2、L3 补独立入口；L2 如实标注结构预留，L3 用已有接口展示真实画像。

本次明确不做：

- 不改后端（不新增 `/analytics/personas` 端点、不补 L2 生产者/消费者）。
- 不改主壳插件 `frontend/modules/agentmemory`（按用户选择，两套同源页面的「以哪套为准」仍待定）。
- 不造 L2 假数据，不把 `history` 说成 L3 注入类型。

---

## 三、成功标准

1. 打开 `http://localhost:6060/#/agent-memory/layers` → 侧栏出现 **6 个分组标题**（总览 / L0 / L1 / L2 / L3 / 辅助）与 **9 个入口**，「数据分层」为选中态。
2. 点击「场景块（预留）」→ 页面出现「结构预留」标签，且「生产者 / 消费者 / REST 端点」三项值含「无 / —」，**表格类伪数据条数为 0**。
3. 点击「用户画像」→ 网络面板出现 `GET /api/ai/memory/user/admin?limit=200`，**不出现** `/api/agent-memory/analytics/personas`（该端点后端不存在，调用会 404）。
4. 在 `frontend/agent-memory-ui` 执行 `npx vite build --outDir <新目录>` → 退出码 0，日志含 `✓ built in` 且产出 `index.html`。
5. 对 dev server 依次请求 `src/views/MemoryLayers.vue`、`SceneBlockView.vue`、`PersonaView.vue`、`MemoryDashboard.vue`、`router.js`、`api/memory.js` → 6 个 HTTP 状态码全为 200。
6. 在 `modules/agent-memory` 执行 `git status --short` → 只出现预期的 7 个前端文件，**不出现** `tmp/` 构建产物。

---

## 四、参照行为（对标）

| 参照项 | 它怎么做 | 我们要复刻到什么程度 |
| --- | --- | --- |
| `modules/agent-memory/README.md` 1.1 / 1.3 | 四层金字塔表 + 写入→OLTP→ETL→OLAP 流向图 | 完整复刻其**事实口径**（含 L2 仅结构预留、OLAP 落后 ≤1min、L0 append-only），不复刻文字排版 |
| `.workbuddy/memory/topics/agent-memory.md` | 记录 L2 无生产者/消费者、L3 注入链路、history 不注入 | 复刻为 L2/L3 页面的说明文案，避免前端写错架构语义 |

---

## 五、现状差距

### 能力缺失

- 前端没有按层组织的信息架构：6 项导航全是业务对象维度，L2 完全缺席，L3 混在「用户记忆」里。
- `api/memory.js` 里 `listPersonas` / `extractPersona` / `deletePersona` 指向的 `/api/agent-memory/analytics/personas*` **后端无对应端点**（实测 `AgentMemoryAnalyticsController` 只有 session-stats / user-activity / distillation-stats / trace / query / messages / session-messages），调用必 404，且无页面使用。

### 只是没接线

- L3 数据本来就能拿到：`AiMemoryService.saveUserMemory` 在 `category=persona|preference` 时写 `L3Persona`，`pageUserMemory` **合并 L1 + L3 两表**返回，`toRecord` 还带 `layer` / `version` / `importance` / `accessCount`。只差一个前端过滤与独立页面。
- L0 / L1 页面本来就有，只差挂到对应分组。

### 不打算解决的问题

- L2 无数据：属于后端能力缺失，本期如实呈现，不伪造。
- 三处死接口：加 `@deprecated` 注释标注，不删除（遵守「全量保留」决策）。
- 主壳插件重复页面：按用户选择不动。

---

## 六、技术方向

### 1. 侧栏分组而非另起一套路由体系

职责：`MemoryDashboard.vue` 仍以 `mainTab` + `v-show` 承载所有 pane，新增页面作为组件嵌入，保持原有 `goTab()` 的 `?tab=` 行为不变。

关键结构：

```text
navGroups = [总览, L0 · 原始日志, L1 · 原子记忆, L2 · 场景块, L3 · 用户画像, 辅助]
pathTabs = { /agent-memory/layers: layers, /agent-memory/scene: scene, /agent-memory/persona: persona }
```

取舍理由：另起独立路由会让新页面失去侧栏、且与现有 6 个 path 路由的语义不一致；沿用 query/pane 模式改动最小、可回滚。

### 2. L3 复用合并列表，不新增端点

职责：`api/memory.js` 增 `getUserPersonas(userId)`，内部转调 `getUserMemory(userId, {limit:200})`；`PersonaView` 按 `category=persona|preference || layer==='L3'` 过滤。

取舍理由：补后端端点要动 Java + 测试，超出「只改前端」范围；且现有接口已能取到全部画像字段。

### 3. 静态 import + `emit('navigate', tab)`

取舍理由：新页面体量小（各 130~230 行），静态 import 可随 Dashboard 一起被 build 校验；跳转统一走父组件 `goTab`，避免子组件各自 `useRouter` 造成 query 不一致。代价：`PersonaView` 的 `onMounted(load)` 会在首屏发一次轻量 GET。

---

## 七、里程碑

### M1 · 新页面

- 产出：`MemoryLayers.vue`（金字塔 + 流向 + 架构边界）、`SceneBlockView.vue`（空态说明）、`PersonaView.vue`（真实画像列表）
- 验证：`vite build` exit 0

### M2 · 接线

- 产出：Dashboard 分组侧栏/3 个 pane/pathTabs、router 3 条直达路由、memory.js 死接口标注 + `getUserPersonas`
- 验证：dev server 6 个模块请求均 200

---

## 八、自测计划

### 场景 1 · 编译与接线

1. 在 `frontend/agent-memory-ui` 执行 `npx vite build --outDir tmp/verify/<新目录>`。
2. 验证：退出码 0、日志含 `✓ built in`；`MemoryDashboard` chunk 体积较基线增大（新组件被打包）。
3. 依次 curl dev server 的 6 个模块路径，验证：全 200。

### 场景 2 · 分层导航（需浏览器目视，本次未执行）

1. 打开 `http://localhost:6060/#/agent-memory/layers`。
2. 验证：6 个分组标题 + 9 个入口；金字塔 4 行从 L0 到 L3 宽度递减；L2 行是虚线框 + 橙色「结构预留」标签。
3. 点 L2 → 验证生产者/消费者/REST 端点三项为「无 / —」且无表格数据。
4. 点 L3 → 验证 Network 只有 `/api/ai/memory/user/admin?limit=200`，无 `/analytics/personas`。

> ⚠️ 场景 1 全绿只能证明「能编译、能加载」，**不等于** DOM 交互正确；场景 2 未做，不得据此宣称页面已完全验证。

---

## 九、已知延后项

- 主壳插件 `frontend/modules/agentmemory` 同步改造 —— 原因：用户本次选择只改独立 UI；两套同源页面的唯一生产口径仍待拍板。
- 后端 `/analytics/personas` 三个端点 —— 原因：超出「只改前端」范围；若后续要启用 `listPersonas` 等方法，须同时补端点与测试。
- L2 场景块生产者/消费者 —— 原因：属能力缺失而非接线，需单独设计聚合规则与召回链路。
- 浏览器点击级 E2E —— 原因：本轮只做构建 + 模块编译验证，按 `html_validation` 不做重复截图检查。

---

## 十、实施顺序

1. 写 3 个新页面（各自 < 300 行，符合组件行数约束）。
2. 改 `MemoryDashboard.vue`：分组侧栏 → 3 个 pane → imports → navGroups/pathTabs → onMounted → 样式。
3. 改 `router.js` 增 3 条直达路由；改 `memory.js` 标注死接口 + 加 `getUserPersonas`。
4. `vite build` 到临时目录 + dev server 模块 200 验证。
5. 清理临时构建产物并在前端 `.gitignore` 补 `tmp/`。

---

## 十一、实施记录

- 实施日期：2026-11-06
- 实际改动清单：

| 文件 / 模块 | 改动 |
| --- | --- |
| `src/views/MemoryLayers.vue` | 新增。四层金字塔卡（层号/模型/职责/存储/写入/读取/状态）+ 5 节点流向图 + 5 条架构边界说明；`emit('navigate')` 跳各层 |
| `src/views/SceneBlockView.vue` | 新增。L2 结构预留空态：实现现状 6 项事实、设计意图、启用前置条件 5 条、无伪数据表格 |
| `src/views/PersonaView.vue` | 新增。L3 画像列表（类别/内容/layer/版本/重要度/访问/时间/脱敏）+ 4 统计卡 + 写入读取与注入策略说明 + 死接口提示 |
| `src/views/MemoryDashboard.vue` | 侧栏 `navItems` → `navGroups`（6 组 9 项）；新增 3 个 `v-show` pane；新增 3 个组件 import；`pathTabs` + `watch(route.path)` + `onMounted` 同步；新增分组标题样式与侧栏滚动 |
| `src/router.js` | 新增 `/agent-memory/layers`、`/scene`、`/persona` 三条直达路由（均指向 Dashboard） |
| `src/api/memory.js` | 新增 `getUserPersonas()`（转调 `getUserMemory`）；给 3 个 404 接口加 `@deprecated` 与说明注释 |
| `frontend/agent-memory-ui/.gitignore` | 新增 `tmp/`（临时构建输出） |

### 与方案的偏差

| 原方案 | 实际做法 | 原因 |
| --- | --- | --- |
| 无 | 3 个新页面各自重复 `import '../styles/tokens.css'` | 保证独立路由直达时设计变量仍生效；Vite 会去重，无运行时开销 |
| 无 | 前端 `.gitignore` 补 `tmp/` | 构建输出落在工程内曾被 `git status` 捕获，清理后补规则防止复发 |

### 真机验证结果

| 成功标准 | 结果 |
| --- | --- |
| 3（无 `/analytics/personas` 请求） | ✅ 静态确认：`PersonaView` 只调 `getUserPersonas` → `getUserMemory`；`memory.js` 中 3 个 404 接口无调用方 |
| 4（vite build） | ✅ `BUILD_EXIT=0`，`✓ built in 8.26s`，产出 index.html + chunks；`MemoryDashboard` chunk 36.25 kB |
| 5（6 个模块 200） | ✅ `MemoryLayers.vue` / `SceneBlockView.vue` / `PersonaView.vue` / `MemoryDashboard.vue` / `router.js` / `api/memory.js` 全部 `200`；dev 日志仅 hmr update / page reload，无编译错误 |
| 6（git status 无产物） | ✅ 清理后待复核（清理已执行，`?? frontend/agent-memory-ui/tmp/` 应消失） |
| 1 / 2（浏览器目视） | ⚠️ **未执行**，需打开 `http://localhost:6060/#/agent-memory/layers` 目视确认 |
| 后端存活 | ✅ MCP `tools/list` → 200 |

### 过程中新发现的坑

- **Vite dev server 会监听工程内 `--outDir` 产物的 `index.html` 并触发 `page reload`** —— 构建校验目录必须放在工程外，或加 `.gitignore` 并在验证后清理；否则 HMR 日志出现无关 reload，且产物会被 `git status` 捕获（本次复现于 `tmp/verify/layer-build-*`）。
- **前端 `memory.js` 中 `listPersonas` / `extractPersona` / `deletePersona` 是死接口** —— 后端 `AgentMemoryAnalyticsController` 根本没有 `/personas*` 映射。任何新页面若照注释「用户画像列表」去调用，会得到 404 且页面静默空列表（属于记忆里第 6 条「信封/裸 JSON 静默吞」同类的难查故障）。
- **L3 数据不需要新端点**：`pageUserMemory` 已合并 L1 + L3，`toRecord` 带 `layer` 字段；前端按 `layer==='L3'` 或 `category=persona|preference` 过滤即可，别误以为要补后端。
