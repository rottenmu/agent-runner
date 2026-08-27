# 飞书渠道绑定 CRUD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有飞书渠道绑定页面改造成只展示绑定关系、并支持新增、编辑和删除的完整管理页面。

**Architecture:** `ps_feishu_config.agent_id` 继续作为唯一绑定数据源，现有 `PUT /api/biz/feishu/config/{id}/agent-binding` 统一承载新增、修改和解除操作。飞书配置分页接口增加兼容的 `bound` 参数；前端拆出独立绑定弹窗，主页面负责列表、分页和操作编排。

**Tech Stack:** Java 17、Spring Boot 3.4.5、MyBatis-Plus、JUnit 5、Mockito、Vue 3、Element Plus、Node.js test runner。

## Global Constraints

- 主列表只展示 `agent_id IS NOT NULL` 的飞书配置。
- 新增时只能选择 `agent_id IS NULL` 的飞书配置。
- 编辑时飞书配置只读，只允许更换智能体。
- 新增和编辑只允许选择 `enabled=true` 的智能体。
- 删除仅把 `agent_id` 更新为 `null`，不得删除飞书配置或智能体。
- 绑定写入继续复用 `PUT /api/biz/feishu/config/{id}/agent-binding`。
- `bound` 未传时保持飞书配置分页接口的原有行为。
- 不增加数据库表、字段、初始化器或模块依赖。
- 不修改 AI 运行时消息路由。
- 页面不得展示 `appSecret`、`verificationToken` 或 `encryptKey`。
- 后端 Java 注释遵守 `docs/rules/BACKEND_JAVA_COMMENT_RULES.md`。
- 后端与前端代码行数遵守 `docs/rules/CODE_SIZE_RULES.md`。
- Controller 和 Service Bean 保持唯一普通 `public` 构造器注入。
- 不编辑或提交 `target/`、`dist/` 等生成产物。

---

## 文件结构

### 新增文件

- `frontend/modules/ai/src/components/AiFeishuBindingDialog.vue`：新增/编辑绑定弹窗，负责表单展示、校验和提交事件。

### 修改文件

- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigController.java`：接收并透传 `bound`。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigService.java`：扩展分页方法签名。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigServiceImpl.java`：按 `bound` 增加 `IS NULL` 或 `IS NOT NULL` 条件。
- `frontend/modules/ai/src/views/AiFeishuBindingManage.vue`：只展示已绑定关系并编排新增、编辑、删除。
- `frontend/modules/ai/tests/feishu-binding-static.test.mjs`：验证 CRUD 页面契约和敏感字段约束。

### 测试文件

- `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuConfigServiceTest.java`
- `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuConfigControllerTest.java`

---

### Task 1: 飞书配置分页支持绑定状态筛选

**Files:**

- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigController.java`
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigService.java`
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigServiceImpl.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuConfigServiceTest.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuConfigControllerTest.java`

**Interfaces:**

- Consumes: `GET /api/biz/feishu/config/page` 的现有分页参数。
- Produces: 可选查询参数 `bound: Boolean`。
- Produces: `FeishuConfigService.page(long, long, String, String, Integer, Boolean)`。

- [ ] **Step 1: 编写 Service 绑定筛选失败测试**

在 `FeishuConfigServiceTest` 增加包装器捕获测试：

