# agent-runner 前后端代码阅读报告（codegraph 驱动）

> 阅读时间：2026-09-13 · 工具：codegraph 1.5.0（tree-sitter 解析 + SQLite 知识图谱）
> 索引进度：后端 881 文件 / 18,151 节点 / 36,342 边（62.9 MB）；前端 193 文件 / 3,128 节点 / 8,437 边（8.6 MB）

---

## 一、索引部署方式（本次新增）

codegraph 的索引是**按项目根创建 `.codegraph/`** 的。原索引建在仓库根 `D:\codehub\agent_runner`，
但只扫到 Java（873 file 节点里 function 仅 97 个，前端 `usePiSession` 查询返回空 `[]`）——
因此**在 `frontend/` 下单独 `codegraph init`**，得到独立的 JS/Vue 索引：

| 索引 | 位置 | 覆盖 | 节点特征 |
|---|---|---|---|
| 后端 | `agent_runner/.codegraph/` | 881 文件 | method 6743 / import 5760 / class 804 / **route 298** |
| 前端 | `frontend/.codegraph/` | 193 文件 | function 1211 / constant 1050 / **component 79** |

前端索引多出 `component` 节点类型、后端多出 `route` —— 两者各自贴合语言特性，值得保留为两个独立索引用。

---

## 二、后端架构（Maven 多模块，Java）

### 模块规模（Java 文件数）

```
framework/                      modules/
  137  framework-ai              170  module-feishu      ← 最大
   34  framework-autoconfig      163  agent-harness      ← 次大（核心）
   22  framework-common           92  module-sys
                                 48  agent-memory
                                 33  agent-rag
                                 31  module-tools
                                 26  module-security
                                 23  agent-auth
                                 18  agent-intent
                                 13  agent-datasource
                                  9  agent-trace
```

### 分层约定

`framework/` 是技术底座（AI 抽象 + 自动装配 + 通用工具），`modules/` 是业务能力，
`agent-application` 是可启动壳（聚合依赖 + `application.yml`）。每个 agent-* 模块内部再分
`-core` / `-autoconfig` 两层，例如 agent-memory：

```
agent-memory-core       业务实现（OltpMemoryRepository / OlapAnalyticsRepository / AiMemoryService）
agent-memory-autoconfig Spring 装配（AgentMemoryAutoConfiguration 产出 oltp/olap 两个 Bean）
```

### 核心链路 1：agent-harness 会话执行

codegraph explore 给出的主链路：

```
AiAgentService ─use─> AiHarnessAgentRegistry.withAgent()      （7 个调用点）
   (modules/agent-spring-boot-starter)   (framework/framework-ai/.../ai/agent/, 第 83 行)
                          │
                          ├─ AgentEntry（agent 实例 + 引用计数）
                          └─ defersEvictedAgentCloseUntilActiveUseCompletes （延迟关闭，防竞态）

AiAgentManagementService ─> toAgent(request) ─> AiManagedAgent   （持久化实体）
  (modules/agent-harness/agent-harness-core/.../ai/management/)   saveAgent / seedAgents / listAgents
```

> 注：`AiHarnessAgentRegistry` 位于 **framework/framework-ai**（框架层）而非 modules/agent-harness，
> 但被 modules 层的 `AiAgentService` 消费——框架反向被业务模块驱动，符合「framework 提供底座」的分层。

**爆炸半径提示**（codegraph 自动标注）：`AiHarnessAgentRegistry.withAgent` 有 3 个测试文件覆盖
（`AiHarnessAgentRegistryTest` / `AiAgentServiceHarnessRoutingTest` / `AiChannelHandlerTest`），
是改动时必须回归的点；而 `AiAgentManagementService.toAgent` / `saveAgent` **无测试覆盖** ⚠️。

### 核心链路 2：agent-memory 四层记忆

```
MemoryStorageFacade  ──┬──> OltpMemoryRepository        （H2OltpMemoryRepository 实现，事务读写）
                       └──> OlapAnalyticsRepository     （ArrowOlapAnalyticsRepository 实现，列存分析）

存储适配：mapAtomicMemory → getString / fromBytes
         （原子记忆序列化，recallAtomicByType / BySession / listAtomicByTrace 三个召回入口）
```

