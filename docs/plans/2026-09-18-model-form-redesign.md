# 模型「新建 / 编辑」表单改造方案（对齐参考图）

> 触发：用户给了一张「添加模型」弹窗截图，要求参考实现。
> 决策（用户已选）：① 高级配置（工具调用/图片输入/思考模式/自定义协议）**先不做**；
> ② 测试连接**如实接现有模拟接口**；③ 现有字段**全部保留**，在图的基础上补齐。

## 一、参考图逐项落位

| 参考图元素 | 本次处理 | 落点 |
|---|---|---|
| 标题「添加模型」 | 沿用既有 `新增{def.title}` | `ResourceManagerModal` 头 |
| 供应商（仅支持 OpenAI 兼容协议 API） | **保留**，label 改为该措辞 | `defs.js` model.fields |
| 供应商下拉值「自定义」 | 已有 `custom · 自定义 OpenAI 兼容` | 不变 |
| 接口地址 + 占位符 `https://api.example.com/v1/chat/completions` | **保留**，占位符对齐 | 字段表 + 预设 |
| API Key + 右侧「测试连接」按钮 | **新增按钮**（见第二节） | 字段表 `testable` + 槽位渲染 |
| 模型名称 + 占位符 `输入模型参数值，例如 gpt-4o 或 openai/gpt-4o` | **保留**，占位符对齐 | 字段表 |
| 高级配置 4 复选框 | **不做**（用户决策①） | — |
| 输入 / 输出上下文 + 预设值 | **不做**（用户决策①） | — |
| 取消 / 保存 | **保留**，新增态按钮文案对齐为「保存」 | 弹窗 foot |

## 二、测试连接：为什么必须标注「模拟」

后端 `AiModelConfigService.simulateConnection()` **不发任何 HTTP 请求**：

```java
private boolean hasConnectionFields(entity) {
    return StringUtils.hasText(entity.getEndpoint())
        && StringUtils.hasText(entity.getApiKey())
        && StringUtils.hasText(entity.getModelId());
}
private int simulatedLatency(entity) {   // 与网络无关的假延迟
    return Math.max(1, Math.min(999, endpoint.length() + modelId.length()));
}
```

实测（2026-09-18，配置 id=1）：

```json
POST /api/biz/ai/model-configs/1/test
{"code":200,"msg":"success","data":{"status":"success","latency":77,"message":"连接校验通过",...}}
```

**结论**：UI 若把「连接校验通过」原样展示，用户会以为真的连通了 —— 这与「网关 200 但信封里是 404」
同一种误读。因此：

- 按钮文案保留「测试连接」（用户预期），但结果文案由 `formatModelTestResult()` 统一拼成
  `OK: 连接校验通过（模拟校验） · 77ms`
- 按钮旁常驻一行灰字说明，不依赖用户点完才知道
- 文案**只此一处**（`api/resources.js` 的 `MODEL_TEST_NOTE` / `formatModelTestResult`），
  弹窗与列表页共用，避免两处措辞漂移

**接口按 id 定位**（`@PostMapping("/{id}/test")`），所以：

- 新建态没有 id → 按钮**置灰** + `title` 提示「先保存后测试」
- 编辑态才能测

## 三、校验：以「后端真实边界」为准，不用猜的

原 `defs.js` 里 model 的 `validate` 是我按常识猜的（温度 0~2 / maxTokens 正整数），
**与后端不符**。实测（探测请求，造的脏数据已清理）：

| 字段 | 后端真实规则 | 原来猜的 |
|---|---|---|
| `configName` | ≤ 128 字 | 无 |
| `endpoint` | ≤ 512 字 | 无 |
| `temperature` | **0 ~ 1** | 0 ~ 2 ❌ |
| `topP` | 0 ~ 1 | 0 ~ 1 ✅ |
| `maxTokens` | **256 ~ 8192** | 正整数 ❌ |

