# module-ai 技能 ZIP 导入设计

## 1. 背景

`module-ai` 当前在智能体管理页面中提供 API 技能的新建、编辑、删除和提示词模板绑定能力。前端通过
`/api/biz/ai/skills` 调用 `AiSkillAdminController`，后端最终由
`AiAgentManagementService#createApiSkill` 完成名称校验、运行时技能注册、MyBatis-Plus 持久化和失败回滚。

本次增加 ZIP 技能导入能力。用户在技能页点击“导入技能”，通过弹窗拖入一个 ZIP 文件，手工点击
“开始导入”。后端安全解析 ZIP 根目录的 `skill.json`，转换为现有技能创建请求并调用
`createApiSkill` 创建技能。

## 2. 已确认决策

- 采用独立导入服务，不在 Controller 或浏览器中解析 ZIP。
- 一个 ZIP 只允许定义一个技能。
- ZIP 根目录使用 `skill.json`，不兼容 `SKILL.md`。
- 文件选择后不自动上传，用户点击“开始导入”后提交。
- 技能名称已存在时拒绝导入，不覆盖现有技能。
- 导入包不允许指定 `agentId`、`promptTemplateId` 或 `apiRegistryId`。
- 导入成功后技能处于未绑定状态，用户后续通过现有页面完成绑定。
- 文件格式和字段错误返回业务状态 `400`。
- ZIP 或解压内容超过限制返回业务状态 `413`。
- 持久化、技能注册表等服务端故障返回业务状态 `500`。
- 不新增数据库表、字段、SQL 初始化器或数据库迁移脚本。

## 3. 范围

### 3.1 包含

- 技能页“导入技能”入口。
- 单文件拖拽选择、文件信息展示和手工提交弹窗。
- multipart ZIP 上传接口。
- ZIP 安全校验和 `skill.json` 严格解析。
- 清单到 `AiManagedSkillRequest` 的转换。
- 调用现有 `createApiSkill` 创建 API 技能。
- 前后端自动化测试和相关模块构建验证。
- 对现有超长技能编辑页面做与本功能直接相关的最小组件拆分。

### 3.2 不包含

- 批量导入多个技能。
- 覆盖、合并或更新已有技能。
- 导入 `SKILL.md`、脚本、JAR、图片或其他资源文件。
- 自动绑定智能体、提示词模板或 API 注册表。
- ZIP 预览后再次确认。
- 导出技能 ZIP。
- 新增技能类型或改变现有 API 技能执行方式。

## 4. 总体架构

### 4.1 前端组件

#### `AiAgentManage.vue`

- 保留智能体管理页面级导航、筛选和数据刷新编排。
- 在技能页操作区显示“新建技能”和“导入技能”。
- 接收技能编辑、导入组件的成功事件，并重新获取技能列表。

#### `AiSkillEditorDrawer.vue`

- 从现有页面迁移技能新建、编辑表单及保存逻辑。
- 保持现有 API、字段、校验和用户交互不变。
- 保存成功后向父页面发送 `saved` 事件。

#### `AiSkillImportDialog.vue`

- 使用 Element Plus 拖拽上传区域。
- 设置 `auto-upload="false"`、`limit="1"` 和 `accept=".zip"`。
- 展示文件名和文件大小，允许删除或重新选择。
- 未选择合法文件时禁用“开始导入”。
- 上传期间禁止重复提交。
- 成功后清空文件、关闭弹窗并发送 `imported` 事件。
- 失败后保留文件和弹窗，展示后端错误信息。

#### `agent.js`

新增 `importSkillZip(file)`：

- 创建 `FormData`。
- 使用字段名 `file` 添加 ZIP。
- 调用 `/biz/ai/skills/import`。
- 不手工设置 multipart boundary，由请求库和浏览器生成。

### 4.2 后端组件

#### `AiSkillAdminController`

新增：

