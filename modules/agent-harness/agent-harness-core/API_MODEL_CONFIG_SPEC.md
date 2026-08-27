# AI 模型配置管理接口规范

## 1. 路径约定

- 后端真实 Controller 路径：`/api/biz/ai/model-configs`
- 前端 request baseURL：`/api`
- 前端实际调用路径：`/biz/ai/model-configs`

也就是说，前端代码中不要再手动拼接 `/api`，否则会变成 `/api/api/biz/ai/model-configs`。

## 2. 统一返回体

所有接口返回平台统一结构：

```json
{
  "code": 200,
  "msg": "success",
  "data": {}
}
```

后端响应永不返回明文 `apiKey`，只返回 `apiKeyMasked`。前端展示、列表、导出都只能使用 `apiKeyMasked`。

编辑模型配置时：

- `apiKey` 传空字符串、`null` 或不传：保留原密钥
- `apiKey` 传 `***` 或以 `***` 开头的脱敏占位：保留原密钥
- `apiKey` 传新的明文值：覆盖原密钥

## 3. 数据结构

### 3.1 查询参数

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `env` | string | 否 | 运行环境，支持 `dev`、`staging`、`prod` |
| `provider` | string | 否 | 模型供应商，支持 `bailian`、`custom` |
| `status` | string | 否 | 启停状态，支持 `enabled`、`disabled` |
| `keyword` | string | 否 | 配置名称或模型 ID 关键字 |

### 3.2 新增/编辑请求体

```json
{
  "configName": "生产百炼",
  "description": "项目工作台默认模型",
  "provider": "bailian",
  "endpoint": "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
  "apiKey": "sk-xxxx",
  "modelId": "qwen-plus",
  "env": "prod",
  "enabled": true,
  "temperature": 0.7,
  "topP": 0.8,
  "maxTokens": 4096,
  "tags": ["项目", "默认"]
}
```

新增时 `configName`、`provider`、`endpoint`、`apiKey`、`modelId`、`env` 必填。编辑时 `apiKey` 可用空值或 `***` 保留原密钥。

### 3.3 响应对象

```json
{
  "id": 1,
  "configName": "生产百炼",
  "description": "项目工作台默认模型",
  "provider": "bailian",
  "endpoint": "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
  "apiKeyMasked": "sk-****5678",
  "modelId": "qwen-plus",
  "env": "prod",
  "enabled": true,
  "temperature": 0.7,
  "topP": 0.8,
  "maxTokens": 4096,
  "tags": ["项目", "默认"],
  "lastTestStatus": "untested",
  "lastTestLatency": 0,
  "lastTestMessage": "尚未测试",
  "lastTestedAt": null,
  "createdAt": "2026-07-23T10:00:00",
  "updatedAt": "2026-07-23T10:05:00"
}
```

## 4. 接口清单

### 4.1 查询模型配置列表

- 后端路径：`GET /api/biz/ai/model-configs`
- 前端路径：`GET /biz/ai/model-configs`
- 查询参数：`env`、`provider`、`status`、`keyword`
- 业务说明：返回模型配置列表，支持按启停状态 `enabled/disabled` 筛选。

响应示例：

```json
{
  "code": 200,
  "msg": "success",
  "data": [
    {
      "id": 1,
      "configName": "生产百炼",
      "provider": "bailian",
      "apiKeyMasked": "sk-****5678",
      "modelId": "qwen-plus",
      "env": "prod",
      "enabled": true
    }
  ]
}
```

### 4.2 查询模型配置详情

- 后端路径：`GET /api/biz/ai/model-configs/{id}`
- 前端路径：`GET /biz/ai/model-configs/{id}`
- 路径参数：`id` 模型配置主键
- 业务说明：返回单条模型配置详情，密钥仅返回 `apiKeyMasked`。

### 4.3 新增模型配置

- 后端路径：`POST /api/biz/ai/model-configs`
- 前端路径：`POST /biz/ai/model-configs`
- 请求体：见“新增/编辑请求体”
- 业务说明：创建新的模型配置，新增时 `apiKey` 必填。

### 4.4 编辑模型配置

- 后端路径：`PUT /api/biz/ai/model-configs/{id}`
- 前端路径：`PUT /biz/ai/model-configs/{id}`
- 路径参数：`id` 模型配置主键
- 请求体：见“新增/编辑请求体”
- 业务说明：更新模型配置。`apiKey` 为空或 `***` 表示保留原密钥。

### 4.5 删除模型配置

- 后端路径：`DELETE /api/biz/ai/model-configs/{id}`
- 前端路径：`DELETE /biz/ai/model-configs/{id}`
- 路径参数：`id` 模型配置主键
- 业务说明：逻辑删除模型配置。

响应示例：

```json
{
  "code": 200,
  "msg": "success",
  "data": null
}
```

### 4.6 复制模型配置

- 后端路径：`POST /api/biz/ai/model-configs/{id}/copy`
- 前端路径：`POST /biz/ai/model-configs/{id}/copy`
- 路径参数：`id` 源模型配置主键
- 业务说明：复制一份新配置，并将连接测试状态重置为未测试。

### 4.7 测试模型连接

- 后端路径：`POST /api/biz/ai/model-configs/{id}/test`
- 前端路径：`POST /biz/ai/model-configs/{id}/test`
- 路径参数：`id` 模型配置主键
- 业务说明：触发模型连接测试，并更新最近一次测试状态。

响应示例：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "status": "success",
    "latency": 35,
    "message": "连接校验通过",
    "testedAt": "2026-07-23T10:10:00"
  }
}
```

### 4.8 批量导入模型配置

- 后端路径：`POST /api/biz/ai/model-configs/import`
- 前端路径：`POST /biz/ai/model-configs/import`
- 业务说明：批量导入模型配置，逐条执行校验和创建。

请求示例：

```json
{
  "configs": [
    {
      "configName": "生产百炼",
      "provider": "bailian",
      "endpoint": "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
      "apiKey": "sk-xxxx",
      "modelId": "qwen-plus",
      "env": "prod"
    }
  ]
}
```

响应示例：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "successCount": 1,
    "skippedCount": 0
  }
}
```

### 4.9 导出模型配置

- 后端路径：`GET /api/biz/ai/model-configs/export`
- 前端路径：`GET /biz/ai/model-configs/export`
- 查询参数：`env`、`provider`、`status`、`keyword`
- 业务说明：按查询条件导出模型配置，导出内容不包含明文 `apiKey`。

## 5. 前端对接建议

前端建议封装为独立 API 文件，例如 `modelConfigApi.js`：

```js
export function listModelConfigs(params) {
  return request.get('/biz/ai/model-configs', { params })
}

export function updateModelConfig(id, data) {
  return request.put(`/biz/ai/model-configs/${id}`, data)
}
```

页面编辑弹窗回显时，把后端返回的 `apiKeyMasked` 放入密钥输入框即可。用户未改动时提交 `***` 或空值，后端会保留原密钥。
