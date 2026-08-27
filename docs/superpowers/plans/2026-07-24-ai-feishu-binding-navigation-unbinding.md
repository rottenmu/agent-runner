# 飞书渠道绑定导航与解绑能力 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为飞书渠道绑定页面增加返回、明确的新增绑定和解绑操作，并提供语义明确的解绑 API。

**Architecture:** 后端增加 Controller 级解绑别名，并继续委托既有 `FeishuConfigService.bindAgent(id, null)`，不扩展数据库或 Service 契约。前端保留既有绑定编辑弹窗，使用新 API 完成行内解绑；返回按钮通过 Vue Router 回到智能体管理页。

**Tech Stack:** Java 17、Spring Boot 3.4.5、JUnit 5、Vue 3、Vue Router、Element Plus、Node.js test runner、Vite

## Global Constraints

- 不新增数据库表、字段、SQL、初始化器或 MySQL 迁移。
- 不修改 `FeishuConfigService`、Mapper 和运行时消息路由。
- 解绑只将 `ps_feishu_config.agent_id` 更新为 `null`，不删除飞书配置或智能体。
- 顶部按钮为返回智能体管理、刷新、新增绑定飞书。
- 行操作顺序为解绑、编辑、删除；编辑和删除保留现有行为。
- 当前目标文件包含既有未提交改动，实施后不得自动 `git add` 或 `git commit`。

---

## 文件结构

- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigController.java`：增加解绑 HTTP 别名。
- `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuConfigControllerTest.java`：覆盖解绑请求和空 `agentId`。
- `frontend/modules/ai/src/api/feishu-binding.js`：增加 `unbindFeishuAgent(configId)`。
- `frontend/modules/ai/src/views/AiFeishuBindingManage.vue`：增加返回、改名新增按钮、增加解绑按钮和解绑流程。
- `frontend/modules/ai/tests/feishu-binding-static.test.mjs`：覆盖新 API 和页面契约。

### Task 1: 提供飞书配置解绑 API 别名

**Files:**

- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigController.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuConfigControllerTest.java`

**Interfaces:**

- Produces: `PUT /api/biz/feishu/config/{id}/agent-unbinding`。
- Produces: `R<FeishuConfigResponse>`，其中 `agentId` 为 `null`。
- Consumes: `FeishuConfigService.bindAgent(Long id, String agentId)`。

- [ ] **Step 1: 编写解绑接口失败测试**

在 `bindsAgentToFeishuConfig` 后新增：

```java
@Test
void unbindsAgentFromFeishuConfig() throws Exception {
    mockMvc.perform(put("/api/biz/feishu/config/10/agent-unbinding"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(10))
            .andExpect(jsonPath("$.data.agentId").doesNotExist());

    assertThat(service.lastBoundAgentId).isNull();
}
```

在 `RecordingConfigService` 增加字段并在 `bindAgent` 中记录：

```java
private String lastBoundAgentId;

@Override
public FeishuConfigResponse bindAgent(Long id, String agentId) {
    lastBoundAgentId = agentId;
    FeishuConfigResponse response = response(id);
    response.setAgentId(agentId);
    return response;
}
```

- [ ] **Step 2: 运行 RED 测试**

Run:

```text
mvn -pl modules/module-feishu/module-feishu-core -am -Dtest=FeishuConfigControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，因为 Controller 尚未注册 `agent-unbinding` 路径。

- [ ] **Step 3: 增加 Controller 解绑别名**

在既有 `bindAgent` 方法后增加：

```java
/**
 * 通过 PUT /api/biz/feishu/config/{id}/agent-unbinding 解除指定飞书配置的智能体绑定。
 *
 * @param id 飞书配置主键，不允许为空且必须存在
 * @return 包含空 agentId 的脱敏飞书配置响应
 */
@PutMapping("/{id}/agent-unbinding")
public R<FeishuConfigResponse> unbindAgent(@PathVariable Long id) {
    return R.ok(configService.bindAgent(id, null));
}
```

- [ ] **Step 4: 运行 GREEN 测试**

Run:

```text
mvn -pl modules/module-feishu/module-feishu-core -am -Dtest=FeishuConfigControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS，解绑请求返回 200 且 Service 收到 `null`。

### Task 2: 增加前端返回、解绑和明确新增绑定入口

**Files:**

- Modify: `frontend/modules/ai/src/api/feishu-binding.js`
- Modify: `frontend/modules/ai/src/views/AiFeishuBindingManage.vue`
- Test: `frontend/modules/ai/tests/feishu-binding-static.test.mjs`

**Interfaces:**

- Produces: `unbindFeishuAgent(configId)`，调用 `PUT /biz/feishu/config/${configId}/agent-unbinding`。
- Produces: `backToAgentManage()`，调用 `router.push('/ai/agents')`。
- Produces: `unbindBinding(row)`，确认后调用 `unbindFeishuAgent(row.id)`，再调用 `refreshBindingData()`。

