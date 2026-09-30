# 把 pi-gui 渲染层移植进 agent-harness-ui（Codex 风格运行时控制台）

> 状态：已落地（见第十一节实施记录）
> 起草日：2026-09-10
> 关联模块：`frontend/agent-harness-ui`
> 参照源码：`tmp/pi-gui-ref/apps/desktop/src`（MIT，浅克隆留档）
> 规范依据：`docs/rules/PLAN_DOC_RULES.md`

---

## 一、置信度与剩余风险

- 当前置信度：82%
- 主要剩余风险：**pi-gui 的时间线/侧栏行为建立在一份 22.6k 行的 React 实现上，逐条复刻会让 Vue 工作量失控**。风险的具体暴露形式是「界面看起来像了，但会话状态、投递语义、虚拟滚动三者各自为政」——即只搬了皮肤没搬契约。缓解方式：以「契约（`sessionDriver`）+ 设计令牌」两条主干先钉死，视觉细节允许简写，并在第八节用可判定的标准卡住。

---

## 二、目标

本次要达成：

1. **设计系统落地**：把 pi-gui 的 Codex 尺度体系（4px 间距基准、紧凑字号阶、圆角 ramp、`color-mix()` 派生边框、半像素发丝描边、0.12/0.15/0.24s 动效）做成 `agent-harness-ui` 的令牌层，并支持**浅/深双主题纯变量翻转**。
2. **信息架构对齐**：把工程从「以评测用例为中心」扩成「以会话为中心」，新增 Codex 风格的**运行时控制台**表面（左栏会话分组导航 / 中栏时间线 / 右栏产出与差异 / 底部日志与上下文统计），并按 pi-gui 的密度与层级重做视觉。
3. **契约层重建**：按 pi-gui 的 `SessionDriver` 形状建立前端会话契约 —— 3 态状态机（`idle | running | failed`）+ `runningRunId` + `queuedMessages[]` + 8 个类型化事件 + `deliverAs: steer | followUp` 两种投递语义。
4. **关键机制移植**：composer 主控点（大圆角输入盒 + `/` 命令面板 + 模型/思考级别切换 + `Enter`/`Shift+Enter` 提示）、时间线项 5 类型化（含轻量 activity 行与总结卡）、工具行 `+N/-N` 与 view-in-diff、Diff 侧栏。

本次明确不做（延后项见第九节）：

- 不移植 Electron 专有层：真 PTY 终端（`node-pty`/xterm）、git worktree、OS 系统通知、窗口聚焦判定。
- 不移植 `@earendil-works/pi-coding-agent` 的任何具体实现细节 —— 本仓库 runtime 是自研的 `modules/agent-harness`，只取契约形状。
- 不做 1:1 像素级复刻；pi-gui 的 `theme-presets.ts`(816) / `tree-modal.tsx`(802) / 设置页三大 section 不在本期范围。
- 不删除现有评测工作台表面（`HarnessWorkbench.vue`），本期只做**并行新增 + 外壳统一**。

---

## 三、成功标准

本方案完成后，以下每一条都应能在真机上直接判定为真：

1. **令牌层生效**：浏览器中移除 `<html class="dark">` → 用 `getComputedStyle` 抽查 8 个代表元素（页面底、卡片、边框、主文本、次文本、强调、成功、失败），**浅深两态的取值互不相同**；再切回 `dark` → 8 个取值全部回到原值。
2. **无残留硬编码**：在 `src/` 下 grep 色值字面量（6 位 / 3 位 hex、`rgba(`、`hsl(`）——**任何 `.vue` 的样式块与任何 `.css` 文件中命中数 = 0**（色值只允许出现在 `styles/theme-values.css` 一处）。`.js` 中不允许出现作为**样式来源**的色值；仅允许两类：注释里的说明文字，以及 mock 的展示内容字符串。
3. **密度对齐**：真机上抽查 6 个元素，满足——正文（时间线消息）行高 ≤ 22px；字号 ∈ {11,12,14,15}px；padding 取值来自 `--space-*` 阶（∈ {2,4,6,8,10,12,16,20,24,28,32}）。命中 6/6。
   > 起草时此处写的是「padding 为 4 的倍数」。实施中发现该判定形式与令牌阶冲突：Codex 尺度本身带 2 / 6 / 10px 半步（`--space-0-5/1-5/2-5`），两行式会话行的 6px 行距正是靠 `--space-1-5` 得到的，禁掉半步等于禁掉参照实现自己的密度。已改为「来自 `--space-*` 阶」，判定力等价（仍是离散白名单，不是"看起来对齐"）。偏差记录见第十一节。
