# 智能体技能选择注册接口实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在智能体技能创建抽屉中搜索平台已注册 API，选择后自动回填技能的 `path` 和 `method`，同时保留手工维护 `baseUrl` 的能力。

**Architecture:** AI 前端模块直接调用系统模块公开的 `/api/biz/sys/api-registry` 契约，不导入系统前端插件源码。注册接口选择组件负责远程搜索和候选项展示，纯函数负责统一响应解包与配置快照合并，管理页面只接收选中事件并更新技能表单。

**Tech Stack:** Vue 3 Composition API、Element Plus、Axios、Node.js 内置测试运行器、Vite 5。

## Global Constraints

- 前端开发端口固定为 `15200`，禁止修改。
- API 基础地址继续由用户手工填写，禁止写死后端端口或环境地址。
- 只查询 `status=1` 的接口，并在前端排除 `deprecated=true` 的接口。
- 选择注册接口只覆盖 `apiConfig.path` 和 `apiConfig.method`。
- 不保存 `api_registry.id`，不修改后端技能 DTO、数据库结构和技能执行器。
- AI 前端不得导入 `frontend/modules/sys` 源码。
- 公共组件有效代码不超过 300 行，前端单个函数有效代码不超过 50 行。
- 当前工作区已有未提交修改；实施前后均不得覆盖或回退与本功能无关的改动。

---

## 文件结构

- Create: `frontend/modules/ai/src/api/api-registry.js`
  - AI 模块自己的 API 注册信息查询适配器。
- Create: `frontend/modules/ai/src/utils/api-registry.js`
  - 响应校验、候选项过滤、展示文本和技能配置合并纯函数。
- Create: `frontend/modules/ai/src/components/AiApiRegistrySelect.vue`
  - 注册接口远程搜索选择组件。
- Create: `frontend/modules/ai/tests/api-registry-selection.test.mjs`
  - 纯函数行为和前端静态契约测试。
- Modify: `frontend/modules/ai/src/views/AiAgentManage.vue`
  - 接入选择组件，并将选中接口回填到技能表单。

---

### Task 1: API 查询与配置快照规则

**Files:**
- Create: `frontend/modules/ai/src/api/api-registry.js`
- Create: `frontend/modules/ai/src/utils/api-registry.js`
- Create: `frontend/modules/ai/tests/api-registry-selection.test.mjs`

**Interfaces:**
- Produces: `queryEnabledApiRegistry(keyword: string): Promise<object>`
- Produces: `normalizeApiRegistryResponse(response: object): object[]`
- Produces: `mergeRegistryApiConfig(apiConfig: object, api: object): object`
- Produces: `formatApiRegistryOption(api: object): string`

- [ ] **Step 1: 编写失败测试，固定查询契约和回填边界**

创建 `frontend/modules/ai/tests/api-registry-selection.test.mjs`：

```js
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import {
  formatApiRegistryOption,
  mergeRegistryApiConfig,
  normalizeApiRegistryResponse
} from '../src/utils/api-registry.js'

const root = resolve(import.meta.dirname, '..')
const apiSource = readFileSync(join(root, 'src', 'api', 'api-registry.js'), 'utf8')

assert.match(apiSource, /\/biz\/sys\/api-registry/, '应调用系统 API 注册信息接口')
assert.match(apiSource, /status:\s*1/, '查询应固定限定启用状态')

const rows = normalizeApiRegistryResponse({
  code: 200,
  data: [
    { id: 1, status: 1, deprecated: false, method: 'get', path: '/api/biz/project/list' },
    { id: 2, status: 1, deprecated: true, method: 'GET', path: '/deprecated' },
    { id: 3, status: 0, deprecated: false, method: 'POST', path: '/disabled' }
  ]
})
assert.deepEqual(rows.map(item => item.id), [1])
assert.throws(
  () => normalizeApiRegistryResponse({ code: 403, msg: '无权限' }),
  /无权限/
)

const original = {
  enabled: true,
  baseUrl: 'http://backend.example.com',
  path: '/old',
  method: 'POST',
  headers: { Authorization: 'token' },
  timeoutMillis: 5000
}
const merged = mergeRegistryApiConfig(original, {
  path: '/api/biz/project/list',
  method: 'get'
})
assert.equal(merged.path, '/api/biz/project/list')
assert.equal(merged.method, 'GET')
assert.equal(merged.baseUrl, original.baseUrl)
assert.equal(merged.headers, original.headers)
assert.equal(merged.timeoutMillis, original.timeoutMillis)
assert.equal(original.path, '/old', '合并函数不得修改原对象')

assert.equal(
  formatApiRegistryOption({
    moduleName: '项目管理',
    method: 'GET',
    path: '/api/biz/project/list',
    summary: '查询项目列表'
  }),
  '项目管理 · GET · /api/biz/project/list · 查询项目列表'
)
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
node frontend/modules/ai/tests/api-registry-selection.test.mjs
```