```text
POST /api/biz/ai/skills/import
Content-Type: multipart/form-data
参数：file
返回：R<AiManagedSkill>
```

Controller 只负责请求映射、统一返回体和导入异常到业务状态的转换，不读取 ZIP 条目。

#### `AiSkillZipImportService`

职责：

1. 校验上传文件是否为空、文件名和压缩大小。
2. 调用 `AiSkillZipParser` 获取导入清单。
3. 将清单转换为 `AiManagedSkillRequest`。
4. 强制保持 `agentId`、`promptTemplateId` 和 `apiRegistryId` 为空。
5. 调用 `AiAgentManagementService#createApiSkill`。

解析完成前不得调用创建方法。创建阶段继续复用现有名称冲突校验、运行时注册、持久化和回滚逻辑。

#### `AiSkillZipParser`

职责：

- 校验 ZIP 文件头。
- 使用 `ZipInputStream` 流式读取，不解压到磁盘。
- 限制条目数量和解压内容大小。
- 只接受根目录唯一的 `skill.json` 普通文件，拒绝包括目录在内的任何其他条目。
- 严格反序列化 JSON，拒绝未知字段。
- 输出 `AiSkillImportManifest`。

#### `AiSkillImportManifest`

导入专用 DTO，不复用带环境字段的管理端请求 DTO。嵌套 API 配置同样使用导入专用结构，从类型层面排除
`agentId`、`promptTemplateId` 和 `apiRegistryId`。

#### `AiSkillImportException`

表示可预期的导入错误，携带业务状态和安全的用户提示：

- `400`：文件或清单格式错误。
- `413`：压缩包、条目或解压内容超过限制。

未包装持久化或注册表异常，由统一异常处理链返回 `500`。

## 5. ZIP 包规范

### 5.1 目录结构

```text
skill-package.zip
└── skill.json
```

ZIP 必须恰好包含一个条目，并且该条目必须是根目录普通文件 `skill.json`。以下结构均非法：

```text
folder/skill.json
../skill.json
/skill.json
skill.json + README.md
两个同名 skill.json 条目
skill.json + 任意目录条目
```

### 5.2 `skill.json` 示例

```json
{
  "schemaVersion": 1,
  "name": "quality_query",
  "description": "根据业务参数查询质量检验结果",
  "readOnly": true,
  "apiConfig": {
    "enabled": true,
    "baseUrl": "https://api.example.com",
    "path": "/quality/query",
    "method": "POST",
    "headers": {
      "Authorization": "Bearer token"
    },
    "timeoutMillis": 3000
  }
}
```

### 5.3 字段规则

| 字段 | 必填 | 默认值 | 规则 |
| --- | --- | --- | --- |
| `schemaVersion` | 是 | 无 | 当前只允许整数 `1` |
| `name` | 是 | 无 | 去除首尾空白后复用现有技能名称校验 |
| `description` | 是 | 无 | 去除首尾空白后不能为空 |
| `readOnly` | 否 | `true` | 布尔值 |
| `apiConfig` | 是 | 无 | API 技能配置对象 |
| `apiConfig.enabled` | 否 | `true` | 布尔值 |
| `apiConfig.baseUrl` | 是 | 无 | 复用现有 API 技能地址校验 |
| `apiConfig.path` | 是 | 无 | 复用现有 API 技能路径校验 |
| `apiConfig.method` | 否 | `POST` | 支持现有 HTTP 方法集合 |
| `apiConfig.headers` | 否 | `{}` | 字符串到字符串的 JSON 对象 |
| `apiConfig.timeoutMillis` | 否 | `3000` | 必须大于 0 |

JSON 使用严格模式。未知字段、错误类型、整数配置中的小数值和显式出现的环境相关字段均返回 `400`，不静默忽略。

## 6. 文件安全限制

- 压缩文件最大 `2 MB`。
- `module-ai-autoconfig` 提供低优先级 Servlet Multipart 默认值：单文件 `2 MB`、单请求 `3 MB`、
  内存阈值 `2 MB`；应用显式配置可覆盖这些默认值，导入服务仍独立执行 `2 MB` 业务上限。
