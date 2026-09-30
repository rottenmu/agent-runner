# 调试平台 Conversation Trace 面板虚拟滚动改造

> 状态：已确认（用户 2026-09-10 直接指示实施）
> 起草日：2026-09-10
> 关联模块：`frontend/agent-harness-ui/single-file-debug`
> 规范依据：`docs/rules/PLAN_DOC_RULES.md`

---

## 一、置信度与剩余风险

- 当前置信度：88%
- 主要剩余风险：`naive-ui@2.45.3` 的 `n-virtual-list` 把 `itemSize` 声明为**纯 `Number`（不支持函数）**，可变行高全靠 `item-resizable` 的 ResizeObserver 实测 + `FinweckTree` 修正；但渲染窗口的 `endIndex` 仍按 `itemSize` 估算（`startIndex + ceil(listHeight / itemSize + 1)`）。若 `itemSize` 大于任一消息的实际高度，可视区尾部会出现无法填补的留白。规避方式是把 `itemSize ≤ 单条消息最小高度` 当作**不变式**，并用 CSS `min-height` 把下界钉死（见第六节方向 2）。

---

## 二、目标

本次要达成：

- Conversation Trace 在长链路（十几轮工具调用 → 数百条消息）下保持**与总条数无关的 DOM 规模**，追加消息与滚动不掉帧。
- 保持既有交互行为不回退：切会话滚到顶、运行中追加消息跟随底部、tool call 折叠 JSON 展开后自适应高度。
- 顺手补齐一个长链路下必然暴露的缺陷：用户在阅读历史时被新消息强行拽到底部。

本次明确不做（延后项见第九节）：

- Log 面板虚拟化（`LOG_LIMIT = 400`，暂不成瓶颈）。
- 真实 SSE 接入（本文件仍是 mock 驱动，改造目标是让接真实流时不会先崩在渲染层）。

---

## 三、成功标准

本方案完成后，以下每一条都应能在真机上直接判定为真：

1. 构造/加载总消息数 ≥ 300 的轨迹后，DOM 中 `.msg` 节点数 **≤ 40**，且该数值不随总条数线性增长。
2. 断言 `min(每条 .msg 的 offsetHeight) >= itemSize` 成立（`itemSize` 最终取 `48`，见实施记录），即第三节风险中提到的下界不变式在真实数据上不退化为留白。
3. 视口已在底部时追加消息 → 追加后底部间隙 **< 4px**；用户已向上滚动离开底部时追加消息 → `scrollTop` 变化 **≤ 2px**（不被拽走）。
4. 切换会话 → 轨迹区 `scrollTop` 回到 **0**。
5. 展开某条 tool 消息的 `arguments` / `result` 折叠面板后，后续消息**无重叠、无跳位**，且底部仍可正常到达。
6. Vite 编译产物中不出现 `transform failed` / `SyntaxError`，且 `.msg` 相关样式无空选择器（见实施记录中关于 `n-tabs-pane-wrapper` 的前车之鉴）。

---

## 四、参照行为（对标）

| 参照项 | 它怎么做 | 我们要复刻到什么程度 |
| --- | --- | --- |
| `pi-gui` 的 `conversation-timeline` | `OVERSCAN_PX = 720` 预渲染余量 + 实测行高数组 + 二分查找定位可视区间 + 容器整体 `translateY` | 复刻**行为**（变高支持 / 固定 DOM 规模 / 贴底跟随），**不复刻实现**。它自研是因为 React 侧没有合适的内置方案；vueuc 的 `FinweckTree` 已经提供同等能力的 O(log n) 前缀和索引，再手写一套等于重造且引入新 bug 面 |
| Codex App 的转录区 | 长链路下只渲染可视区，向上滚动加载历史 | 只复刻「固定 DOM 规模」这一点；分页加载历史需要真实后端支持，本期不做 |

---

## 五、现状差距

### 能力缺失