4. **发丝描边**：任取一个浮层/弹窗，`getComputedStyle` 的 `box-shadow` 能匹配 `/0px 0px 0px 0\.5px/`（浏览器会把 `0 0 0` 序列化成 `0px 0px 0px`，断言按实际序列化结果写）。
5. **会话状态机只有 3 态**：构造「运行中再发一条消息」→ 状态值仍为 `running`（`.status-label` 文本不变），而 `queuedMessages.length` 由 0 变 1。
6. **投递语义两分**：把投递方式切到 `steer` 后重发 → `queuedMessages.length` 回到 0，且时间线中新增一条 `User (steer)` 类型的项（该类型项计数 +1）。
7. **命令面板**：在 composer 输入 `/` → 面板出现且列出 ≥ 3 项（`/model`、`/thinking`、`/tools`）；按 `Esc` → `document.querySelectorAll('.slash-menu-item').length === 0`。
8. **时间线 5 类型**：加载 mock 会话 → `.timeline-item--user / --assistant / --activity / --tool / --summary` 五个类名**各自命中数 ≥ 1**。
9. **工具行差异统计**：工具行内存在 `+N/-N` 文本（正则 `/\+\d+\s+-\d+/` 命中 ≥ 1）；点击该行 view-in-diff → diff 侧栏宽度由 0 变 `> 0`。
10. **Diff 侧栏快捷键**：按 `Ctrl+D` → 侧栏宽度 `> 0`；再按一次 → 宽度 `= 0`。
11. **虚拟滚动不回退**：切到长链路 mock 会话（`sess-long`，247 条时间线项）→ DOM 中 `.timeline-item` 节点数 **≤ 40**，且滚到底后 `scrollHeight - scrollTop - clientHeight ≤ 60px`。
12. **既有表面不回归**：切到评测工作台 → 用例树节点数 ≥ 1，点「Run Selected」→ 顶栏 `status-text` 出现且日志区新增 ≥ 1 行。

---

## 四、参照行为（对标）

| 参照项 | 它怎么做 | 我们要复刻到什么程度 |
| --- | --- | --- |
| 令牌体系 `styles/tokens.css` | 4px 间距基准；字号阶 11/12/14/15/16/20/28；边框用 `color-mix(in srgb, var(--text-strong) 5%/8%/13%, transparent)`；`--elevation-hairline: 0 0 0 0.5px` | **全量复刻尺度与写法**。但**色相保留本工程既有规范**（`#14161a/#1e2229/#4096ff/…` 是需求固定项），只借它的推导机制不借它的紫调色板 |
| 主题机制 `styles/base.css` | 边框/叠加/强调/状态色全部由 `color-mix()` 从前景色派生 → 暗色模式只重赋少数「真正有独立明暗取值」的 token | 复刻该机制。本期做**浅+深双态**（当前工程只有深色） |
| `packages/session-driver/src/types.ts` | `SessionStatus = idle｜running｜failed` 三态；排队用 `runningRunId` + `queuedMessages[]` 表达；消息投递 `deliverAs: steer｜followUp` | 复刻契约形状到 `src/api/sessionDriver.js`。**不复刻**它的实现（我们后端是自研 harness） |
| `timeline-item.tsx` | 5 类渲染：`--user` / `--assistant` / activity（含 `--error`/`--summary` 变体）/ tool call / `--summary-card` | 复刻 5 类与命名。附件（图片/文件）、Fork 动作留到延后项 |
| 工具行 | `› Read /path/repo.ts  read done`，同一行放折叠箭头+工具名+等宽路径+右侧 muted 元信息；写文件工具行带 `+N -N` 与 view-in-diff | 复刻行内结构与 `+N/-N`；**不做**真实的文件级 diff 计算，差异数据由消息负载给出 |
| `composer-surface.tsx` | 22px 大圆角盒子，占位文案 + 底部提示行（`Enter to send · Shift+Enter for newline` + 当前模型/思考级别）+ 圆形发送按钮，是模型/动作切换主控点 | 复刻结构与职责分工。附件拖拽、@mention 留延后 |
| `conversation-timeline.tsx` | `OVERSCAN_PX=720` + 实测行高数组 + 二分查找定位区间 | **只复刻可观察行为**（DOM 节点数与总条数解耦）。我们用 naive-ui 内置 `n-virtual-list` 达成同等效果，不手写二分（见六-3） |
| `plans/sidebar-unseen-notification-consistency` | 把「是否正在被查看」抽成唯一判定源，禁止两处各自推导 | 复刻原则：`session-visibility.js` 作为 `unseen` / `status` / `lastError` 的唯一推导处 |
| 侧栏 `sidebar.tsx` | 主按钮 → 图标导航（Threads/Skills/Extensions/Settings）→ 可折叠分节 `THREADS` → 按 workspace 分组 → 两行式会话行，悬停才出置顶/归档 | 复刻到「主按钮 + 分节 + 分组 + 两行式 + 悬停动作」；图标导航先做 2 项（Threads / Settings），其余留延后 |