Expected: FAIL，提示无法找到 `src/utils/api-registry.js` 或 `src/api/api-registry.js`。

- [ ] **Step 3: 实现最小 API 查询适配器**

创建 `frontend/modules/ai/src/api/api-registry.js`：

```js
import request from '../../../../web-shell/src/api/request'

export function queryEnabledApiRegistry(keyword = '') {
  const normalizedKeyword = String(keyword || '').trim()
  const params = { status: 1 }
  if (normalizedKeyword) {
    params.keyword = normalizedKeyword
  }
  return request.get('/biz/sys/api-registry', { params })
}
```

- [ ] **Step 4: 实现响应规范化与配置合并纯函数**

创建 `frontend/modules/ai/src/utils/api-registry.js`：

```js
export function normalizeApiRegistryResponse(response) {
  if (response?.code !== 200) {
    throw new Error(response?.msg || 'API 注册信息加载失败')
  }
  const rows = Array.isArray(response.data) ? response.data : []
  return rows.filter(isSelectableApi)
}

export function mergeRegistryApiConfig(apiConfig = {}, api = {}) {
  return {
    ...apiConfig,
    path: String(api.path || '').trim(),
    method: String(api.method || 'GET').trim().toUpperCase()
  }
}

export function formatApiRegistryOption(api = {}) {
  return [api.moduleName || api.moduleCode, api.method, api.path, api.summary]
    .filter(Boolean)
    .join(' · ')
}

function isSelectableApi(api) {
  return api?.status === 1
    && api.deprecated !== true
    && Boolean(String(api.path || '').trim())
    && Boolean(String(api.method || '').trim())
}
```

- [ ] **Step 5: 运行测试并确认通过**

Run:

```powershell
node frontend/modules/ai/tests/api-registry-selection.test.mjs
```

Expected: PASS，进程退出码为 `0`。

- [ ] **Step 6: 提交该任务**

```powershell
git add frontend/modules/ai/src/api/api-registry.js frontend/modules/ai/src/utils/api-registry.js frontend/modules/ai/tests/api-registry-selection.test.mjs
git commit -m "feat: 增加技能注册接口查询规则"
```

---

### Task 2: 注册接口远程搜索组件

**Files:**
- Create: `frontend/modules/ai/src/components/AiApiRegistrySelect.vue`
- Modify: `frontend/modules/ai/tests/api-registry-selection.test.mjs`

**Interfaces:**
- Consumes: `queryEnabledApiRegistry(keyword)`
- Consumes: `normalizeApiRegistryResponse(response)`
- Consumes: `formatApiRegistryOption(api)`
- Produces: Vue 事件 `select(api: object)`

- [ ] **Step 1: 扩展失败测试，固定组件交互契约**

向测试文件追加：