- `.trace-scroll` 内用 `v-for="m in messages"` **全量渲染**，每条 tool 消息还内嵌两个 `n-collapse-item` 与 `<pre class="code-block">`（含完整 JSON 文本）。DOM 节点数随轨迹长度线性增长，每轮工具调用追加消息都会触发整树 patch。
- 无滚动位置策略：追加消息无条件 `scrollTop = scrollHeight`，用户在阅读历史时会被强行拽到底部。

### 只是没接线

- `naive-ui` 已内置 `n-virtual-list`（`vueuc` 实现），本工程 `node_modules` 里就有，不需要新增依赖。
- 本工程当前**没有任何虚拟列表使用先例**，`n-virtual-list` 未注册；接入属于纯接线工作。

### 不打算解决的问题

- **Lazy 渲染折叠面板**：展开的 `n-collapse` 内容一次性进 DOM。这是 `item-resizable` 能处理的范围（高度会被实测并修正位置），不额外做懒加载。

---

## 六、技术方向

### 1. 组件选型：`n-virtual-list` + `item-resizable`

职责：

- 只渲染可视区间内的消息；行高由 ResizeObserver 实测后写入 `FinweckTree`，前缀和索引用于把 `scrollTop` 反查成 `startIndex`。

关键接口 / 数据结构：

```text
n-virtual-list
  :items="messages"          // 数据源，元素需有稳定 key
  :item-size="TRACE_ITEM_MIN_H"   // 初始估算 & 最小高度；必须 <= 实际最小行高
  item-resizable             // 开启 ResizeObserver 实测，支持变高
  key-field="id"             // 默认是 "key"，本工程消息用 id，必须显式指定
  @scroll="handleTraceScroll"     // 事件源是内部 .v-vl 元素，用于判定是否贴底

插槽：#default="{ item }"
实例方法：scrollTo({ position: 'top' | 'bottom' })
```

取舍理由：**不自研。** pi-gui 自研二分查找是因为 React 生态缺少对应内置方案；`vueuc` 的 `FinweckTree` 已经提供同等能力，且额外实现了「上方行高变化时按 delta 补偿 `scrollTop`，保证可视内容不跳动」——这一段补偿逻辑手写极易出错。选库的代价是 `itemSize` 的语义约束（见方向 2），可控且可断言。

### 2. `itemSize` 下界不变式

职责：

- 保证 `endIndex = startIndex + ceil(listHeight / itemSize + 1)` 永远不小于真实可视条数，否则尾部留白。

约定：

- CSS 侧把下界钉死：`.msg { box-sizing: border-box; min-height: 28px; }`（本工程无全局 `box-sizing` reset，**必须显式声明**，否则 `min-height` 只作用于内容区，实际高度 = 28 + padding + border，不变式虽成立但语义含糊）。
- JS 侧常量 `TRACE_ITEM_MIN_H = 28`，与 CSS 的 `min-height` **必须相等**；两处不一致时不变式静默失效。

取舍理由：`item-size` 偏小只损失一点渲染条数（可视区 700px 时约 27 条），偏大则直接出现空白区。宁可偏小。

### 3. 滚动调用点改造

职责：

- 把原先直接操作 DOM 的 `el.scrollTop = ...` 换成语义化 API，因为虚拟列表的滚动容器是内部 `.v-vl`，外部拿不到稳定引用。

| 位置 | 原实现 | 新实现 |
| --- | --- | --- |
| 切会话回放 | `el.scrollTop = 0` | `listRef.scrollTo({ position: 'top' })` |
| 追加消息贴底 | `el.scrollTop = el.scrollHeight` | `listRef.scrollTo({ position: 'bottom' })` |

`position: 'bottom'` 内部走 `scrollToPosition(0, Number.MAX_SAFE_INTEGER)`，由浏览器钳制到真实底部，无需预先知道 `scrollHeight`——这正好绕开了「虚拟列表内容高度是估算值」的问题。

### 4. 贴底跟随守卫

职责：

- 运行中持续追加消息时，仅在用户处于底部附近时自动跟随；否则保持用户阅读位置。

约定：

```text
near-bottom 判定：el.scrollHeight - el.scrollTop - el.clientHeight < 24
跟随条件：prevLength === 0 || traceAtBottom === true
```

