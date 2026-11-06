# L1 页面补真实数据与默认取数修复；L2 保持如实标注

> 状态：已落地
> 起草日：2026-11-06
> 关联模块：`modules/agent-memory/frontend/agent-memory-ui`（6060）、`agent-memory-core` MCP 通道（9900）
> 规范依据：`docs/rules/PLAN_DOC_RULES.md`

---

## 一、置信度与剩余风险

- 当前置信度：97%
- 主要剩余风险：写入的 user/global 记忆是「本会话已沉淀的结论」，不是业务运行时持续产出的数据；换库、清库或另一个独立 H2 实例上，L1 仍会变空（属于数据真空，不是代码缺陷）。

---

## 二、目标

本次要达成：

- L1 三个页面（用户记忆 / 会话记忆 / 全局记忆）**默认可见真实数据**，不需要用户先猜 ID。
- 会话记忆页首屏自动带出「确实有 L1 变量」的最近活跃会话，并提供最近会话下拉。
- L2 场景块保持「结构预留」的如实标注，不填假数据、不改后端。

本次明确不做：

- 不改任何后端 Java 代码、不新增 REST 端点、不改 DDL。
- 不补 L2 生产者 / 消费者链路（用户选择「保持如实标注」）。
- 不动 L3 画像（仍 0 条，本轮不扩范围）。

---

## 三、成功标准

1. 打开 `?tab=user` → 可见 pane 的表格行数 **≥ 5**，`.el-empty` 数为 **0**，类别列出现「自定义 / 历史」。
2. 打开 `?tab=global` → 表格行数 **≥ 3**，`.el-empty` 数为 **0**。
3. 打开 `?tab=session` → 会话 ID 输入框非空、下拉存在、表格行数 **≥ 5**、`.el-empty` 数为 **0**。
4. 同页 → 默认会话 ID 形如 `[0-9a-f-]{16,}`，且不是只有 L0 轨迹的伪会话（如 `user-admin`）。
5. 打开 `?tab=scene` → 文本仍含「结构预留」与「没有数据，也没有生产者与消费者」，`.el-empty` **≥ 1**，表格行数 **= 0**。
6. `?tab=scene` 页 → 一级菜单仍为「消息记录」，`.dsh-nav-sub-label` 数量 **= 4**（回归上一轮改版）。
7. 全流程 `pageerror` **= 0**、`console error` **= 0**（仅拦截既有 `/favicon.ico`）。
8. 直接打 REST：`GET /api/ai/memory/user/admin?limit=200` 返回 `total ≥ 5`；`GET /api/ai/memory/global` 返回 `total ≥ 3`。

---

## 四、参照行为（对标）

无。

---

## 五、现状差距

取证（2026-11-06 20:50，Playwright + curl）：

| 层 | 现象 | 根因 |
| --- | --- | --- |
| L1 用户记忆 | `/api/ai/memory/user/admin` 200 但 `{"records":[],"total":0}`（body 64B） | 独立应用用 `core/data` 自己的 H2 库，与主应用库不共享；`admin` 下从未写过 user 层记忆 |
| L1 全局记忆 | `/api/ai/memory/global` 200 但 `records=[]` | 同上，global 层 0 条 |
| L1 会话记忆 | 页面 `No Data`，相关请求数 **0** | `SessionMemoryManage.vue:load()` 在 `sessionId` 为空时直接 `return`，且该页**没有** `onMounted(load)` |
| L2 场景块 | 静态说明页，明确写「无生产者与消费者」 | `H2OltpMemoryRepository` 有 `l2_scene_block` 建表与 `saveSceneBlock/listScenesBySession`，但全仓 `saveSceneBlock` 只在测试 Fake 中出现，**零生产调用**；`/analytics/scenes`、`/analytics/scene-blocks` 均 404 |

性质区分：L1 是「数据真空 + 页面默认不带查询条件」，不是接口故障；L2 是架构上刻意的结构预留（18:10 已确认「L2 如实呈现、不动后端」）。

---

## 六、技术方向

- **租户一致性前置校验**：MCP `MemoryMcpToolkit.call()` 的 `tenantId` 默认 `default`（:126），`AiMemoryController` 各端点 `@RequestParam(defaultValue="default")`（:86/:122 等），前端 `api/request.js` 不带租户头 → 两条通道落同一库，MCP 写入能被页面查到。这是「写进去还看不到」的典型翻车点，先验证再写。
- **数据写入**：走现有 MCP `memory_write`，`target=user`（`userId=admin`、`category=custom/history`，避免 `persona/preference` 被分流到 L3）、`target=global`（具名 key），`importance=0.9`。内容是本会话已沉淀的真实决策与通道实测结论，**不造业务假数据**。零后端代码改动。
- **页面取数**：`SessionMemoryManage.vue` 用 `getMessages({page:0,size:50})`（OLAP 固定 `ts DESC`）取 distinct `session_id` 作最近会话候选；再并行 `getSessionVars(id,{limit:1})` 探测，默认落在第一个有 L1 变量的会话。只复用既有端点，不新增 API。
- **不引入 `getSessionStats()`**：上一轮删除总览 Tab 时的 E2E 明确断言 `analytics/session-stats` 等三个 dashboard 接口不再请求，复用它会破坏该回归。

