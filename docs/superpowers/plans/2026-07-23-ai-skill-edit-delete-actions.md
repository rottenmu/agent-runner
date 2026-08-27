# 智能体技能编辑删除功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在智能体管理的技能页面补齐自定义 API 技能的编辑、删除入口和交互验证，并保证创建技能时保存的 API 注册表信息可以编辑回显。

**Architecture:** 沿用当前前端源码级 AI 模块和后端已有技能 CRUD 接口。前端只对 `source = api` 的自定义技能展示编辑、删除操作，内置 Bean 技能继续只读；后端接口不新增，仅复用 `PUT /api/ai/skills/{name}` 和 `DELETE /api/ai/skills/{name}`。

**Tech Stack:** Vue 3、Element Plus、Vite、Node `node --test`、Spring Boot 3.4.5、Java 17。

## Global Constraints

- 本仓库以后生成或修改的 `.md` 文档默认使用中文。
- 不编辑 `target/`、`dist/` 等生成产物。
- 前端主壳端口固定为 `15200`，本任务不修改端口配置。
- 后端 Java 代码注释遵守 `docs/rules/BACKEND_JAVA_COMMENT_RULES.md`。
- 后端与通用代码行数规范遵守 `docs/rules/CODE_SIZE_RULES.md`。
- Service 层、Controller 层 Bean 采用普通 public 构造器注入，本任务不新增后端 Bean。
- 本任务不新增 SQLite、H2 或本地数据库配置。

---

## File Structure

- Modify: `frontend/modules/ai/src/views/AiAgentManage.vue`
  - 修复技能页相关中文文案。
  - 明确自定义 API 技能的编辑、删除操作区。
  - 抽出 `isApiSkill(skill)`，避免模板中反复硬编码来源判断。
  - 删除成功后同步移除本地智能体绑定。
- Modify: `frontend/modules/ai/tests/api-registry-selection.test.mjs`
  - 修复测试文件中文断言文案乱码，保留现有 API 注册表选择测试。
- Create: `frontend/modules/ai/tests/skill-edit-delete-actions.test.mjs`
  - 用静态测试锁定技能页编辑、删除入口、API 封装和只读边界。
- Existing: `frontend/modules/ai/src/api/agent.js`
  - 已存在 `updateSkill(name, data)` 和 `deleteSkill(name)`，本任务只验证不改动。

---

### Task 1: 技能编辑删除静态测试

**Files:**
- Create: `frontend/modules/ai/tests/skill-edit-delete-actions.test.mjs`
- Read: `frontend/modules/ai/src/views/AiAgentManage.vue`
- Read: `frontend/modules/ai/src/api/agent.js`

**Interfaces:**
- Consumes: `openEditSkill(skill)`、`removeSkill(skill)`、`updateSkill(name, data)`、`deleteSkill(name)`。
- Produces: 静态测试约束，保证自定义 API 技能有可见编辑、删除入口，内置技能不暴露危险操作。

- [ ] **Step 1: Write the failing test**

Create `frontend/modules/ai/tests/skill-edit-delete-actions.test.mjs`:

```javascript
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')
const pageSource = readFileSync(join(root, 'src', 'views', 'AiAgentManage.vue'), 'utf8')
const apiSource = readFileSync(join(root, 'src', 'api', 'agent.js'), 'utf8')

assert.match(apiSource, /export function updateSkill\(name,\s*data\)/, '技能编辑接口封装必须存在')
assert.match(apiSource, /request\.put\(`\/ai\/skills\/\$\{encodeURIComponent\(name\)\}`,\s*data\)/, '技能编辑必须调用 PUT /ai/skills/{name}')
assert.match(apiSource, /export function deleteSkill\(name\)/, '技能删除接口封装必须存在')
assert.match(apiSource, /request\.delete\(`\/ai\/skills\/\$\{encodeURIComponent\(name\)\}`\)/, '技能删除必须调用 DELETE /ai/skills/{name}')

assert.match(pageSource, /function isApiSkill\(skill\)/, '页面必须集中判断 API 自定义技能')
assert.match(pageSource, /v-if="isApiSkill\(skill\)"/, '仅 API 技能展示编辑删除操作区')
assert.match(pageSource, /@click(?:\.stop)?="openEditSkill\(skill\)"/, '技能卡片必须提供编辑入口')
assert.match(pageSource, /@click(?:\.stop)?="removeSkill\(skill\)"/, '技能卡片必须提供删除入口')
assert.match(pageSource, /editingSkillName \? await updateSkill/, '编辑技能时必须调用 updateSkill')
assert.match(pageSource, /:disabled="Boolean\(editingSkillName\)"/, '编辑技能时技能名称必须不可修改')
assert.match(pageSource, /skill\.referenceCount/, '删除提示必须考虑技能引用数量')
assert.match(pageSource, /将同步从已绑定的智能体中移除/, '删除确认必须说明绑定关系会同步移除')
assert.match(pageSource, /agents\.value = agents\.value\.map/, '删除成功后必须同步清理本地智能体绑定')
assert.doesNotMatch(pageSource, buildMojibakePattern(), '技能管理页面不能保留中文乱码文案')
```

