# L3 用户画像补真实数据与 layer 过滤死条件修复

> 状态：已落地
> 起草日：2026-11-06
> 关联模块：`modules/agent-memory/agent-memory-core`、`modules/agent-memory/frontend/agent-memory-ui`
> 规范依据：`docs/rules/PLAN_DOC_RULES.md`

---

## 一、置信度与剩余风险

- 当前置信度：95%
- 主要剩余风险：写入是 append-only，重复执行导入脚本会产生重复画像行；另外 `agent-memory.persona.enabled` 若关闭，画像数据可见但不会注入系统提示词（本次未验证运行时注入）。

---

## 二、目标

本次要达成：

- 让 L3「用户画像」页默认可见真实的跨会话画像（persona 2 条 + preference 3 条），不再是 No Data。
- 修掉 `PersonaView.vue` 中 `r.layer === 'L3'` 恒为 false 的死条件，使 layer 分支真正生效、habit 类别不再漏行，并把层列显示统一成大写。
- 保持四层口径一致：L0/L1 已可见、L2 仍如实标注「结构预留」，零后端改动、零重启。

本次明确不做（延后项见第九节）：

- 不补 L2 生产者 / REST 端点 / 列表（上一轮已确认「保持如实标注」）。
- 不改后端 Java 代码、不加 DDL、不改 MCP schema、不重启 6060/9900。
- 不写伪造业务数据；5 条画像全部提炼自用户长期画像，与 L1 的 5 条项目事实不重复。
- 不同步 16600 主壳 `frontend/modules/ai/menus.js`（上一轮用户确认只改独立 UI）。

---

## 三、成功标准

本方案完成后，以下每一条都应能在真机上直接判定为真：

1. `GET /api/ai/memory/user/admin?limit=200` → `total=10`，其中 `layer='l3'` 的行恰为 5（persona 2 / preference 3），每条 `version=1`、`importance=0.9`。
2. 打开 `#/agent-memory?tab=persona` → 表格 5 行、`el-empty` 为 0、统计卡「画像条数 / persona / preference / 平均重要度」依次为 `5 / 2 / 3 / 0.90`、层列文本含大写 `L3` 且不含小写 `l3`。
3. 拦截同一响应，把一条 L3 行的 `category` 改成 `other`、`layer` 保持 `l3` → 表格仍 5 行（证明 layer 分支生效）。
4. 再把该行 `layer` 改成 `l1` → 表格降到 4 行（证明过滤不是恒真守卫）。
5. 把 `isPersonaRow` 临时回退成旧条件后跑同一脚本 → 第 3 条必须 FAIL、总结果 `21/22`（鉴别力对照），恢复后回到 `22/22`。
6. 两个既有 E2E 回归：`e2e-message-records-nav.cjs` `22/22`、`e2e-l1-data.cjs` `18/18`。
7. JS 运行时异常 0、console error 0、对 `/analytics/personas`（后端不存在的端点）请求数 0。

---

## 四、参照行为（对标）

- `AiMemoryService.saveUserMemory:222`：`category=persona|preference` → `oltp.savePersona(L3Persona)`；`history|custom` → `oltp.saveAtomicMemory(L1)`。
- `H2UserMemoryPageDao.L3_SELECT:37`：SQL 里是 `'l3' AS layer`（小写常量），UNION ALL 后经 `AiMemoryService.toRecord:363` 原样透传。
- `AgentMemoryProperties.Persona.DEFAULT_TYPES` = `persona,preference,habit`（页面过滤类别与之对齐）。
- 上一轮 `tmp/verify/write_l1_real_memories.py`：MCP `memory_write` + 默认 `tenantId=default` + REST 回读验证，本轮沿用同一通道。

---

## 五、现状差距

- 上一轮为了让 L1 有数据，刻意用 `custom`/`history` 写 user 记忆，5 条全部落到 L1；`category=persona|preference|habit` 三个查询都为 0，L3 因此一直是空。
- `PersonaView.vue:load()` 用 `r.layer === 'L3'` 过滤，后端返回小写 `l3`，该条件恒 false；只靠 category 兜底，遇到 habit 类别画像会漏行。
- 层列直接渲染 `row.layer`，L3 行显示小写 `l3`，与页面标题 `L3` 不一致。
- `PersonaView.vue` 本身有 `userId=ref('admin')` 与 `onMounted(load)`，**不是** L1 会话页那种「默认不带条件」的坑，无需照搬下拉修复。

---

## 六、技术方向