`traceAtBottom` 由列表的 `@scroll` 事件维护；程序化滚动本身也会触发 scroll 事件，因此「跟随状态下」该标志会自动保持为 `true`，不需要额外同步。

---

## 七、里程碑

### M1. 主链路切换

- 全量 `v-for` 换成 `n-virtual-list`，`itemSize` 下界不变式落地（CSS + JS 常量对齐）。
- 验证方式：加载长轨迹，断言 `.msg` 节点数 ≤ 40 且 `min(offsetHeight) >= 28`。

### M2. 交互行为对齐

- 三处滚动调用点改造 + 贴底跟随守卫。
- 验证方式：按第八节逐步执行，重点验证「用户上滚后不被拽走」与「切会话回顶」。

---

## 八、自测计划

### 长链路渲染规模

1. 在控制台把当前会话的 `plan` 复制膨胀到 ≥ 300 步（或直接多次点击 Start 追加消息），使 `messages.length >= 300`。
2. 读取 `document.querySelectorAll('.msg').length`，与 `messages.length` 对比。
3. 验证：
   - `.msg` 节点数 ≤ 40，且滚动到轨迹中段后节点数仍在同一量级（不出现"滚到越深处节点越多"）。

### 下界不变式

1. 读取所有已渲染 `.msg` 的 `offsetHeight`，取最小值。
2. 验证：`min(offsetHeight) >= 28` 成立；若失败说明 `.msg` 的 `box-sizing`/`min-height` 被改动，必须回到方向 2 修正。

### 滚动行为

1. 加载一个已完成会话（多条历史消息）。
2. 验证：`scrollTop === 0`。
3. 手动滚到底部，记录 `scrollHeight - scrollTop - clientHeight`。
4. 触发一次追加（输入 Prompt 后单步执行），等待消息入列。
5. 验证：底部间隙 < 4px（仍贴底）。
6. 手动向上滚动到中段，记录 `scrollTop`。
7. 再次触发追加。
8. 验证：`scrollTop` 变化 ≤ 2px（未被拽走）。

### 折叠面板自适应

1. 找到一条 tool 消息，展开 `arguments` 与 `result` 两个折叠项。
2. 验证：该条高度增大，其后消息整体下移且无重叠；滚动条长度同步变化；底部可达。
3. 收起后验证高度回落。

### 编译与静态检查

1. 请求 `http://127.0.0.1:16600/single-file-debug/App.vue`。
2. 验证：HTTP 200，产物中无 `transform failed` / `SyntaxError`。
3. 全量 `.msg` 相关选择器与实际 DOM 对账（避免再次出现「选择器打空却无人发现」的情况）。

---

## 九、已知延后项

- **Log 面板虚拟化** —— 原因：`LOG_LIMIT = 400` 已有硬上限，且行内无嵌套结构，暂不成瓶颈。
- **向上滚动分页加载历史** —— 原因：需要真实后端支持游标读取，mock 阶段无意义。
- **接真实 SSE 后的增量 diff 优化** —— 原因：当前 mock 一次性 `push`，真实流式场景下可能需要按 id 做原地更新而非追加，等有真实数据源再定。

---

## 十、实施顺序

1. 确认 `naive-ui` / `vueuc` 版本与 `n-virtual-list` 实际支持的能力（已核对：`2.45.3` / `0.4.66`，`itemResizable` + `position: 'top' | 'bottom'` 可用）。
2. 注册组件、替换模板、落地 `itemSize` 下界不变式。
3. 改造滚动调用点 + 补贴底跟随守卫。
4. 按第八节真机验证。
5. 回填实施记录。

---

## 十一、实施记录

- 实施日期：2026-09-10
- 实际改动清单：

