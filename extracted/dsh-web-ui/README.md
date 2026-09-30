# Agent Runner — 前端页面 + CSS 抽取版

> **显示名已改为「Agent Runner」。** 页面结构与样式抽取自
> [`deepseek-harness`](https://github.com/deepseek-ai/deepseek-harness)（MIT），
> 下文凡提「源码」均指该仓库。

从 [`deepseek-harness`](https://github.com/deepseek-ai/deepseek-harness)（MIT）里**只取前端页面结构与样式**，
剔除宿主（Cordis）、RPC 传输、状态管理、数据层与全部业务功能，做成可直接双击打开的两个静态页面。

无构建、无依赖、无框架。打开 `index.html` 即可。

---

## 为什么需要"抽取"而不是"跑起来"

该仓库的前端**不是一个能独立启动的 React 应用**。它是 51 个 client 包在 Cordis 容器里按需装载的组合结果：

- 宿主侧只把一份 `WebBootGraph` 写进 `window.__DSH_BOOT__`，浏览器侧再据此装配出完整 UI；
- 业务数据住在对象层（`api/*-controller/client`），**永不进 store**，全部经 `/api` 走的 RPC 取得；
- 组件层由自研的 Slot 组合系统按 key 挂载，单个组件无法脱离注册表渲染。

所以「只要页面和样式」这件事，只能靠**抽取**：把设计令牌层、页面结构几何、组件样式表逐条搬出来，
把框架写在元素上的 `data-*` 属性改成由一页极简脚本写同样名字的属性，让 CSS 里原有的选择器一行不改地生效。

---

## 文件

```
dsh-web-ui/
├── index.html              会话页（data-phase="active"）：完整三栏 + 对话正文 + 停靠输入卡
├── hero.html               新建会话首屏（data-phase="hero"）：同一张页面的另一个相位
└── styles/
    ├── dsw-tokens.css      设计令牌层 —— 全工程唯一色值源（三层：静态色阶 / 语义别名 / 场景专名）
    └── dsw-page.css        页面结构样式 —— 按源码模块分段，每段标注来源文件
```

---

## 改名范围（display name → Agent Runner）

只改**用户可见的品牌字样**，其余一律不动：

| 已改 | 位置 |
|---|---|
| 侧栏品牌字标 `deepseek harness` → `Agent Runner` | `index.html`、`hero.html` 的 `.sb-brand-name` |
| 浏览器页签标题 | 两页的 `<title>` |

| 刻意未改 | 原因 |
|---|---|
| `--dsw-static-deepseek-*` 调色板令牌名 | 是源码的原始调色板命名，别名层（`--dsw-alias-button-info-fill` 等 10 余处）按名引用；改名会破坏「与源码逐字一致」的可溯源性与 diff 能力 |
| 模型下拉 `deepseek-v4` / `deepseek-v4-flash` | 是模型名，不是品牌名 |
| 右栏文件树里的 `agent-harness-ui` | 是当前项目名，与本页品牌无关 |
| README / CSS 注释里的仓库引用 | 属溯源信息，改了就无法回溯到源码 |
| 品牌图标 `#i-whale` | DeepSeek 的品牌图形，**未替换**（见下） |

> ⚠️ 侧栏那个鲸鱼图标仍是 DeepSeek 的品牌图形。若要彻底脱离，需要另画一个中性标记替换
> `index.html` / `hero.html` 里的 `<symbol id="i-whale">`。

---

## 量化溯源

样式里的**每一个数值**都来自源文件，没有一处是我调的。对照表：

| 抽取内容 | 源文件 | 关键数值（源码原值） |
|---|---|---|
| 静态色阶 `--dsw-static-*` | `ui-theme/src/styles/design-platform.css` | 6 族共 70 余档，逐行照录 |
| 语义别名 `--dsw-alias-*` | 同上 | 浅色 `body` / 深色 `body[data-ds-dark-theme]` 各一套，逐行照录 |
| 场景专名 `--dsw-specific-*` | 同上 | 侧栏底、气泡、输入框、菜单等 11 项 |
| 基座字体/缓动 | `ui-theme/src/styles/base.css` | 等宽栈刻意不带裸 `monospace`（Windows 下 CJK 会掉到 SimSun） |
| 阴影阶 | `ui-theme/src/styles/gradient-shadow-text.css` | `stroke + 两层柔光`；`--dsw-elevation-soft` 用于输入卡 |
| 文本阶梯 | 同上 | `14/22`、`500 14/22`、`13/20`、`12/18` 等 8 档 |
| 圆角形状 | `ui-theme/src/styles/corner-shape.css` | `superellipse(1.5)`；全圆形状需显式 `corner-shape: round` 退出 |
| 滚动条皮肤 | `ui-theme/src/styles/scrollbar.css` | 双路径门控（WebKit 伪元素 / Firefox 标准属性） |
| 三栏外壳 | `ui-layout/src/client/AppFrame.module.css` | `grid-template-rows: 100%`；拖拽期间 `transition: none` |
| 列宽常量 | `ui-layout/src/client/columns.ts` | 侧栏 280 / 最小 264 / 最大 420；收起 56；中栏最小 400；右栏最小 300、默认 45%、上限 70%；自动收起断点 1024 |
| 侧栏 | `ui-sidebar/src/client/SidebarRoot.module.css` | 内边距 `6px 12px`；品牌行 60px；新建会话 **38px 高 / 12px 圆角**；导航项 **36px / 8px 圆角**（`padding: 7px 8px`） |
| 会话列 | `ui-conversation/src/client/skeleton/ConversationRoot.module.css` | 头部 **76px**（= 侧栏页签条 38 + 面板头 38，两条水平线在列边缘对齐）；页签 `13/16 500`、间距 36、指示条 2px 压在分隔线上；正文宽 `clamp(680px, 列宽×64%, 920px)`，输入卡 = 正文 + 32px |
| 输入卡 | `ui-conversation/src/client/skeleton/InputBar.module.css` | **22px 圆角**、`border: 0` + `elevation-soft`；草稿 `min-height 36`、`padding 4px 8px 0 14px`；附件圆钮 **28px**；模式下拉 `28px 高 / 8px 圆角 / 13-20-500`；发送钮 **34px 圆、info-fill 蓝、translateY(-2px)**；按钮行 `padding 2px 8px 6px`；草稿上限 14 行 = 336px |
| 首屏 | `ui-conversation/src/client/skeleton/HeroShell.module.css` | 标题 **26/32 500**；鱼与标题 `column-gap 10 / row-gap 12`（溢出驱动换行，无断点）；草稿地板 52px（双行） |
| 用户气泡 | `ui-chat/src/client/chat/MessageItem.module.css` | **22px 圆角**、`padding 10px 16px`；宽度上限 = 内容轴 **70.2%**（= 525/748）|
| 助手正文 | `ui-chat/src/client/chat/AssistantMarkdown.module.css` | 块间距 16；动作条 `margin-top 16 / margin-left -6`（对齐 28px 命中区） |
| 正文滚动口 | `ui-chat/src/client/chat/ChatView.module.css` | `padding 16px 32px`（= 侧余量 + 16）；`container-type: inline-size` |
| 轮次过程行 | `ui-chat/src/client/chat/TurnProcessNodeView.module.css` | **33px 高**、底部 `0.5px` 发丝线；chevron 收起 `rotate(-90deg)` |
| 轮次状态行 | `ui-chat/src/client/chat/ChatView.module.css` | **26px 行**，正文号 + 22px 行距；时钟锁 `tabular-nums` 走次级字号 |
| 统计胶囊 | `ui-chat/src/client/chat/StatsPills.module.css` | `padding 1px 8px`、`border-radius 24px`、`13/20` 三级墨色 |
| 真实文案 | `ui-conversation/src/client/locales.ts`、`ui-chat/src/client/locale.ts` | 首屏「探索未至之境 / 预览版」、占位符「发消息或创建任务, / 调用指令, @ 文件或对话」、统计「2 轮 5 步 · 缓存命中 68%」、页签「对话 / 轨迹」 |

### 一处非源码直录

右栏（`dsw-page.css` 第 7 段，`.rs-*`）是唯一的例外。源码里它由
`ui-sidebar-right` 的 dockkit 分裂树驱动（`ui-dockkit/src/components/dockkit.module.css`），
其尺寸只有一句公开约束：**页签条 38px + 面板头 38px = 76px**，用来与中栏头部的水平线对齐。
我用这条约束 + 全站一致的令牌词汇重写了右栏，**未逐条比对 dockkit 源文件**。
若要完全一致，以那份 CSS 为准。该段在样式文件里也在开头标了 ⚠。

### 两条有意的实现方式差异

1. **列宽**：源码由 `AppFrame.tsx` 用 JS 求值后写 inline style；此页改用 `--af-sidebar` / `--af-rightbar`
   两个 CSS 变量承载同一套常量。断点 1024px 的自动收起用媒体查询表达。
2. **内容宽度轴**：源码用 `ResizeObserver` 发布列宽为 `--dsh-conversation-column-width`；
   此页改用 `clamp(680px, 64%, 920px)` —— 百分比在 `max-width` 上对包含块求解，**效果等价且无需测量**。
   这也是唯一不需要 JS 的量差。

其余一切（含选择的字色、圆角、行高、动画曲线、`data-*` 属性名）与源码逐字一致。

---

## 设计系统里值得单独知道的几件事

1. **三层令牌，缺一不可**
   `--dsw-static-*`（原始调色板，主题无关）→ `--dsw-alias-*`（组件唯一消费层）→ `--dsw-specific-*`（场景专名）。
   组件里出现任何字面色值都算违规。因此**深色不是一个主题文件，而是给 `<body>` 加一个 `data-ds-dark-theme`**：
   别名层的赋值整体翻转，所有派生值自动跟随。本页的外观开关做的就是这一件事。

2. **浮层不占布局**
   高层级表面一律 `border: 0`，分离感由 `0 0 0 0.5px` 的发丝描边 + 两层极淡柔光承担。
   输入卡的阴影因此是 `--dsw-elevation-soft`，而不是任何 `border`。

3. **右栏是轨道，不是盒子**
   `.af-rightbar-col` 设 `overflow: visible` 而左栏裁剪：右栏的占用者把面板锚在**框架右边缘**上
   （那条边永远不动），轨道归零时面板就从中栏上方悬着 —— 所以宽度动画期间面板不会跟着漂。

4. **折叠是滑走 + 淡出，不是变形**
   收起的 150ms 里内容保持冻结的展开布局原地淡出，滑动的轨道把它裁掉，落定后才切到 56px 图轨布局。
   所以滑动全程**零回流**。

5. **色即状态**
   圆点只表达 `running`（蓝，唯一会动的）/ `failed`（红）；成功态在会话行里刻意**不显示**
   （`opacity: 0`），因为"什么都没发生"才是常态，不该占视觉预算。

---

## 页面里有什么 / 没有什么

**有**：三栏外壳与列宽、侧栏（品牌行 / 新建会话 / 面板导航 / 工作区分组的会话树 / 底部设置）、
中栏（76px 头部 + 面包屑 + 页签 + 对话正文 + 轮次过程折叠 + 停靠输入卡 + 统计胶囊）、
右栏（页签条 + 文件树 + 文档/差异预览）、首屏相位、浅深双主题、侧栏折叠。

**脚本只有 7 处展示态切换**（深色主题、侧栏折叠、右栏开合、中栏页签、右栏页签、工具行展开、草稿空态）。
**没有**：任何 HTTP 请求、RPC、SSE、状态管理、路由、i18n 运行时、虚拟滚动、Markdown 编译、Monaco、插槽注册表。

---

## 用法

直接双击 `index.html`，或起个静态服务：

```bash
cd dsh-web-ui
python -m http.server 16700 --bind 127.0.0.1
# → http://127.0.0.1:16700/index.html
```

需要把某一段样式搬进自己的工程时：`dsw-page.css` 每一段开头都写了来源文件，
按 `/* from: ... */` 去找即可；颜色一律改引 `dsw-tokens.css` 的语义别名，
不要引入字面色值，否则浅深主题会各坏一半。

---

## 许可

页面结构与样式抽取自 `deepseek-harness`，MIT License，版权归 DeepSeek 所有。
本目录仅是结构/样式的再组织，不含该项目的任何运行时逻辑。