```java
@Test
void pageFiltersBoundConfigsWhenBoundIsTrue() {
    when(mapper.selectPage(any(Page.class), any(Wrapper.class)))
            .thenReturn(new Page<>(1, 10));

    service.page(1, 10, null, null, null, true);

    ArgumentCaptor<Wrapper<FeishuConfigEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
    verify(mapper).selectPage(any(Page.class), captor.capture());
    assertThat(captor.getValue().getSqlSegment().toLowerCase())
            .contains("agent_id is not null");
}

@Test
void pageFiltersUnboundConfigsWhenBoundIsFalse() {
    when(mapper.selectPage(any(Page.class), any(Wrapper.class)))
            .thenReturn(new Page<>(1, 10));

    service.page(1, 10, null, null, null, false);

    ArgumentCaptor<Wrapper<FeishuConfigEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
    verify(mapper).selectPage(any(Page.class), captor.capture());
    assertThat(captor.getValue().getSqlSegment().toLowerCase())
            .contains("agent_id is null")
            .doesNotContain("agent_id is not null");
}

@Test
void pageKeepsOriginalQueryWhenBoundIsNull() {
    when(mapper.selectPage(any(Page.class), any(Wrapper.class)))
            .thenReturn(new Page<>(1, 10));

    service.page(1, 10, null, null, null, null);

    ArgumentCaptor<Wrapper<FeishuConfigEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
    verify(mapper).selectPage(any(Page.class), captor.capture());
    assertThat(captor.getValue().getSqlSegment().toLowerCase())
            .doesNotContain("agent_id");
}
```

将现有 `pageReturnsMaskedRecords` 调用改为：

```java
Page<FeishuConfigResponse> result = service.page(1, 10, "生产", "cli", 1, null);
```

- [ ] **Step 2: 运行 Service 测试并确认 RED**

Run:

```text
mvn -pl modules/module-feishu/module-feishu-core -am -Dtest=FeishuConfigServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，`page` 尚不接受第六个 `Boolean bound` 参数。

- [ ] **Step 3: 编写 Controller 透传失败测试**

在 `FeishuConfigControllerTest.pagesConfigsWithQueryConditions` 的请求中增加：

```java
.param("bound", "true")
```

在 `RecordingConfigService` 增加字段：

```java
private Boolean lastBound;
```

将记录服务的方法签名和实现改为：

```java
@Override
public Page<FeishuConfigResponse> page(
        long current,
        long size,
        String configName,
        String appId,
        Integer enabled,
        Boolean bound) {
    lastBound = bound;
    FeishuConfigResponse response = response(null);
    Page<FeishuConfigResponse> page = new Page<>(current, size, 1);
    page.setRecords(List.of(response));
    return page;
}
```

请求断言后增加：

```java
assertThat(service.lastBound).isTrue();
```

- [ ] **Step 4: 运行 Controller 测试并确认 RED**

Run:

```text
mvn -pl modules/module-feishu/module-feishu-core -am -Dtest=FeishuConfigControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，Controller 尚未接收或透传 `bound`。

- [ ] **Step 5: 扩展 Service 接口和实现**

将 `FeishuConfigService.page` 改为：

```java
/**
 * 分页查询飞书配置，可按绑定状态筛选。
 *
 * @param current 当前页，从 1 开始
 * @param size 每页条数
 * @param configName 配置名称，允许为空
 * @param appId 飞书 App ID，允许为空
 * @param enabled 启用状态，允许为空
 * @param bound 绑定状态；true 查询已绑定，false 查询未绑定，null 不筛选
 * @return 脱敏后的飞书配置分页结果
 */
Page<FeishuConfigResponse> page(
        long current,
        long size,
        String configName,
        String appId,
        Integer enabled,
        Boolean bound);
```

将 `FeishuConfigServiceImpl.page` 的签名同步，并在排序前应用条件：

```java
if (Boolean.TRUE.equals(bound)) {
    wrapper.isNotNull(FeishuConfigEntity::getAgentId);
} else if (Boolean.FALSE.equals(bound)) {
    wrapper.isNull(FeishuConfigEntity::getAgentId);
}
wrapper.orderByDesc(FeishuConfigEntity::getUpdateTime);
```

原有名称、App ID、启用状态过滤逻辑保持不变。

- [ ] **Step 6: 扩展 Controller 参数**

将分页方法最后两个参数和调用改为：

```java
@RequestParam(required = false) Integer enabled,
@RequestParam(required = false) Boolean bound) {
    return R.ok(configService.page(current, size, configName, appId, enabled, bound));
}
```

同步补齐方法 JavaDoc，明确 `bound` 三态规则。