---

## 七、里程碑

- **M1**：租户一致性校验 + 8 条真实记忆写入 + REST 可见性验证。
- **M2**：会话页默认取数与下拉 + L1/L2/菜单点击级 E2E + 上一轮菜单 E2E 回归。

---

## 八、自测计划

1. 执行 `python D:/codehub/agent_runner/tmp/verify/write_l1_real_memories.py` → 预期打印 8 行 `error=None`。
2. `curl --noproxy '*' 'http://127.0.0.1:9900/api/ai/memory/user/admin?limit=200'` → 预期 `total=5`；`/api/ai/memory/global` → `total=3`。
3. `curl --noproxy '*' -o /dev/null -w "%{http_code}" http://localhost:6060/src/views/SessionMemoryManage.vue` → 预期 `200`。
4. 执行 `node D:/codehub/agent_runner/tmp/verify/e2e-l1-data.cjs` → 预期 `RESULT 18/18`。
5. 执行 `node D:/codehub/agent_runner/tmp/verify/e2e-message-records-nav.cjs` → 预期 `RESULT 22/22`（上一轮改版无回归）。

---

## 九、已知延后项

- **L2 完整链路未补**：生产者（L0/L1 → 场景块聚合）、REST 端点、前端列表均未做，按用户选择保持如实标注；若要做须先另出方案文档。
- **L3 画像仍 0 条**：本轮未扩范围；`category=persona/preference` 会落 L3，这次刻意只用 `custom/history`。
- 写入的 8 条记忆是静态结论，不是持续生产者；清空 `core/data` 或换实例后 L1 会重新变空。
- 8400 条 M3 历史回填未清理、favicon 404 未修、改动未提交 git、`npm run build` 未代跑。

---

## 十、实施顺序

租户一致性核对 → MCP 写入 8 条 → REST 验证 → 会话页改造 → L1/L2 E2E → 菜单 E2E 回归 → 回填文档与日志。

---

## 十一、实施记录

**改动文件（1 个）**：`modules/agent-memory/frontend/agent-memory-ui/src/views/SessionMemoryManage.vue`
- 工具栏新增「最近活跃会话」下拉（`filterable clearable`，与手输输入框共用 `sessionId`）。
- `load()` 在 ID 为空时清空列表；空态文案改为按 ID 有无区分。
- 新增 `onMounted(initRecentSessions)`：OLAP 前 50 条取 distinct session_id（最多 8 个）→ 并行探测 `getSessionVars(id,{limit:1})` → 默认选第一个有 L1 变量的会话 → `load()`。
- `api/memory.js` 未改（复用既有 `getMessages` / `getSessionVars`）。

**数据写入（8 条，均在 `tenantId=default`）**：user/custom 3 条 + user/history 2 条（userId=admin）、global 3 条（具名 key `project.agent-runner.runtime` / `project.agent-memory.mcp-channel` / `project.agent-memory.ui-nav`）。全部 `error=None`，REST 回读 `user total=5`、`global total=3`。

**验证结果**：
- Vite 即时编译 `SessionMemoryManage.vue` → `200` / 42141 bytes。
- `e2e-l1-data.cjs` → **18/18 全绿**：user 5 行、global 3 行、session 17 行（默认 ID `f8da8a70-ef02-4e04-ab62-0af32ba10758`）、L2 仍「结构预留」且表格 0 行、菜单仍「消息记录」+ 4 个 L0~L3 子标签、JS/console 零异常。
- `e2e-message-records-nav.cjs` 回归 → **22/22 全绿**。
- 零后端代码改动、零重启（HMR 生效）。

**与方案的偏差（1 处，已修）**：首版只按「最新一条 L0 消息的 session_id」取默认值，实测选中 `user-admin` —— 那正是 M1 写入 user 记忆时 `recordMemoryWrite("user-"+userId, …)` 留下的 L0 轨迹，**该会话没有任何 L1 会话变量**，页面仍 `No Data`（E2E 15/18）。修正为「并行探测候选会话的 L1 条数，默认落在第一个非空会话」，重跑 18/18。这条坑写进第六节技术方向，避免以后再按「最新消息」推断会话归属。

**未做**：git 提交、`npm run build`、L2 链路、L3 画像数据。
