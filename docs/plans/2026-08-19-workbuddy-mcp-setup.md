# WorkBuddy 配置智能体内存 MCP 指南

> 让 WorkBuddy（QClaw/OpenClaw 生态）通过 MCP 工具管理智能体记忆（四层 H2 + 文件兼容）
> 2026-08-19 | 已验证：MCP 协议握手 initialize / tools/list / tools/call 全部通过

## 一、前置条件

1. 后端服务运行于 `http://localhost:9900`（含 Arrow add-opens 参数）：
   ```bash
   java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
        -jar agent-application/target/agent-application-1.0.0.jar --server.port=9900
   ```
2. 获取访问令牌（MCP 端点鉴权用）：
   ```bash
   curl -s -X POST http://localhost:9900/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"admin","department":"行政"}'
   # 取返回 JSON 中 data.token 值
   ```

## 二、配置 MCP 服务器（核心步骤）

编辑 WorkBuddy 全局 MCP 配置 `C:\Users\<你>\ .workbuddy\mcp.json`，在 `mcpServers` 中加入：

```json
{
  "mcpServers": {
    "agent-memory": {
      "type": "http",
      "url": "http://localhost:9900/api/agent-memory/mcp",
      "headers": {
        "Authorization": "<上一步获取的令牌>"
      },
      "description": "智能体记忆管理：memory_write / memory_read / memory_delete / memory_search"
    }
  }
}
```

> 说明：
> - `type: http` + `url` 指向记忆 MCP 端点（标准 MCP Streamable HTTP transport，已实现
>   `initialize` 握手 + `tools/list` + `tools/call`，协议版本 2024-11-05）。
> - **鉴权格式**：平台使用 Sa-Token，`Authorization` 头传**裸 token（不带 Bearer 前缀）**；
>   令牌过期后需重新登录并更新 headers（或改用环境变量注入）。

## 三、在 WorkBuddy 中启用（信任）

1. 打开 WorkBuddy **连接管理**页（Connector 面板）；
2. 点击右上角 **自定义连接（Custom Connectors）** 入口；
3. 列表中找到 `agent-memory`，点击 **信任（Trust）** 启用；
4. 回到对话，输入"列出可用的记忆工具"验证：应看到 4 个 `mcp__agent-memory__memory_*` 工具。

## 四、安装记忆管理 Skill（可选增强）

将随包交付的 Skill 安装为用户级技能，让智能体在对话中自动遵循记忆使用规则：

```bash
# 安装到 WorkBuddy 用户技能目录
"C:\Users\Lenovo\.local\bin\skillhub.cmd" --skip-self-upgrade install memory-manager \
  --dir "C:\Users\Lenovo\.workbuddy\skills"   # 或手动复制 SKILL.md 到 ~/.workbuddy/skills/memory-manager/
```

SKILL.md 来源：`modules/agent-memory/module-agent-memory-core/src/main/resources/skill/memory-manager/SKILL.md`

## 五、使用示例

对话中智能体会主动调用（或手动要求）：

| 场景 | 触发话语 | 工具调用 |
|---|---|---|
| 记住用户偏好 | "记住用户喜欢周报邮件" | `memory_write(target=user, userId=xxx, category=preference, content=...)` |
| 回答前查证 | "查一下该用户之前提过什么需求" | `memory_search(query=需求, userId=xxx)` |
| 会话状态 | "记录当前审批到第 2 步" | `memory_write(target=session, sessionId=..., key=approval_step, value=...)` |
| 全局规则 | "以后周报每周五发" | `memory_write(target=global, key=week-rule, content=...)` |

安全行为（SKILL.md 内已约束）：
- 写入自动脱敏（手机号 → `138****5678`），不读取/绕过原值；
- `memory_delete` 不带 key/id 会清空范围，执行前向用户二次确认。

## 六、验证清单

- [ ] `initialize` 返回 serverInfo（agent-memory / 2024-11-05）
- [ ] `tools/list` 返回 4 个工具
- [ ] `memory_write` 写入成功且敏感字段脱敏（sensitiveMasked=true）
- [ ] `memory_search` 检索命中
- [ ] WorkBuddy 连接管理页显示 `agent-memory` 已信任
