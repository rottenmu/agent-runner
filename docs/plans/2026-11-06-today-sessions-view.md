# 「今日对话」下钻到今日会话列表并查看会话内容

> 状态：已落地（含实施记录）
> 起草日：2026-11-06
> 关联模块：`modules/agent-memory/frontend/agent-memory-ui`（纯前端）；数据源 `/api/agent-memory/analytics/messages`（OLAP 宽表）、`/api/agent-memory/arch/stats`（L0 计数）
> 规范依据：`docs/rules/PLAN_DOC_RULES.md`

触发原因：记忆总览 →「会话消息」Tab 里的「今日对话」卡片此前只是 `archStats.todayDialogues` 的**纯展示数字**，无点击行为，用户无法下钻到「今天有哪些会话、每个会话说了什么」。

---

## 一、置信度与剩余风险

- 当前置信度：95%
- 主要剩余风险：列表读 OLAP Arrow 宽表（ETL 每分钟同步），卡片数字读 H2 L0 实时计数；≤60s 内两者可能对不上，表现为「卡片有数、列表为空或偏少」，需靠页面提示 + 刷新区分，不能据此判定写入失败。

## 二、目标

- 点击「今日对话」卡片 → 跳转到独立的今日会话列表页，URL 可直达/收藏。
- 列表按会话聚合今日 L0 消息：会话 ID、消息数、首/末时间、最新内容摘要。
- 点击任一会话 → 查看该会话**当天全部消息**（时间正序、含 role/source/trace）。
- 明确不做：不改后端接口/DDL、不重启服务、不做实时（<1s）写入、不做消息分页或虚拟滚动、不补 favicon、不改「今日对话」数字的统计口径。

## 三、成功标准（真机可判定）

1. 打开 `#/agent-memory` 并切到「会话消息」Tab，`.stat-today-link` 存在且可见，其数字等于 `/api/agent-memory/arch/stats` 的 `todayDialogues`。
2. 点击后 URL 变为 `#/agent-memory/today-sessions`，页面出现「今日会话」标题。
3. 列表行数 = 按 `ts` 聚合出的今日会话数。
4. 页面「今日消息（OLAP 已同步）」条数 = 卡片数字（ETL 同步完成后）。
5. 点「查看内容」→ 抽屉可见且唯一，timeline 节点数 = 该会话今日消息数，抽屉标题含所选会话 ID。
6. 抽屉首条内容 = 该会话当天最早一条消息。
7. 点「返回会话消息」→ 离开今日会话页，且激活 Tab 回到「会话消息」。
8. 两个 SFC 编译 0 错误；Vite 即时编译新模块与改动的模块均返回 200。
9. 早停生效：今日数据只需 1 页（`size=500`），不触发 `MAX_PAGES=20` 上限。
10. 除既有 favicon 404 外无 `pageerror` / `console.error`。

## 四、参照行为（对标）

无。内部增量功能，无外部产品对标。

## 五、现状差距

- **没接线，非能力缺失**：后端 `/analytics/messages` 已固定 `ORDER BY ts DESC` 分页返回 `trace_id/session_id/role/content/ts/source`，`ts` 为毫秒 epoch；`/arch/stats` 已提供 `todayDialogues`。前端只是没有把这些数据按日聚合展示。
- 缺 3 样东西：卡片点击行为、今日会话视图、路由与壳内 pane 映射。
- 口径歧义：`todayDialogues` 实为 **H2 `l0_raw_log` 今日行数**（`countL0Today()`：`ts >= startOfToday()`），不是「会话个数」；下钻后展示的是会话数，必须显式说明差异。
- 后端不提供按日/按会话分组的接口；若新增接口需改 Java 并重启 9900，成本高于前端聚合。

## 六、技术方向

