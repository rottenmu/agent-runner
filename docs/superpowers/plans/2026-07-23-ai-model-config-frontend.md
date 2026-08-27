# 智能体模型配置管理前端 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `frontend/modules/ai` 中新增“模型配置管理”页面，集中维护阿里云百炼 DashScope 和自定义大模型配置。

**Architecture:** 该能力作为 AI 前端插件页面接入主壳，通过 `/ai/model-configs` 路由访问，不创建独立 Vite 应用。页面使用 Element Plus 展示列表、模板、抽屉表单，模型清单、localStorage 存储和连接测试拆成独立小文件，避免把逻辑继续堆到 `AiAgentManage.vue`。

**Tech Stack:** Vue 3 `<script setup>`、Element Plus、Vite、Node.js `node:test`、浏览器 `localStorage`。

## Global Constraints

- 设计依据：`docs/superpowers/specs/2026-07-23-ai-model-config-frontend-design.md`。
- 页面必须落在 `frontend/modules/ai`，不落在 `frontend/modules/sys`。
- 页面路由固定为 `/ai/model-configs`。
- 路由名称固定为 `AiModelConfigManage`。
- 菜单名称固定为 `模型配置管理`。
- 前端开发服务端口固定为 `15200`，不得修改 `frontend/web-shell/vite.config.js` 或任何前端端口配置。
- 不创建独立 Vite 项目，不新增 `package.json`。
- 不新增后端接口，不新增数据库表。
- 持久化使用浏览器 `localStorage`。
- 支持环境：`dev`、`staging`、`prod`。
- 阿里云百炼 Endpoint 固定为 `https://dashscope.aliyuncs.com/compatible-mode/v1`。
- 导出 JSON 时 API Key 必须脱敏为 `***`。
- 页面级 `.vue` 文件有效代码不超过 `500` 行，JS 工具文件不超过 `200` 行，单个函数不超过 `50` 行。
- 不编辑 `target/`、`dist/` 等生成产物。

---

## File Structure

- Create: `frontend/modules/ai/src/model-config/bailianModels.js`
  - 负责提供商、百炼 Endpoint、百炼模型分组和扁平模型 ID。
- Create: `frontend/modules/ai/src/model-config/modelConfigStore.js`
  - 负责配置示例数据、localStorage 版本初始化、CRUD 辅助、筛选、导入导出脱敏。
- Create: `frontend/modules/ai/src/model-config/connectionTest.js`
  - 负责前端模拟连接测试，返回延迟、状态和结果详情。
- Create: `frontend/modules/ai/src/views/AiModelConfigManage.vue`
  - 负责页面布局、配置列表、模型模板、抽屉表单、导入导出交互。
- Create: `frontend/modules/ai/tests/model-config-static.test.mjs`
  - 负责静态验证菜单、路由、模型清单、示例数据、导出脱敏和页面关键交互锚点。
- Modify: `frontend/modules/ai/menus.js`
  - 新增 AI 插件一级菜单“模型配置管理”。
- Modify: `frontend/modules/ai/routes.js`
  - 新增 `/ai/model-configs` 路由。

---

### Task 1: 锁定模型配置页面契约测试

**Files:**
- Create: `frontend/modules/ai/tests/model-config-static.test.mjs`

**Interfaces:**
- Consumes: 计划中的 `bailianModels.js`、`modelConfigStore.js`、`menus.js`、`routes.js`、`AiModelConfigManage.vue`。
- Produces: 可重复运行的静态测试，后续任务必须让该测试通过。

- [ ] **Step 1: Write the failing test**

Create `frontend/modules/ai/tests/model-config-static.test.mjs` with this content:

