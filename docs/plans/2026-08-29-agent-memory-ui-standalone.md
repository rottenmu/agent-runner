# agent-memory-ui 独立运行实现方案

- 日期：2026-08-29
- 状态：待审核
- 来源：将 `frontend/modules/agentmemory`（web-shell 前端插件）改造成**可独立运行的 Vue3 应用**

## 一、目标

1. 现有 6 个视图组件（总览/用户/会话/全局/溯源/策略）**零改造成本**平移复用；
2. 独立 `npm run dev` 启动，不再依赖 web-shell 主壳；
3. dev 阶段通过 Vite proxy 对接后端（`http://localhost:9900`）；
4. 生产可 `npm run build` 产出静态资源，由后端 `/static` 托管或任意静态服务器部署。

## 二、工程结构

```
frontend/agent-memory-ui/
├── package.json
├── vite.config.js            # dev server + proxy /api → localhost:9900
├── index.html
└── src/
    ├── main.js               # 挂载 Vue + Element Plus（全量）+ 路由
    ├── App.vue               # dsh 布局壳（左侧导航 + 主内容区，复用现有风格）
    ├── router.js             # 7 条路由（与现有 routes.js 对齐）
    ├── api/
    │   ├── request.js        # 独立 axios 封装（baseURL=/api，从 web-shell 拷贝）
    │   └── memory.js         # 从模块目录拷贝（仅 import 路径改为 ./request）
    ├── styles/tokens.css     # 从模块目录拷贝
    └── views/                # 从 modules/agentmemory/src/views 拷贝 6 个组件
        ├── MemoryDashboard.vue
        ├── UserMemoryManage.vue
        ├── SessionMemoryManage.vue
        ├── GlobalMemoryManage.vue
        ├── TraceAnalysis.vue
        └── MemoryPolicy.vue
```

## 三、关键改造点

| 依赖 | 现状（web-shell 插件） | 改造后（独立应用） |
|---|---|---|
| API 封装 | `import request from '../../../../web-shell/src/api/request'` | 独立 `src/api/request.js`（axios，baseURL=/api，token 拦截保持） |
| 路由 | 主壳注入 | `src/router.js` 自建 7 条路由（懒加载） |
| 布局 | 主壳侧边菜单 | `App.vue` 内建左侧导航（复用现有 dsh 风格，含 6 个 Tab） |
| Element Plus | 主壳全局挂载 | 独立 `main.js` 全量引入 |
| 图标 | 主壳提供 | `@element-plus/icons-vue` 独立引入 |
| 后端地址 | 主壳 proxy | vite proxy `/api` → `http://localhost:9900` |

**视图组件本身不改动**——`MemoryDashboard.vue` 已自带 dsh 布局与 Tab 切换逻辑（`router.push({ path: '/agent-memory', query: { tab } })`），路由同名后行为完全一致；`TraceAnalysis.vue` 的 props/路由 query 联动也保留。

## 四、依赖清单

vue ^3.4、element-plus ^2.9、@element-plus/icons-vue、axios ^1.7、vue-router ^4.3、vite ^5.4、@vitejs/plugin-vue（与 web-shell 版本对齐）

## 五、实施步骤

1. 初始化工程（package.json / vite.config / index.html / main.js / router.js / App.vue / api / styles）
2. 拷贝 6 个视图组件，修正 memory.js 的 import 路径
3. `npm install && npm run dev` 启动验证，逐 Tab 冒烟（CRUD/溯源/策略/画像）
4. `npm run build` 验证生产构建产物

## 六、说明

- 不改动 `frontend/modules/agentmemory` 原有插件代码（web-shell 集成不受影响），两处并存；

## 七、实施记录（2026-08-29 已落地）

- ✅ 工程初始化完成：package.json / vite.config.js（proxy /api→9900，端口 5174）/ index.html / main.js / router.js / App.vue / api / styles
- ✅ 6 个视图组件 + tokens.css + memory.js 从 `modules/agentmemory` 拷贝，仅修正 request import 路径（改为独立 `src/api/request.js`）
- ✅ MemoryDashboard.vue 补充 `ElMessage/ElMessageBox` 显式 import（原插件环境容错，独立应用需声明）
- ✅ `npm install` 完成（vue3 / element-plus / axios / vue-router / vite5）
- ✅ `npm run dev` 启动成功（:5174），全部模块编译通过
- ✅ `npm run build` 成功（16.7s），dist 产物完整（index.html + assets）
- ⚠️ 提示：后端（agent-application :9900）未启动时页面可打开但接口返回失败，启动后端后可完整冒烟 CRUD
- 若后续需要单一代码源，可再改为模块引用方式（alias 指向 modules 目录），本次按"独立拷贝"快速落地。