其中 `buildMojibakePattern()` 使用 Unicode 码点运行时构造疑似乱码 token，测试文件内不直接写入乱码样本。
- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
node --test frontend/modules/ai/tests/skill-edit-delete-actions.test.mjs
```

Expected: FAIL because `isApiSkill(skill)` and corrected Chinese copy are not fully present yet.

---

### Task 2: 修复技能页编辑删除交互

**Files:**
- Modify: `frontend/modules/ai/src/views/AiAgentManage.vue`

**Interfaces:**
- Consumes: `updateSkill(name, data)`、`deleteSkill(name)`、`mergeRegistryApiConfig(original, api)`。
- Produces:
  - `function isApiSkill(skill): boolean`
  - `function openEditSkill(skill): void`
  - `async function removeSkill(skill): Promise<void>`

- [ ] **Step 1: Add the source helper**

Add this helper near `promptTemplateName(id)`:

```javascript
function isApiSkill(skill) {
  return skill?.source === 'api'
}
```

- [ ] **Step 2: Update skill card action area**

Replace the skill card footer action condition with:

```vue
<div v-if="isApiSkill(skill)" class="skill-card__actions">
  <el-button text size="small" @click.stop="openEditSkill(skill)">编辑</el-button>
  <el-button text size="small" type="danger" @click.stop="removeSkill(skill)">删除</el-button>
</div>
<span v-else class="skill-card__readonly">内置技能</span>
```

- [ ] **Step 3: Keep edit form name immutable**

Ensure the skill name input remains:

```vue
<el-input
  v-model="skillForm.name"
  :disabled="Boolean(editingSkillName)"
  placeholder="例如：remote_supplier_lookup"
