# AI 模型模板大模型下拉菜单实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将模型模板页的百炼模型按钮列表替换为可搜索、按类别分组的大模型下拉菜单。

**Architecture:** 页面继续以 `BAILIAN_MODEL_GROUPS` 为唯一数据源，通过 Element Plus `el-select` 和 `el-option-group` 展示。选择模型 ID 后查找对应模型对象并复用 `openFromBailianModel` 打开新建配置抽屉。

**Tech Stack:** Vue 3、Element Plus、Node.js Test Runner。

## Global Constraints

- 不修改后端接口和数据库。
- 不修改模型配置保存、编辑、导入、导出和连接测试行为。
- 自定义模型入口保持不变。
- 不修改无关工作区文件。

---

### Task 1: 将模型按钮替换为分组下拉菜单

**Files:**
- Modify: `frontend/modules/ai/src/views/AiModelConfigManage.vue`
- Modify: `frontend/modules/ai/tests/model-config-static.test.mjs`

**Interfaces:**
- Consumes: `BAILIAN_MODEL_GROUPS`、`flatBailianModels`、`openFromBailianModel(model)`
- Produces: `selectedTemplateModelId`、`handleTemplateModelSelect(modelId)`

- [ ] **Step 1: 添加失败的静态测试**

```javascript
assert.match(page, /v-model="selectedTemplateModelId"/)
assert.match(page, /<el-option-group/)
assert.match(page, /v-for="group in BAILIAN_MODEL_GROUPS"/)
assert.match(page, /@change="handleTemplateModelSelect"/)
assert.match(page, /function handleTemplateModelSelect\(modelId\)/)
assert.doesNotMatch(
  page,
  /<button v-for="model in group\.models"/,
  '模型模板不能继续按按钮铺开模型'
)
```

- [ ] **Step 2: 运行测试并确认失败**

```bash
node --test frontend/modules/ai/tests/model-config-static.test.mjs
```

Expected: FAIL，因为页面仍使用模型按钮。

- [ ] **Step 3: 实现分组下拉菜单**

模板核心结构：

```vue
<el-select
  v-model="selectedTemplateModelId"
  class="template-model-select"
  filterable
  clearable
  placeholder="请选择大模型"
  @change="handleTemplateModelSelect"
>
  <el-option-group
    v-for="group in BAILIAN_MODEL_GROUPS"
    :key="group.group"
    :label="group.group"
  >
    <el-option
      v-for="model in group.models"
      :key="model.id"
      :label="`${model.name} · ${model.id}`"
      :value="model.id"
    />
  </el-option-group>
</el-select>
```

脚本逻辑：

```javascript
const selectedTemplateModelId = ref('')

function handleTemplateModelSelect(modelId) {
  if (!modelId) return
  const model = flatBailianModels.find(item => item.id === modelId)
  if (!model) return
  openFromBailianModel(model)
  selectedTemplateModelId.value = ''
}
```

删除 `.model-group` 和 `.model-group button` 样式，增加：

```css
.template-model-select {
  width: 100%;
}
```

- [ ] **Step 4: 运行全部 AI 前端测试**

```bash
node --test frontend/modules/ai/tests/*.test.mjs
```

Expected: 10 个测试全部 PASS。

- [ ] **Step 5: 构建前端主壳**

```bash
cd frontend/web-shell
npm run build
```

Expected: 构建成功；现有 chunk size 警告可接受。

- [ ] **Step 6: 检查差异**

```bash
git diff --check
git status --short
```

Expected: 无空白错误，不修改无关文件。