**关键发现**：`OltpMemoryRepository` 是**全仓最热的接口**——**39 个调用方**，分布在
9 个类：`AgentMemoryAutoConfiguration`、`AiConversationMemory`、`AiMemoryService`、
`TrajectoryRecorder` 等，且有 5 个测试覆盖。任何接口签名变更都是高危操作。

对比之下 `AgentMemoryAutoConfiguration` 的 `oltp` / `olap` 两个 Bean 方法
**无测试覆盖** ⚠️，但被 `MemoryStorageFacade` 依赖——装配层是薄弱环节。

---

## 三、前端架构（4 个 Vite 工程）

### 工程规模（js/vue/mjs 文件数）

```
 97  modules/            ← 8 个源码级插件（由 web-shell 动态 import）
 66  agent-harness-ui/   ← 当前主力应用
 28  web-shell/          ← 主壳，负责插件加载与路由
 12  agent-memory-ui/    ← 独立小应用（与 modules/agentmemory 互为拷贝）
```

### 插件加载机制（web-shell）

```js
// web-shell/src/plugin-loader/local-modules.js
export const localPluginModules = {
  ai:          () => import('../../../modules/ai/index.js'),
  feishu:      () => import('../../../modules/feishu/index.js'),
  workflow:    () => import('../../../modules/workflow/index.js'),
  datasource:  () => import('../../../modules/datasource/index.js'),
  rag:         () => import('../../../modules/rag/index.js'),
  tools:       () => import('../../../modules/tools/index.js'),
  agentmemory: () => import('../../../modules/agentmemory/index.js'),
  agenttrace:  () => import('../../../modules/agent-trace-ui/index.js')
}
```

调用链：`main.js → localPluginModules() + shellDefaultPlugins()` → `resolvePluginModules()` →
`mergeShellDefaultPlugins()`（`plugin-loader.js`，**有测试** `plugin-loader.test.mjs`）。

### 单例状态层（agent-harness-ui）

codegraph 的 `callers` 反查给出精确引用点：

| 单例 | 调用方数 | 引用位置 |
|---|---:|---|
| `useWorkspace` | **6** | `usePiSession.js:96`、`AppShell.vue:80`、`SessionSidebar.vue:176`、`SettingsPanel.vue` ×2、1 more |
| `usePiSession` | 4 | `AppShell.vue:80`、`MemoryPage.vue:135`、`RuntimeConsole.vue:180`、`SettingsPanel.vue:248` |
| `createHttpSessionDriver` | 1 | `RuntimeConsole.vue` |

两者都是 `let singleton = null` + 惰性初始化模式（`useWorkspace.js:218`）。

### 会话契约层（设计亮点）

`api/sessionDriver.js` 定义**8 种事件契约**（`SessionStatus` / `SessionEvent`），
`api/sessionDriverHttp.js` 是其 HTTP 实现，头注释说明了关键取舍：

> 当前 `POST /api/biz/ai/chat` 是**同步返回**，所以流式事件（`ASSISTANT_DELTA` 只到一次、
> `TOOL_*` 不会发出）以全量形态降级出现。这是**占位**而非妥协——后端补上 SSE
> （`api/harness.js` 的 `attachSse` 接入位）后，只需替换 `sendUserMessage`，
> 消费方 `usePiSession.onDriverEvent` **一行都不用改**。

三条契约纪律：① 失败也走事件（`RUN_FAILED`）不抛异常断状态机；② `cancelCurrentRun` 只取消传输层、
**不产生** `RUN_FAILED`（用户主动取消 ≠ 失败）；③ 空实现方法保留形状以通过 `checkDriverShape` 校验。

---

## 四、前后端对接核对（本次实测）

从两端提取端点做**双向比对**，结论：**全部对齐，无孤儿端点**。

前端 28 个端点 → 后端全部有 Controller 实现：

| 域 | 前端端点 | 后端 Controller |
|---|---|---|
| 会话 | `/api/biz/ai/chat`、`/api/biz/ai/observ/**`、`observ/sessions/{fork,resume,context}` | `/api/biz/ai`、`/api/biz/ai/observ` |
| 资源 | `/api/biz/ai/{agents,mcp-configs,skills,model-configs}` + `/{id}` | `/api/biz/ai/{agents,mcp-configs,skills,model-configs}` |
| 记忆 | `/api/agent-memory/arch/{configs,stats}`、`analytics/{messages,trace,session-stats,user-activity,distillation-stats}`、`file/config` | `/api/agent-memory/{arch,analytics,file}` |
| 记忆(旧) | `/api/ai/memory/{global,policy,session/{id}}`、`/api/ai/mcp` | `/api/ai/memory`、`/api/ai/mcp` |
| 其他 | `/api/auth/login`、`/api/biz/ds/datasources`、`/api/harness/stream` | `/api/auth`、`/api/biz/ds/datasources` |