- [ ] **Step 7: 修正所有编译调用点**

Run:

```text
rg -n "\.page\([^\\n]*configName|service\.page\(" modules/module-feishu
```

所有调用 `FeishuConfigService.page` 的位置都显式传入第六个参数；不筛选的旧测试和内部调用传 `null`。

- [ ] **Step 8: 运行后端测试**

Run:

```text
mvn -pl modules/module-feishu/module-feishu-core -am -Dtest=FeishuConfigServiceTest,FeishuConfigControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 两个测试类全部 PASS。

- [ ] **Step 9: 提交**

```text
git add modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigController.java modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigService.java modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigServiceImpl.java modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuConfigServiceTest.java modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuConfigControllerTest.java
git commit -m "feat(feishu): 支持按绑定状态查询配置"
```

---

### Task 2: 新增飞书绑定编辑弹窗

**Files:**

- Create: `frontend/modules/ai/src/components/AiFeishuBindingDialog.vue`
- Modify: `frontend/modules/ai/tests/feishu-binding-static.test.mjs`

**Interfaces:**

- Consumes props: `modelValue: Boolean`、`mode: "create" | "edit"`、`binding: Object | null`、`configs: Array`、`agents: Array`、`saving: Boolean`。
- Produces emits: `update:modelValue(Boolean)`、`submit({ configId, agentId, originalAgentId })`。

- [ ] **Step 1: 编写弹窗静态失败测试**

在 `feishu-binding-static.test.mjs` 读取组件：

```javascript
const dialog = readModuleFile('src', 'components', 'AiFeishuBindingDialog.vue')
```

增加断言：

```javascript
assert.match(dialog, /mode === 'create'/)
assert.match(dialog, /请选择飞书配置/)
assert.match(dialog, /请选择智能体/)
assert.match(dialog, /配置名称/)
assert.match(dialog, /App ID/)
assert.match(dialog, /originalAgentId/)
assert.match(dialog, /emit\('submit'/)
assert.match(dialog, /formRef\.value\?\.clearValidate/)
assert.doesNotMatch(dialog, /appSecret|verificationToken|encryptKey/)
```

- [ ] **Step 2: 运行测试并确认 RED**

Run:

```text
node --test frontend/modules/ai/tests/feishu-binding-static.test.mjs
```

Expected: FAIL，弹窗组件文件不存在。

- [ ] **Step 3: 创建弹窗组件**

创建 `AiFeishuBindingDialog.vue`，模板结构：

```vue
<template>
  <el-dialog
    :model-value="modelValue"
    :title="mode === 'create' ? '新增绑定' : '编辑绑定'"
    width="520px"
    destroy-on-close
    @closed="resetForm"
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="飞书配置" prop="configId">
        <el-select
          v-if="mode === 'create'"
          v-model="form.configId"
          filterable
          placeholder="请选择飞书配置"
          class="binding-dialog__control"
        >
          <el-option
            v-for="item in configs"
            :key="item.id"
            :label="`${item.configName}（${item.appId}）`"
            :value="item.id"
          />
        </el-select>
        <div v-else class="binding-dialog__readonly">
          <strong>{{ binding?.configName }}</strong>
          <span>App ID：{{ binding?.appId }}</span>
        </div>
      </el-form-item>
      <el-form-item label="智能体" prop="agentId">
        <el-select
          v-model="form.agentId"
          filterable
          placeholder="请选择智能体"
          class="binding-dialog__control"
        >
          <el-option
            v-for="item in agents"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>
```

脚本使用以下完整状态契约：

```vue
<script setup>
import { nextTick, reactive, ref, watch } from 'vue'

const props = defineProps({
  modelValue: Boolean,
  mode: { type: String, default: 'create' },
  binding: { type: Object, default: null },
  configs: { type: Array, default: () => [] },
  agents: { type: Array, default: () => [] },
  saving: Boolean
})

const emit = defineEmits(['update:modelValue', 'submit'])
const formRef = ref()
const form = reactive({ configId: null, agentId: null, originalAgentId: null })
const rules = {
  configId: [{ required: true, message: '请选择飞书配置', trigger: 'change' }],
  agentId: [{ required: true, message: '请选择智能体', trigger: 'change' }]
}

watch(
  () => [props.modelValue, props.mode, props.binding],
  async ([visible]) => {
    if (!visible) return
    form.configId = props.mode === 'edit' ? props.binding?.id ?? null : null
    form.originalAgentId = props.mode === 'edit' ? props.binding?.agentId ?? null : null
    const currentAgentEnabled = props.agents.some(item => item.id === form.originalAgentId)
    form.agentId = props.mode === 'edit' && currentAgentEnabled
      ? form.originalAgentId
      : null
    await nextTick()
    formRef.value?.clearValidate()
  },
  { immediate: true }
)

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  emit('submit', {
    configId: form.configId,
    agentId: form.agentId,
    originalAgentId: form.originalAgentId
  })
}