---

## 五、现状差距

### 能力缺失

- **无令牌体系**：`styles/tokens.css` 只定义了 15 个色值 + 3 个圆角，**没有间距阶、没有字号阶、没有动效阶、没有派生边框**。结果是每个组件自己写 `padding: 5px 10px 6px` 这类魔法数，密度不可控。
- **只有深色**：`global.css` 把 `#14161a` 写死在 `html/body` 上，`theme.js` 只提供 `darkTheme`。浅色主题在当前结构下无法通过「加一个 class」实现。
- **无状态契约**：`useRunSession.js` 只有 `running: boolean` 一个布尔量，既没有「运行中」与「排队」的区分，也没有 `failed` 这一终态；`runningRunId` 概念完全缺失，无法回答「现在是哪一次运行在跑」。
- **无投递语义**：用户消息只有一种投递方式（直接追加），运行中发消息在语义上是未定义的。
- **时间线项只有 4 类**：`User / Agent / ToolCall / Error`。缺少轻量 **activity 行**（编译、后台进程、终端输出这类「不是工具调用但确实发生了事」的记录）与**总结卡**。
- **无 Diff 面板**：工具行没有 `+N/-N`，也没有从工具行直达差异的入口。
- **composer 不是主控点**：prompt 输入框被塞在中栏底部，模型/思考级别散落在右栏表单里，两者不在同一处，切换模型要跨面板。

### 只是没接线

- **虚拟滚动已具备**：调试平台单文件里已用 `n-virtual-list` 跑通「DOM 节点与总条数解耦」，工程本体（`ConversationTrace.vue`）尚未接。
- **monaco / d3 / 三栏+dock 骨架 / 高危审批弹窗**均已就绪，本期只做令牌化改造，不动逻辑。
- **后端 API 层已通**：`src/api/{client,auth,observ,adapters}.js` + `useBackend.js` 已打通 `:9900`，新表面可直接复用，不需要重写鉴权与降级。

### 不打算解决的问题

- **Electron 专有行为**（PTY / worktree / 窗口聚焦）—— 浏览器环境语义不成立，强行移植会得到「测试通过但功能错误」的假阳性。
- **pi-gui 的紫调色板**（`--accent: #6a55f2`）—— 与本工程既有的 `#4096ff` 规范冲突，且该规范标注为「需求固定，勿改」。
- **逐像素复刻** —— pi-gui 是 macOS-first 的原生窗口，我们跑在 Windows + Chrome，字体栈与滚动条渲染都不同，逐像素对齐没有可判定性。

---

## 六、技术方向

### 1. 令牌层：把色值与尺度分离

职责：

- `src/styles/tokens.css` 只放**尺度**（间距 / 圆角 ramp / 字号阶 / 动效 / 字重 / 字距），以及由色值派生的**语义层**（边框三档、叠加三档、强调 tint、焦点环、状态色、发丝描边）。
- `src/styles/theme-values.css` 是**唯一**允许出现裸色值的文件，分 `:root`（浅）与 `:root.dark`（深）两块。
- `src/theme.js` 的 `PALETTE` 改为从 CSS 变量读取（`getComputedStyle`），NaiveUI `themeOverrides` 从同一份变量派生，**消灭「改色要改两处」**。

关键接口 / 数据结构：

```css
:root {
  --space-1: 4px; --space-2: 8px; --space-3: 12px; --space-4: 16px;
  --radius-sm: 6px; --radius-composer: 22px; --radius-panel: 24px; --radius-pill: 999px;
  --text-xs: 11px; --text-sm: 12px; --text-base: 14px; --text-md: 15px;
  --motion-base: 0.15s; --ease-out: cubic-bezier(0, 0, 0.2, 1);
  --border-default: color-mix(in srgb, var(--ink-strong) 8%, transparent);
  --elevation-hairline: 0 0 0 0.5px var(--border-heavy);
}
```

取舍理由：**不引入 Tailwind / CSS-in-JS**。pi-gui 本身用的就是原生 CSS 变量 + 少量工具类，我们已经是一套 `<style scoped>` 组件，引入构建期方案会为「看起来更现代」付出迁移整工程样式的代价，而令牌层的收益与构建工具无关。同理不引入 UnoCSS —— 目标是尺度约束，不是原子化。

