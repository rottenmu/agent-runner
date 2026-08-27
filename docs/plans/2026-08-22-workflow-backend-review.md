# 工作流后端实现检查报告

> 日期：2026-08-22 ｜ 检查对象：`modules/agent-harness/agent-harness-core/.../workflow/`（WfWorkflowEngine + WfRunService + 9 个模型类）
> 背景：前端工作流设计器已完成 Dify 风格改造（新增 llm/http/code/template 4 类节点），需核对后端执行引擎匹配度。

## 一、后端架构

```text
WfWorkflowService（CRUD/版本）→ WfWorkflowEngine（执行引擎）→ WfRunService（运行控制）
   定义: WfWorkflow / WfWorkflowVersion / WfTemplate
   运行: WfWorkflowRun（状态机: pending→running→manual_wait/success/failed/canceled）
   日志: WfWorkflowRunLog（action/result/input/output/message）
```

## 二、已具备且与前端匹配 ✅

| 能力 | 实现 | 匹配度 |
|---|---|---|
| 节点分发 | switch：start/end/task/async_task/condition/loop/manual/fallback | 与前端旧 8 类一致 |
| 条件分支 | SpEL 表达式 + `branchTarget`（是/否分支） | ✅ 前端 condition 表单对齐 |
| 变量模板 | `renderTemplate`：`{{input.xxx}}` / `{{ctx.xxx}}` / SpEL | ✅ template 节点可直接复用 |
| 重试 | task retry/retryInterval + 重试日志 | ✅ 前端表单对齐 |
| 人工审批 | manual_wait + approve/reject | ✅ 前端审批按钮对齐 |
| 异常兜底 | handleError → findFallbackNode → 兜底节点 | ✅ 前端 fallback 对齐 |
| **运行日志** | `WfWorkflowRunLog`：nodeId/nodeName/nodeType + action（enter/exit/error/retry/breakpoint/manual_wait…）+ result（pending/success/failed…）+ message | ✅ **与前端 applyRunStatus 完全匹配**（按 nodeId+action 着色/高亮） |
| 运行控制 | 启动/单步/恢复/审批/停止（WfRunService） | ✅ 前端工具栏对齐 |

## 三、发现的问题（前端改造后不匹配）❌

### P0-1：新节点类型后端不支持（示例必失败）
- 前端新增 `llm` / `http` / `code` / `template` 4 类节点
- 后端 `switch(type)` 无对应 case → **`default: throw new IllegalArgumentException("不支持的节点类型: " + type)`**
- 后果：示例工作流（开始→LLM→条件→HTTP/模板→结束）**运行即失败**

### P1-2：task 为模拟实现（无真实能力）
- `doTask` 仅支持 `taskType=echo`（渲染 prompt 后回显）与 `taskType=fail`（模拟失败）
- 代码注释明确：`// 简化实现，后续可接入技能 / API / LLM`
- 无模型调用、无 HTTP 客户端、无 JS 执行器

### P2-3：基础设施缺失
- 无 LLM 调用依赖（harness 已有 AiChatClient/模型体系可注入）
- 无 HTTP 客户端封装（Spring RestClient 可直接用）
- 无 JS 执行引擎（需引入 GraalJS 或轻量解释器）

## 四、适配方案

| 方案 | 做法 | 成本 |
|---|---|---|
| **A（完整实现，推荐）** | 后端 switch 增加 4 类 case：<br>① `llm`：注入 harness 模型链路（AiChatClient/DashScope）按 systemPrompt+inputMapping 调用<br>② `http`：Spring RestClient 按 method/url/headers/body 请求<br>③ `code`：GraalJS 受限执行（注入 input，取 return）<br>④ `template`：复用 renderTemplate | 中（需加依赖+模型注入） |
| **B（轻量映射）** | 前端 definitionSnapshot 序列化时将 4 类节点映射为 `task` + `taskType`（llm/api/code/template 字段透传），后端 doTask 扩展 taskType 分支 | 低（语义混在 task，表单字段透传） |
| C（暂缓） | 前端示例保留但不运行，仅编辑器体验 | 零 |

## 五、实施结果（2026-08-22 方案 A 已落地 ✅）

| 节点 | 实现 | 验证 |
|---|---|---|
| `llm` | 注入 AiChatClient（ObjectProvider 可空），按 systemPrompt + inputMapping 调用 | 链路编译/装配 ✓（真实调用依赖模型配置） |
| `http` | RestClient（method/url/headers/body，支持 GET/POST/PUT/PATCH/DELETE） | ✅ E2E：GET 前端页 success |
| `code` | Nashorn（nashorn-core 15.4）受限 JS，IIFE 包装（顶层 return 合法化），输入 input 对象 | ✅ E2E：`input.n*2` → 42 链路 success |
| `template` | 复用 renderTemplate（{{input.xxx}}/{{ctx.xxx}}） | ✅ E2E：`翻倍结果：{{output.result}}` 渲染 success |

- 引擎支持节点类型更新为 12 类：start/end/task/async_task/**llm/http/code/template**/condition/loop/manual/fallback
- 装配：AiWorkflowAutoConfiguration 引擎 bean 注入 AiChatClient；agent-harness-core pom 加 nashorn-core
- E2E 实测：code+template 链路 status=success（9 条日志，exit/success）；http 链路 status=success
- 坑：Nashorn ScriptEngine 顶层 return 非法 → IIFE `(function(){...})()` 包装
- 前端 applyRunStatus 依赖日志字段（nodeId/action/result）已匹配，运行可视化自动生效