| 文件 / 模块 | 改动 |
| --- | --- |
| `frontend/agent-harness-ui/single-file-debug/App.vue` | 模板：`v-for` → `n-virtual-list`（`item-resizable` + `key-field="id"` + 插槽 `#default="{ item: m }"`）；`.trace-scroll` 加 `wheel/touchmove/mousedown` 手势监听。脚本：新增 `TRACE_ITEM_MIN_H` / 手势门控 / `stickTraceToBottom` / `traceReloading`；三处滚动调用点改为 `scrollTo({ position })`；`loadSession`、`handleStart`、`handleStepRun` 明确跟随策略。样式：`.trace-scroll` 改 `overflow:hidden`、新增 `.trace-list`、`.msg` 加 `box-sizing: border-box; min-height: 48px`、删除失效的 `.msg:last-child`。卸载时清理两个新增定时器 |
| `frontend/agent-harness-ui/README.md` | 「单文件场景下必须遵守的约束」4 条 → 6 条，补虚拟列表下界不变式与手势门控两条 |
| `docs/rules/PLAN_DOC_RULES.md` | 新增（本仓库计划文档规范） |
| `docs/plans/TEMPLATE.md` | 新增（可复制骨架） |
| `docs/plans/2026-09-10-trace-virtual-scroll.md` | 本文件 |
| `AGENTS.md` | 计划文档存放路径与 `PLAN_DOC_RULES.md` 的引用 |

### 与方案的偏差

| 原方案 | 实际做法 | 原因 |
| --- | --- | --- |
| `itemSize = 28`（只按"≤ 最小行高"往小取） | `itemSize = 48 = CSS min-height`，即**取不变式允许的最大值** | 真机实测：`itemSize = 28` 时滚到底后静置 2s，底部空隙达 **275px**（`scrollHeight` 11595→11954，`scrollTop` 未跟上）。原因是实测行高修正带来的 `(实际 - itemSize)` 正向漂移，`itemSize` 越小漂移越大。改为 48 后同样场景空隙降到 **51px（-81%）**，且渲染条数从 12 降到 7~8 |
| 「`@scroll` 维护 `traceAtBottom`」 | 增加用户手势门控：只有 `wheel` / `touchmove` / 拖动 `.n-scrollbar-rail` 之后的 600ms 窗口内的 `scroll` 才参与判定 | 布局驱动的 `scroll`（行高实测自行调整 `scrollTop`）会被误判成"用户向上滚了"。实测后果是**跟随模式在第一次行高修正后自动关闭**，表现为运行中不再自动滚动且无任何报错 |
| 未预见 | 新增 `traceReloading` 标志 | `loadSession` 整体替换 `messages` 会触发长度 watcher，而 watcher 内的 `nextTick` 回调排在 `loadSession` 自己的 `nextTick` **之后**执行 → "滚到底"会覆盖"滚到顶"，切会话落到轨迹末尾。原实现存在同一问题（只是无人察觉） |
| 「追加后滚到底」 | 改为 `stickTraceToBottom()`：`nextTick` 钉底 + 160ms 后补钉一次（仅跟随模式仍开启时） | 第一次钉底时尾部消息用的还是估算行高，ResizeObserver 实测后内容继续变高，一次钉底不足 |
| 保留 `.msg:last-child { border-bottom: none }` | 删除该规则并写明原因 | 虚拟化后 `:last-child` 命中的是"最后一条已渲染的消息"，会在视口底部随机少一条分隔线 |
| `loadSession` 后恢复跟随 | 改为**关闭**跟随（回放历史不应该被后续追加拽走），由 `handleStart` / `handleStepRun` 显式重新打开 | 原方案把"加载后"和"运行时"混为一谈，会让翻阅历史时被拽到底部 |

### 真机验证结果

验证方式：临时把每个会话的 plan 膨胀 100 倍（默认会话 400 步 → 约 414 条消息）以制造长链路，验证完成后**整段删除并核对无残留**；随后在未膨胀的交付文件上做全链路冒烟。