- 解压前在内存中校验 EOCD、中央目录、本地条目头和数据描述符的一致性，截断、篡改、加密、ZIP64 和不受支持的压缩包均直接拒绝。
- 中央目录条目数超过 `16` 时返回 `413`；只接受唯一根目录 `skill.json`，额外条目返回 `400`，且不会为判定非法结构而解压额外条目。
- `skill.json` 解压后最大 `256 KB`。
- 同时校验 `.zip` 扩展名和 ZIP 文件头，不信任 Content-Type。
- 不创建临时目录，不将任何 ZIP 条目写入文件系统。
- 拒绝绝对路径、反斜杠路径、嵌套路径、空名称和包含 `..` 的路径。
- 拒绝无法正常读取的加密、损坏或不受支持压缩包。
- 不执行或动态加载 ZIP 内任何内容。
- 日志只允许记录原始文件名、压缩大小和失败类别。
- 日志和异常信息不得输出完整 JSON、请求头、令牌或其他密钥。

前端文件校验只用于快速反馈，后端必须重复执行全部安全校验。

## 7. 请求与处理流程

1. 用户进入智能体管理的技能页。
2. 用户点击“导入技能”打开弹窗。
3. 用户拖入或选择一个 ZIP。
4. 前端显示文件名和大小。
5. 用户点击“开始导入”。
6. 前端使用 `FormData.file` 上传 ZIP。
7. Controller 将 `MultipartFile` 交给导入服务。
8. 导入服务完成文件级校验。
9. Parser 先校验 ZIP 中央目录和本地条目一致性，再受限解压并严格解析唯一的 `skill.json`。
10. 导入服务转换请求并清除环境绑定字段。
11. 导入服务调用 `createApiSkill`。
12. 创建成功后返回 `R<AiManagedSkill>`。
13. 前端关闭弹窗、清空选择、刷新技能列表并显示成功提示。

任何解析或校验失败都会在步骤 10 前终止，不产生数据库或运行时注册表变更。

## 8. 错误处理

| 场景 | 业务状态 | 行为 |
| --- | ---: | --- |
| 文件为空、扩展名错误、ZIP 文件头错误 | `400` | 保留弹窗和文件，显示具体错误 |
| 缺少、重复或嵌套 `skill.json` | `400` | 不调用创建方法 |
| 非法 JSON、未知字段、版本不支持 | `400` | 不调用创建方法 |
| 必填字段为空、字段类型或取值非法 | `400` | 不调用创建方法 |
| 技能名称已存在 | `400` | 沿用现有创建方法提示，不覆盖 |
| ZIP、条目数或解压内容超过限制 | `413` | 立即停止读取 |
| 技能持久化失败 | `500` | 沿用现有运行时注册回滚 |
| 技能注册表异常 | `500` | 不转换成格式错误 |

错误消息必须可供用户定位问题，但不得包含请求头值、密钥或完整清单。

Servlet 容器可能在 Controller 参数绑定前抛出 Multipart 异常。`module-ai-autoconfig` 必须按
`/api/biz/ai/skills/import` 请求路径提前转换：文件超限返回 HTTP `200` 与业务状态 `413`，
畸形 Multipart 返回 HTTP `200` 与业务状态 `400`，其他模块请求继续进入原有异常处理链。

## 9. 数据一致性

- 解析阶段完全只读。
- 一个 ZIP 只创建一个技能，不存在批量部分成功。
- 名称冲突由 `createApiSkill` 的现有同步校验负责。
- 运行时技能注册成功但数据库保存失败时，继续使用现有删除运行时技能的回滚机制。
- 本次不增加新的事务边界，不绕过现有创建方法直接写 Mapper。
- 导入成功后的技能与手工新建技能使用相同数据结构和运行时行为。

## 10. 页面拆分与代码规模

