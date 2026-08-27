# 智能体模型配置管理前端设计

## 1. 背景

当前仓库前端采用 `frontend/web-shell` 主壳加 `frontend/modules` 源码级插件的模式。用户提供的 `vibe-coding-prompt.md` 原始需求是一个独立 Vue 3 / Vite 应用，用于管理阿里云百炼 DashScope 和自定义大模型配置。

根据仓库规范，该能力不应落成独立前端应用，而应作为主壳可加载的前端插件页面接入。用户已确认采用方案 A，并指定“模型配置管理放到智能体管理中”，因此本设计将功能落在 `frontend/modules/ai` 模块内。

## 2. 目标

- 在智能体管理能力下新增“模型配置管理”页面，用于集中维护大模型调用配置。
- 支持阿里云百炼和自定义两类模型提供商。
- 保留纯前端持久化能力，使用浏览器 `localStorage` 保存配置，暂不依赖后端接口。
- 支持开发、预发、生产三个环境的配置隔离。
- 支持配置增删改查、搜索筛选、连接测试、复制、导入、导出和模型模板创建。
- 页面接入主壳菜单与路由，不修改前端固定开发端口 `15200`。

## 3. 非目标

- 不创建新的独立 Vite 项目。
- 不修改 `frontend/web-shell` 端口配置。
- 不新增后端数据库表或后端接口。
- 不真实调用阿里云百炼接口，连接测试先采用前端模拟结果。
- 不把 API Key 明文导出到 JSON 文件。

## 4. 页面归属与路由

新增页面归属 `frontend/modules/ai`。

- 菜单名称：模型配置管理
- 页面路由：`/ai/model-configs`
- 路由名称：`AiModelConfigManage`
- 页面文件：`frontend/modules/ai/src/views/AiModelConfigManage.vue`
- 模块菜单：挂到 AI 插件菜单中，与“智能体对话”“智能体管理”同级展示。

由于当前 AI 模块菜单是扁平结构，本次保持扁平接入，避免改造主壳菜单结构。后续如果主壳支持二级菜单，可再把“智能体管理”“提示词管理”“模型配置管理”收拢到统一父菜单。

## 5. 功能设计

### 5.1 配置列表

配置列表作为默认视图，提供表格化管理能力。

表格字段：

- 名称
- 提供商
- 模型 ID
- 环境
- 状态
- 最后测试结果
- 操作

顶部能力：

- 按名称、模型 ID 搜索。
- 按提供商筛选。
- 按启用状态筛选。
- 当前环境切换：开发、预发、生产。
- 新建配置。
- 导入 JSON。
- 导出当前环境配置 JSON。

行内操作：

- 测试连接。
- 复制配置。
- 编辑配置。
- 删除配置。

### 5.2 模型模板

模型模板作为第二个视图，用于快速创建配置。

模板类型：

- 阿里云百炼
- 自定义

阿里云百炼模板展示模型分组，点击具体模型后打开配置编辑抽屉，并预填：

- 提供商：阿里云百炼
- Endpoint：`https://dashscope.aliyuncs.com/compatible-mode/v1`
- 模型 ID：所选模型
- 默认参数：`temperature = 0.7`、`topP = 0.8`、`maxTokens = 4096`

自定义模板打开空白配置编辑抽屉，由用户输入 Endpoint 和模型 ID。

### 5.3 配置编辑抽屉

编辑器采用抽屉形式，避免离开当前列表上下文。

字段分组：

- 基本信息：名称、描述、环境、状态。
- API 配置：提供商、Endpoint URL、API Key、模型 ID。
- 模型参数：temperature、topP、maxTokens。
- 标签：新增、删除多个标签。

校验规则：

- 名称必填。
- Endpoint 必填。
- API Key 必填。
- 模型 ID 必填。

快捷键：

- `Ctrl/Cmd + N`：新建配置。
- `Esc`：关闭抽屉或弹窗。

### 5.4 连接测试

连接测试先采用前端模拟实现。

测试结果包含：

- 状态：测试中、成功、失败、未测试。
- 延迟毫秒数。
- 结果详情。
- 测试时间。

测试结果写回对应配置，随配置一起持久化。

### 5.5 导入导出

导出：

- 仅导出当前环境配置。
- API Key 必须脱敏，导出值使用 `***`。
- 文件名包含环境和日期，便于归档。

导入：

- 支持选择 JSON 文件。
- 导入配置合并到当前环境。
- 导入数据中的环境字段统一改写为当前环境，保证环境隔离。
- 对缺失关键字段的数据做跳过处理，并给出提示。

