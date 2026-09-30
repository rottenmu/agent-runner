# 模型名已修正 · 但端到端仍被 API-Key 权限阻塞（诊断报告）

> 日期：2026-09-17
> 结论一句话：**「改模型名」这件事已完成并生效；但它没能打通端到端，因为真正挡路的是另一道闸门 —— key 自身没有模型授权。**

---

## 一、本轮做了什么（可复现的动作链）

### 1.1 踩到的架构坑：不能直接改 SQLite

我先把 `ai_managed_agent.model_name` 直接改成 `qwen-max`，**运行时毫无变化**。

根因（已确证）：

```
AiAgentManagementService.loadManagedAgents()   ← 启动时一次性载入
        ↓  agents.put(id, agent)  进 LinkedHashMap
请求路径  agents.get(id)                        ← 走内存 Map，不查库
```

所以**直接改库对已运行进程完全不可见**。日志证据：库里已改为 `qwen-max` 后，
22:00 的请求**仍是** `model=qwen3.8-max-preview`。

**处置**：先用 `tmp/verify/agent-model-backup.json` 把 5 行**还原**，再改走管理接口。

### 1.2 正确路径：走管理接口

新建 `tmp/verify/model-change-via-api.py`，通过 `PUT /api/biz/ai/agents/{id}` 修改。
走接口同时完成三件事（这是直接改库做不到的）：

1. 更新内存 Map
2. 落库
3. 触发 `AiManagedAgentRuntimeInvalidator.invalidate(tenantId, agentId)` 回收运行时实例

**途中踩到的鉴权坑**：`Authorization: <token>`，**不带 `Bearer ` 前缀**。
加了前缀会得到 `HTTP 200` + 信封 `{code:401,"未登录或登录已过期"}` —— 一个很容易误判成
「token 失效」的假 401。token 本身是 36 字符 UUID，服务端同时下发
`Set-Cookie: Authorization=<uuid>`，属 **session 型**凭据。

**执行结果**（`tmp/verify/model-change-apply.log`）：

```
5 个 PUT 全部 HTTP 200 / code 200
回读核对：仍为 qwen3.8-max-preview 的 = 0，已为 qwen-max 的 = 5
```

### 1.3 改动已生效（这一点是成功的）

| id | 名称 | 原 model | 现 model | type |
|---|---|---|---|---|
| a17861956053731 | 测试智能体 | qwen3.8-max-preview | **qwen-max** | conversation |
| a17862024592402 | RAG知识库测试 | qwen3.8-max-preview | **qwen-max** | rag |
| a17862024599103 | 工具调用测试 | qwen3.8-max-preview | **qwen-max** | tool |
| a17862024605874 | 规划执行测试 | qwen3.8-max-preview | **qwen-max** | plan |
| a17862024612565 | 图任务流测试 | qwen3.8-max-preview | **qwen-max** | graph |

（第 6 个 `a17862024585701 普通对话测试` 本来就是 `qwen-plus`，未动。）

生效证据：日志中端点已从错误的 `/multimodal-generation` 变为**正确的**
`/api/v1/services/aigc/text-generation/generation`，且 `model=qwen-max`。

---

## 二、但端到端仍不通 —— 阻塞点已转移

M4 复跑（`tmp/verify/m4-runF-afterapi.log`）：**32 项 / 0 失败**，
但链路里 `model_call` 状态仍是 `failed`（该项在脚本里按「如实记录」处理，不计入失败）。

### 2.1 决定性对照实验

`tmp/verify/key-probe3.log` —— 注册表里只有一把可用的 workspace key：

```
AI_API_KEY  len=117  head=sk-ws-H.PHYPMEE.

  qwen-max               HTTP 403  code=AccessDenied  Access denied by API-Key restrictions.
  qwen3.8-max-preview    HTTP 403  code=AccessDenied  Access denied by API-Key restrictions.
```

**两者报错完全一致** —— 这一条就足以推翻「模型名无权限导致 403」的假设。

`tmp/verify/endpoint-probe.log` —— 排除 base-url 与模型名的干扰：

```
=== 公共 dashscope ===
  qwen-max     HTTP 403  Access denied by API-Key restrictions.
  qwen-plus    HTTP 403  Access denied by API-Key restrictions.

=== 专属 maas 网关 ===
  qwen-max     HTTP 403  Access denied by API-Key restrictions.
  qwen-plus    HTTP 403  Access denied by API-Key restrictions.
```

换网关、换模型，**报错文本一字不变**。结论：

> **403 与 base-url 无关、与模型名无关、与代码无关 —— 是这把 key 自身的授权范围限制。**

### 2.2 补充旁证：key 的形态本身就说明了问题

`sk-ws-H.` 前缀不是标准 DashScope API-Key（标准形如 `sk-` + 32 位十六进制）。
它是**阿里云百炼的 workspace 级凭据**，天然受「模型授权范围 / 调用来源 IP」约束。

另一把 `CODEX_API_KEY` / `OPENAI_API_KEY`（len=35，`sk-yoGNgD1FpXIhv...`）打过去是
`HTTP 401 InvalidApiKey` —— 完全不是同一个体系的 key，属于干扰项。

### 2.3 必须更正的一处旧结论

上一轮我记录：「旧 key 对 `qwen-max`/`qwen-plus`/`qwen-turbo` 均返回 200」。
**本轮复核无法复现**，且 `tmp/verify/recheck-oldkey.log` 显示当时其实已是 403。

判定：旧结论**作废**。可能是当时探测脚本里的 key 被污染/截断，或该 key 权限在此期间被
平台侧回收。无论哪种，**当前事实是：两把 key 对全部 qwen 模型一律 403。**

---

## 三、结论与下一步

### 3.1 净收益

- ✅ 5 个智能体的模型名已从无效的 `qwen3.8-max-preview` 修正为 `qwen-max`（走接口，回读确认）
- ✅ 端点已修正为正确的 `text-generation` 路径
- ✅ 摸清了「改库无效 → 必须走接口」这条架构约束，并沉淀成可复用脚本
- ✅ 阻塞点被精确定位到**单一外部依赖**：key 的模型授权

### 3.2 仍被阻塞的验证项（原因同一）

- **M4-3 场景 5「终态清理」正向验证** —— 需要业务真正跑到终态
- **M4-2b 驳回路径真机验证** —— 同上

### 3.3 需要用户侧操作的（我无法代劳）

在**阿里云百炼控制台**确认以下任一：

1. 该 workspace key 是否被绑定了**模型白名单** → 把 `qwen-max` / `qwen-plus` 加入授权
2. 该 key 是否被绑定了**调用来源 IP 限制** → 放开本机出口 IP
3. 该 key 是否**欠费 / 未开通模型服务**
4. 或直接提供一把**标准 DashScope key**（`sk-` + 32 位十六进制，绑定了 `qwen-max` 权限）

拿到可用 key 后，只需：

```bash
# 1. 更新环境变量（不写入任何文件）
setx AI_API_KEY "<新key>"        # 或 PowerShell: $env:AI_API_KEY="<新key>"

# 2. 用 keeper 方式重启后端（见 tmp/verify/backend-keeper.py）

# 3. 复跑，应看到 model_call 从 failed 变 ok
python tmp/verify/m4-verify.py
```

### 3.4 建议清理（低优先，与技术债一并处理）

- RocksDB 中 `e2e-m3` / `e2e-m4` 两个租户的遗留 `astate` 键
- `ai_managed_agent` 中 12 条 `deleted=1` 的历史 e2e 行
- `AiAgentService` 有效行 645（HEAD 基线 549 已超限）→ 独立技术债任务