### 2. 会话契约：`sessionDriver.js` + `usePiSession.js`

职责：

- `src/api/sessionDriver.js` 只定义**契约**：状态枚举、事件联合类型、`subscribe` 形状。无实现，与 pi-gui 的 `packages/session-driver` 同构。
- `src/composables/usePiSession.js` 是**实现**：持有 `status` / `runningRunId` / `queuedMessages` / `deliverAs`，向 UI 暴露动作。

关键接口 / 数据结构：

```js
// 状态：只有三态。排队不占用状态位。
export const SessionStatus = { IDLE: 'idle', RUNNING: 'running', FAILED: 'failed' }

// 事件联合类型（8 种）
// sessionOpened | assistantDelta | toolStarted | toolUpdated | toolFinished
// | runCompleted | runFailed | hostUiRequest

// 用户消息投递语义
// steer   —— 运行中插话：立即注入当前运行
// followUp—— 排队：等当前运行结束后再发
function sendUserMessage(text, { deliverAs = 'followUp' } = {}) {}
```

取舍理由：**不把四态改成三态就直接兼容**。当前 `Pending/Running/Completed/Failed` 四态里，`Pending` 与 `Completed` 在「有没有一次运行在跑」这个问题上是等价的（都是「没有」），却占用了两个状态位，导致任何「运行是否在进行」的判断都要列举两个值 —— 这正是 pi-gui 复盘里那类「两处各自推导」事故的温床。改成三态 + `runningRunId` 后，「有没有在跑」只需判 `runningRunId !== null`。

### 3. 虚拟滚动：复用已有结论，不手写二分

职责：`ConversationTimeline.vue` 用 `naive-ui` 的 `n-virtual-list`，`item-resizable` 实测变高。

关键约束（沿用 `docs/plans/2026-09-10-trace-virtual-scroll.md` 已实测的结论）：

- `itemSize` 取「不变式允许的最大值」= CSS `min-height`，不是越小越安全；
- 跟随底部的判定必须用手势门控（`wheel`/`touchmove` 后 600ms 窗口），不能直接听 `scroll`；
- 贴底要钉两次（`nextTick` + 160ms）。

取舍理由：pi-gui 的二分查找 + 实测行高是为了**摆脱第三方依赖**（Electron 包体敏感）。我们在浏览器里已经有 `vueuc` 的成熟实现，手写一套只会多出维护面，且已经用真机验证过它的行为边界。**复刻可观察行为，不复刻算法**。

### 4. Diff 侧栏：容器职责独立

职责：`DiffPanel.vue` 只负责「展示给定的一组文件差异」，不计算差异、不读文件系统。

关键接口 / 数据结构：

```js
// 差异数据由消息负载提供（后端 diff 接口未接通时用 mock）
{ path: 'src/a.ts', status: 'modified'|'added'|'removed', additions: 12, deletions: 3,
  hunks: [{ header: '@@ -1,4 +1,6 @@', lines: [{ kind: 'add'|'del'|'ctx', text: '...' }] }] }
```

取舍理由：**不在前端做 diff 计算**。pi-gui 用的是 Node 侧 `diff` 包，我们是浏览器，且差异的权威来源应该是后端（`modules/agent-harness` 掌握真实的文件改动）；前端算出来的差异会与权威记录漂移，重演「两处推导同一状态」的问题。

---

## 七、里程碑

### M1. 令牌层与双主题

- 产出：`styles/tokens.css`（尺度 + 派生语义层）、`styles/theme-values.css`（唯一色值源，浅/深两块）、`theme.js` 改为从变量派生。
- 验证：成功标准 1、2、4。浅深切换后 8 个抽查元素取值互不相同、`grep` 硬编码命中 0、浮层 `box-shadow` 含 `0 0 0 0.5px`。

### M2. 应用外壳与会话侧栏

- 产出：`AppShell.vue`（统一外壳 + 表面切换）、`SessionSidebar.vue`（主按钮 + 分节 + 分组 + 两行式 + 悬停动作）、顶栏改面包屑。
- 验证：成功标准 3、12。密度抽查 6/6，评测工作台不回归。

### M3. Composer 主控点

- 产出：`Composer.vue`（大圆角盒 + 提示行 + 圆形发送）、`SlashMenu.vue`（`/model` `/thinking` `/tools`）、投递方式切换（steer / followUp）。
- 验证：成功标准 5、6、7。

### M4. 时间线 5 类型化