```js
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')

function readModuleFile(...parts) {
  const file = join(root, ...parts)
  assert.ok(existsSync(file), `文件应存在：${parts.join('/')}`)
  return readFileSync(file, 'utf8')
}

const menus = readModuleFile('menus.js')
const routes = readModuleFile('routes.js')
const page = readModuleFile('src', 'views', 'AiModelConfigManage.vue')

const {
  BAILIAN_ENDPOINT,
  BAILIAN_MODEL_GROUPS,
  flatBailianModelIds
} = await import('../src/model-config/bailianModels.js')

const {
  MODEL_CONFIG_STORAGE_KEY,
  MODEL_CONFIG_STORAGE_VERSION,
  createDefaultConfigs,
  exportConfigsForJson,
  normalizeImportedConfigs
} = await import('../src/model-config/modelConfigStore.js')

const requiredModels = [
  'qwen3.8-max-preview',
  'qwen3.7-max',
  'qwen3.7-plus',
  'qwen3.6-plus',
  'qwen3.6-flash',
  'qwen3-max',
  'qwen-plus',
  'qwen-turbo',
  'qwen-long',
  'qwen3-coder-next',
  'qwen3-vl-max',
  'qwen3-vl-plus',
  'qwen3.5-omni-plus',
  'qwen3.5-omni-plus-realtime',
  'qwen3.5-122b-a10b',
  'qwen3.5-32b',
  'qwen3.5-7b',
  'text-embedding-v4',
  'tongyi-embedding-vision-plus',
  'qwen3-rerank'
]

assert.match(menus, /\/ai\/model-configs/, 'AI 菜单应包含模型配置管理路径')
assert.match(menus, /模型配置管理/, 'AI 菜单应显示模型配置管理')
assert.match(routes, /\/ai\/model-configs/, 'AI 路由应包含模型配置管理路径')
assert.match(routes, /AiModelConfigManage/, 'AI 路由应声明 AiModelConfigManage')
assert.match(
  routes,
  /import\('\.\/src\/views\/AiModelConfigManage\.vue'\)/,
  'AI 路由应加载模型配置管理页面'
)

assert.equal(BAILIAN_ENDPOINT, 'https://dashscope.aliyuncs.com/compatible-mode/v1')
assert.ok(Array.isArray(BAILIAN_MODEL_GROUPS), '百炼模型分组必须是数组')
assert.deepEqual(
  requiredModels.filter(modelId => !flatBailianModelIds.includes(modelId)),
  [],
  '百炼模型清单必须包含设计文档中的全部模型 ID'
)

assert.equal(MODEL_CONFIG_STORAGE_KEY, 'production-studio:ai-model-config:v1')
assert.match(MODEL_CONFIG_STORAGE_VERSION, /^2026-07-23-/)

const defaults = createDefaultConfigs()
assert.equal(defaults.length, 5, '首次打开应有 5 条示例配置')
assert.deepEqual(
  defaults.map(item => item.modelId),
  [
    'qwen3.7-max',
    'qwen3.7-plus',
    'qwen3.6-flash',
    'qwen3-coder-next',
    'qwen3.5-omni-plus'
  ],
  '示例配置模型 ID 必须与设计文档一致'
)
assert.deepEqual(
  defaults.map(item => item.env),
  ['dev', 'dev', 'prod', 'staging', 'prod'],
  '示例配置环境必须按设计文档隔离'
)

const exported = exportConfigsForJson([
  {
    id: 'cfg-test',
    name: '测试配置',
    apiKey: 'sk-real-secret',
    env: 'dev',
    modelId: 'qwen-plus'
  }
])
assert.equal(exported[0].apiKey, '***', '导出 JSON 时 API Key 必须脱敏')

const imported = normalizeImportedConfigs(
  [
    {
      name: '导入配置',
      provider: 'custom',
      endpoint: 'https://example.com/v1',
      apiKey: 'sk-imported',
      modelId: 'custom-model',
      env: 'prod'
    }
  ],
  'staging'
)
assert.equal(imported.length, 1, '有效导入配置应被保留')
assert.equal(imported[0].env, 'staging', '导入配置必须合并到当前环境')

assert.match(page, /配置列表/, '页面必须包含配置列表视图')
assert.match(page, /模型模板/, '页面必须包含模型模板视图')
assert.match(page, /Ctrl\/Cmd \+ N/, '页面必须提示新建快捷键')
assert.match(page, /ElMessageBox\.confirm/, '删除前必须使用确认弹窗')
assert.doesNotMatch(page, /乱码|�/, '页面不能出现明显乱码')
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
node --test frontend/modules/ai/tests/model-config-static.test.mjs
```

Expected: FAIL because `AiModelConfigManage.vue` and `src/model-config/*.js` do not exist yet.

---

### Task 2: 实现模型配置数据工具层

**Files:**
- Create: `frontend/modules/ai/src/model-config/bailianModels.js`
- Create: `frontend/modules/ai/src/model-config/modelConfigStore.js`
- Create: `frontend/modules/ai/src/model-config/connectionTest.js`
- Test: `frontend/modules/ai/tests/model-config-static.test.mjs`

