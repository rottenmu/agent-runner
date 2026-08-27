# 智能体技能选择注册接口设计

## 1. 背景

智能体管理的技能创建页面目前要求用户手工填写 API 基础地址、API 路径和 HTTP 方法。平台已经通过系统模块提供 API 注册信息查询接口：

```text
GET /api/biz/sys/api-registry
```

为了减少路径和请求方法填写错误，技能创建页面需要支持搜索平台已注册接口，并将选中接口的路径和 HTTP 方法自动填入技能配置。

## 2. 目标

- 在“智能体管理 - 技能 - 新建技能”中提供注册接口搜索选择能力。
- 调用 `/api/biz/sys/api-registry` 获取接口信息。
- 只查询状态为启用的接口。
- 选中接口后自动填充技能的 `apiConfig.path` 和 `apiConfig.method`。
- `apiConfig.baseUrl` 继续由用户手工填写，禁止写死开发环境端口或地址。
- 保留路径和 HTTP 方法的手工修改能力。
- 不改变现有技能创建、编辑和执行接口契约。

## 3. 非目标

- 不在技能配置表中保存 `api_registry.id`。
- 不让已创建技能与 API 注册表建立运行时强关联。
- 不根据注册表变化自动更新已有技能。
- 不由 AI 后端增加 API 注册信息代理接口。
- 不调整系统模块现有 API 注册表查询接口。
- 不自动生成基础地址、鉴权请求头或请求参数值。

## 4. 方案

采用“AI 前端模块直接查询注册接口并复制配置”的方式。

AI 前端模块新增自身的 API 注册信息查询方法，直接请求系统模块公开的后端契约。前端不导入 `frontend/modules/sys` 的源码，避免形成前端插件之间的编译期依赖。

选择接口只是一种创建配置的辅助方式。选中时将接口的当前 `path` 和 `method` 复制到技能表单，保存后技能继续使用自身配置快照。API 注册表后续重新扫描、停用或修改展示信息，不会隐式改变已有技能。

## 5. 接口契约

### 5.1 请求

```http
GET /api/biz/sys/api-registry?status=1&keyword={keyword}
Authorization: {当前登录令牌}
```

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `status` | 是 | 固定传 `1`，只查询启用接口 |
| `keyword` | 否 | 搜索接口路径、名称、摘要或 Controller 名称 |

请求继续复用主壳的 Axios 实例，由统一请求拦截器注入登录令牌。

### 5.2 响应

后端返回平台统一响应体：

```json
{
  "code": 200,
  "msg": "success",
  "data": [
    {
      "id": 1,
      "moduleCode": "sys",
      "moduleName": "系统管理",
      "method": "GET",
      "path": "/api/biz/sys/user",
      "apiName": "listUsers",
      "summary": "查询用户列表",
      "deprecated": false,
      "status": 1
    }
  ]
}
```

前端必须先校验 `code === 200`，再读取 `data`。对非数组数据按空列表处理。

## 6. 前端结构

### 6.1 API 调用

在 AI 前端模块内增加 API 注册信息查询文件，职责仅限：

- 调用 `/biz/sys/api-registry`。
- 透传 `status` 和 `keyword` 查询条件。
- 继续复用 `frontend/web-shell/src/api/request.js`。

### 6.2 接口选择组件

新增独立的注册接口选择组件，避免继续扩大现有 `AiAgentManage.vue`。

组件职责：

- 管理搜索关键字、加载状态和候选接口列表。
- 打开或输入搜索词时查询启用接口。
- 过滤 `deprecated === true` 的接口。
- 统一格式化选项展示内容。
- 选中后向父页面返回完整接口对象。
- 查询失败时展示错误提示并保留手工配置能力。

组件不负责保存技能，也不直接修改技能表单。

### 6.3 页面接入

技能抽屉在“启用状态”和“API 基础地址”之间增加“选择注册接口”字段。

父页面收到选中事件后执行：

```text
skillForm.apiConfig.path = selected.path
skillForm.apiConfig.method = selected.method
```

父页面不得修改：

- `skillForm.apiConfig.baseUrl`
- `skillForm.apiConfig.headers`
- `skillForm.apiConfig.timeoutMillis`
- 技能名称、描述、智能体和提示词模板

## 7. 交互规则

- 选择框支持搜索和清空。
- 初次打开选择框时查询启用接口。
- 输入关键字后执行短延迟查询，减少连续请求。
- 选项优先显示模块名称、HTTP 方法、接口路径和摘要。
- HTTP 方法使用稳定的短标签展示，接口路径允许换行或省略显示，不能撑破抽屉。
- 选中接口后，路径和 HTTP 方法立即回填。
- 用户可以继续手工修改回填结果。
- 清空选择仅清空选择状态，不反向清空已经回填的路径和方法，避免误删用户调整后的配置。
- 编辑已有技能时不要求恢复注册接口选中状态。

## 8. 权限和异常处理

- 查询接口要求当前用户具备 `sys:api:list` 权限。
- 返回码不是 `200` 时使用响应中的 `msg` 提示。
- 网络失败时显示统一错误提示，不关闭技能抽屉。
- 查询失败不影响用户手工填写 API 配置和保存技能。
- 连续搜索产生并发请求时，仅采用最后一次有效查询结果，避免旧响应覆盖新结果。

## 9. 数据与兼容性

技能保存请求保持现有结构：

```json
{
  "name": "query_project",
  "description": "查询项目数据",
  "apiConfig": {
    "enabled": true,
    "baseUrl": "http://backend.example.com",
    "path": "/api/biz/project/list",
    "method": "GET",
    "headers": {},
    "timeoutMillis": 3000
  }
}
```

不新增数据库字段，不修改 `AiManagedSkillRequest`、`AiSkillApiConfigRequest` 或技能执行器。已有技能创建、编辑和调用行为保持兼容。

## 10. 测试与验证

实施阶段按测试先行完成以下验证：

1. API 查询函数正确请求 `/biz/sys/api-registry`，并传递 `status=1` 与关键字。
2. 统一响应体成功时正确提取 `data`，异常响应产生明确错误。
3. 废弃接口不会出现在候选列表中。
4. 选择接口只回填 `path` 和 `method`。
5. 回填时不会覆盖 `baseUrl`、请求头和超时配置。
6. 清空选择不会清空已回填配置。
7. 查询失败后仍可手工填写并保存技能。
8. AI 前端现有静态测试继续通过。
9. `frontend/web-shell` 执行 `npm run build` 成功。

## 11. 预计改动范围

- `frontend/modules/ai/src/api/`：增加 API 注册信息查询方法。
- `frontend/modules/ai/src/components/`：增加注册接口选择组件。
- `frontend/modules/ai/src/views/AiAgentManage.vue`：接入组件和回填事件。
- `frontend/modules/ai/tests/`：增加查询契约和回填规则测试。

本次不修改 `modules/module-sys`、`modules/ai-agent-spring-boot-starter` 和数据库结构。