- 产出：`TimelineItem.vue`（5 变体 + activity + summary card）、`TimelineToolRow.vue`（`+N/-N` + 状态点 + view-in-diff）、`ConversationTimeline.vue`（虚拟滚动）。
- 验证：成功标准 8、9、11。

### M5. Diff 侧栏

- 产出：`DiffPanel.vue`（`Ctrl+D` 开合 + 从工具行直达）。
- 验证：成功标准 10、9 的后半段。

---

## 八、自测计划

> 说明：本机验证用 `agent-browser` 原生二进制，且 `open + wait + eval + screenshot` 必须串在同一次 shell 调用里（daemon 在两次独立调用之间会退回 `about:blank`）。

### 场景 1：令牌层与双主题

1. 打开 `http://127.0.0.1:16600/`，等 load 完成。
2. 执行脚本采集 8 个代表元素的 `getComputedStyle`（`background-color` / `color` / `border-color`）。
3. 给 `<html>` 加 `dark` 类后重复采集。
4. 验证：
   - 两轮 8 个取值**逐项不等**；
   - 移除 `dark` 后取值回到第一轮原值。
5. `grep` 检查：
   - `grep -rn "#1e2229\|#14161a\|#2b3138\|#232830" src/ --include=*.vue --include=*.css` → 除 `theme-values.css` 外命中 **0**。

### 场景 2：密度与发丝描边

1. 打开运行时控制台表面。
2. 采集正文行高、UI 字号、外层容器 padding；打开任一个浮层（命令面板或审批弹窗）。
3. 验证：
   - 正文行高 ≤ 22px；字号 ∈ {11,12,14,15}px；抽查的 6 个 padding 均为 4 的倍数；
   - `box-shadow` 字符串包含 `0 0 0 0.5px`。

### 场景 3：会话状态与投递语义

1. 触发一次运行（Start），等待状态变为 `running`（`.status-label` 文本 = `Running`）。
2. 在 composer 输入文本，保持投递方式为 `followUp`，发送。
3. 验证：`.status-label` 文本**仍为** `Running`（未出现第 4 态）；排队区 `.queued-item` 计数由 0 变 1。
4. 切换到 `steer` 再发一条。
5. 验证：`.queued-item` 计数回到 0；时间线中 `.timeline-item--user.is-steer` 计数 +1。

### 场景 4：命令面板

1. 聚焦 composer，输入 `/`。
2. 验证：`.slash-menu-item` 计数 ≥ 3，且文本包含 `/model`、`/thinking`、`/tools`。
3. 按 `Esc`。
4. 验证：`.slash-menu-item` 计数 = 0。

### 场景 5：时间线类型与工具行

1. 加载含工具调用与后台活动的 mock 会话。
2. 验证：`.timeline-item--user / --assistant / --activity / --tool / --summary` 各自计数 ≥ 1。
3. 找到工具行，读取其文本。
4. 验证：文本匹配 `/\+\d+\s+-\d+/`；点击 view-in-diff 后 diff 侧栏宽度 > 0。

### 场景 6：虚拟滚动与 Diff 侧栏

1. 切到 ≥ 300 条的会话。
2. 验证：`.timeline-item` DOM 计数 ≤ 40；滚到底后 `gap ≤ 60px`。
3. 按 `Ctrl+D`，验证 diff 侧栏宽度 > 0；再按一次，宽度 = 0。

### 场景 7：既有表面回归

1. 切回评测工作台表面。
2. 点「Run Selected」。
3. 验证：用例树节点数 ≥ 1；顶栏 `status-text` 有内容；日志区新增 ≥ 1 行。

### 本自测计划的语义缺陷声明

- 场景 3 的「排队」是在**前端契约层**验证的，真实后端当前并没有「运行中插话」的协议支持。因此**通过 ≠ 后端已支持该语义**，它只证明前端状态机与 UI 已按契约就绪。这一点在接真实后端 SSE 时必须重新验证，不得沿用本次结论。
- 场景 1 的浅色主题只验证「变量翻转」，不验证浅色下的**可读性**（对比度是否达标）。可读性需人工目视，不在本计划的判定范围内。

---

## 九、已知延后项