- **纯前端零后端改动**：复用 `/analytics/messages`，按 `ts DESC` 分页拉取，遇到第一条 `ts < 今日 00:00` 立即停止（早停），避免拉全表；`MAX_PAGES=20` 兜底防异常死循环。实测今日数据 1 页即停。
- 本地自然日口径与后端 `startOfToday()` 一致（均按本地时区 00:00），保证卡片与列表可对齐。
- `ts` 缺失的行跳过但**不触发早停**，避免脏数据让列表提前截断。
- 聚合后的消息直接用于抽屉展示，不再二次请求 `/session-messages`，避免两次查询口径/延迟不一致。
- 页面顶部对比 L0 计数与 OLAP 可见数，不一致时显示「ETL 同步中」提示，把「查不到 ≠ 没写入」写到 UI 上。
- 视图独立成 `TodaySessions.vue`（253 行，符合组件 ≤300 行），主壳只加 import / pane / 路由映射 / 两个跳转函数，避免继续堆高已达 1128 行的 `MemoryDashboard.vue`。

## 七、里程碑

- M1：新增 `TodaySessions.vue`（列表 + 抽屉 + 早停聚合）。
- M2：主壳接线（卡片可点击、pane、pathTabs、侧栏入口、返回函数）+ 路由。
- M3：编译校验、真实数据复算、浏览器点击级 E2E。

## 八、自测计划

1. 用 `@vue/compiler-sfc` 解析并编译两个 .vue → 预期 `parseErrors=0 / templateErrors=0 / scriptOK=true`。
2. `curl --noproxy '*' http://localhost:6060/src/views/TodaySessions.vue` 等 3 个模块 → 预期均 200。
3. 用 Python 复现早停聚合，对比 `/arch/stats` → 预期 `today_messages == todayDialogues`，`pages_fetched=1`。
4. Playwright 打开 `#/agent-memory` → 点「会话消息」Tab → 断言卡片可见且数字等于后端值。
5. 点卡片 → 断言 URL 含 `today-sessions`、列表行数、统计条数字。
6. 点「查看内容」→ 断言 `.el-drawer.open` 唯一、timeline 数、标题含会话 ID、首条内容。
7. 点「返回会话消息」→ 断言离开该页且激活 Tab 为「会话消息」。
8. 收集 `pageerror` / `console.error` → 预期为空；若有 404，先定位 URL 再判断是否本次引入。

⚠️ 语义缺陷声明：OLAP 有 ≤60s ETL 延迟，步骤 3/5 的「数量相等」只在同步完成后成立；不相等时必须先查 L0 计数与 ETL 日志，不能判为导入失败。

## 九、已知延后项

- 后端按日分组接口、服务端时间范围参数 → 本期前端聚合已够用，改后端需重启 9900。
- 抽屉消息超过数百条时的分页 / 虚拟滚动 → 今日实测 281 条渲染正常，暂不做。
- `favicon.ico` 404 → 既有问题（`index.html` 未声明 icon、无 `public/`），与本次改动无关，未顺手修改。
- 跨天/跨时区切换后的日期重算 → 当前按浏览器本地自然日，刷新页面即重算。
- URL 直达时的 loading/错误态细化 → 现有 `v-loading` 与空态已覆盖。

## 十、实施顺序

M1 写新视图 → M2 主壳与路由接线 → M3 编译校验 → 真实数据复算 → 点击级 E2E → 回填本节。

## 十一、实施记录

- 实施日期：2026-11-06 19:52 ~ 20:00
- 实际改动清单：

| 文件 | 改动 |
| --- | --- |
| `src/views/TodaySessions.vue` | **新增** 253 行：早停分页聚合、今日会话表格、内容抽屉（时间正序 timeline）、L0/OLAP 差异提示、返回按钮 |
| `src/views/MemoryDashboard.vue` | 「今日对话」卡片改为可点击（含 `role/tabindex/键盘`）；新增 `today` pane 与 `TodaySessions` import；`pathTabs` 加 `/agent-memory/today-sessions`；L0 侧栏组加「今日会话」；新增 `goToday()` / `backFromToday()`；4 行 CSS |
| `src/router.js` | 新增 `/agent-memory/today-sessions` 路由（复用 `MemoryDashboard` 主壳） |