- [ ] **Step 1: 编写前端失败契约测试**

在静态测试增加：

```javascript
assert.match(api, /agent-unbinding/)
assert.match(api, /export function unbindFeishuAgent\(configId\)/)
assert.match(page, /useRouter/)
assert.match(page, /返回智能体管理/)
assert.match(page, /function backToAgentManage\(\)/)
assert.match(page, /router\.push\('\/ai\/agents'\)/)
assert.match(page, /新增绑定飞书/)
assert.doesNotMatch(page, />新增绑定</)
assert.match(page, />解绑</)
assert.match(page, /async function unbindBinding\(row\)/)
assert.match(page, /unbindFeishuAgent\(row\.id\)/)
assert.ok(
  page.indexOf('>解绑</') < page.indexOf('>编辑</')
    && page.indexOf('>编辑</') < page.indexOf('>删除</'),
  '行操作顺序应为解绑、编辑、删除'
)
```

- [ ] **Step 2: 运行 RED 测试**

Run:

```text
node --test frontend/modules/ai/tests/feishu-binding-static.test.mjs
```

Expected: FAIL，因为 API、返回函数、解绑按钮和新文案尚不存在。

- [ ] **Step 3: 增加前端解绑 API**

在 `feishu-binding.js` 的 `bindFeishuAgent` 后新增：

```javascript
export function unbindFeishuAgent(configId) {
  return request.put(`/biz/feishu/config/${configId}/agent-unbinding`)
}
```

- [ ] **Step 4: 调整页面顶部和行操作**

在脚本导入中增加：

```javascript
import { useRouter } from 'vue-router'
```

在状态声明前增加：

```javascript
const router = useRouter()
```

将顶部操作区调整为：

```vue
<div class="feishu-binding__header-actions">
  <el-button @click="backToAgentManage">返回智能体管理</el-button>
  <el-button :icon="Refresh" :loading="loading" @click="refreshBindingData">刷新</el-button>
  <el-button type="primary" @click="openCreateDialog">新增绑定飞书</el-button>
</div>
```

将行操作区调整为：

```vue
<div class="feishu-binding__actions">
  <el-button type="warning" plain :loading="isSaving(row.id)" @click="unbindBinding(row)">解绑</el-button>
  <el-button :disabled="isSaving(row.id)" @click="openEditDialog(row)">编辑</el-button>
  <el-button type="danger" plain :loading="isSaving(row.id)" @click="deleteBinding(row)">删除</el-button>
</div>
```

在 `openCreateDialog` 前增加：

```javascript
function backToAgentManage() {
  router.push('/ai/agents')
}
```

在 `deleteBinding` 前增加：

```javascript
async function unbindBinding(row) {
  try {
    await ElMessageBox.confirm(
      `确认解除“${row.configName}”与智能体的绑定关系？`,
      '解绑飞书渠道',
      { type: 'warning' }
    )
  } catch {
    return
  }
  markSaving(row.id, true)
  try {
    await unbindFeishuAgent(row.id)
    ElMessage.success('绑定已解除')
    await refreshBindingData()
  } catch (error) {
    ElMessage.error(error?.message || '解绑绑定失败')
  } finally {
    markSaving(row.id, false)
  }
}
```

保持 `deleteBinding` 原有实现，不改为调用新解绑 API。

- [ ] **Step 5: 运行 GREEN 测试**

Run:

```text
node --test frontend/modules/ai/tests/feishu-binding-static.test.mjs
```

Expected: PASS，API、返回、新增绑定文案和行操作顺序契约满足。

- [ ] **Step 6: 运行后端与前端回归**

Run:

```text
mvn -pl modules/module-feishu/module-feishu-core -am test
node --test frontend/modules/ai/tests/*.test.mjs
```

Expected: 后端 Reactor BUILD SUCCESS，AI 模块测试全部 PASS。

- [ ] **Step 7: 运行前端生产构建**

Working directory: `frontend/web-shell`

Run:

```text
npm run build
```

Expected: Vite 构建成功；既有第三方 PURE 注释和大 chunk 警告允许保留。

- [ ] **Step 8: 只读审查与边界检查**

Run:

```text
git diff --check
git status --short
```

只读审查必须确认解绑别名只委托 `bindAgent(id, null)`；未修改 Service、Mapper、数据库或运行时路由；返回按钮跳转智能体管理；顶部文案为新增绑定飞书；行操作顺序为解绑、编辑、删除；解绑失败不提前修改列表；解绑成功后的刷新失败只显示刷新警告。

- [ ] **Step 9: 保留实现改动待用户统一提交**

目标文件已有既有未提交改动。完成验证后只汇报本次修改范围和测试结果，不执行 `git add` 或 `git commit`。