**Interfaces:**
- Consumes: Task 1 contract test.
- Produces:
  - `BAILIAN_ENDPOINT: string`
  - `BAILIAN_MODEL_GROUPS: Array<{ group: string, models: Array<{ id: string, name: string, category: string }> }>`
  - `flatBailianModelIds: string[]`
  - `MODEL_CONFIG_STORAGE_KEY: string`
  - `MODEL_CONFIG_STORAGE_VERSION: string`
  - `createDefaultConfigs(): ModelConfig[]`
  - `createEmptyConfig(env: string, provider?: string, modelId?: string): ModelConfig`
  - `exportConfigsForJson(configs: ModelConfig[]): ModelConfig[]`
  - `normalizeImportedConfigs(configs: unknown[], env: string): ModelConfig[]`
  - `runModelConnectionTest(config: ModelConfig): Promise<TestResult>`

- [ ] **Step 1: Implement Bailian model catalog**

Create `frontend/modules/ai/src/model-config/bailianModels.js`:

```js
export const BAILIAN_ENDPOINT = 'https://dashscope.aliyuncs.com/compatible-mode/v1'

export const PROVIDERS = [
  { value: 'bailian', label: '阿里云百炼', color: '#ff6a00' },
  { value: 'custom', label: '自定义', color: '#4b5563' }
]

export const BAILIAN_MODEL_GROUPS = [
  {
    group: '文本推理',
    models: [
      { id: 'qwen3.8-max-preview', name: 'Qwen3.8 Max Preview', category: '文本推理' },
      { id: 'qwen3.7-max', name: 'Qwen3.7 Max', category: '文本推理' },
      { id: 'qwen3.7-plus', name: 'Qwen3.7 Plus', category: '文本推理' },
      { id: 'qwen3.6-plus', name: 'Qwen3.6 Plus', category: '文本推理' },
      { id: 'qwen3.6-flash', name: 'Qwen3.6 Flash', category: '文本推理' },
      { id: 'qwen3-max', name: 'Qwen3 Max', category: '文本推理' },
      { id: 'qwen-plus', name: 'Qwen Plus', category: '文本推理' },
      { id: 'qwen-turbo', name: 'Qwen Turbo', category: '文本推理' },
      { id: 'qwen-long', name: 'Qwen Long', category: '文本推理' }
    ]
  },
  {
    group: '代码模型',
    models: [{ id: 'qwen3-coder-next', name: 'Qwen3 Coder Next', category: '代码模型' }]
  },
  {
    group: '多模态/视觉',
    models: [
      { id: 'qwen3-vl-max', name: 'Qwen3 VL Max', category: '多模态/视觉' },
      { id: 'qwen3-vl-plus', name: 'Qwen3 VL Plus', category: '多模态/视觉' }
    ]
  },
  {
    group: '全模态',
    models: [
      { id: 'qwen3.5-omni-plus', name: 'Qwen3.5 Omni Plus', category: '全模态' },
      {
        id: 'qwen3.5-omni-plus-realtime',
        name: 'Qwen3.5 Omni Plus Realtime',
        category: '全模态'
      }
    ]
  },
  {
    group: '开源可自部署',
    models: [
      { id: 'qwen3.5-122b-a10b', name: 'Qwen3.5 122B A10B', category: '开源可自部署' },
      { id: 'qwen3.5-32b', name: 'Qwen3.5 32B', category: '开源可自部署' },
      { id: 'qwen3.5-7b', name: 'Qwen3.5 7B', category: '开源可自部署' }
    ]
  },
  {
    group: '向量/重排',
    models: [
      { id: 'text-embedding-v4', name: 'Text Embedding V4', category: '向量/重排' },
      {
        id: 'tongyi-embedding-vision-plus',
        name: '通义视觉向量 Plus',
        category: '向量/重排'
      },
      { id: 'qwen3-rerank', name: 'Qwen3 Rerank', category: '向量/重排' }
    ]
  }
]

export const flatBailianModels = BAILIAN_MODEL_GROUPS.flatMap(group => group.models)
export const flatBailianModelIds = flatBailianModels.map(model => model.id)
```

- [ ] **Step 2: Implement localStorage store helpers**

Create `frontend/modules/ai/src/model-config/modelConfigStore.js` with focused functions. Keep the file under 200 effective lines.