当前 `AiAgentManage.vue` 有 1299 行、约 1070 行有效代码，已超过页面组件 500 行上限。本次实施同步完成最小相关拆分：

- 将现有技能新建和编辑表单迁移到 `AiSkillEditorDrawer.vue`。
- 将 ZIP 导入交互实现于 `AiSkillImportDialog.vue`。
- 父页面只通过属性和 `saved`、`imported` 事件协调子组件。
- 技能相关样式随组件迁移，避免父页面继续堆积。

目标：

- `AiAgentManage.vue` 有效代码不超过 500 行。
- `AiSkillEditorDrawer.vue` 和 `AiSkillImportDialog.vue` 各自有效代码不超过 300 行。
- 前端单个函数有效代码不超过 50 行。
- 后端单个方法有效代码不超过 80 行。
- Controller 有效代码不超过 400 行，Service 有效代码不超过 500 行。

## 11. 测试设计

### 11.1 Parser 单元测试

- 正常 ZIP 能解析全部允许字段和默认值。
- UTF-8 中文名称、描述和请求头能正确解析。
- 空 ZIP、损坏 ZIP、伪造扩展名和错误文件头失败。
- 缺少、重复、嵌套 `skill.json`、目录条目和额外普通文件失败。
- 绝对路径、反斜杠路径和 `..` 路径失败。
- 非法 JSON、未知字段、未知版本和错误字段类型失败。
- ZIP 条目数、压缩大小和解压大小超限失败。

### 11.2 导入服务单元测试

- 有效清单只调用一次 `createApiSkill`。
- 转换后的请求中环境绑定字段为空。
- 解析失败时不调用 `createApiSkill`。
- 技能重名错误原样返回，不触发更新或覆盖。
- 持久化和注册表异常保持为 `500` 类服务端错误。

### 11.3 Controller 测试

- multipart `file` 参数上传成功并返回创建后的技能。
- 缺少文件、格式错误返回 `400`。
- 大小超限返回 `413`。
- 非预期服务异常返回 `500`。
- 返回体保持平台 `R<T>` 结构。

### 11.4 自动配置测试

- `ObjectMapper` 和管理服务存在时创建 Parser、导入服务和 Controller。
- 依赖缺失时保持现有条件装配语义。

### 11.5 前端测试

- 技能页显示“导入技能”按钮并可打开弹窗。
- 弹窗只保留一个 `.zip` 文件。
- 未选择文件时不能提交。
- 使用 `FormData` 的 `file` 字段调用导入接口。
- 上传期间不能重复提交。
- 成功后关闭弹窗并触发技能刷新。
- 失败后保留文件、保持弹窗并显示后端错误。
- 现有技能新建和编辑行为在组件拆分后保持不变。

## 12. 验证命令

后端优先运行最窄范围测试，再扩大验证：

```text
mvn -pl modules/module-ai/module-ai-core -am test
mvn -pl modules/module-ai/module-ai-autoconfig -am test
```

前端：

```text
node --test frontend/modules/ai/tests/*.test.mjs
cd frontend/web-shell
npm run build
```

最终运行 `git diff --check`，并检查所有新增或修改文件的有效代码行数。

## 13. 验收标准

- 技能页可以打开独立 ZIP 导入弹窗。
- 用户可以拖入一个 ZIP，查看文件信息并手工开始导入。
- 符合规范的 `skill.json` 能通过现有创建方法生成 API 技能。
- 导入技能不自动绑定智能体、提示词模板或 API 注册表。
- 重名技能不会被覆盖。
- 非法和超限压缩包不会产生数据库或运行时注册表变更。
- 用户能看到明确且不泄密的失败原因。
- 导入完成后技能列表立即刷新。
- 现有技能新建、编辑、删除和提示词模板绑定行为不回归。
- 不增加数据库结构或自动初始化逻辑。
- 前后端相关测试、构建、差异和代码规模检查通过。