后端报错文案原样：`temperature 必须在 0 到 1 之间` / `maxTokens 必须在 256 到 8192 之间`
/ `配置名称不能超过 128 个字符` / `服务地址不能超过 512 个字符`。

前端 `validate` 按上表**对齐后端**（提前拦，不用等一次 round-trip），措辞也用后端原话。

## 四、结构：不新建组件以外的机制

- 复用 `ResourceManagerModal`（弹窗壳 + 保存编排）与 `ResourceFormField`（字段控件），
  只在 `ResourceFormField` 加**具名插槽**支持「输入框 + 右侧按钮」并排（参考图的 API Key 行）
- 新增 `ModelCreateForm.vue` 只承载 model 的**字段编排**（分组、分组标题、按钮行、测试态）
- 弹窗对 `type === 'model'` 分流到新表单；`testResult` 由弹窗（父）持有，便于将来在页脚复用
- 行数硬约束：弹窗类组件 ≤200 行 → 新组件与改动后的弹窗都要量

### ⚠️ 踩坑：弹窗卡片**不能**抽成组件（曾把外壳抽成 `ResourceShellModal`）

为了压 `ResourceManagerModal` 的行数，一度把「卡片 + 标题栏 + 关闭按钮」抽成
`ResourceShellModal.vue`，于是 `n-modal` 的默认插槽根从**原生 div** 变成了**组件根**。

后果不是样式错乱，而是**弹窗内所有 `n-select` 的下拉彻底打不开**：

- `n-modal` 会给插槽根元素透传一个 `class="n-modal"`（模态定位靠它）。
  多套一层组件后这个透传的落点变了，连带把内层 `n-select` 的 Teleport
  目标解析成 `n-modal-scroll-content` 里的一个**空 text 节点**（nodeType 3）；
- `prepareAnchor` 对 text 节点调 `insertBefore` 直接抛
  `HierarchyRequestError: This node type does not support this method.`；
- Vue 把这个异常**吞成一条 console.error**，界面上只表现为「点了没反应」。

极易误判成「Playwright 点不动 naive-ui 下拉」，实际是应用 bug，
且**通用弹窗（智能体/MCP/技能/数据源）也一起中招**，不只是模型弹窗。

真机对照（`tmp/verify/`）：

| 实现 | 下拉 option 数 | 结论 |
|---|---|---|
| HEAD 版（`n-modal > 原生 div`） | 2 | 正常 |
| 抽出 `ResourceShellModal` 后 | 0 + 报错 | 复现 |
| 卡片 div 写回弹窗内（现状） | 2 | 恢复 |

**结论**：卡片 / 标题栏 / 关闭按钮的标记必须**直接写在 `ResourceManagerModal.vue` 内**，
宁可不「复用」也不抽这一层。定位证据脚本：`probe-head-modal2.mjs`、
`probe-generic-vs-model.mjs`、`probe-anchor-detail.mjs`、`probe-dropdown-stack.mjs`。

### 其它两个「反直觉」的战果

- **`n-select` 没有 `input`**：非 filterable 的 `n-select` 根下**不存在** `<input>`，
  用 `field.locator('input')` 定位会一直超时。稳定落点是 `.rff .n-select .n-base-selection`。
  下拉浮层 teleport 到 `body`，候选取 `page` 而非 `dialog`。
- **`defs.js` 有效行 303 超限** → 把模型专属枚举/预设（供应商、地址预设、运行环境、
  `numOrNull`）拆到同目录 `resources/modelOptions.js`，`defs.js` 回落到 290。

## 五、已知欠债（不在本次范围）

- `ResourcePage.vue` 620 行（有效行 462，未超 500 上限）—— 若要在列表页也加「测试」按钮，需先拆分
- `defs.js` model.fields 的 `required` 会被 `ResourceFormField` 渲染成 `*`，
  参考图**没有**任何 `*` 标记 → 必填改由后端 + 前端 `validate` 承载，字段表里标 `noMark: true`
- 后端 `testConnection` 仍是**模拟校验**（不发真实 HTTP），见第二节