```js
import { BAILIAN_ENDPOINT } from './bailianModels.js'

export const MODEL_CONFIG_STORAGE_KEY = 'production-studio:ai-model-config:v1'
export const MODEL_CONFIG_STORAGE_VERSION = '2026-07-23-bailian-v1'
export const MODEL_CONFIG_ENVS = [
  { value: 'dev', label: '开发' },
  { value: 'staging', label: '预发' },
  { value: 'prod', label: '生产' }
]

export function createDefaultConfigs() {
  const now = '2026-07-23T00:00:00.000Z'
  return [
    buildSample('cfg-qwen37-max', 'Qwen3.7-Max 旗舰模型', 'qwen3.7-max', 'dev', 'success', now),
    buildSample('cfg-qwen37-plus', 'Qwen3.7-Plus 均衡模型', 'qwen3.7-plus', 'dev', 'success', now),
    buildSample('cfg-qwen36-flash', 'Qwen3.6-Flash 极速模型', 'qwen3.6-flash', 'prod', 'success', now),
    buildSample('cfg-qwen-coder', 'Qwen3-Coder-Next 代码模型', 'qwen3-coder-next', 'staging', 'success', now),
    buildSample('cfg-qwen-omni', 'Qwen3.5-Omni-Plus 全模态', 'qwen3.5-omni-plus', 'prod', 'untested', now)
  ]
}

export function createInitialState() {
  return {
    version: MODEL_CONFIG_STORAGE_VERSION,
    configs: createDefaultConfigs()
  }
}

export function loadModelConfigState(storage = window.localStorage) {
  const raw = storage.getItem(MODEL_CONFIG_STORAGE_KEY)
  if (!raw) return createInitialState()
  const parsed = safeJsonParse(raw)
  if (!parsed || parsed.version !== MODEL_CONFIG_STORAGE_VERSION) return createInitialState()
  return Array.isArray(parsed.configs) ? parsed : createInitialState()
}

export function saveModelConfigState(state, storage = window.localStorage) {
  storage.setItem(MODEL_CONFIG_STORAGE_KEY, JSON.stringify(state))
}

export function createEmptyConfig(env, provider = 'bailian', modelId = 'qwen3.7-plus') {
  const now = new Date().toISOString()
  return {
    id: createId(),
    name: '',
    description: '',
    provider,
    endpoint: provider === 'bailian' ? BAILIAN_ENDPOINT : '',
    apiKey: '',
    modelId,
    env,
    enabled: true,
    temperature: 0.7,
    topP: 0.8,
    maxTokens: 4096,
    tags: [],
    lastTestResult: createUntestedResult(),
    createdAt: now,
    updatedAt: now
  }
}

export function validateConfig(config) {
  const errors = []
  if (!String(config.name || '').trim()) errors.push('名称必填')
  if (!String(config.endpoint || '').trim()) errors.push('Endpoint 必填')
  if (!String(config.apiKey || '').trim()) errors.push('API Key 必填')
  if (!String(config.modelId || '').trim()) errors.push('模型 ID 必填')
  return errors
}

export function upsertConfig(configs, config) {
  const next = { ...config, updatedAt: new Date().toISOString() }
  const index = configs.findIndex(item => item.id === next.id)
  if (index < 0) return [next, ...configs]
  return configs.map(item => (item.id === next.id ? next : item))
}

export function removeConfig(configs, id) {
  return configs.filter(item => item.id !== id)
}

export function duplicateConfig(config) {
  return {
    ...config,
    id: createId(),
    name: `${config.name} 副本`,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
  }
}

export function filterConfigs(configs, filters) {
  const keyword = String(filters.keyword || '').trim().toLowerCase()
  return configs.filter(item => {
    const matchEnv = item.env === filters.env
    const matchProvider = !filters.provider || item.provider === filters.provider
    const matchStatus = filters.status === '' || item.enabled === (filters.status === 'enabled')
    const matchKeyword = !keyword || `${item.name} ${item.modelId}`.toLowerCase().includes(keyword)
    return matchEnv && matchProvider && matchStatus && matchKeyword
  })
}

export function exportConfigsForJson(configs) {
  return configs.map(item => ({ ...item, apiKey: '***' }))
}

export function normalizeImportedConfigs(configs, env) {
  if (!Array.isArray(configs)) return []
  return configs.filter(isImportableConfig).map(item => ({
    ...createEmptyConfig(env, item.provider, item.modelId),
    ...item,
    id: createId(),
    env,
    updatedAt: new Date().toISOString()
  }))
}

function buildSample(id, name, modelId, env, status, now) {
  return {
    ...createEmptyConfig(env, 'bailian', modelId),
    id,
    name,
    description: `${name} 示例配置`,
    apiKey: 'sk-example-masked',
    tags: ['百炼', env],
    lastTestResult: {
      status,
      latency: status === 'success' ? 126 : 0,
      message: status === 'success' ? '模拟连接成功' : '尚未测试',
      testedAt: status === 'success' ? now : ''
    },
    createdAt: now,
    updatedAt: now
  }
}

function createUntestedResult() {
  return { status: 'untested', latency: 0, message: '尚未测试', testedAt: '' }
}

function createId() {
  return `cfg-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`
}

function safeJsonParse(raw) {
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function isImportableConfig(item) {
  return item && item.name && item.endpoint && item.apiKey && item.modelId
}
```

