# web-shell 前端外壳基于 DeepSeek Harness Web-UI 重写 — 方案

## 背景与目标

参考 **DeepSeek Harness (dsh) Web UI** 的设计语言，重写 agent_runner 前端外壳（web-shell）：

| 维度 | dsh 现状 | agent_runner 现状 |
|---|---|---|
| 设计令牌 | `--dsw-alias-brand-primary` 品牌蓝 **#4176E6**（deepseek-500/450）+ 语义令牌系统（ui-theme） | 渐变蓝紫 `#4f6ef7 → #8b5cf6`（global.css） |
| 布局 | 极简"毛坯房"：侧栏（会话列表/新会话）+ 主区（hero 标语/输入框/消息流），大留白、无炫技 | Element Plus 标准后台（侧栏 220px + 顶栏 + 内容） |
| 组件体系 | 插件化 ui-primitives / ui-theme，CSS Modules + 语义令牌 | Element Plus 组件 + 模块 scoped 样式 |

**目标**：整体按 dsh 美学重构——品牌蓝令牌全替换、布局毛坯风、Element Plus 全局覆盖、模块页面随令牌自动跟随。

## 设计语言提炼（dsh）

1. **品牌蓝单色系**：主色 #4176E6（deepseek-500）、强调 #5E8CFF（deepseek-450）、按下 #3667CC；**禁渐变**（现有蓝紫渐变移除）
2. **毛坯房美学**：浅灰画布 `#F5F6F8`、白卡片、hairline 细边框 `#E8E9EC`、**克制圆角**（按钮 6px、卡片 10px）、**浅阴影**、大留白
3. **排版**：标题负字距、正文 14px、数字/ID 用 mono；**字重 400/500/600** 三档
4. **交互**：hover 浅灰底 + 文字加深；选中品牌蓝 soft 底 + 品牌蓝文字；动效 150~260ms

## 实施方案

### 1. 设计令牌全重做（`src/styles/global.css`）

- 品牌色：`--brand-500: #4176E6`（dsw deepseek-500）、`--brand-600: #3667CC`、`--brand-400: #5E8CFF`；删除渐变令牌 `--brand-gradient`（影响 MainLayout 徽标/选中态，同步改）
- 中性色：画布 `#F5F6F8`、表面 `#FFFFFF`、hairline `#E8E9EC`、文字 `#171717/#4D4D4D/#888888/#B0B0B0`（对齐 dsw 灰阶）
- 圆角：`--radius-sm: 6px / md: 8px / lg: 10px / xl: 14px`（收敛现有 10/14/18）
- 阴影：收敛为 `0 1px 2px rgba(23,23,23,0.04)` 级别（毛坯风）
- 暗色模式：对齐 dsw 深色令牌，保留 `data-theme="dark"` 切换
- **Element Plus 全局覆盖**（global.css 追加）：`--el-color-primary: #4176E6` 系列（hover/active/light），使按钮/链接/选中态/表格全部跟随品牌蓝

### 2. 布局毛坯风重构（`src/layouts/MainLayout.vue`）

**保留全部功能逻辑**（权限菜单过滤、暗色切换、折叠、面包屑、用户菜单、移动端抽屉、插件菜单渲染），仅重构视觉：

- **侧栏**：宽 240px（`--sidebar-width`），浅灰底 `#FAFAFA`（dsw 侧栏风格）+ hairline 右边框；Logo 区品牌蓝方块 + mono 文字；菜单项 hover 浅灰、选中品牌蓝 soft 底 + 品牌蓝文字（替代现渐变选中）
- **顶栏**：白底 hairline，折叠按钮 + 面包屑（品牌蓝文字）+ 主题切换 + 用户（头像品牌蓝底）
- **内容区**：浅灰画布、大留白（padding 24px）；路由过渡保留

### 3. 模块页面跟随（agentmemory 等）

- agentmemory 的 `tokens.css`：`--amm-primary` 从 `#5E6AD2`（薰衣草蓝）映射为 **#4176E6**（品牌蓝），与外壳一致；其余令牌不动
- 其他模块页面依赖 Element 覆盖自动跟随；`--brand-*`/`--bg-*` 变量引用处自动变

### 4. 改动文件清单

| 文件 | 改动 |
|---|---|
| `src/styles/global.css` | 令牌全量替换（品牌蓝/中性/圆角/阴影/暗色）+ Element Plus 主题覆盖 |
| `src/layouts/MainLayout.vue` | 视觉重构（侧栏/顶栏/内容区毛坯风），逻辑保留 |
| `frontend/modules/agentmemory/src/styles/tokens.css` | `--amm-primary` 系列 → 品牌蓝 #4176E6 |
| 其他模块 scoped 样式 | 仅当显式引用旧渐变/色值时微调（构建后排查） |

## 验证方式

1. `npm run build` 通过 + dev 热更新查看
2. 外壳：侧栏/顶栏/折叠/暗色/移动端抽屉/用户菜单功能回归
3. 页面：记忆管理（重点）+ 随机抽查 2~3 个模块页，确认品牌蓝一致、无残留蓝紫渐变
4. 无 console 报错

## 风险控制

- 仅改视觉层，不动路由/权限/插件加载逻辑
- 令牌收敛为单点替换，页面尽量零改动（靠变量与 el 覆盖跟随）
- 分两步落地：①令牌 + MainLayout → ②模块微调
