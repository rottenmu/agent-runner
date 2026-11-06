# 记忆总览改名为「消息记录」，并把 L0~L3 收进该一级菜单

> 状态：已落地
> 起草日：2026-11-06
> 关联模块：`modules/agent-memory/frontend/agent-memory-ui`（独立 UI，6060）
> 规范依据：`docs/rules/PLAN_DOC_RULES.md`

---

## 一、置信度与剩余风险

- 当前置信度：98%
- 主要剩余风险：主壳 16600 的 `frontend/modules/ai/menus.js` 里子菜单仍叫「记忆总览」，用户在两套同源前端间切换会看到名称不一致；表现为「菜单名没改干净」的观感，而非功能故障。

---

## 二、目标

本次要达成：

- 侧栏「记忆总览」菜单项与页面标题改名为「消息记录」。
- 把 L0 / L1 / L2 / L3 四个分层分组从平级分组收敛为「消息记录」一级菜单下的子分组，各层页面仍独立、点击切换；「数据分层」保留为同组入口。
- 「辅助 / 记忆策略」保持独立分组，不被吞并。

本次明确不做（延后项见第九节）：

- 不改 web-shell 主壳（16600）菜单与插件 `frontend/modules/agentmemory`（用户确认「只改独立 UI」）。
- 不改后端、不改 REST 端点、不改 `mainTab` 的内部 key（`overview` / `layers` / `today` …）、不改 `?tab=xxx` 与直达路由。
- 不改「消息记录」页内的「会话消息」Tab 内容（画像配置表格与今日消息卡片）。

---

## 三、成功标准

本方案完成后，以下每一条都应能在真机上直接判定为真：

1. 打开 `http://localhost:6060/#/agent-memory` → 侧栏 `.dsh-nav-section-head` 数量为 **1**，文本恰为「消息记录」。
2. 同页 → 可见 pane 的 `h1` 文本恰为「消息记录」，侧栏全文不再包含「记忆总览」或独立成行的「总览」。
3. 同页 → 第一个 `.dsh-nav-section` 内 `.dsh-nav-sub-label` 数量为 **4**，依次为 L0 / L1 / L2 / L3；带缩进的 `.dsh-nav-sub-item` 数量为 **8**。
4. 同页 → `getComputedStyle(.dsh-nav-sub-item).paddingLeft === '22px'`，`getComputedStyle(.dsh-nav-section-head).fontWeight >= 600`。
5. 点击一级菜单「消息记录」 → URL 含 `tab=overview`，可见 pane 标题为「消息记录」。
6. 依次点击 L0「今日会话」/ L1「用户记忆」/ L2「场景块」/ L3「用户画像」 → URL 分别含 `tab=today` / `tab=user`，且对应 pane 的 `h1` 含「用户画像」/「场景块」。
7. 点击「今日消息」卡片 → URL 仍含 `/agent-memory/today-sessions`（下钻回归不破坏）。
8. 全流程 `pageerror` 数为 **0**、`console error` 数为 **0**（仅拦截既有 `/favicon.ico` 404 噪音）。

---

## 四、参照行为（对标）

无。本次是既有侧栏的信息架构调整，不复刻外部产品行为。

---

## 五、现状差距

- 现状：`MemoryDashboard.vue` 的 `navGroups` 是 6 个**平级**分组（总览 / L0 / L1 / L2 / L3 / 辅助），`总览` 组内含「数据分层」与「记忆总览」两项。L0~L3 在视觉层级上与「总览」同级，用户无法把它们归到「消息记录」名下。
- 差距性质：**只是导航数据结构没有层级**，不是能力缺失——pane、`goTab`、`pathTabs`、路由都已具备，不需要新增页面或接口。
- 上一轮已删除内层「总览」Tab，`mainTab='overview'` 与 `?tab=overview` 仍指侧栏「记忆总览」页，改名时**不能动这些 key**，否则会破坏 `backFromToday()`、直达路由和既有 E2E。

---

## 六、技术方向

- `navGroups`（两层：分组 → 按钮）改为 `navSections`（三层：一级菜单 → 分层子组 → 按钮）。
- 一级菜单带 `tab` 字段时渲染为**可点击** `.dsh-nav-section-head`（`tab='overview'`，点击走 `goTab`）；不带的（辅助）渲染为纯文字标题，避免造出无意义的点击目标。
- 一级菜单下的所有子项统一加 `.dsh-nav-sub-item`（`padding-left: 22px`），L0~L3 标签用 `.dsh-nav-sub-label` 缩进 20px，形成视觉父子关系；辅助组子项不加缩进类。
- URL、`mainTab` key、`goTab`、`pathTabs`、`refreshAll()`、所有子页组件与 API 调用**一律不改**，改动范围收敛在 `MemoryDashboard.vue` 的模板 / 导航常量 / scoped CSS。
- 取舍：不引入 `el-menu` 等组件重写侧栏——现有 `v-show` 常驻 pane + query 切 tab 的机制已被 22 条点击级断言覆盖，换组件会把「同名页常驻 DOM」的已知坑重新引回来。

