# 会话消息统计卡跳转独立画像配置页 + 列表分页

> 状态：已落地
> 起草日：2026-11-06
> 关联模块：`modules/agent-memory/frontend/agent-memory-ui`
> 规范依据：`docs/rules/PLAN_DOC_RULES.md`

---

## 一、置信度与剩余风险

- 当前置信度：95%
- 主要剩余风险：客户端分页一次最多取 200 条（后端 `MemoryArchRepository.list` 对 `size` 做了 `min(size,200)` 截断），若 `memory_arch_config` 超过 200 行，列表会静默不完整；当前 13 条，短期无影响。

---

## 二、目标

本次要达成：

- 「会话消息」Tab 中的 **SOUL 配置 / USER 档案 / 已提取** 三张统计卡变成可点击（鼠标 + 键盘），跳转到对应的画像配置列表页，筛选条件写在 URL 上，可收藏/刷新/回退。
- 跳转后的页面、以及「会话消息」Tab 内的表格，**都支持分页**（每页 10 条，可切 10/20/50，可翻页/跳页）。
- 把重复了两处的表格/工具栏/弹窗抽成公共组件，避免 `MemoryDashboard.vue` 继续膨胀。

本次明确不做（延后项见第九节）：

- 不改后端 Java 代码、不加 DDL、不新增 REST 参数、不重启 6060/9900（用户已确认「零后端·前端分页」）。
- 不改 16600 主壳 `frontend/modules/ai/menus.js`。
- 不动 L0~L3 四层页面与「今日消息」卡的下钻逻辑。
- 不造业务数据；列表展示的是库里真实的 13 条配置。

---

## 三、成功标准

本方案完成后，以下每一条都应能在真机上直接判定为真（数据基线：`/arch/stats` → SOUL 2 / USER 11 / 已提取 11；`/arch/configs` → total 13）：

1. 打开 `#/agent-memory?tab=overview` → 恰有 3 张 `role="button"`、`tabindex="0"`、带「查看列表 →」的统计卡；统计数字为 2 / 11 / 11。
2. 同页表格存在 `el-pagination`，默认显示 **10 行**、总数 **13**；点下一页 → **3 行**且首行内容与第 1 页不同。
3. 点 **SOUL 配置** → URL 为 `/agent-memory/arch-configs?type=SOUL`，标题含「SOUL 配置」，列表 **2 行**且每行类型为 SOUL。
4. 点 **USER 档案**（含 Enter 键聚焦触发）→ URL `?type=USER`，第 1 页 **10 行**、总数 **11**，点下一页 **1 行**。
5. 点 **已提取** → URL `?extracted=1`，标题含「已提取」，第 1 页 **10 行**、总数 **11**，每行 `type=USER` 且 `source=自动`（与 `countExtracted()` 口径一致）。
6. 独立页「范围」切到「全部」→ URL 无筛选参数，第 1 页 10 行、总数 13；「返回消息记录」回到 overview 且 h1 为「消息记录」。
7. 首屏 `/arch/configs` 恰好 1 次；刷新按钮点击后新增 `/arch/configs` 与 `/arch/stats` **各恰好 1 次**（不重复加载）。
8. 三个既有 E2E 回归：`e2e-message-records-nav.cjs` 22/22、`e2e-l1-data.cjs` 18/18、`e2e-l3-persona.cjs` 22/22；JS 运行时异常与 console error 均为 0。

---

## 四、参照行为（对标）

- `/agent-memory/today-sessions` 的既有模式：路由仍指向 `MemoryDashboard.vue`，由 `pathTabs` 把 path 映射成 `mainTab`，以 `v-show` pane 呈现，保留左侧栏壳体。本次 `/agent-memory/arch-configs` 沿用此模式。
- `MemoryArchController.list:35`：支持 `type/q/page/size`，返回 `{rows,total,page,size}`；**没有 `source` 参数**。
- `MemoryArchRepository.countExtracted:46`：`已提取 = type='USER' AND source='自动'`；`extract(id)` 即 `updateSource(id,'自动')`。
- `MemoryArchRepository.list:70`：`size` 被 `Math.max(1, Math.min(size, 200))` 截断，故一次最多取 200 条。

---

## 五、现状差距

- 五张统计卡中只有「今日消息」可点击（`goToday()`）；SOUL / USER / 已提取 三张是纯 `<div>`，无 `role`/`tabindex`/点击处理。
- `MemoryDashboard.vue` 的 `loadArch()` 硬编码 `page:0, size:100`，表格无 `el-pagination`，一次最多显示 100 行且无翻页能力。
- 画像配置的表格、工具栏、编辑弹窗、CRUD 全部内联在 `MemoryDashboard.vue`（691 行，已超页面 500 行上限），无法在独立页复用。
- `TodaySessions.vue:277` 常驻子页自己会调 `getArchStats()`，首屏 `/arch/stats` 本来就有两次；写断言时必须区分「本页刷新」与「常驻副本」，否则会误判成重复加载。

---

## 六、技术方向

- **新增公共组件** `src/components/ArchConfigTable.vue`（244 行 ≤300）：一次 `listArchConfigs({page:0,size:200})` 取回全量，前端按 `type / q / extractedOnly` 过滤，`computed` 切片分页；`el-pagination` 提供 total / sizes / prev / pager / next / jumper；`defineExpose({reload})` 供父组件刷新；CRUD 后 `emit('changed')` 让统计卡刷新。
  - 三种筛选统一走客户端分页，避免「服务端分页 + 客户端过滤」两套口径混用；后端缺 `source` 过滤是采用客户端分页的直接原因。
