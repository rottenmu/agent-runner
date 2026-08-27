# P2-1：远程沙箱（对应 dsh A7 沙箱扩展）

- 日期：2026-08-24
- 范围：`agent-spring-boot-starter`（沙箱 SPI）+ `module-tools`（命令工具接入）+ `scripts/sandbox-server.py`（远程沙箱服务端）
- 对齐目标：DeepSeek Harness（dsh）A7「沙箱」——文件系统/子进程共享执行世界，可切换 E2B 远程执行

---

## 一、背景与痛点

改造前 `SandboxBackend` 仅是"被动 SPI"：只有 `wrapCommand`（命令改写审计）和 `isAllowed`（白名单检查），**没有真正执行能力**。而命令类工具（`CodeTool` / `PythonScriptExecutor`）**直接 `new ProcessBuilder` 起进程**，完全绕过沙箱 SPI：

| 消费方 | 现状 | 问题 |
|---|---|---|
| `CodeTool`（code 技能） | 直接 ProcessBuilder | 无法切换远程执行、无统一执行语义 |
| `PythonScriptExecutor`（脚本工具） | 直接 ProcessBuilder | 同上 |
| plugin 机制 | 仅注册/查询 SandboxBackend | SPI 悬空无消费者 |

→ 远程沙箱（E2B 式）落地的前提：**先把命令执行链收口到沙箱 SPI**，否则远程后端无从接入。

---

## 二、SPI 扩展设计

### 2.1 值对象（新增）

| 类 | 说明 |
|---|---|
| `SandboxCommand` | 执行命令（不可变）：`argv` / `env` / `stdin` / `timeoutSeconds` / `workdir` |
| `SandboxResult` | 执行结果（不可变）：`exitCode` / `stdout` / `stderr` / `timedOut` / `error` |

### 2.2 SandboxBackend（扩展）

```java
public interface SandboxBackend {
    String name();                         // local / remote / docker
    List<String> wrapCommand(List<String> argv);   // 兼容保留（审计/改写）
    boolean isAllowed(String executable);           // 兼容保留（白名单）
    SandboxResult execute(SandboxCommand command);  // ★ 新增唯一执行入口
}
```

**约定**：`execute` 返回 `SandboxResult` 而非抛异常——执行级错误（IO/远程不可达/超时/策略拒绝）统一放入 `error` 字段，与业务逻辑解耦。

### 2.3 后端实现

| 后端 | 行为 |
|---|---|
| `LocalSandboxBackend` | 本机 ProcessBuilder 执行（stdin/env/workdir/超时强杀），零回归基线 |
| `HttpRemoteSandboxBackend` | `POST {baseUrl}/api/sandbox/execute` 转发（JSON 协议），远程不可达 → `failed` |

### 2.4 本地执行超时语义（关键修复）

```
原实现：readAllBytes() 读流 → waitFor(timeout)
         ↓ 进程不退出时 readAllBytes 阻塞至自然退出，超时失效（sleep 30 + timeout 1 = 实际 30s）
修复后：waitFor(timeout) 判超时 → 未完成先 destroyForcibly → 再读流（进程死后 EOF）
         ↓ sleep 30 + timeout 1 = 1s 返回 timedOut=true ✓
```

---

## 三、命令执行链收口

| 消费方 | 改造 |
|---|---|
| `CodeTool` | 构造注入 `SandboxBackend`（可空，缺省 `localFallback` 回退本地）→ `call()` 走 `sandbox.execute(command)` |
| `PythonScriptExecutor` | 构造注入 `SandboxBackend`（可空）→ `execute()` 走沙箱 |
| `ToolScriptService` | 持有沙箱，三处 `new PythonScriptExecutor` 透传 |
| `ToolsAutoConfiguration` | 新增 `sandboxBackend` Bean（配置 `ai.tools.remote-sandbox-url` 非空 → 远程，否则本地）；`codeTool` / `toolScriptService` 注入 |

**保留旧构造器**（`CodeTool(props)` / `PythonScriptExecutor(4参)`）——零迁移成本，无沙箱时回退本地直通，行为与原实现一致。

---

## 四、远程沙箱服务端（scripts/sandbox-server.py）

零依赖 Python 标准库实现（`http.server.ThreadingHTTPServer`），与 Java 后端协议对齐：

```
POST /api/sandbox/execute          # 协议端点
  body:  {"argv":[...], "env":{...}|null, "stdin":"..."|null,
          "timeoutSeconds":10, "workdir":null}
  resp:  {"exitCode":0, "stdout":"...", "stderr":"",
          "timedOut":false, "error":null}
```

**安全**：
- 白名单可执行程序（仅 python 系；`--token` 可选 Bearer 鉴权）
- workdir 钳制在沙箱根内（`--workdir`，默认系统 TEMP）
- timeout 上限钳制（`--max-timeout`，默认 60，硬上限 300）
- 受限参数拦截（`--`、`-m pip`、`-c socket` 等）

**启动**：`python3 scripts/sandbox-server.py --port 8799 --token xxx --max-timeout 60`

**配置接入**（application.yml）：

```yaml
ai:
  tools:
    remote-sandbox-url: http://127.0.0.1:8799   # 空 = 本地直通
    remote-sandbox-token: xxx                    # 可选
    remote-sandbox-timeout-seconds: 30
```

---

## 五、验证记录

| # | 验证项 | 结果 |
|---|---|---|
| 1 | 定点编译 tools-autoconfig（-am） | ✅ 18s 通过 |
| 2 | LocalSandboxBackendTest 6 例 | ✅ 全绿（执行/退出码/stdin/拒绝/超时/env） |
| 3 | HttpRemoteSandboxBackendTest 3 例 | ✅ 全绿（转发/鉴权 401/不可达） |
| 4 | SandboxSmokeTest 端到端 3 例 | ✅ 全绿（本地 42 / 远程 stdin+env 透传 / 远程退出码 7） |
| 5 | module-tools 全量单测 | ✅ 通过 |
| 6 | starter 全量单测 | ✅ 通过（无回归） |
| 7 | 远程服务端脚本 4 项协议 | ✅ 正常执行 42 / 无 token 401 / cmd.exe 拒绝 / 退出码 7 透传 |
| 8 | 全量 install | ✅ 通过 |

**测试修复记录**：
- 超时失效：readAllBytes 读流先行阻塞 → 调整为先 waitFor 再读流
- 冒烟 env 透传：测试服务端模拟器漏转发 env → 修复模拟器（协议本身无 bug）
- 冒烟 lambda effectively-final：`env` 局部变量在 lambda 内赋值 → final Map 容器

---

## 六、边界与约束

| 约束 | 说明 |
|---|---|
| 远程协议要求 HTTP 200 + JSON | 非 200 → `error: HTTP xxx`；超时 → `error: 远程沙箱不可达` |
| 远程依赖网络可达 | 不可达时工具返回 `远程沙箱不可达: ...`（不抛异常，业务可降级） |
| 白名单执行 | 服务端只允许 python 系；客户端 `isAllowed` 双保险 |
| 输出截断 | 本地 stdout/stderr 截断 100KB；CodeTool 业务截断 6000 字符保持现状 |

---

## 七、后续建议

1. **远程沙箱管理界面**：沙箱实例 CRUD + 连通性测试（复用 AiModelConfig 的连通探测模式）
2. **容器后端**：`DockerSandboxBackend`（`docker exec`/`docker run` 包装），丰富 `SandboxBackend` 生态
3. **预设 workdir**：`ai.tools.remote-sandbox-workdir` 配置默认远端工作目录
4. **P2-2 agent preset/模式**：本次范围外，待收尾后单独评估