- [ ] **Step 3: Implement simulated connection test**

Create `frontend/modules/ai/src/model-config/connectionTest.js`:

```js
export async function runModelConnectionTest(config) {
  const startedAt = performance.now()
  await wait(350 + Math.floor(Math.random() * 500))
  const hasRequiredFields = Boolean(config.endpoint && config.apiKey && config.modelId)
  const latency = Math.max(1, Math.round(performance.now() - startedAt))
  return {
    status: hasRequiredFields ? 'success' : 'failed',
    latency,
    message: hasRequiredFields ? '模拟连接成功' : 'Endpoint、API Key 或模型 ID 不完整',
    testedAt: new Date().toISOString()
  }
}

function wait(ms) {
  return new Promise(resolve => window.setTimeout(resolve, ms))
}
```

- [ ] **Step 4: Run the contract test**

Run:

```bash
node --test frontend/modules/ai/tests/model-config-static.test.mjs
```

Expected: still FAIL because menu, route and page are not implemented yet.

---

### Task 3: 接入 AI 模块菜单和路由

**Files:**
- Modify: `frontend/modules/ai/menus.js`
- Modify: `frontend/modules/ai/routes.js`
- Test: `frontend/modules/ai/tests/model-config-static.test.mjs`

**Interfaces:**
- Consumes: Task 1 route/menu assertions.
- Produces:
  - Menu item `{ path: '/ai/model-configs', title: '模型配置管理' }`
  - Route `{ path: '/ai/model-configs', name: 'AiModelConfigManage', component: () => import('./src/views/AiModelConfigManage.vue') }`

- [ ] **Step 1: Modify AI menus**

Update `frontend/modules/ai/menus.js`:

```js
export const menus = [
  { path: '/ai/workbench', title: '智能体对话' },
  { path: '/ai/agents', title: '智能体管理' },
  { path: '/ai/model-configs', title: '模型配置管理' }
]
```

- [ ] **Step 2: Modify AI routes**

Update `frontend/modules/ai/routes.js`:

```js
export const routes = [
  { path: '/ai', name: 'AiRoot', redirect: '/ai/workbench' },
  { path: '/ai/workbench', name: 'AiWorkbench', component: () => import('./src/views/AiWorkbench.vue') },
  { path: '/ai/agents', name: 'AiAgentManage', component: () => import('./src/views/AiAgentManage.vue') },
  { path: '/ai/prompts', name: 'AiPromptTemplateManage', component: () => import('./src/views/AiPromptTemplateManage.vue') },
  {
    path: '/ai/model-configs',
    name: 'AiModelConfigManage',
    component: () => import('./src/views/AiModelConfigManage.vue')
  }
]
```

- [ ] **Step 3: Run the contract test**

Run:

```bash
node --test frontend/modules/ai/tests/model-config-static.test.mjs
```

Expected: FAIL only because `AiModelConfigManage.vue` does not exist yet.

---

### Task 4: 实现模型配置管理页面

**Files:**
- Create: `frontend/modules/ai/src/views/AiModelConfigManage.vue`
- Test: `frontend/modules/ai/tests/model-config-static.test.mjs`

**Interfaces:**
- Consumes:
  - `BAILIAN_ENDPOINT`
  - `PROVIDERS`
  - `BAILIAN_MODEL_GROUPS`
  - `flatBailianModels`
  - `MODEL_CONFIG_ENVS`
  - `loadModelConfigState`
  - `saveModelConfigState`
  - `createEmptyConfig`
  - `validateConfig`
  - `upsertConfig`
  - `removeConfig`
  - `duplicateConfig`
  - `filterConfigs`
  - `exportConfigsForJson`
  - `normalizeImportedConfigs`
  - `runModelConnectionTest`
- Produces: 用户可访问、可操作的 `/ai/model-configs` 页面。

- [ ] **Step 1: Create page template**

Create `frontend/modules/ai/src/views/AiModelConfigManage.vue` with these sections in order:

