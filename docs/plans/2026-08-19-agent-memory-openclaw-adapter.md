# 智能体内存模块 — QClaw WorkBuddy（OpenClaw 生态）适配方案

> 基于 AgentScope-Java 2.0.2（HarnessAgent filesystem 记忆 + H2 四层记忆模块）
> 日期：2026-08-19 | 状态：**全部完成**（WorkBuddy MCP+Skill 适配 + 文件兼容模式 + 4 REST 接口）

## 一、架构

```text
┌─────────────────────────────────────────────────────────────┐
│  WorkBuddy / QClaw（OpenClaw 生态客户端）                      │
│  ├─ mcp.json（HTTP transport → /api/agent-memory/mcp）        │
│  └─ Skills（memory-manager SKILL.md 适配器）                   │
└──────────────┬──────────────────────────────┬───────────────┘
               │ MCP tools/list·call          │ REST（待实施）
┌──────────────▼──────────────────────────────▼───────────────┐
│  module-agent-memory（com.zimo.module.agentmemory）           │
│  ├─ mcp/MemoryMcpEndpoint      HTTP JSON-RPC 端点 ✅          │
│  ├─ mcp/MemoryMcpToolkit       4 工具定义+执行 ✅              │
│  ├─ memory/AiMemoryService     H2 四层记忆（OLTP 运行时） ✅    │
│  ├─ storage/*                  OLTP(H2)+OLAP(Arrow) 门面/SPI ✅│
│  └─ memoryfile/*               文件兼容模式（待实施）           │
└──────────────────────────┬──────────────────────────────────┘
                           │
        AgentScope-Java 2.0.2 HarnessAgent（BaseStore → RocksDB 工作区 MEMORY.md）
```

## 二、WorkBuddy 适配（已完成，2026-08-19）

### 1. MCP 工具（深度接管，基于 H2 四层记忆）
| 工具 | 功能 | 关键参数 |
|---|---|---|
| `memory_write` | 写入会话/用户/全局记忆（自动脱敏+白名单） | target、sessionId、userId、category、key、content |
| `memory_read` | 读取记忆（分页） | target、sessionId、userId、category、limit |
| `memory_delete` | 删除记忆（key/id 空=清空） | target、sessionId、userId、key、id |
| `memory_search` | 跨目标检索（用户记忆+全局） | query、userId、limit |

### 2. 交付代码
- `mcp/McpRequest.java` / `mcp/McpResponse.java`：轻量 JSON-RPC 信封（独立于 starter，避免循环依赖）
- `mcp/MemoryMcpToolkit.java`：工具 schema + 执行（复用 AiMemoryService）
- `mcp/MemoryMcpEndpoint.java`：`POST /api/agent-memory/mcp`（tools/list + tools/call）
- autoconfig：MemoryMcpToolkit / MemoryMcpEndpoint Bean 已装配
- `resources/mcp/memory-mcp.json`：WorkBuddy 客户端注册模板（HTTP transport + Bearer token）
- `resources/skill/memory-manager/SKILL.md`：WorkBuddy skill 适配器（yaml frontmatter + 使用规则）

### 3. 验证结果（实测）
- tools/list → 4 工具注册 ✅
- memory_write → 写入成功 + **自动脱敏**（`138****5678`、`sensitiveMasked:true`）✅
- memory_search → 检索命中（count=1）✅
- memory_delete → 删除成功 ✅

## 三、文件兼容模式（已完成，2026-08-19）

### 方案 A：文件兼容模式（与 OpenClaw MEMORY.md 文件互通）
- `memoryfile/MemoryFileEntry`：主题 + 条目 record
- `memoryfile/MemoryFileStore`：MEMORY.md 序列化（`# 标题` / `## 主题` / `- 条目`，OpenClaw/Claude Code 互通）+ **双级锁**（进程内 ReentrantReadWriteLock + 跨进程 FileLock 独立 .lock 文件）+ **原子写**（tmp + rename）
- `memoryfile/MemoryFileService`：按 agentId 隔离（`{baseDir}/{agentId}/MEMORY.md`，防路径穿越）+ write / read / delete / search
- `memoryfile/MemoryFileController`：4 个 REST 接口（`GET/POST/DELETE /api/agent-memory/file/{agentId}` + `/search?q=`）
- 配置：`agent-memory.file-base-dir`（默认 `./data/agent-memory-files`）
- 验证：13 单测全绿 + 端到端（写入 3 条 / 读取 / 检索命中 / 删除主题 / 落盘 MEMORY.md 格式互通）

## 四、分步实施清单（WorkBuddy 适配已完）

- [x] 1. MCP 工具定义（4 工具 schema）与执行
- [x] 2. HTTP JSON-RPC MCP 端点 + autoconfig 装配
- [x] 3. WorkBuddy mcp.json 配置模板 + SKILL.md skill 适配器
- [x] 4. 端到端验证（list/write/search/delete + 脱敏）
- [x] 5. MEMORY.md 序列化工具类（MemoryFileStore）
- [x] 6. 文件读写锁（进程内 ReentrantReadWriteLock + 跨进程 FileLock）
- [x] 7. 4 个 REST 接口（/api/agent-memory/file/**）
- [x] 8. 文件兼容模式端到端验证（落盘 OpenClaw 兼容 MEMORY.md）

## 五、配置（WorkBuddy 客户端接入）

```json
// WorkBuddy mcp.json 注册 agent-memory server
{ "mcpServers": { "agent-memory": {
    "type": "http", "url": "http://localhost:9900/api/agent-memory/mcp",
    "headers": { "Authorization": "Bearer ${AGENT_MEMORY_TOKEN}" } } } }
```
SKILL.md 适配器随包交付（resources/skill/memory-manager/SKILL.md）。