- **新增独立页** `src/views/ArchConfigs.vue`（111 行）：从 `route.query` 派生 `scope`（ALL/SOUL/USER/EXTRACTED），URL 是唯一筛选来源；范围切换用 `router.push` 改 query；`v-if="active"` 控制表格挂载，避免非激活时发请求。
- **路由** `router.js` 加 `/agent-memory/arch-configs` → `MemoryDashboard.vue`，`pathTabs` 加 `'arch'`；返回按钮复用 `backFromToday()`（显式把 `mainTab` 切回 `overview`，因为 `pathTabs` 不含 `/agent-memory`）。
- **卡片** 加 `.stat-card-link`、role/tabindex、`aria-label`、Enter 键处理与「查看列表 →」提示；`goArch(kind)` 把筛选写进 URL。
- **瘦身** `MemoryDashboard.vue` 删除 9 个状态、14 个函数、9 个 API import 与死 CSS，691 → 552 行；`refreshAll()` 改为「子组件 reload + loadArchStats」，`onMounted` 只补统计卡，避免首屏重复请求。

---

## 七、里程碑

| 里程碑 | 内容 | 判据 |
| --- | --- | --- |
| M1 | 公共列表组件（分页 + CRUD） | Vite 200，组件 ≤300 行 |
| M2 | 独立页 + 路由 + pathTabs | 三个 URL 均可直达且保留侧栏 |
| M3 | 卡片可点击 + 键盘可达 | 3 张卡 role/tabindex/hint 齐全 |
| M4 | 点击级 E2E + 鉴别力对照 + 三回归 | 38/38、对照 31/38、22+18+22 |

---

## 八、自测计划

```bash
curl -s --noproxy '*' "http://127.0.0.1:9900/api/agent-memory/arch/stats"
curl -s --noproxy '*' -o /dev/null -w "%{http_code}\n" "http://localhost:6060/src/components/ArchConfigTable.vue"
node tmp/verify/e2e-arch-card-nav-pagination.cjs
node tmp/verify/e2e-message-records-nav.cjs
node tmp/verify/e2e-l1-data.cjs
node tmp/verify/e2e-l3-persona.cjs
```

鉴别力对照：临时把 `ArchConfigTable.vue` 的 `pageSize` 由 `10` 改成 `200`（模拟无分页）跑同一脚本，应有多条行数/翻页断言 FAIL；恢复后必须回到 38/38。

---

## 九、已知延后项

- 数据超过 200 条时，客户端分页会漏数据；彻底解决需给后端加 `source/extracted` 过滤参数并改走服务端真分页，同时改 Java + 重启 9900。
- Element Plus 未配中文 locale，分页器显示 `Total 13 / 10/page / Go to`；本次未改全局语言配置。
- `MemoryDashboard.vue` 552 行仍超仓库 500 行上限（本轮已减 139 行），后续可继续拆统计卡区块。
- 改动提交 git、`npm run build` 生产构建（按约定不代跑）。
- 16600 主壳菜单同步、L2 完整链路仍未做。

---

## 十、实施顺序

1. 核对后端接口参数与 `已提取` 真实口径 → 2. 确认跳转形态与分页口径（用户选择「独立画像配置页」+「零后端·前端分页」）→ 3. 抽公共组件 → 4. 新建独立页与路由 → 5. 三张卡可点击 + 主壳瘦身 → 6. 点击级 E2E → 7. 鉴别力对照 + 三回归 → 8. 文档/日志/会话记忆。

---

## 十一、实施记录

- **实测基线**：`/arch/stats` = `{soulConfigCount:2, userProfileCount:11, todayDialogues:358, totalDialogues:8752, extracted:11}`；`/arch/configs` total=13（SOUL 2 + USER 11，其中 11 条 `source=自动`）。
- **Vite**：4 个改动文件即时编译均 200（MemoryDashboard 84382 / ArchConfigs 26741 / ArchConfigTable 65211 / router 14475 bytes），组件产物含 `el-pagination`。
- **偏差 1**：E2E 首版 `rows()` 误写成 `async` 返回 Promise → `count is not a function`（5/6）。改同步返回 locator（与上一轮 `personaRows()` 同一个坑）。
- **偏差 2**：分页器断言写成中文 `共 13 条`，实际 Element Plus 未配中文 locale、显示 `Total 13 10/page ...`。改为 `totalText(n)` 兼容 `Total N` 与 `共 N 条`，但**数字仍精确**。
- **偏差 3**：首屏断言「/arch/stats 恰好 1 次」FAIL，实测 2 次。定位到常驻子页 `TodaySessions.vue:277` 自己调 `getArchStats()`，是既有行为而非本次引入。改为「首屏 configs 恰好 1 次 / stats 至少 1 次」，并把「刷新后新增 configs、stats 各恰好 1 次」作为更严格的判据。
- **鉴别力对照**：把 `pageSize` 临时改成 `200` 跑同一脚本，首版因「下一页被禁用」点击超时提前终止（7/9），不符合「超时≠断言失败」口径；给 `nextPage()` 加 `isEnabled()` 保护后重跑，对照版 **31/38**，7 条分页数字断言精准 FAIL（13 行、11 行、按钮禁用、无法换页），而跳转/URL/口径/菜单 31 条仍 PASS；恢复 `pageSize=10` 后 **38/38**。
- **回归**：`e2e-message-records-nav.cjs` 22/22、`e2e-l1-data.cjs` 18/18、`e2e-l3-persona.cjs` 22/22。
- **零后端改动、零重启**，HMR 生效。改动仍未提交 git，`npm run build` 未代跑。