后端另有 **30+ 个前端未调用的端点**（`/api/biz/feishu/**`、`/api/biz/rag`、`/api/biz/intent`、
`/api/biz/security`、`ab-tests`、`collab`、`api-docs`、`alerts/**` 等）——属于**已实现未接入 UI**
的能力，是前端可扩展的方向。

⚠️ 注意记忆域存在**两套并存端点**：`/api/agent-memory/*`（新，OLAP 分析向）与
`/api/ai/memory/*`（旧，会话/全局/策略向），前端两边都在用。

---

## 五、质量信号汇总（codegraph 爆炸半径标注）

### 有测试覆盖（可安全改动）

| 符号 | 位置 | 测试文件 |
|---|---|---|
| `withAgent` | `AiHarnessAgentRegistry:83` | 3 个（Registry / Routing / ChannelHandler） |
| `OltpMemoryRepository` | `storage/OltpMemoryRepository:14` | 5 个（EventLog / Phase2 / Trajectory / AsyncLogSync / +1） |
| `resolvePluginModules` | `web-shell/plugin-loader:6` | `plugin-loader.test.mjs` |
| `shellDefaultPlugins` | `web-shell/local-modules:12` | `plugin-loader.test.mjs` |

### 无测试覆盖（⚠️ 改动需人工验证）

| 符号 | 位置 | 引用数 |
|---|---|---:|
| `toAgent` / `saveAgent` | `AiAgentManagementService:617/662` | 2 / 3 |
| `oltp` / `olap` Bean | `AgentMemoryAutoConfiguration:120/125` | 1 / 1 |
| `usePiSession` | `agent-harness-ui/composables/usePiSession.js:91` | 4 |
| `useWorkspace` / `addWorkspace` | `composables/useWorkspace.js:218/149` | 6 / 1 |
| `SessionEvent` / `SessionStatus` | `api/sessionDriver.js:56/34` | 2 / 3 |
| `createHttpSessionDriver` | `api/sessionDriverHttp.js:35` | 1 |

**风险集中区**：前端 `agent-harness-ui/src/composables/` 与 `src/api/` 全部无测试覆盖，
但被 4-6 个组件依赖——这是应用的状态中枢。

---

## 六、值得注意的架构决策

1. **单例 + `v-show` 保活**：`usePiSession` / `useWorkspace` 是模块级单例（非 Pinia），
   配合 `v-show` 而非 `v-if` 切换表面，避免 Monaco 编辑器宿主 DOM 被销毁。
2. **契约先行**：`sessionDriver.js` 把「同步 HTTP」与「未来 SSE」的差异收敛在一个实现文件内，
   这是本次阅读中设计质量最高的一处。
3. **两套记忆端点并存**：`/api/agent-memory/*` 与 `/api/ai/memory/*` 职责边界需要产品层面确认。
4. **agent-memory-ui 与 modules/agentmemory 高度重叠**（`src/` 下 12 vs 9 文件）：存在双份维护成本，
   且 `agent-memory-ui` 曾是独立 git 仓库（已并入前端主仓库）。
5. **`ensureBackendAuth` 被 14 处调用**：静默登录（admin/admin）是全前端最热的鉴权入口，
   改动需评估所有资源页面。

---

## 七、如何复现本次阅读

```bash
# 后端索引（已存在，重建用）
cd D:/codehub/agent_runner && codegraph init

# 前端索引（本次新建）
cd D:/codehub/agent_runner/frontend && codegraph init

# 核心阅读命令
codegraph status                      # 索引统计 + 节点类型分布
codegraph explore "<自然语言问题>"     # 一次拿源码 + 调用路径 + 爆炸半径
codegraph callers <symbol>            # 反查引用点（含 file:line）
codegraph callees <symbol>            # 正查调用目标
codegraph impact <symbol>             # 影响面分析
```