function resetForm() {
  form.configId = null
  form.agentId = null
  form.originalAgentId = null
  formRef.value?.clearValidate()
}
</script>
```

样式保持组件少于 200 行：

```vue
<style scoped>
.binding-dialog__control {
  width: 100%;
}

.binding-dialog__readonly {
  display: grid;
  gap: 4px;
}

.binding-dialog__readonly span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
```

- [ ] **Step 4: 运行静态测试**

Run:

```text
node --test frontend/modules/ai/tests/feishu-binding-static.test.mjs
```

Expected: PASS。

- [ ] **Step 5: 提交**

```text
git add frontend/modules/ai/src/components/AiFeishuBindingDialog.vue frontend/modules/ai/tests/feishu-binding-static.test.mjs
git commit -m "feat(ai): 增加飞书绑定编辑弹窗"
```

---

### Task 3: 将飞书绑定页面改为关系 CRUD

**Files:**

- Modify: `frontend/modules/ai/src/views/AiFeishuBindingManage.vue`
- Modify: `frontend/modules/ai/tests/feishu-binding-static.test.mjs`

**Interfaces:**

- Consumes: `listFeishuConfigs({ bound: true | false, ... })`。
- Consumes: `bindFeishuAgent(configId, agentId)`。
- Consumes: `AiFeishuBindingDialog` 的 props 和 `submit` 事件。
- Produces: 只展示已绑定关系的列表，以及新增、编辑、删除操作。

- [ ] **Step 1: 编写页面 CRUD 失败测试**

在 `feishu-binding-static.test.mjs` 增加：

```javascript
assert.match(page, /新增绑定/)
assert.match(page, /openCreateDialog/)
assert.match(page, /openEditDialog/)
assert.match(page, /deleteBinding/)
assert.match(page, /AiFeishuBindingDialog/)
assert.match(page, /bound:\s*true/)
assert.match(page, /bound:\s*false/)
assert.match(page, /originalAgentId/)
assert.match(page, /绑定新增成功/)
assert.match(page, /绑定修改成功/)
assert.match(page, /绑定删除成功/)
assert.match(page, /确认删除飞书配置/)
assert.doesNotMatch(page, /@change="agentId => saveBinding/)
assert.doesNotMatch(page, /<el-select[\\s\\S]*绑定操作[\\s\\S]*saveBinding/)
```

- [ ] **Step 2: 运行测试并确认 RED**

Run:

```text
node --test frontend/modules/ai/tests/feishu-binding-static.test.mjs
```

Expected: FAIL，页面仍保留行内选择，且没有新增/编辑入口。

- [ ] **Step 3: 调整页面模板**

头部动作区改为：

```vue
<div class="feishu-binding__header-actions">
  <el-button type="primary" @click="openCreateDialog">新增绑定</el-button>
  <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
</div>
```

操作列替换为：

```vue
<el-table-column label="操作" width="170" fixed="right">
  <template #default="{ row }">
    <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
    <el-button
      link
      type="danger"
      :loading="isSaving(row.id)"
      @click="deleteBinding(row)"
    >
      删除
    </el-button>
  </template>
</el-table-column>
```

表格后增加弹窗：

```vue
<AiFeishuBindingDialog
  v-model="dialog.visible"
  :mode="dialog.mode"
  :binding="dialog.binding"
  :configs="unboundConfigs"
  :agents="enabledAgents"
  :saving="dialogSaving"
  @submit="submitBinding"
/>
```

- [ ] **Step 4: 调整页面状态和加载逻辑**

导入组件：

```javascript
import AiFeishuBindingDialog from '../components/AiFeishuBindingDialog.vue'
```

增加状态：

```javascript
const unboundConfigs = ref([])
const dialogSaving = ref(false)
const dialog = reactive({ visible: false, mode: 'create', binding: null })
```

主列表请求固定增加：

```javascript
bound: true
```

增加未绑定候选加载：

```javascript
async function loadUnboundConfigs() {
  const result = unwrap(await listFeishuConfigs({
    current: 1,
    size: 100,
    bound: false
  })) || {}
  unboundConfigs.value = Array.isArray(result.records) ? result.records : []
}

async function refreshBindingData() {
  const results = await Promise.allSettled([loadData(), loadUnboundConfigs()])
  if (results.some(item => item.status === 'rejected')) {
    ElMessage.warning('绑定已保存，但部分列表刷新失败，请点击刷新重试')
  }
}
```

`loadData` 继续只在成功时替换当前列表，确保失败时保留原数据。智能体列表加载成功后继续通过 `enabledAgents` 过滤。

- [ ] **Step 5: 实现新增和编辑入口**

```javascript
async function openCreateDialog() {
  try {
    await loadUnboundConfigs()
    dialog.mode = 'create'
    dialog.binding = null
    dialog.visible = true
  } catch (error) {
    ElMessage.error(error?.message || '加载未绑定飞书配置失败')
  }
}

function openEditDialog(row) {
  dialog.mode = 'edit'
  dialog.binding = { ...row }
  dialog.visible = true
}
```

编辑模式不读取未绑定候选，弹窗通过只读区域展示当前飞书配置。

- [ ] **Step 6: 实现新增和编辑提交**

```javascript
async function submitBinding({ configId, agentId, originalAgentId }) {
  if (dialog.mode === 'edit' && agentId === originalAgentId) {
    dialog.visible = false
    return
  }
  dialogSaving.value = true
  markSaving(configId, true)
  try {
    await bindFeishuAgent(configId, agentId)
    ElMessage.success(dialog.mode === 'create' ? '绑定新增成功' : '绑定修改成功')
    dialog.visible = false
    await refreshBindingData()
  } catch (error) {
    ElMessage.error(error?.message || '保存绑定失败')
  } finally {
    markSaving(configId, false)
    dialogSaving.value = false
  }
}
```

将 Set 更新抽为小函数：

```javascript
function markSaving(id, saving) {
  const next = new Set(savingIds.value)
  if (saving) next.add(id)
  else next.delete(id)
  savingIds.value = next
}
```

- [ ] **Step 7: 实现删除绑定**

```javascript
async function deleteBinding(row) {
  const boundName = agentName(row.agentId)
  try {
    await ElMessageBox.confirm(
      `确认删除飞书配置“${row.configName}”与智能体“${boundName}”的绑定关系？`,
      '删除绑定',
      { type: 'warning' }
    )
  } catch {
    return
  }
  markSaving(row.id, true)
  try {
    await bindFeishuAgent(row.id, null)
    ElMessage.success('绑定删除成功')
    await refreshBindingData()
  } catch (error) {
    ElMessage.error(error?.message || '删除绑定失败')
  } finally {
    markSaving(row.id, false)
  }
}
```

删除请求成功前不从 `configs` 中移除行，失败时原列表保持不变。

- [ ] **Step 8: 删除旧行内绑定代码**

移除以下旧页面成员：

```text
validAgentId
saveBinding
unbind
```

保留：

```text
agentName
isInvalidBinding
bindingText
bindingTagType
isSaving
```

确保失效绑定仍显示“绑定已失效”，并能进入编辑弹窗重新选择启用智能体。

- [ ] **Step 9: 运行 AI 前端测试**

Run:

```text
node --test frontend/modules/ai/tests/*.test.mjs
```

Expected: 全部 PASS。

- [ ] **Step 10: 构建前端**

Run（工作目录 `frontend/web-shell`）：

```text
npm run build
```

Expected: Vite 构建成功，生成 `AiFeishuBindingManage` 页面 chunk。

- [ ] **Step 11: 提交**

```text
git add frontend/modules/ai/src/views/AiFeishuBindingManage.vue frontend/modules/ai/tests/feishu-binding-static.test.mjs
git commit -m "feat(ai): 完善飞书绑定 CRUD 管理"
```

---

### Task 4: 集成验证与代码审查

**Files:**

- Verify: `modules/module-feishu/module-feishu-core`
- Verify: `frontend/modules/ai`
- Verify: `frontend/web-shell`

**Interfaces:**

- Consumes: Tasks 1-3 的后端筛选参数和前端 CRUD。
- Produces: 可交付的 CRUD 页面；不产生数据库迁移。

- [ ] **Step 1: 运行飞书 core 全量测试**

Run:

```text
mvn -pl modules/module-feishu/module-feishu-core -am test
```

Expected: BUILD SUCCESS，原绑定保存、运行配置和新增分页筛选测试全部通过。

- [ ] **Step 2: 运行 AI 前端测试**

Run:

```text
node --test frontend/modules/ai/tests/*.test.mjs
```

Expected: 全部 PASS。

- [ ] **Step 3: 运行前端生产构建**

Run（工作目录 `frontend/web-shell`）：

```text
npm run build
```

Expected: 构建成功。

- [ ] **Step 4: 检查数据库和运行时边界**

Run:

```text
git diff -- modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config frontend/modules/ai
rg -n "CREATE TABLE|ALTER TABLE|SchemaInitializer|resolveForMessage|getActiveAgentId" modules/module-feishu/module-feishu-core/src/main/java frontend/modules/ai
```

Expected:

- 本次改动不包含建表、改表或初始化器；
- 不修改 `resolveForMessage`、`getActiveAgentId` 等运行时路由；
- 前端只使用配置摘要和绑定接口。

- [ ] **Step 5: 检查敏感字段和生成产物**

Run:

```text
rg -n "appSecret|verificationToken|encryptKey" frontend/modules/ai/src/views/AiFeishuBindingManage.vue frontend/modules/ai/src/components/AiFeishuBindingDialog.vue
git status --short
git diff --check
```

Expected:

- 两个 Vue 文件不包含敏感字段；
- `target/`、`dist/` 未进入 Git 改动；
- 无空白错误；既有无关工作区改动保持不变。

- [ ] **Step 6: 请求代码审查**

审查范围必须覆盖：

```text
bound 三态查询兼容性
新增只能选择未绑定配置
编辑锁定飞书配置
失效绑定可恢复
删除仅解除绑定
请求失败不提前改列表
弹窗状态重置
无敏感字段泄漏
```

Critical 和 Important 问题修复后，重新运行 Steps 1-3。

- [ ] **Step 7: 交付说明**

明确告知用户：

```text
本次没有新增数据库 SQL；继续使用 ps_feishu_config.agent_id。
新增、编辑、删除均复用原 agent-binding 接口。
```

---

## 自检结果

- 后端 `bound=true/false/null` 三态分别有测试与实现步骤。
- 新增、编辑、删除、失效绑定、相同值不重复提交和取消删除均有明确交互。
- 页面、弹窗和 API 类型命名在所有任务中一致。
- 删除语义始终为 `agentId=null`，未引入物理删除。
- 未引入新表、字段、初始化器、模块依赖或运行时改动。
- 计划未包含占位内容或未定义接口。