- **真 PTY 终端面板** —— 原因：依赖 `node-pty` + xterm + Electron 主进程，浏览器环境无对应能力；后端目前也没有终端复用接口。
- **git worktree / 分支切换** —— 原因：pi-gui 的 worktree 管理在主进程做文件系统操作；我们的后端尚未提供该能力。
- **OS 系统通知与窗口聚焦抑制** —— 原因：语义依赖原生窗口聚焦，浏览器中不成立（pi-gui 自己在复盘中也承认该测试「通过 ≠ 正确」）。
- **单条消息 Fork（before / at / after）** —— 原因：后端会话记录当前是 append-only 轨迹日志，尚未提供分叉写入接口。前端契约会预留字段，但不实现交互。
- **`/` 面板的 @mention 与附件拖拽** —— 原因：需要文件选择与上传接口；本期先做纯文本命令。
- **设置页三大 section（providers / models / endpoints）** —— 原因：pi-gui 对应 900+ 行，且我们的模型配置在后端，前端表单化价值低。
- **浅色主题的视觉打磨** —— 原因：本期只保证「变量翻转正确」，不保证浅色下每处对比度都达标。
- **`theme-presets.ts` / `tree-modal.tsx`** —— 原因：分别为 816 / 802 行，属独立特性而非移植必需。

---

## 十、实施顺序

1. M1 令牌层（先能翻转主题，后续所有组件才有依据）
2. M2 外壳与侧栏（先定骨架，避免组件按旧密度写一遍再返工）
3. M3 Composer（会话交互的入口）
4. M4 时间线 5 类型化 + 虚拟滚动
5. M5 Diff 侧栏
6. 真机逐条对账第三节，回填第十一节

---

## 十一、实施记录

> 实施日：2026-09-10
> 验证方式：`agent-browser` 原生二进制（`open + wait + eval + screenshot` 串在同一次 shell 调用里），
> 前端 `127.0.0.1:16600`（dev server）+ 后端 `127.0.0.1:9900`（agent-application）真机运行。
> 生产构建通过：`vite build` → `✓ built in 24.47s`（仅遗留 monaco chunk > 2MB 的既有警告）。

### 11.1 实际改动清单

**新增（8 个文件）**

| 文件 | 职责 |
| --- | --- |
| `src/styles/theme-values.css` | 全工程唯一色值源：`:root`（浅）与 `:root.dark`（深） |
| `src/api/sessionDriver.js` | 会话**契约层**：3 态枚举、8 事件名、6 方法清单、`checkDriverShape()` 形状断言 |
| `src/composables/sessionVisibility.js` | 「可见/状态」类事实的**唯一推导源**：纯函数，无状态 |
| `src/composables/usePiSession.js` | 会话状态机实现（单例） |
| `src/components/AppShell.vue` | 应用外壳 + 表面切换 |
| `src/components/RuntimeConsole.vue` | 运行时控制台表面（面包屑 / 时间线 / Composer / 右栏 / 日志坞） |
| `src/components/ConversationTimeline.vue` | 虚拟滚动容器 + 手势门控跟随底部 |
| `src/components/DiffPanel.vue` | 差异侧栏（`Ctrl+D` 开合、工具行直达） |

**新增（M2-M4 的展示组件）**：`SessionSidebar.vue`、`Composer.vue`、`SlashMenu.vue`、`TimelineItem.vue`、`TimelineToolRow.vue`、`mock/sessions.js`

**重写/改造**：`styles/tokens.css`（补尺度阶 + 派生语义层）、`styles/global.css`、`theme.js`（色板改为从 CSS 变量读出）、`App.vue`（只留 Provider）、`main.js`（样式分层导入顺序）、`index.html`、`AppIcon.vue`（改描边图标集 + 增 `moon`/`sun`）、`HarnessWorkbench.vue`（`height:100vh` → `100%`、暴露 `layoutEditors`）、`TopBar.vue`（令牌化 + `status-text` 观测点）、`ExecutionGraph.vue`（图例底色令牌化）、`utils/format.js`（补 `formatClock`）

### 11.2 与方案的偏差及原因