- **数据侧**：新增 `tmp/verify/write_l3_real_personas.py`，经 MCP `memory_write(target=user, userId=admin, category=persona|preference, importance=0.9)` 写 5 条；`tenantId` 走默认 `default`，与 REST 一致（上一轮已核对 `MemoryMcpToolkit:126` 与 `AiMemoryController` 默认值相同）。
- **前端侧**：`PersonaView.vue` 新增 `PERSONA_CATEGORIES`、`isPersonaRow(r)`（category/layer 都 `toLowerCase` 后比较）、`layerLabel(r)`；`countOf` 同步大小写不敏感；`rows.value = all.filter(isPersonaRow)`。
- **验证侧**：`tmp/verify/e2e-l3-persona.cjs` 用 `context.route` 改写 REST 响应构造两组鉴别力场景，并做「旧实现回退 → 必须 FAIL → 恢复」的真实代码对照，避免恒真守卫。

---

## 七、里程碑

| 里程碑 | 内容 | 判据 |
| --- | --- | --- |
| M1 | 数据写入 + REST 回读 | total 5→10，L3=5，persona=2/preference=3 |
| M2 | 前端死条件修复 | Vite 即时编译 200，产物含 `isPersonaRow` |
| M3 | 点击级 E2E + 回退对照 + 双回归 | 22/22、回退 21/22、22/22 + 18/18 |
| M4 | 方案文档 / 工作日志 / 会话记忆 | 三处均回填 |

---

## 八、自测计划

```bash
python tmp/verify/write_l3_real_personas.py
curl -s --noproxy '*' "http://127.0.0.1:9900/api/ai/memory/user/admin?limit=200"
curl -s --noproxy '*' -o /dev/null -w "%{http_code}\n" "http://localhost:6060/src/views/PersonaView.vue"
node tmp/verify/e2e-l3-persona.cjs
node tmp/verify/e2e-message-records-nav.cjs
node tmp/verify/e2e-l1-data.cjs
```

另：临时把 `isPersonaRow` 的 return 改成旧条件跑一次 `e2e-l3-persona.cjs`，确认 21/22 后立刻改回并重跑。

---

## 九、已知延后项

- L2 完整链路（L0/L1 → 场景块生产者 + REST 端点 + 前端列表），需另出方案文档。
- 16600 主壳菜单「记忆总览」改名同步。
- 改动提交 git、`npm run build` 生产构建（按约定不代跑）。
- L3 画像是否真的进入系统提示词（`persona.enabled` 开关与 `PersonaFragmentComposer` 运行时链路）未验证。
- 导入脚本无幂等保护，重跑会追加重复画像行。

---

## 十、实施顺序

1. 读 `PersonaView.vue` / `AiMemoryService` / `H2UserMemoryPageDao`，确认分流规则与 layer 大小写。
2. 写 5 条真实画像 + REST 回读，确认 `layer='l3'`、`version=1`。
3. 改 `PersonaView.vue`，Vite 即时编译验证。
4. 写点击级 E2E，跑全绿 → 回退对照 → 恢复 → 双回归。
5. 回填方案文档、工作日志、agent-memory 会话记忆。

---

## 十一、实施记录

- **实测数据**：MCP 写入 5 条全部成功（`allowed/error` 均为空）；REST `/user/admin` 由 `total=5` 变 `total=10`，L3 行 5 条（`layer='l3'`、`version=1`、`importance=0.9`）；`category=persona` → 2，`preference` → 3。
- **Vite**：`/src/views/PersonaView.vue` 即时编译 `200 / 46691 bytes`，产物含 3 处 `isPersonaRow`。
- **偏差 1**：E2E 首版把 `personaRows()` 写成 `async`，返回 Promise 导致 `count is not a function`（`0/1`）。改为同步返回 locator 后通过。
- **鉴别力对照**：临时把 `isPersonaRow` 的 return 改回 `category === 'persona' || category === 'preference' || layer === 'L3'`，同一脚本结果 **21/22**，`category=other 但 layer=l3` 场景掉到 4 行并精准 FAIL；恢复后 **22/22**。证明第 3 条成功标准不是恒真守卫。
- **回归**：`e2e-message-records-nav.cjs` **22/22**、`e2e-l1-data.cjs` **18/18**；L1 用户记忆页 UNION 后显示 10 行，会话页默认 ID 仍是 `f8da8a70-…`。
- **副作用（已确认无害）**：每次 `saveUserMemory` 都会 `recordMemoryWrite("user-admin", …)` 追加 L0 轨迹，本轮新增 5 条；因 `SessionMemoryManage` 已改为「并行探测真正有 L1 变量的会话」，默认会话未被带偏。
- **零后端改动、零重启**，HMR 生效。改动仍未提交 git，`npm run build` 未代跑。