---

## 七、里程碑

- **M1（单批次）**：导航数据结构 + 模板 + 样式 + 文案改名，随后立即跑编译与点击级 E2E。

---

## 八、自测计划

1. 执行 `curl -s --noproxy '*' -o /dev/null -w "%{http_code}" http://localhost:6060/src/views/MemoryDashboard.vue` → 预期 `200`（Vite 即时编译 SFC 无语法错）。
2. 在同一响应体中检索「消息记录」 → 预期命中 ≥ 1（说明改后的模块已被 dev server 编译，不是旧缓存）。
3. 执行 `node D:/codehub/agent_runner/tmp/verify/e2e-message-records-nav.cjs` → 预期输出 `RESULT 22/22`，退出码 0。
4. 逐条核对第三节 1~8 的观察量；若第 4 条 FAIL，优先检查 scoped CSS 定义顺序是否被 `.dsh-nav-item` 的 `padding` 覆盖。
5. 目视侧栏：一级「消息记录」加粗、其下 L0~L3 四个小标题与 8 个子项缩进、「辅助」在下方独立成组。

---

## 九、已知延后项

- **主壳 16600 菜单不同步**：`frontend/modules/ai/menus.js:27` 的子菜单仍是「记忆总览」，按用户选择「只改独立 UI」保留；若需统一，须另开一轮（两套前端以哪套为准尚未定案）。
- **页面内容与新名称语义不完全一致**：「消息记录」页内「会话消息」Tab 仍是画像配置（SOUL/USER）表格 + 今日消息卡片，不是消息流水列表；本次只改导航名称与层级，不动内容。
- `npm run build` 未代跑、改动未提交 git；`vite build` 产物未生成（避免污染 `tmp/` 与触发 dev server 无关 reload）。
- 既有 `/favicon.ico` 404 未修，E2E 中仅拦截该资源。

---

## 十、实施顺序

改 `MemoryDashboard.vue`（模板 → 导航常量 → 文案 → CSS）→ 改 `api/memory.js` 注释 → Vite 即时编译校验 → 点击级 E2E → 回填本文档与工作日志。

---

## 十一、实施记录

**已改动文件（2 个）**：

| 文件 | 改动 |
| --- | --- |
| `modules/agent-memory/frontend/agent-memory-ui/src/views/MemoryDashboard.vue` | 侧栏模板由 `navGroups` 双层改为 `navSections` 三层渲染；`h1` 与一级菜单文案改「消息记录」；新增 `.dsh-nav-section` / `-head` / `-title` / `-sub-label` / `-sub-item` 样式与 768px 断点覆盖；3 处注释同步 |
| `modules/agent-memory/frontend/agent-memory-ui/src/api/memory.js` | `getMessages()` 注释中「记忆总览」改为「消息记录」 |

**导航终态**：

```
消息记录（可点，tab=overview）
  数据分层
  L0 · 原始日志   今日会话 / 溯源分析
  L1 · 原子记忆   用户记忆 / 会话记忆 / 全局记忆
  L2 · 场景块     场景块（预留）
  L3 · 用户画像   用户画像
辅助
  记忆策略
```

**验证结果**：

- Vite 即时编译 `GET /src/views/MemoryDashboard.vue` → `200`，响应体 128936 bytes，含 6 处「消息记录」。
- Playwright 点击级 E2E `tmp/verify/e2e-message-records-nav.cjs` → **22/22 全绿**，含第三节全部 8 条成功标准 + CSS 回归（缩进 22px、字重 600）+ JS/console 零异常；点击路径覆盖 overview / today / user / persona / scene 与今日消息卡片下钻。
- 9900 后端未重启、6060 dev server 未重启（HMR 生效），零后端改动。

**与方案的偏差**：无。实施中额外补了两条样式回归断言（`paddingLeft` / `fontWeight`）与一条「侧栏不再出现独立成行『总览』」断言，E2E 由 20 条扩到 22 条，原因是仅有文本断言不足以证明层级样式真的生效。

**未做**：git 提交、`npm run build`、主壳菜单同步（见第九节）。