/>
```

- [ ] **Step 4: Preserve API registry data on edit**

Ensure `openEditSkill(skill)` resets `apiRegistryId` from persisted data:

```javascript
apiConfig: {
  apiRegistryId: skill.apiConfig?.apiRegistryId || null,
  enabled: skill.enabled !== false,
  baseUrl: skill.apiConfig?.baseUrl || '',
  path: skill.apiConfig?.path || '',
  method: skill.apiConfig?.method || 'POST',
  headers: skill.apiConfig?.headers || {},
  timeoutMillis: skill.apiConfig?.timeoutMillis || 3000
}
```

- [ ] **Step 5: Improve delete confirmation**

Replace `removeSkill(skill)` with:

```javascript
async function removeSkill(skill) {
  const referenceText = Number(skill.referenceCount || 0) > 0
    ? `该技能当前被 ${skill.referenceCount} 个智能体引用，删除后将同步从已绑定的智能体中移除。`
    : '该技能未被智能体引用。'
  try {
    await ElMessageBox.confirm(
      `确认删除技能「${skill.name}」？${referenceText}`,
      '删除确认',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  await deleteSkill(skill.name)
  skills.value = skills.value.filter(item => item.name !== skill.name)
  agents.value = agents.value.map(agent => ({
    ...agent,
    skillIds: agent.skillIds.filter(name => name !== skill.name)
  }))
  ElMessage.success('已删除')
}
```

- [ ] **Step 6: Fix Chinese copy in the touched page**

Replace visible garbled Chinese strings in `AiAgentManage.vue` with readable Chinese, including:

```text
智能体与技能
管理你的 AI 能力配置
智能体
技能
提示词管理
所有资源绑定当前用户
搜索
新建智能体
新建技能
暂无智能体
暂无技能
保存成功
已启用
已停用
已删除
绑定成功
请求头 JSON 必须是对象
请求头 JSON 格式不正确
```

- [ ] **Step 7: Run the test to verify it passes**

Run:

```bash
node --test frontend/modules/ai/tests/skill-edit-delete-actions.test.mjs
```

Expected: PASS.

---

### Task 3: 修复并保留 API 注册表选择测试

**Files:**
- Modify: `frontend/modules/ai/tests/api-registry-selection.test.mjs`

**Interfaces:**
- Consumes: `buildApiRegistryQueryParams(keyword)`、`createApiRegistryQuery(request)`、`mergeRegistryApiConfig(original, api)`、`createApiRegistrySearchController(options)`。
- Produces: 可读中文断言文案，继续覆盖创建和编辑技能时 API 注册表选择逻辑。

- [ ] **Step 1: Rewrite garbled assertion copy only**

Keep the same assertions and replace garbled strings with readable Chinese examples:

```javascript
assert.deepEqual(buildApiRegistryQueryParams(' 项目 '), { status: 1, keyword: '项目' })
await queryEnabledApiRegistry('  库存  ')
assert.deepEqual(calls, [{
  path: '/biz/sys/api-registry',
  options: { params: { status: 1, keyword: '库存' } }
}])
```

- [ ] **Step 2: Preserve API registry ID behavior**

Ensure the test still includes:

```javascript
const linkedMerged = mergeRegistryApiConfig(original, {
  id: 1001,
  path: '/api/biz/project/list',
  method: 'get'
})
assert.equal(linkedMerged.apiRegistryId, 1001)
```

- [ ] **Step 3: Run focused frontend tests**

Run:

```bash
node --test frontend/modules/ai/tests/api-registry-selection.test.mjs frontend/modules/ai/tests/skill-edit-delete-actions.test.mjs
```

Expected: PASS.

---

### Task 4: 前端构建验证

**Files:**
- Read: `frontend/web-shell/package.json`
- Build: `frontend/web-shell`

**Interfaces:**
- Consumes: AI 前端模块导出的 Vue 页面。
- Produces: 可打包的前端主壳。

- [ ] **Step 1: Run module tests**

Run:

```bash
node --test frontend/modules/ai/tests/api-registry-selection.test.mjs frontend/modules/ai/tests/current-user-static.test.mjs frontend/modules/ai/tests/prompt-template-static.test.mjs frontend/modules/ai/tests/skill-edit-delete-actions.test.mjs
```

Expected: PASS.

- [ ] **Step 2: Run web-shell build**

Run from `frontend/web-shell`:

```bash
npm run build
```

Expected: build succeeds. Existing bundle-size warnings are acceptable if no new errors appear.

- [ ] **Step 3: Review changed files**

Run:

```bash
git diff -- frontend/modules/ai/src/views/AiAgentManage.vue frontend/modules/ai/tests/api-registry-selection.test.mjs frontend/modules/ai/tests/skill-edit-delete-actions.test.mjs docs/superpowers/specs/2026-07-23-ai-skill-edit-delete-actions-design.md docs/superpowers/plans/2026-07-23-ai-skill-edit-delete-actions.md
```

Expected: diff only includes the planned AI skill edit/delete UI, Chinese copy/test cleanup, and docs.

---

## Self-Review

- Spec coverage: 创建技能 API 注册表信息、编辑回显、删除同步解除绑定、内置技能只读、中文文案修复、前端验证均已映射到任务。
- Placeholder scan: 本计划没有 TBD、TODO 或未定义的“后续处理”占位语。
- Type consistency: 计划中使用的 `apiRegistryId`、`openEditSkill`、`removeSkill`、`updateSkill`、`deleteSkill` 均与当前代码命名一致。