```js
const pickerSource = readFileSync(
  join(root, 'src', 'components', 'AiApiRegistrySelect.vue'),
  'utf8'
)
const pickerLineCount = pickerSource.split(/\apiResponse?\n/).length

assert.ok(pickerLineCount <= 300, `接口选择组件不得超过 300 行，当前 ${pickerLineCount} 行`)
assert.match(pickerSource, /queryEnabledApiRegistry/, '组件应查询启用接口')
assert.match(pickerSource, /remote-method/, '组件应支持远程搜索')
assert.match(pickerSource, /visible-change/, '首次展开时应加载接口')
assert.match(pickerSource, /emit\('select',\s*selected\)/, '选中后应向父组件返回接口对象')
assert.match(pickerSource, /250/, '搜索应使用短延迟防抖')
assert.match(pickerSource, /requestSequence/, '组件应防止旧响应覆盖新结果')
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
node frontend/modules/ai/tests/api-registry-selection.test.mjs
```

Expected: FAIL，提示找不到 `AiApiRegistrySelect.vue`。

- [ ] **Step 3: 实现独立选择组件**

创建 `frontend/modules/ai/src/components/AiApiRegistrySelect.vue`。组件必须包含以下核心模板：

```vue
<template>
  <el-select
    v-model="selectedId"
    class="api-registry-select"
    clearable
    filterable
    remote
    :loading="loading"
    :remote-method="scheduleSearch"
    placeholder="搜索并选择已注册接口"
    @visible-change="handleVisibleChange"
    @change="handleChange"
  >
    <el-option
      v-for="api in options"
      :key="api.id"
      :label="formatApiRegistryOption(api)"
      :value="api.id"
    />
  </el-select>
</template>
```

脚本使用递增序号处理并发，只接受最后一次查询结果：

```vue
<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { queryEnabledApiRegistry } from '../api/api-registry'
import {
  formatApiRegistryOption,
  normalizeApiRegistryResponse
} from '../utils/api-registry'

const emit = defineEmits(['select'])
const selectedId = ref(null)
const options = ref([])
const loading = ref(false)
let debounceTimer
let requestSequence = 0

function scheduleSearch(keyword) {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => loadOptions(keyword), 250)
}

function handleVisibleChange(visible) {
  if (visible && options.value.length === 0) {
    loadOptions('')
  }
}

function handleChange(id) {
  if (id == null) return
  const selected = options.value.find(api => api.id === id)
  if (selected) emit('select', selected)
}

async function loadOptions(keyword) {
  const sequence = ++requestSequence
  loading.value = true
  try {
    const response = await queryEnabledApiRegistry(keyword)
    if (sequence === requestSequence) {
      options.value = normalizeApiRegistryResponse(response)
    }
  } catch (error) {
    if (sequence === requestSequence) {
      options.value = []
      ElMessage.error(error?.message || 'API 注册信息加载失败')
    }
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

onBeforeUnmount(() => clearTimeout(debounceTimer))
</script>
```

样式只负责稳定宽度和下拉选项文本布局，不增加装饰性卡片：

```vue
<style scoped>
.api-registry-select {
  width: 100%;
}
</style>
```

- [ ] **Step 4: 运行测试并确认通过**

Run:

```powershell
node frontend/modules/ai/tests/api-registry-selection.test.mjs
```

Expected: PASS，进程退出码为 `0`。

- [ ] **Step 5: 提交该任务**

```powershell
git add frontend/modules/ai/src/components/AiApiRegistrySelect.vue frontend/modules/ai/tests/api-registry-selection.test.mjs
git commit -m "feat: 增加技能注册接口选择组件"
```

---

### Task 3: 技能抽屉接入与完整验证

**Files:**
- Modify: `frontend/modules/ai/src/views/AiAgentManage.vue`
- Modify: `frontend/modules/ai/tests/api-registry-selection.test.mjs`

**Interfaces:**
- Consumes: `<AiApiRegistrySelect @select="applyRegisteredApi" />`
- Consumes: `mergeRegistryApiConfig(apiConfig, api)`

