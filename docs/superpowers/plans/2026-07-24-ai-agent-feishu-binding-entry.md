# 智能体管理页飞书渠道绑定入口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 移除 AI 模块左侧独立的“飞书渠道绑定”菜单，并在智能体管理页“新建智能体”按钮右侧增加跳转入口。

**Architecture:** 保留现有 `/ai/feishu-bindings` 路由和绑定管理页面，只调整菜单配置与智能体管理页顶部操作区。智能体管理页通过 Vue Router 执行站内跳转，不复制或嵌入飞书绑定业务逻辑。

**Tech Stack:** Vue 3、Vue Router、Element Plus、Node.js test runner、Vite

## Global Constraints

- 仅调整前端入口与导航，不修改飞书绑定 API、后端 Service、DAO 或数据库。
- 从 `frontend/modules/ai/menus.js` 移除“飞书渠道绑定”菜单。
- 保留 `/ai/feishu-bindings` 路由及 `AiFeishuBindingManage.vue` 页面。
- “绑定飞书渠道”按钮仅在智能体标签页显示，并位于“新建智能体”按钮右侧。
- 智能体列表空状态区域不增加第二个飞书绑定入口。
- 不编辑或提交 `dist/` 等构建产物。
- 前端代码行数遵守 `docs/rules/CODE_SIZE_RULES.md`。

---

## 文件结构

### 修改文件

- `frontend/modules/ai/menus.js`：移除独立菜单项。
- `frontend/modules/ai/src/views/AiAgentManage.vue`：增加按钮、Vue Router 实例和跳转处理函数。
- `frontend/modules/ai/tests/feishu-binding-static.test.mjs`：更新菜单、路由和按钮跳转静态契约。

### 保留不变

- `frontend/modules/ai/routes.js`：继续注册 `/ai/feishu-bindings`。
- `frontend/modules/ai/src/views/AiFeishuBindingManage.vue`：继续承载绑定列表及新增、编辑、删除功能。
- `frontend/modules/ai/src/components/AiFeishuBindingDialog.vue`：继续承载绑定表单。

---

### Task 1: 将飞书绑定入口迁移到智能体管理页

**Files:**

- Modify: `frontend/modules/ai/menus.js`
- Modify: `frontend/modules/ai/src/views/AiAgentManage.vue`
- Test: `frontend/modules/ai/tests/feishu-binding-static.test.mjs`

**Interfaces:**

- Consumes: Vue Router 的 `useRouter(): Router`。
- Consumes: 现有路由 `/ai/feishu-bindings`。
- Produces: `openFeishuBindings(): void`，调用 `router.push('/ai/feishu-bindings')`。
- Produces: 仅在 `activeTab === 'agents'` 时显示的“绑定飞书渠道”按钮。

- [ ] **Step 1: 编写入口迁移失败测试**

在 `frontend/modules/ai/tests/feishu-binding-static.test.mjs` 中读取智能体页面：

```javascript
const agentPage = readModuleFile('src', 'views', 'AiAgentManage.vue')
```

将原菜单断言：

```javascript
assert.match(menus, /\/ai\/feishu-bindings/)
assert.match(menus, /飞书渠道绑定/)
assert.ok(
  menus.indexOf('/ai/agents') < menus.indexOf('/ai/feishu-bindings')
    && menus.indexOf('/ai/feishu-bindings') < menus.indexOf('/ai/model-configs'),
  '飞书渠道绑定菜单应位于智能体管理之后、模型配置管理之前'
)
```

替换为：

```javascript
assert.doesNotMatch(menus, /\/ai\/feishu-bindings/)
assert.doesNotMatch(menus, /飞书渠道绑定/)
```

保留现有路由断言，并增加：

```javascript
assert.match(agentPage, /useRouter/)
assert.match(agentPage, /绑定飞书渠道/)
assert.match(
  agentPage,
  /v-if="activeTab === 'agents'"[^>]*@click="openFeishuBindings"/
)
assert.match(agentPage, /function openFeishuBindings\(\)/)
assert.match(agentPage, /router\.push\('\/ai\/feishu-bindings'\)/)
assert.ok(
  agentPage.indexOf('新建智能体') < agentPage.indexOf('绑定飞书渠道'),
  '绑定飞书渠道按钮应位于新建智能体按钮右侧'
)
```

- [ ] **Step 2: 运行测试并确认 RED**

Run:

```text
node --test frontend/modules/ai/tests/feishu-binding-static.test.mjs
```

Expected: FAIL，菜单仍包含 `/ai/feishu-bindings`，且智能体页面尚未包含 `openFeishuBindings`。

- [ ] **Step 3: 移除独立菜单项**

将 `frontend/modules/ai/menus.js` 调整为：

```javascript
export const menus = [
  { path: '/ai/workbench', title: '智能体对话' },
  { path: '/ai/agents', title: '智能体管理' },
  { path: '/ai/model-configs', title: '模型配置管理' }
]
```

不要修改 `frontend/modules/ai/routes.js`。

- [ ] **Step 4: 在智能体页面增加跳转按钮**

在 `AiAgentManage.vue` 的 Vue 导入后增加：

```javascript
import { useRouter } from 'vue-router'
```

在页面状态声明前创建 Router：

```javascript
const router = useRouter()
```

在顶部操作区的“新建智能体”按钮后增加：

```vue
<el-button v-if="activeTab === 'agents'" @click="openFeishuBindings">
  绑定飞书渠道
</el-button>
```

空状态区域保持不变，不增加该按钮。

在 `openCreate` 附近增加：

```javascript
function openFeishuBindings() {
  router.push('/ai/feishu-bindings')
}
```

- [ ] **Step 5: 运行目标测试并确认 GREEN**

Run:

```text
node --test frontend/modules/ai/tests/feishu-binding-static.test.mjs
```

Expected: PASS，菜单移除、路由保留、按钮位置和跳转契约全部满足。

- [ ] **Step 6: 运行 AI 模块全量测试**

Run:

```text
node --test frontend/modules/ai/tests/*.test.mjs
```

Expected: AI 模块全部测试 PASS。

- [ ] **Step 7: 运行前端生产构建**

Working directory: `frontend/web-shell`

Run:

```text
npm run build
```

Expected: Vite 构建成功；既有第三方 PURE 注释和大 chunk 警告允许保留，但不得出现编译错误。

- [ ] **Step 8: 检查改动边界**

Run:

```text
git diff --check
git diff -- frontend/modules/ai/menus.js frontend/modules/ai/routes.js frontend/modules/ai/src/views/AiAgentManage.vue frontend/modules/ai/tests/feishu-binding-static.test.mjs
git status --short
```

Expected:

- `routes.js` 无改动；
- 不包含 API、后端、SQL 或数据库改动；
- `dist/` 未进入 Git 改动；
- 无空白错误。

- [ ] **Step 9: 请求代码审查**

只读审查必须确认：

```text
飞书渠道绑定菜单已移除；
/ai/feishu-bindings 路由仍保留；
按钮只在智能体标签页显示；
按钮位于新建智能体按钮右侧；
空状态区域没有重复入口；
未复制飞书绑定业务逻辑；
未修改后端和数据库。
```

Critical 和 Important 问题修复后，重新运行 Steps 5-7。

- [ ] **Step 10: 保留实现改动待用户统一提交**

当前 `menus.js`、`AiAgentManage.vue` 和飞书绑定测试已包含本轮开始前的未提交改动，执行时不得把这些既有改动混入自动提交。完成验证后只汇报本次修改范围和测试结果，不执行 `git add` 或 `git commit`；由用户决定后续统一提交方式。