**未改动**：任何后端 Java、DDL、autoconfig、API 封装、样式变量；9900 与 6060 服务均未重启（Vite HMR 自动生效）。

### 与方案的偏差

| 原方案 | 实际做法 | 原因 |
| --- | --- | --- |
| 查看会话内容时再调 `/session-messages` | 直接展示聚合阶段已取到的今日消息 | 避免两次查询在 ETL 延迟窗口内口径不一致，且少一次请求 |
| 「今日对话」数字即会话数 | 页面显式写明卡片是**今日 L0 消息条数**，本页按会话聚合 | `countL0Today()` 统计的是行数，不是会话数；不澄清会让 281 → 1 看起来像数据丢失 |
| 无差异提示 | 调 `/arch/stats` 对比 L0 与 OLAP，不一致时显示同步提示 | 复现「查不到 ≠ 没写入」，避免用户把 ETL 延迟误判为功能坏了 |
| E2E 用 `.el-drawer` 判可见 | 改用 `.el-drawer.open` | 主壳常驻一个隐藏的「会话消息预览」抽屉，`.el-drawer` 命中 2 个元素触发 Playwright strict mode；与工作记忆里「v-show 壳体同名页」同类坑 |

### 真机验证结果（逐条对账第三节）

1. ✅ SFC 编译：`TodaySessions.vue` / `MemoryDashboard.vue` 均 `parseErrors=0 / templateErrors=0 / scriptOK=true`。
2. ✅ Vite 即时编译 `TodaySessions.vue`(58644B) / `MemoryDashboard.vue`(261870B) / `router.js`(12989B) 均 200。
3. ✅ 真实数据复算：`today_start=2026-11-06 00:00:00`，`pages_fetched=1`、`today_messages=281`、`today_sessions=1`；`/arch/stats` 的 `todayDialogues=281`，**diff=0**（ETL 已同步）。
4. ✅ Playwright 点击级 E2E **10/10 全绿**：卡片可见且数字 281 → URL `#/agent-memory/today-sessions` → 列表 1 行 → 抽屉唯一可见、timeline **281** 条、标题含 `f8da8a70…`、首条内容为「阅读 Agent runner代码」→ 返回 `#/agent-memory?tab=overview` 且激活 Tab 为「会话消息」→ 无 `pageerror`/`console.error`。
5. ✅ 统计条实测：今日会话 1 个 / 今日消息 281 条 / 最近活跃 19:08:52。

### 验证过程中新发现的坑

- **既有 favicon 404**：首次 E2E 唯一 FAIL 是 `console.error: Failed to load resource 404`。分步定位后确认首屏即有、`page.on('response')` 捕获不到（浏览器自动请求），`curl /favicon.ico` 返回 404，`index.html` 未声明 icon 且无 `public/` 目录。与本次改动无关；最终验证用 `context.route('**/favicon.ico', 204)` 仅拦截该资源，其余错误仍一律判失败，**没有放宽其它断言**。
- **抽屉选择器必须带 `.open`**：`MemoryDashboard` 的「会话消息预览」抽屉常驻 DOM（隐藏），任何 `.el-drawer` 的 `isVisible()` / `count()` 都要限定 `.el-drawer.open`，否则严格模式报错或误判。
- **`el-tab-pane` 未激活时卡片不可点**：用户要求「会话消息 tab 中点击」，E2E 必须先切 Tab；直接对隐藏 pane 里的卡片 click 会超时。
- **卡片数字与列表行数不是同一量纲**：281 条消息聚合成 1 个会话，页面已用副标题与统计条写明，避免被当成数据丢失。
- 改动**未提交 git**（3 个前端文件 + 1 份方案文档）；生产构建 `npm run build` 未代跑，需自行执行。

## 十二、缺陷修正：「今日对话有 281 个，查看会话只有一条记录」（20:10 用户反馈）