```vue
<template>
  <section class="ai-model-config-page">
    <header class="ai-model-config-page__header">
      <div>
        <p class="ai-model-config-page__eyebrow">智能体管理</p>
        <h1>模型配置管理</h1>
        <span>集中维护百炼 DashScope 和自定义大模型配置，支持 Ctrl/Cmd + N 新建。</span>
      </div>
      <div class="ai-model-config-page__actions">
        <el-segmented v-model="filters.env" :options="envOptions" />
        <el-button @click="triggerImport">导入</el-button>
        <el-button @click="exportCurrentEnv">导出</el-button>
        <el-button type="primary" @click="openCreate">新建配置</el-button>
      </div>
    </header>

    <el-tabs v-model="activeView" class="ai-model-config-tabs">
      <el-tab-pane label="配置列表" name="configs">
        <div class="ai-model-config-toolbar">
          <el-input v-model.trim="filters.keyword" clearable placeholder="搜索名称或模型 ID" />
          <el-select v-model="filters.provider" clearable placeholder="全部提供商">
            <el-option v-for="provider in PROVIDERS" :key="provider.value" :label="provider.label" :value="provider.value" />
          </el-select>
          <el-select v-model="filters.status" placeholder="全部状态">
            <el-option label="全部状态" value="" />
            <el-option label="启用" value="enabled" />
            <el-option label="停用" value="disabled" />
          </el-select>
        </div>

        <el-table class="ai-model-config-table" :data="filteredConfigs" border empty-text="当前环境暂无模型配置">
          <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
          <el-table-column label="提供商" width="128">
            <template #default="{ row }">
              <el-tag :style="providerStyle(row.provider)" effect="plain">{{ providerName(row.provider) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="modelId" label="模型 ID" min-width="190" show-overflow-tooltip />
          <el-table-column label="环境" width="90">
            <template #default="{ row }">{{ envName(row.env) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最后测试" min-width="180">
            <template #default="{ row }">{{ testResultText(row.lastTestResult) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button text @click="testConfig(row)">测试</el-button>
              <el-button text @click="copyConfig(row)">复制</el-button>
              <el-button text @click="openEdit(row)">编辑</el-button>
              <el-button text type="danger" @click="deleteConfig(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="模型模板" name="templates">
        <div class="ai-model-template-grid">
          <article class="ai-model-template-card">
            <div class="ai-model-template-card__head">
              <strong>阿里云百炼</strong>
              <span>固定 Endpoint，选择模型即可创建配置</span>
            </div>
            <div v-for="group in BAILIAN_MODEL_GROUPS" :key="group.group" class="ai-model-group">
              <h3>{{ group.group }}</h3>
              <button v-for="model in group.models" :key="model.id" type="button" @click="openFromBailianModel(model)">
                {{ model.id }}
              </button>
            </div>
          </article>
          <article class="ai-model-template-card">
            <div class="ai-model-template-card__head">
              <strong>自定义</strong>
              <span>输入任意兼容 OpenAI 协议的 Endpoint 和模型 ID</span>
            </div>
            <el-button type="primary" plain @click="openCustomTemplate">创建自定义配置</el-button>
          </article>
        </div>
      </el-tab-pane>
    </el-tabs>

    <input ref="importInputRef" class="ai-model-config-page__file" type="file" accept="application/json" @change="handleImportFile" />

    <el-drawer v-model="drawerVisible" size="520px" :title="editingId ? '编辑模型配置' : '新建模型配置'">
      <el-form class="ai-model-config-form" label-position="top">
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="环境"><el-select v-model="form.env"><el-option v-for="env in MODEL_CONFIG_ENVS" :key="env.value" :label="env.label" :value="env.value" /></el-select></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
        <el-form-item label="提供商"><el-segmented v-model="form.provider" :options="providerOptions" @change="handleProviderChange" /></el-form-item>
        <el-form-item label="Endpoint" required><el-input v-model="form.endpoint" /></el-form-item>
        <el-form-item label="API Key" required><el-input v-model="form.apiKey" type="password" show-password /></el-form-item>
        <el-form-item label="模型 ID" required>
          <el-select v-if="form.provider === 'bailian'" v-model="form.modelId" filterable>
            <el-option v-for="model in flatBailianModels" :key="model.id" :label="model.id" :value="model.id" />
          </el-select>
          <el-input v-else v-model="form.modelId" />
        </el-form-item>
        <el-form-item label="Temperature"><el-slider v-model="form.temperature" :min="0" :max="1" :step="0.1" show-input /></el-form-item>
        <el-form-item label="Top P"><el-slider v-model="form.topP" :min="0" :max="1" :step="0.1" show-input /></el-form-item>
        <el-form-item label="Max Tokens"><el-slider v-model="form.maxTokens" :min="256" :max="8192" :step="256" show-input /></el-form-item>
        <el-form-item label="标签">
          <div class="ai-model-tag-editor">
            <el-tag v-for="tag in form.tags" :key="tag" closable @close="removeTag(tag)">{{ tag }}</el-tag>
            <el-input v-model.trim="tagDraft" placeholder="回车添加标签" @keyup.enter="addTag" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" @click="saveConfig">保存</el-button>
      </template>
    </el-drawer>
  </section>
</template>
```