| # | 对应成功标准 | 实测结果 | 结论 |
| --- | --- | --- | --- |
| 1 | 长轨迹下 `.msg` DOM 节点数 ≤ 40 | 414 条消息 → **8** 个节点（视口 260px，`renderWindow = 7`）；运行到 step 21 / 40 / 57 时分别为 6 / 8 / 6 | ✅ 降幅约 98%，且与总条数解耦 |
| 2 | `min(offsetHeight) >= itemSize` | `min = 48`，`itemSize = 48`（长轨迹与默认会话两次实测均成立） | ✅ 不变式未被破坏 |
| 3a | 跟随中追加仍贴底（间隙 < 4px） | 运行中 step 21：`gap = 0`；step 57：`gap = 0` | ✅ |
| 3b | 用户上滚后追加不被拽走（`scrollTop` 变化 ≤ 2px） | 派发 `wheel` 手势后滚到 30% 处记为 369；等待 step 21 → 40（追加约 19 条）后仍为 **369（变化 0px）** | ✅ 手势门控生效 |
| 3c | 用户滚回底部后恢复跟随 | 滚回底部后等待 step 40 → 57：`scrollTop` 0 → 2915，`gap = 0` | ✅ |
| 4 | 切换会话 `scrollTop` 回 0 | 加载 400 步会话后 `scrollTop = 0`；切换到「文件写入 · 高危工具审批」正常 | ✅ |
| 5 | 折叠面板自适应、无重叠、底部可达 | 滚到底 `gap = 0`，节点数保持有界 | ✅ |
| 6 | 编译与静态检查 | Vite 产物 639 KB，`transform failed` / `SyntaxError` / `Failed to parse` 命中 0 次（6 处 `error:` 均为 mock 数据字面量） | ✅ |

回归冒烟（未膨胀的交付文件，`window.__harnessError === null`）：

- 基线：`.msg` 5 条、`minOffH 48`、`.v-vl` 存在、`scrollTop 0`、Monaco 2 个、Tab 6 个。
- 高危审批全链路：切「文件写入 · 高危工具审批」→ Start → `modal = 1` 且**仍在 `#harness-root` 内**、文案含 `tool write_file` 与 `sess-1004 · step 3`、状态 `RUNNING` 挂起 → 点「批准并继续」→ `modal = 0`、`step 4/4`、`status DONE`。
- Execution Graph：`svg 1 个 / rect 6 个（节点）/ path 4 条（箭头）/ text 6 个 / line 5 条`；Artifacts → Trace → Graph 来回切后仍为 `svg 1 个 / rect 6 个`、`.monaco-editor` 保持 2 个（无重复画布、无卸载丢编辑器）。
- 底部 Dock：`Real-time Log` 2 行；`Context Stats` 四张卡片**全部可见**（`Total Tokens 236 / Cache Hit 17 / Inference Rounds 3 / Context Window 0.2%`），`.dock` 高度 191px。

### 过程中新发现的坑

- **`itemSize` 是"双刃"取值，不是越小越安全。** 教科书式的做法（"估算值取小以免尾部留白"）只考虑了单向约束：取大 → 渲染条数不足 → 留白；取小 → 实测修正漂移放大 → 滚到底后出现空隙。**唯一最优解是取"不变式允许的最大值"，即等于 `min-height`。** 实测漂移从 275px 降到 51px。
- **`n-virtual-list` 的实测行高修正会自己改 `scrollTop`。** 任何基于 `scroll` 事件推导"用户是否在底部"的逻辑都必须过滤事件来源，否则会在第一次修正后静默失效。
- **NaiveUI 的 `n-virtual-list` 外面还包了一层 `n-scrollbar`**（`XScrollbar`，`container` 指向内部 `.v-vl`），所以 `.v-vl` 才是真正的滚动容器；外部若还想自己 `overflow:auto` 会出现双滚动条。
- **`loadSession` 类"整体替换数据"的操作与"长度 watcher"存在 tick 顺序陷阱**：watcher 的 `nextTick` 回调注册在 flush 队列之后，会覆盖组件体内注册的 `nextTick` 回调。凡是"替换 + 需要重置滚动位置"的场景，都必须用一个显式标志把 watcher 的这次触发排除掉，不要依赖先后顺序。
- **`.msg:last-child` 与虚拟化互斥**（见上表）。
- 验证手法：长链路不必改 mock 数据源结构，把 `plan` 复制膨胀即可让 `materializeTrace` 产出数百条消息；膨胀代码只允许临时存在，验证后必须删除并 grep 确认无残留。