### 根因：口径 + 聚合粒度双重错配

| 层 | 事实 |
| --- | --- |
| 卡片数字 | `archStats.todayDialogues` = H2 `l0_raw_log` **今日消息行数**（`countL0Today()`），不是对话数、更不是会话数 |
| 上一版聚合 | 按 `session_id` 分组；导入器用**宿主 jsonl 文件名**作 sessionId，今日消息全部来自同一个会话文件 `f8da8a70…` → `session_id distinct = 1` |
| 结果 | 281 条消息被压成 1 行，视觉上像「数据丢了」 |

真机数据（20:10，会话持续追加已由 281 涨到 **294** 条）：`session_id` distinct=**1**；`trace_id` distinct=**59**，其中 **42 组含 user/assistant**（真实问答轮，277 条消息，每组都有 user 提问），另 **17 条**是 `trace-*` 的 `memory_write` 系统写入轨迹（每组 1 条）。`trace_id` 在导入时等于 `conversationRequestId`，所以「一轮问答」才是「今日对话」的正确粒度。守恒：`277 + 17 = 294`。

### 修正内容（纯前端，零后端改动 / 零 DDL / 零新增接口）

- `TodaySessions.vue` **默认视图改为「按对话轮」**：key=`trace_id`，一行一轮；列为 开始时间 / 用户提问（该轮首条 user）/ 消息数 / 会话 ID 缩写 / 「查看内容」。
- 纯 `system` 的写入轨迹**不计入对话轮**，单独计数并以提示条说明；切到「按会话」可看到含它们的全部消息，保证条数守恒、不丢数据。
- 新增维度切换按钮「按对话轮 / 按会话」，**只重算 computed，不重新请求**（`rowsCache` 缓存今日消息）；会话视图保留原 6 列与全量抽屉。
- 统计卡改 4 个：今日对话轮 / 今日消息（OLAP 已同步）/ 宿主会话数 / 最近活跃；副标题直接写明「卡片是消息条数、本页按一轮问答展开、今日来自 N 个宿主会话」。
- 抽屉标题按维度切换，新增「会话 ID」描述项；主壳卡片 label 由「**今日对话**」改为「**今日消息**」并加 `title` 口径说明，hint 改「查看对话 →」。
- `TodaySessions.vue` 253 → **334 行**（页面 ≤500 行规范内），`MemoryDashboard.vue` 仅改 1 行文案。

### 真机验证（17/17 全绿）

1. ✅ SFC 编译：`TodaySessions.vue` / `MemoryDashboard.vue` 均 `parseErrors=0 / templateErrors=0 / scriptOK=true`；`router.js` 解析通过。
2. ✅ Vite 即时编译 3 个模块全 200（HMR 生效，未重启 6060/9900）。
3. ✅ Python 复刻前端 `aggregate()` 复算：`294 条 / 42 轮 / 1 会话 / 17 条系统轨迹`，`277+17=294` 守恒，42 轮**全部含 user 提问**。
4. ✅ Playwright 点击级 **17/17**：卡片「今日消息 294」→ 跳 `today-sessions` → 统计卡「今日对话轮 42」且**列表 42 行**（核心回归断言 `rows === turnNum && turnNum > 1`）→ 首行展示用户提问 → 抽屉 12 条 timeline 与该行消息数一致、含 `user`、标题「本轮对话 · 19:51:48」→ 切「按会话」列表 1 行、抽屉 **294 条**且显示会话 ID → 返回后激活 Tab 仍是「会话消息」→ 无 `pageerror`/`console.error`（仅拦截既有 favicon 404）。
5. ✅ 卡片 294 与 OLAP 294 对齐（diff=0，ETL 已同步）。

### 结论

不是丢数据，是**把一个会话文件里的 294 条消息当成 1 条记录展示**。现在默认按轮展开为 42 条对话记录，同时保留「按会话」视图并把卡片口径改清楚；两种视图消息条数守恒。改动**仍未提交 git**，`npm run build` 未代跑。