- [ ] **Step 2: Add script setup logic**

In the same file, add `<script setup>` after the template. Keep event handlers focused and under 50 effective lines:

```vue
<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { BAILIAN_ENDPOINT, BAILIAN_MODEL_GROUPS, PROVIDERS, flatBailianModels } from '../model-config/bailianModels'
import {
  MODEL_CONFIG_ENVS,
  createEmptyConfig,
  duplicateConfig,
  exportConfigsForJson,
  filterConfigs,
  loadModelConfigState,
  normalizeImportedConfigs,
  removeConfig,
  saveModelConfigState,
  upsertConfig,
  validateConfig
} from '../model-config/modelConfigStore'
import { runModelConnectionTest } from '../model-config/connectionTest'

const state = reactive(loadModelConfigState())
const activeView = ref('configs')
const drawerVisible = ref(false)
const editingId = ref('')
const importInputRef = ref()
const tagDraft = ref('')
const form = reactive(createEmptyConfig('dev'))
const filters = reactive({ env: 'dev', provider: '', status: '', keyword: '' })

const envOptions = computed(() => MODEL_CONFIG_ENVS.map(item => ({ label: item.label, value: item.value })))
const providerOptions = computed(() => PROVIDERS.map(item => ({ label: item.label, value: item.value })))
const filteredConfigs = computed(() => filterConfigs(state.configs, filters))

function persist() {
  saveModelConfigState(state)
}

function resetForm(config) {
  Object.assign(form, config)
}

function openCreate() {
  editingId.value = ''
  resetForm(createEmptyConfig(filters.env))
  drawerVisible.value = true
}

function openEdit(config) {
  editingId.value = config.id
  resetForm({ ...config, tags: [...config.tags] })
  drawerVisible.value = true
}

function openFromBailianModel(model) {
  editingId.value = ''
  resetForm({
    ...createEmptyConfig(filters.env, 'bailian', model.id),
    name: `${model.name} 配置`,
    description: `${model.category}模型配置`
  })
  drawerVisible.value = true
}

function openCustomTemplate() {
  editingId.value = ''
  resetForm({ ...createEmptyConfig(filters.env, 'custom', ''), endpoint: '' })
  drawerVisible.value = true
}

function handleProviderChange(provider) {
  if (provider === 'bailian') form.endpoint = BAILIAN_ENDPOINT
  if (provider === 'custom') form.endpoint = ''
}

function saveConfig() {
  const errors = validateConfig(form)
  if (errors.length) {
    ElMessage.warning(errors[0])
    return
  }
  state.configs = upsertConfig(state.configs, { ...form, tags: [...form.tags] })
  persist()
  drawerVisible.value = false
  ElMessage.success('模型配置已保存')
}

async function deleteConfig(config) {
  await ElMessageBox.confirm(`确认删除模型配置「${config.name}」？`, '删除确认', { type: 'warning' })
  state.configs = removeConfig(state.configs, config.id)
  persist()
  ElMessage.success('模型配置已删除')
}

function copyConfig(config) {
  state.configs = upsertConfig(state.configs, duplicateConfig(config))
  persist()
  ElMessage.success('已复制配置')
}

async function testConfig(config) {
  const result = await runModelConnectionTest(config)
  state.configs = upsertConfig(state.configs, { ...config, lastTestResult: result })
  persist()
  ElMessage[result.status === 'success' ? 'success' : 'error'](result.message)
}

function triggerImport() {
  importInputRef.value?.click()
}

async function handleImportFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  const raw = await file.text()
  const imported = normalizeImportedConfigs(JSON.parse(raw), filters.env)
  state.configs = [...imported, ...state.configs]
  persist()
  ElMessage.success(`已导入 ${imported.length} 条配置`)
}

function exportCurrentEnv() {
  const data = exportConfigsForJson(state.configs.filter(item => item.env === filters.env))
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `ai-model-config-${filters.env}-${new Date().toISOString().slice(0, 10)}.json`
  link.click()
  URL.revokeObjectURL(url)
}

function addTag() {
  if (!tagDraft.value || form.tags.includes(tagDraft.value)) return
  form.tags.push(tagDraft.value)
  tagDraft.value = ''
}

function removeTag(tag) {
  form.tags = form.tags.filter(item => item !== tag)
}

function providerName(value) {
  return PROVIDERS.find(item => item.value === value)?.label || value
}

function providerStyle(value) {
  const color = PROVIDERS.find(item => item.value === value)?.color || '#4b5563'
  return { color, borderColor: color }
}

function envName(value) {
  return MODEL_CONFIG_ENVS.find(item => item.value === value)?.label || value
}

function testResultText(result) {
  if (!result || result.status === 'untested') return '未测试'
  return `${result.status === 'success' ? '成功' : '失败'} · ${result.latency}ms`
}

function handleShortcut(event) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'n') {
    event.preventDefault()
    openCreate()
  }
  if (event.key === 'Escape' && drawerVisible.value) drawerVisible.value = false
}

onMounted(() => window.addEventListener('keydown', handleShortcut))
onBeforeUnmount(() => window.removeEventListener('keydown', handleShortcut))
</script>
```