## 6. 数据模型

核心字段：

```js
{
  id: string,
  name: string,
  description: string,
  provider: 'bailian' | 'custom',
  endpoint: string,
  apiKey: string,
  modelId: string,
  env: 'dev' | 'staging' | 'prod',
  enabled: boolean,
  temperature: number,
  topP: number,
  maxTokens: number,
  tags: string[],
  lastTestResult: {
    status: 'success' | 'failed' | 'untested',
    latency: number,
    message: string,
    testedAt: string
  },
  createdAt: string,
  updatedAt: string
}
```

## 7. 百炼模型清单

必须包含以下模型：

- 文本推理：`qwen3.8-max-preview`、`qwen3.7-max`、`qwen3.7-plus`、`qwen3.6-plus`、`qwen3.6-flash`、`qwen3-max`、`qwen-plus`、`qwen-turbo`、`qwen-long`
- 代码模型：`qwen3-coder-next`
- 多模态/视觉：`qwen3-vl-max`、`qwen3-vl-plus`
- 全模态：`qwen3.5-omni-plus`、`qwen3.5-omni-plus-realtime`
- 开源可自部署：`qwen3.5-122b-a10b`、`qwen3.5-32b`、`qwen3.5-7b`
- 向量/重排：`text-embedding-v4`、`tongyi-embedding-vision-plus`、`qwen3-rerank`

## 8. 文件拆分

为遵守前端代码行数规范，本功能拆分为多个小文件。

- `frontend/modules/ai/src/views/AiModelConfigManage.vue`
  - 页面布局、Tab、表格、抽屉调度。
- `frontend/modules/ai/src/model-config/bailianModels.js`
  - 提供商定义、百炼 Endpoint、百炼模型分组。
- `frontend/modules/ai/src/model-config/modelConfigStore.js`
  - localStorage 初始化、版本控制、CRUD、环境隔离、导入导出数据处理。
- `frontend/modules/ai/src/model-config/connectionTest.js`
  - 前端连接测试模拟、延迟和失败结果生成。
- `frontend/modules/ai/tests/model-config-static.test.mjs`
  - 静态验证路由、菜单、模型清单、localStorage 版本和导出脱敏规则。

## 9. 样式设计

- 延续 AI 模块已有的轻量后台风格。
- 使用浅色背景、中性灰和阿里云百炼橙色 `#ff6a00`。
- 使用 Element Plus 表格、抽屉、表单、标签、按钮。
- 卡片圆角不超过 8px。
- 不新增独立应用侧边栏，避免与主壳菜单重复。
- 页面在桌面和移动宽度下均不出现文本遮挡和按钮溢出。

## 10. 初始示例数据

首次打开页面时写入 5 条示例数据：

1. `Qwen3.7-Max 旗舰模型`，模型 `qwen3.7-max`，环境 `dev`，启用，测试成功。
2. `Qwen3.7-Plus 均衡模型`，模型 `qwen3.7-plus`，环境 `dev`，启用，测试成功。
3. `Qwen3.6-Flash 极速模型`，模型 `qwen3.6-flash`，环境 `prod`，启用，测试成功。
4. `Qwen3-Coder-Next 代码模型`，模型 `qwen3-coder-next`，环境 `staging`，启用，测试成功。
5. `Qwen3.5-Omni-Plus 全模态`，模型 `qwen3.5-omni-plus`，环境 `prod`，启用，未测试。

localStorage 使用版本号控制，模型清单或示例结构更新时可覆盖旧缓存。

## 11. 验证计划

最小验证：

- 运行 `node --test frontend/modules/ai/tests/model-config-static.test.mjs`。
- 运行 `cd frontend/web-shell && npm run build`。

人工验证：

- 访问 `/ai/model-configs`。
- 检查菜单是否出现“模型配置管理”。
- 检查默认示例数据是否按环境隔离展示。
- 新建、编辑、复制、删除配置。
- 测试连接后结果持久化。
- 导出 JSON 时 API Key 是否已脱敏。
- 导入 JSON 后是否合并到当前环境。

## 12. 风险与处理

- 当前 AI 模块已有 `AiAgentManage.vue` 页面较大，本次不继续向该文件堆叠模型配置逻辑，而是新增独立页面。
- 原提示词要求“左侧固定侧边栏”，但主壳已有导航，本次改为页面内 Tab，保证与平台体验一致。
- 当前实现不接后端，未来如果需要数据库持久化，可在保持页面交互不变的前提下替换 `modelConfigStore.js` 的存储实现。