- [ ] **Step 1: 扩展失败测试，固定页面接入行为**

向测试文件追加：

```js
const agentPage = readFileSync(join(root, 'src', 'views', 'AiAgentManage.vue'), 'utf8')

assert.match(agentPage, /AiApiRegistrySelect/, '技能抽屉应接入注册接口选择组件')
assert.match(agentPage, /@select="applyRegisteredApi"/, '页面应监听注册接口选择事件')
assert.match(agentPage, /mergeRegistryApiConfig/, '页面应通过纯函数合并接口配置')
assert.match(agentPage, /skillRegistryPickerKey/, '每次打开技能抽屉应重置接口选择状态')
assert.match(
  agentPage,
  /Object\.assign\(skillForm\.apiConfig,\s*mergeRegistryApiConfig/,
  '页面应只更新技能 API 配置对象'
)
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
node frontend/modules/ai/tests/api-registry-selection.test.mjs
```

Expected: FAIL，提示 `AiAgentManage.vue` 尚未接入选择组件。

- [ ] **Step 3: 在技能抽屉中接入组件**

在“启用状态”和“API 基础地址”之间加入：

```vue
<label class="agent-form__field">
  <span>选择注册接口</span>
  <AiApiRegistrySelect
    :key="skillRegistryPickerKey"
    @select="applyRegisteredApi"
  />
</label>
```

在 `<script setup>` 中增加导入：

```js
import AiApiRegistrySelect from '../components/AiApiRegistrySelect.vue'
import { mergeRegistryApiConfig } from '../utils/api-registry'
```

在现有技能表单状态附近增加：

```js
const skillRegistryPickerKey = ref(0)
```

在 `openCreateSkill()` 和 `openEditSkill(skill)` 中，每次打开抽屉前执行：

```js
skillRegistryPickerKey.value += 1
```

新增回填方法：

```js
function applyRegisteredApi(api) {
  Object.assign(
    skillForm.apiConfig,
    mergeRegistryApiConfig(skillForm.apiConfig, api)
  )
}
```

该方法不得直接赋值 `baseUrl`、`headers` 或 `timeoutMillis`。

- [ ] **Step 4: 运行 AI 模块全部静态测试**

Run:

```powershell
Get-ChildItem frontend/modules/ai/tests/*.test.mjs | ForEach-Object { node $_.FullName; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }
```

Expected: 所有测试进程退出码为 `0`，包括新增的注册接口选择测试和既有提示词、当前用户测试。

- [ ] **Step 5: 构建前端主壳**

Run:

```powershell
npm run build
```

Workdir: `frontend/web-shell`

Expected: Vite 构建成功，无 Vue 编译错误；不得修改开发服务器端口 `15200`。

- [ ] **Step 6: 检查改动范围和规则**

Run:

```powershell
git diff --check
git diff --stat -- frontend/modules/ai
(Get-Content frontend/modules/ai/src/components/AiApiRegistrySelect.vue).Count
```

Expected:

- `git diff --check` 无新增空白错误。
- 改动仅位于计划列出的 AI 前端文件。
- `AiApiRegistrySelect.vue` 不超过 300 行。

- [ ] **Step 7: 提交页面接入**

```powershell
git add frontend/modules/ai/src/views/AiAgentManage.vue frontend/modules/ai/tests/api-registry-selection.test.mjs
git commit -m "feat: 技能创建支持选择注册接口"
```

---

## 最终验收

- [ ] 新建技能时可以搜索已启用的 API 注册信息。
- [ ] 候选项不包含停用或废弃接口。
- [ ] 选择接口后只自动填充 `path` 和 `method`。
- [ ] `baseUrl`、请求头和超时配置保持不变。
- [ ] 清空选择不会清空已回填的技能配置。
- [ ] 查询失败时仍能手工填写并保存技能。
- [ ] 不修改后端、数据库和前端端口配置。
- [ ] AI 模块测试与主壳构建全部通过。