- [ ] **Step 3: Add scoped styles**

In the same file, add styles that keep cards at 8px radius and avoid nested card visuals:

```vue
<style scoped>
.ai-model-config-page {
  min-height: calc(100vh - 110px);
  padding: 20px;
  background: #f6f8fb;
}

.ai-model-config-page__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.ai-model-config-page__eyebrow {
  margin: 0 0 4px;
  color: #ff6a00;
  font-size: 13px;
  font-weight: 600;
}

.ai-model-config-page h1 {
  margin: 0 0 6px;
  color: #111827;
  font-size: 24px;
}

.ai-model-config-page__header span {
  color: #6b7280;
}

.ai-model-config-page__actions,
.ai-model-config-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.ai-model-config-tabs {
  padding: 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.ai-model-config-toolbar {
  margin-bottom: 12px;
}

.ai-model-config-toolbar .el-input {
  max-width: 280px;
}

.ai-model-template-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(260px, 0.7fr);
  gap: 14px;
}

.ai-model-template-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
  background: #fff;
}

.ai-model-template-card__head {
  display: grid;
  gap: 4px;
  margin-bottom: 14px;
}

.ai-model-template-card__head strong {
  color: #111827;
  font-size: 16px;
}

.ai-model-template-card__head span,
.ai-model-group h3 {
  color: #6b7280;
  font-size: 13px;
}

.ai-model-group {
  margin-top: 12px;
}

.ai-model-group button {
  margin: 0 8px 8px 0;
  padding: 7px 10px;
  color: #374151;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  cursor: pointer;
}

.ai-model-group button:hover {
  color: #ff6a00;
  border-color: #ff6a00;
}

.ai-model-config-page__file {
  display: none;
}

.ai-model-config-form :deep(.el-select),
.ai-model-config-form :deep(.el-input-number) {
  width: 100%;
}

.ai-model-tag-editor {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  width: 100%;
}

.ai-model-tag-editor .el-input {
  max-width: 180px;
}

@media (max-width: 920px) {
  .ai-model-config-page__header,
  .ai-model-template-grid {
    grid-template-columns: 1fr;
    flex-direction: column;
  }
}
</style>
```

- [ ] **Step 4: Run the contract test**

Run:

```bash
node --test frontend/modules/ai/tests/model-config-static.test.mjs
```

Expected: PASS.

---

### Task 5: 构建验证与代码尺寸检查

**Files:**
- Read: `frontend/modules/ai/src/views/AiModelConfigManage.vue`
- Read: `frontend/modules/ai/src/model-config/*.js`
- Test: `frontend/modules/ai/tests/model-config-static.test.mjs`

**Interfaces:**
- Consumes: 完整模型配置前端页面。
- Produces: 可交付验证结果。

- [ ] **Step 1: Run static contract test**

Run:

```bash
node --test frontend/modules/ai/tests/model-config-static.test.mjs
```

Expected: PASS.

- [ ] **Step 2: Check Vue and JS file line counts**

Run:

```bash
powershell -Command "(Get-Content 'frontend/modules/ai/src/views/AiModelConfigManage.vue').Count; (Get-Content 'frontend/modules/ai/src/model-config/bailianModels.js').Count; (Get-Content 'frontend/modules/ai/src/model-config/modelConfigStore.js').Count; (Get-Content 'frontend/modules/ai/src/model-config/connectionTest.js').Count"
```

Expected:

- `AiModelConfigManage.vue` line count below `500`.
- `bailianModels.js` line count below `200`.
- `modelConfigStore.js` line count below `200`.
- `connectionTest.js` line count below `200`.

- [ ] **Step 3: Build web shell**

Run:

```bash
cd frontend/web-shell
npm run build
```

Expected: build exits with code `0` and does not change frontend dev server port.

- [ ] **Step 4: Inspect working tree**

Run:

```bash
git status --short
```

Expected: changed files are limited to the AI frontend model config feature and this plan, plus pre-existing unrelated dirty files that were already present before implementation.

