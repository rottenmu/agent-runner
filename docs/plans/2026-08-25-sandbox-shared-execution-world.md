# P4 沙箱共享执行世界（对齐 dsh A7：文件系统 + 子进程共享执行世界）

> 日期：2026-08-25
> 模块：`agent-spring-boot-starter`（抽象）+ `module-tools`（装配）+ `scripts/sandbox-server.py`（服务端）
> 目标：补齐 dsh A7 剩余差距——在命令执行沙箱之上增加**工作区文件系统抽象**，文件操作与子进程执行共享同一沙箱边界。

## 1. 背景

P2-1 已落地 `SandboxBackend` 命令执行抽象（本地直通 + HTTP 远程），但 dsh A7 的核心语义是「**文件系统与子进程共享执行世界**」：agent 的文件读写与命令执行应在同一受限工作区内，可整体切到远程。当前缺文件系统抽象 → 本次补齐。

## 2. 设计

### 2.1 抽象（starter `sandbox/` 包，独立于 SandboxBackend）

| 类 | 说明 |
| --- | --- |
| `SandboxFileOp` | 操作请求：`op`（read/write/list/delete/exists，含 ls/cat/mkdir 别名）+ `path`（相对工作区根）+ `content` |
| `SandboxFileResult` | 统一结果：`success/content/entries/exists/error`，工厂 ok/okList/okExists/okWrite/okDelete/failed |
| `SandboxFileSystem` | 接口：`workdir()` / `allowedExtensions()` / `execute(op)`；静态 `normalizeSafe`（语义校验：禁绝对路径、禁 `..`、`.` 归一、`\`→`/`） |

### 2.2 本地实现 `LocalSandboxFileSystem`

- 工作区根（`ToolsProperties.fileWorkspace`，默认 `data/tool-files`）自动创建
- **双重防护**：语义层 normalizeSafe 禁绝对路径/`..`；物理层 realpath 复核不越出根目录（防符号链接逃逸）
- 空路径 = 根目录（list 列根 / delete 拒绝根）
- 可写扩展名白名单（`allowedExtensions`，空=不限制）
- 读/写 1MB 上限

### 2.3 远程实现 `HttpRemoteSandboxFileSystem`

- `POST {baseUrl}/api/sandbox/file`，SandboxFileOp JSON → SandboxFileResult JSON
- Bearer token 鉴权；不可达/非 200 → `failed` 不抛异常
- 工作区根与白名单由服务端维护

### 2.4 服务端 `sandbox-server.py` 扩展

- 新增 `/api/sandbox/file` 端点（read/write/list/delete/exists）
- `_resolve_path`：空路径=根目录、禁绝对路径/`..`、realpath 复核
- `--max-file-bytes`（默认 1MB）钳制读写大小
- delete 捕获环境安全拦截错误 → 明确失败信息

## 3. 装配

`ToolsAutoConfiguration` 新增 `sandboxFileSystem` Bean（跟随 `remoteSandboxUrl`：非空→远程，否则本地）；`ToolsProperties` 新增 `sandboxFileAllowedExtensions`（逗号分隔，空=不限制）。

## 4. 测试

| 测试类 | 用例数 | 覆盖点 |
| --- | --- | --- |
| `LocalSandboxFileSystemTest` | 10 | 读写/列表/exists+delete/绝对路径拒绝/`..`拒绝/未知 op+空路径/缺失文件/白名单/根目录删除拒绝/normalizeSafe |
| `HttpRemoteSandboxFileSystemTest` | 5 | read 转发/list 转发/401/不可达/空 baseUrl |

回归：starter 全量 **172 通过，0 失败**（较 157 新增 15 个沙箱文件系统用例）；module-tools 编译通过。

服务端 curl 冒烟 8 项：write/read/list 根/delete 根拒绝/traversal 拒绝/absolute 拒绝/exists 全部符合预期。

## 5. 关键坑

- **`normalizeSafe("./a")` 误判**：StringTokenizer 按 `/` 切分后 `.` token 被当越界 → 修正为跳过 `.`。
- **空路径语义两端不一致**：Java 端空路径=根目录，Python 端最初抛「路径不能为空」 → 统一为空路径=根目录。
- **环境 safe-delete 拦截**：Windows 沙箱环境 `os.remove` 被 WorkBuddy 安全 shim 拦截（回收站不可用），delete 需捕获并返回明确失败，而非误导性错误。
- **Git Bash 后台服务残留**：`cmd &` 起 python 服务会残留多进程占用端口 → 用 run_in_background + PowerShell 精确清理。

## 6. 后续

- 剩余 dsh 差距：**A1 插件事件级瀑布+回滚（大）**；本机 Claude Code CLI 桥（用户已排除）
- 可选增强：SandboxFileSystem 接入工具层（如让 CodeTool 读文件前经文件系统白名单校验）