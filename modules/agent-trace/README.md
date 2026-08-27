# agent-trace — 智能体全链路监测 / 观测核心工程

智能体运行全链路的可观测性核心：将主链路（AiAgentService）/ RAG 检索等事件，
按 **OpenTelemetry GenAI 语义约定**（`gen_ai.*`）转换为 span 树并导出。

## 定位

- **观测语义**：对齐 OTel GenAI SemConv（operation.name / provider.name / request.model / usage.* / session.id / agent.name）
- **接入方式**：实现 `TraceObserver` 注册到 `TraceCollector`——事件源零侵入
- **架构参考**：AgentScope `OtelTracingMiddleware`（invoke_agent / chat / execute_tool 三层 span 树）

## 模块结构

```
agent-trace-core         观测核心（零依赖 OTel SDK，自包含模型）
  com.zimo.module.trace.genai
    GenAiAttributeNames     gen_ai.* 属性常量
    GenAiSpanKind           AGENT/LLM/TOOL/RETRIEVER/TASK/STEP（含 stepType 映射）
    GenAiOperationName      invoke_agent/chat/execute_tool/retrieval
    GenAiSpan               span 模型（name/kind/attributes/起止/状态）
    GenAiSpanExporter       导出 SPI（OTLP/日志可插拔）
    LogGenAiSpanExporter    默认结构化日志导出
    GenAiTraceObserver      TraceObserver 实现（onBegin 根 span / onStep 子 span / onEnd 导出）
agent-trace-autoconfig    自动装配
  GenAiTraceAutoConfiguration  装配 exporter + observer，注册到 TraceCollector
```

## 链路事件流

```
AiAgentService（invokeHarness）       RAG 检索
        │ TraceCollector.begin/step/end（分发）
        ▼
GenAiTraceObserver（已注册）
  onBegin → invoke_agent <name> [AGENT] 根 span
  onStep  → execute_tool <name> [TOOL] / 步骤 [STEP] 子 span
  onEnd   → 收尾（status/prompt/response/tokens）→ GenAiSpanExporter.export
        ▼
LogGenAiSpanExporter（默认） | 自定义 OTLP 导出器
```

## span 命名与属性（OTel GenAI 语义）

| span | 命名 | kind | 关键属性 |
|---|---|---|---|
| 根 | `invoke_agent <agentName>` | AGENT | gen_ai.session.id / agent.name / intent / trace_id |
| 工具 | `execute_tool <name>` | TOOL | gen_ai.tool.name / input / output |
| 步骤 | `<name>` | STEP/TASK | — |
| 收尾 | — | — | gen_ai.prompt / completion / usage.output_tokens |

## 扩展

- **自定义导出**：实现 `GenAiSpanExporter` 为 bean（如 OTLP → Jaeger/Tempo/ARMS），自动替换默认日志导出
- **模型调用 span**：LLM 节点（chat <model>）可在观测事件桥补充模型维度后映射为 LLM kind

## 验证

```bash
./mvnw test -pl modules/agent-trace/agent-trace-core   # 4 单测（span 树/kind 映射/截断）
```

运行期冒烟：对话触发后日志输出 `[genai-span] trace=… | invoke_agent … [AGENT] status=… dur=… attrs=…`。