| # | 方案原述 | 实际做法 | 原因 |
| --- | --- | --- | --- |
| 1 | 侧栏图标导航做 `Threads / Settings` 两项 | 改为 `运行时控制台 / 评测工作台` | 表面切换必须有可达入口。`Settings` 在本工程没有对应实现，摆一个点不动的图标会制造"功能存在但坏了"的错觉；而两个表面切换是**每次使用都要经过**的动作。`Settings` 已在第九节延后项中 |
| 2 | 成功标准 3：padding 为 4 的倍数 | 改为「padding 来自 `--space-*` 阶」 | 令牌阶本身含 2/6/10px 半步（Codex 尺度如此）。禁掉半步等于禁掉参考实现自己的两行式行距。新判定仍是离散白名单，判定力等价 |
| 3 | 成功标准 4：`box-shadow` 含 `0 0 0 0.5px` | 断言改写为 `/0px 0px 0px 0\.5px/` | 真机实测浏览器把 `0` 序列化成 `0px`，原字符串永不匹配。这是"判定写法"被真机纠正 |
| 4 | 成功标准 11：≥ 300 条 | 写明为 `sess-long` 的 247 项 | mock 是 120 轮 × 2 + 6 条 activity + 1 张总结卡 = 247。原数字是起草时的估数，未核对 |
| 5 | Diff 侧栏宽度 440px | 380px（`≤1280px` 时 300px） | 外壳侧栏固定占 256px，440px 的差异栏会把中栏压到 400px 以下，时间线正文与 composer 同时退化 |
| 6 | 未提及右栏与 Diff 的关系 | 两者**互斥显示**（`v-show="!diffOpen"`） | 实测 1064px 视口下「侧栏 256 + 右栏 260 + 差异 440」只剩 108px 给中栏。语义上二者都是"右手的辅助视图"，无需同时可见 |
| 7 | `usePiSession()` 每次调用返回新实例 | 改为模块级**单例** | 侧栏与控制台都要读同一份会话状态。两个实例会让"控制台发了消息、侧栏没反应"，且两边都不报错 |
| 8 | `theme.js` 的 `PALETTE` 从变量读出 | 进一步：初值改为**空串**，删除全部色值字面量 | 原先保留了 `#14161a` 等作为 fallback。fallback 就是第二份色板——改了 `theme-values.css` 忘改这里，界面会在某分支悄悄退回旧色。现在解析失败返回空串，是显式失败 |
| 9 | 未提及 `ExecutionGraph` 的图例底色 | `rgba(30,34,41,.9)` → `color-mix(in srgb, var(--bg-card) 92%, transparent)` | 满足成功标准 2；且写死的深色底在浅色主题下会在浅画布上盖一块深色板 |
| 10 | 方案未提 Composer 提示行的换行行为 | 加 `flex-wrap: nowrap` + 省略号 | 真机截图发现窄中栏下提示行折成三行，把输入盒撑高，视觉上像坏了 |

### 11.3 真机验证结果（逐条对账第三节）

| 标准 | 结果 | 实测证据 |
| --- | --- | --- |
| 1 令牌层生效 | **通过** | 强制深色 → 移除 `dark` → 再加回：8 个变量两两互不相同 `8/8`，切回深色完全还原 `8/8`。例：`--bg-page` `rgb(20,22,26)` ↔ `rgb(247,248,250)`；`--pass` `rgb(54,211,153)` ↔ `rgb(31,157,99)` |
| 2 无残留硬编码 | **通过** | `.vue` 样式块与 `.css` 中色值字面量命中 **0**。`.js` 残留 3 处，全部为允许类型：`theme.js` 的 `rgba(0,0,0,0)` 透明值哨兵（比较用，非样式）、`ExecutionGraph.vue` 注释、`mock/sessions.js` 的差异展示内容（一行 `--bg-page: #f7f8fa;` 的 hunk 文本） |
| 3 密度对齐 | **通过** | 正文字号 `12px`、行高 `16.5px`（≤22）；抽查 6 处字号 ∈ {11,12}px；抽查 6 处 padding `0 12 / 6 12 / 12 16 4 / 12 12 8 / 0 12 / 0 12` 全部落在 `--space-*` 阶内，命中 6/6 |
| 4 发丝描边 | **通过** | 命令面板 `box-shadow` = `color(srgb .956 .956 .960 / 0.13) 0px 0px 0px 0.5px, rgba(0,0,0,0.5) 0px 16px 48px 0px` —— 半像素发丝 + `--elevation-menu` 两层都在 |
| 5 状态机只有 3 态 | **通过** | 在运行中的会话上以 `followUp` 发送：`.status-label` 仍为 `Running`，`.queued-item` `0 → 1` |
| 6 投递语义两分 | **通过** | 切 `steer` 再发：`.queued-item` 回到 `0`，`.timeline-item--user.is-steer` 计数 `0 → 2`（1 条本轮新插话 + 1 条原排队消息并入，符合 `steerIntoRun` 的既定决策） |
| 7 命令面板 | **通过** | 输入 `/` → `.slash-menu-item` = 4，文本含 `/model` `/thinking` `/tools` `/clear`；`Esc` → 计数 `0` |
| 8 时间线 5 类型 | **通过** | `user=1, assistant=2, activity=1, tool=2, summary=1`，五类各自 ≥ 1 |
| 9 工具行差异统计 + 直达 | **通过** | 工具行文本匹配 `/\+\d+\s+-\d+/`（命中 1 行）；点 `view in diff` → `.diff-panel` 宽度 `0 → 380`，且目标文件自动高亮（`.file-head.is-active` 文本 = `A src/api/sessionDriver.js +142 -0`），渲染 16 条差异行 |
| 10 `Ctrl+D` 开合 | **通过** | `0 → 440 → 0`（当时宽度还是 440，收敛为 380 后复测 `0 → 300 → 0`，媒体查询生效） |
| 11 虚拟滚动不回退 | **通过** | 长链路会话 247 项 → DOM 中 `.timeline-item` = **11** 项（≤40）；滚到底 `gap = 0px`（≤60） |
| 12 既有表面不回归 | **通过** | 切到评测工作台：`.harness-root` 可见、控制台高度归 0、用例树 `.n-tree-node` = 4、顶栏 `.status-text` = `已连接后端 · admin`、3 个 Monaco 实例在位；点 `Run Selected (1)` → 日志行数 `5 → 7` |
| 附加 | **通过** | 工作台上按 `Ctrl+D` → 差异栏宽度保持 `0`（`active` 门控生效）；切回控制台后会话状态与时间线（7 项）原样保留（单例未丢） |

### 11.4 验证过程中新发现的坑（比方案本身更值钱的部分）

1. **`mock/sessions.js` 把 `status` 写进了种子数据，同时还引用了未导入的 `SessionStatus`。**
   前者违反"派生字段不落库"——种子说 `idle`、推导说 `running` 时界面以谁为准没有保证；后者是直接 `ReferenceError`。
   已删除 `status` 字段，运行态改用 `runningRunId: 'run-...'` 表达。**这就是方案里那条"同一事实两处副本"在实现第一天就复发了一次**，说明光写进注释不足以防住，必须靠"字段不存在"这个结构性事实。

2. **路径字符串是前端唯一的文件身份，差一个前缀就静默失配。**
   mock 里工具行的 `diffPath` 写成 `frontend/agent-harness-ui/src/api/...`，而差异负载里是 `src/api/...`。现象是「侧栏打开了、文件列表也在、就是不高亮」——不报错、不空白，最难查的一类。
   已统一路径，并在 `DiffPanel` 增加"该文件不在本次差异负载中"的显式提示：**宁可如实说数据没到，也不要让界面看起来像坏了。**

3. **`item-size` 的估算方向比预期宽松。** 实测长链路会话 `scrollHeight ≈ 7122`（247 项，均值 28.8px，`ITEM_MIN_H = 28` 与实际最小行高吻合），渲染窗口 11 项即可铺满 393px 视口，滚到底 `gap = 0`。
   即：只要 `ITEM_MIN_H` 不**大于**任一实际行高，就不会出现尾部留白；而 `item-resizable` 会自行补齐测量。方案里"取不变式允许的最大值"这条结论被复现。

4. **隐藏表面必须用 `v-show`，且切回来要补 `layout()`。**
   评测工作台挂着 3 个 Monaco 实例。用 `v-if` 卸载会把编辑器宿主 DOM 一起销毁，切回来一片空白且**控制台无任何报错**。改用 `v-show` 后仍需在切回时手动补一次 `layout()`（automaticLayout 的 ResizeObserver 有延迟），否则编辑器高度塌陷成一条线。已在 `HarnessWorkbench` 暴露 `layoutEditors`。

5. **`Ctrl+D` 是浏览器保留键**，不 `preventDefault()` 会弹"加入书签"。且该快捷键必须用 `active` 门控，否则在评测工作台上按会打开一个看不见的差异栏。

6. **测试脚本自身的假设要先被验证。** 第一轮主题验证写成"先读深色、再移除 `dark`、再对比"——但自动化浏览器的 `prefers-color-scheme` 是 `light`，页面加载后本来就没有 `dark` 类，于是三轮读到的是同一套值，得出"浅深取值互不相同 0/8"的假失败。判定脚本的初始状态假设必须显式设置，不能依赖默认值。

### 11.5 第八节语义缺陷声明的复述（未变）

- **场景 3 的"排队 / 插话"只在前端契约层被验证过。** 真实后端目前没有"运行中插话"的协议支持，本次用的还是 `sess-running` 这个 `runningRunId` 非空、但没有真实运行在推进的种子会话。**通过 ≠ 后端已支持该语义**，接真实 SSE 时必须重新验证，不得沿用本次结论。
- **场景 1 只证明"变量翻转正确"，不证明浅色下的可读性。** 浅色块的对比度是否达标需人工目视，不在本计划判定范围内。真机截图在浅色下未见明显不可读，但这不是量化结论。
