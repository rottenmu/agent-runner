# agent-memory 留在 agent_runner 内作为可运行模块，前端归入模块 frontend 目录

> 状态：已落地（静态核验完成；运行态启动待用户手动验收）
> 起草日：2026-11-06
> 关联模块：`modules/agent-memory`（Maven reactor 模块 + 独立可启动应用）、`frontend/agent-memory-ui`（已移入模块）、`modules/pom.xml`
> 规范依据：`docs/rules/PLAN_DOC_RULES.md`
> 前置决策：① 2026-11-06「全量保留，不删除任何模块」；② 同日否决 `2026-11-06-agent-memory-extraction.md` 的平级拆解方案

---

> 📌 **后续变更（2026-11-06 17:15）**：用户要求「把 `agent-memory-application` 中的代码配置文件整合到
> `agent-memory-core` 中」，已实施，见 `docs/plans/2026-11-06-agent-memory-merge-application-into-core.md`。
> 因此本文件的**成功标准 4**（聚合 pom 同时含 core + application）、第五节「聚合两个子模块」、
> 以及「`agent-memory-application/target/` 产物 / `application.yml`」等描述已被后续变更取代。
> 本文件其余结论（留在 reactor 内、前端归位到 `frontend/`、`run.sh` 固定 CWD、前后端端口联动）**仍有效**。

## 一、置信度与剩余风险

- 当前置信度：88%（目录/配置/文档的静态核验 100%；后端与前端的**运行态未实测**，按用户约定不代为启动）
- 主要剩余风险：`run.sh` 默认 9900 与 `agent-application` 的 yml 默认端口相同；若两进程同时以默认配置启动，会以后启动者 `Address already in use` 的形式暴露。已通过 `AGENT_MEMORY_PORT` 前后端联动给出规避路径，但本机主应用受 `SERVER__PORT=58090` 覆盖，实际是否冲突取决于启动环境。

---

## 二、目标

本次要达成：

- `agent-memory` **继续是 `agent_runner` 的 Maven reactor 模块**（`modules/pom.xml` 的 `<module>agent-memory</module>` 不动、parent `com.zimo:modules:1.0.0` 不动），不搬到与仓库平级的目录。
- 该模块**可以运行**：`agent-memory-application` 独立 `java -jar` 启动，MCP / REST 可用；数据文件落点稳定可预期。
- 新建 `modules/agent-memory/frontend/`，把 `frontend/agent-memory-ui` 整体移入，形成「模块自带后端 + 自带前端」的自洽目录。
- 文档口径统一：旧拆解方案作废、模块 README 写明运行方式、审计文档中的前端路径同步。

本次明确不做（延后项见第九节）：

- 不创建 `D:/codehub/agent-memory`；不从 reactor 移除模块；不改任一 pom 的依赖坐标或 `<modules>`。
- 不改 `framework-ai` 对 `com.zimo.module.agentmemory.*` 的 9 处 main / 11 处 test import，不做读侧 HTTP 化。
- 不改主应用 `application.yml` 的 `agent-memory.*` 段、不排除 `AgentMemoryAutoConfiguration`、不搬移 H2 数据文件。
- 不改默认端口 9900、不改 `~/.workbuddy/mcp.json`、不改 `frontend/modules/agentmemory` 主壳插件（两套前端并存，按全量保留决策）。
- 不执行任何构建/启动命令，不提交 git；运行命令写进文档由用户手动执行。

---

## 三、成功标准

本方案完成后，以下每一条都应能在真机上直接判定为真：

1. `ls D:/codehub/agent_runner/modules/agent-memory/frontend/agent-memory-ui/package.json` 返回文件；`ls D:/codehub/agent_runner/frontend/agent-memory-ui` 返回「No such file or directory」；`ls -d D:/codehub/agent-memory` 返回「No such file or directory」。
2. 移动前后对该工程执行 `find <dir> -type f | wc -l`，两次输出相同（实测 `11820`）；`src/views/` 下 `.vue` 文件数 `= 6`。
3. 在 `modules/agent-memory` 执行 `git status --porcelain | wc -l`，结果为 `13` = 移动前既有的 `10`（6 M + 4 ??）+ 本次新增 `3`（`M README.md`、`M agent-memory-application/run.sh`、`?? frontend/`）；`git check-ignore -v frontend/agent-memory-ui/node_modules/.package-lock.json` 与 `.../dist/index.html` 均返回命中规则（不把依赖与产物纳入版本控制）。外层仓库 `git status --short` 中不得出现该前端工程任何文件。
4. `modules/pom.xml` 含 `<module>agent-memory</module>`；`modules/agent-memory/pom.xml` 的 `<modules>` 同时含 `agent-memory-core` 与 `agent-memory-application`，parent 仍为 `com.zimo:modules:1.0.0`。
5. `bash -n modules/agent-memory/agent-memory-application/run.sh` 退出码 `0`；`node --check` 对 `frontend/agent-memory-ui/vite.config.js` 退出码 `0`；`grep -n 'cd "\$SCRIPT_DIR"' run.sh` 有输出。
6. 用户手动执行 `./mvnw -pl modules/agent-memory/agent-memory-application -am package -DskipTests` → 退出码 `0`，产出 `target/agent-memory-application-1.0.0.jar` 且大小 `> 100 MB`（历史实测 155 MB）。
7. 用户手动执行 `./run.sh` → 日志出现 `Started AgentMemoryApplication`；`netstat -ano | grep LISTENING | grep :9900` 有输出；`curl --noproxy '*' -X POST http://127.0.0.1:9900/api/agent-memory/mcp -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'` 返回 HTTP `200` 且响应体含 `"result"` 与长度 `> 0` 的 `tools` 数组。
8. 后端启动后 `ls -la agent-memory-application/data/agent-memory.mv.db` 的 mtime 更新（证明 `./data` 落在模块目录，而非调用者 CWD）；启动日志中 `Database may be already in use` 出现次数 `= 0`。
9. 用户手动在前端目录执行 `npm install && npm run dev` → `http://localhost:6060` 返回 HTTP `200`；页面发起的 `/api/ai/memory/policy` 经 Vite 代理返回 HTTP `200`；带 `AGENT_MEMORY_PORT=9901` 时后端与前端 proxy 目标端口同时为 `9901`。

---

## 四、参照行为（对标）

| 参照项 | 它怎么做 | 我们要复刻到什么程度 |
|---|---|---|
| `docs/plans/2026-08-29-agent-memory-backend-standalone.md`（已落地） | 在同一仓库内新增 `agent-memory-application`，只装配记忆能力，`java -jar` 单独启动；9900 + `unset SERVER__PORT` + `--add-opens` | **完整复刻**运行形态；本次只额外固定 CWD、补前端归位与运行文档，**不复制**「两者不要同时运行」这个限制为强制约束 |
| `docs/plans/2026-08-29-agent-memory-ui-standalone.md` | 独立 Vue3 前端 6060，Vite proxy `/api` → 9900 | **完整复刻**；仅把工程物理位置移到所属模块下，端口与代理语义不变 |
| 仓库既有坑：`application.yml` 的 `jdbc:sqlite:./data/...` 相对 CWD | 从不同目录启动会读到空库，报 `no such table` | **反向复刻**：`run.sh` 显式 `cd "$SCRIPT_DIR"`，让 `./data/*` 与调用者 CWD 解耦 |

---

## 五、现状差距

### 能力缺失

- 模块 README 第 55 行写「独立构建本仓库需先安装父 pom 或改用独立 parent；本仓库为 agent_runner 平台代码拆分」，与「就是 reactor 模块、不脱离仓库」的定位矛盾，且**没有任何运行章节**（`run.sh` 存在但 README 未提及）。
- `run.sh` 未 `cd` 到自身目录：`application.yml` 的 `h2-url` / `olap-arrow-data-file` / `file-base-dir` 全是 `./data/*`，实际落点取决于调用者 CWD，注释却写「数据落模块目录」——与 SQLite 相对 CWD 那个已踩过的坑同源。
- 后端端口可用 `AGENT_MEMORY_PORT` 改，但前端 `vite.config.js` 的 proxy **写死** `http://localhost:9900`，改端口后前端必然连不上。
- 前端工程散在 `frontend/` 根目录，与所属后端模块分离；`agent-memory-ui` 与主壳插件 `frontend/modules/agentmemory` 的并存关系只在审计文档里说明，模块自身文档缺失。

### 只是没接线

- **独立应用形态早已具备**：`AgentMemoryApplication.java`、`application.yml`（9900）、`spring-boot-maven-plugin repackage`、`run.sh`、155 MB 可执行 jar、模块内已存在的 `data/agent-memory.mv.db` 全部就位，2026-08-29 端到端验证过 MCP / REST / ETL。本次**没有新增任何后端代码**。
- `agent-memory` 已是 reactor 模块：`modules/pom.xml` 注册、parent `com.zimo:modules:1.0.0`、聚合 `core` + `application` 两个子模块，无需任何构建改动。
- 嵌套 git 已独立：`modules/agent-memory/.git`；外层 `.gitignore:5 frontend/` 与 `:41 modules/agent-memory/` 使两处都不被外层仓库跟踪 → 移动**不影响外层索引**。
- 前端工程自带 `.gitignore`（`node_modules/` / `dist/` / `*.log`），移入嵌套仓库后不会污染 `git status`。

### 不打算解决的问题

- `framework-ai` 编译期依赖 `agent-memory-core`、双进程若指向同一 H2 文件会互锁：本次不拆读侧，也**不改主应用装配**；仅在 README 写清「当前 CWD 不同 → 数据文件天然分开，别用 `AGENT_MEMORY_H2_URL` 指向同一路径」。
- `agent-memory-ui` 与主壳 `agentmemory` 6 个同名页面的归并：属产品取舍，全量保留决策下不裁决。
- L2 场景层空转、OLAP 只写不读等历史功能缺陷：见 `docs/2026-11-06-agent-only-scope-audit.md` §3，与目录调整无关。
- 嵌套仓库 6 M + 4 ?? 未提交改动：沿用现状，本次不代为提交。

---

## 六、技术方向

### 1. 目录归位（唯一物理改动）

职责：新建 `modules/agent-memory/frontend/`，把 `frontend/agent-memory-ui` 整体移入为
`modules/agent-memory/frontend/agent-memory-ui/`。

关键结构：

```text
modules/agent-memory/
├── pom.xml                       # 不变，parent 仍是 com.zimo:modules:1.0.0
├── agent-memory-core/            # 不变
├── agent-memory-application/     # 不变（run.sh 小修）
└── frontend/                     # 新建
    └── agent-memory-ui/          # 原 frontend/agent-memory-ui 整体移入
```

取舍理由：保留 `agent-memory-ui` 这一层子目录，使工程名、README 里的目录树、以及将来 `frontend/` 下新增其他前端资产互不冲突；相比把内容摊平到 `frontend/` 根目录，回滚与识别都更直接。移动用 `mv`（同盘 rename），文件数前后逐一对账。

### 2. 固定 CWD，让「可运行」可复现

职责：`run.sh` 在解析 `SCRIPT_DIR` 后 `cd "$SCRIPT_DIR"`，使 `./data/*` 稳定落在 `agent-memory-application/data/`。

关键接口：`AGENT_MEMORY_H2_URL` / `AGENT_MEMORY_OLAP_FILE` 默认值均为相对路径；jar 用绝对路径启动，故 `cd` 不影响可执行文件路径与 `"$@"` 透传。

取舍理由：不改 yml 为绝对路径（会破坏既有 08-29 验证过的配置与数据位置），只改启动脚本一处，改动面最小、可回滚。

### 3. 前后端端口联动

职责：`vite.config.js` 的 proxy target 由 `process.env.AGENT_MEMORY_PORT || '9900'` 推导，与 `run.sh` 的 `PORT="${AGENT_MEMORY_PORT:-9900}"` 同名对齐。

关键接口：

```text
后端：AGENT_MEMORY_PORT=9901 ./run.sh
前端：AGENT_MEMORY_PORT=9901 npm run dev
默认：两端都是 9900，行为与改动前一致
```

取舍理由：不改默认端口（前端 proxy、主壳 `memory.js`、全局 `~/.workbuddy/mcp.json` 都还指向 9900），只消除「改了后端端口前端就断」这一处不对称。

### 4. 文档口径统一

职责：作废旧拆解方案但保留调研结论；模块 README 补「独立运行」章节并修正模块结构树；前端 README 标注新位置；两份 11-06 审计文档中的旧路径同步。

取舍理由：避免「文档还建议拆、实际已决定不拆」的矛盾；历史报告类文档（09-13 codegraph 报告、09-13 walkthrough）记录的是当时快照，不改原文。

---

## 七、里程碑

### M1. 目录归位 + git 安全核验

- 产出：`modules/agent-memory/frontend/agent-memory-ui/`（11820 个文件）；源路径消失；未创建平级目录。
- 验证：成功标准 1、2、3、4。

### M2. 可运行性补强 + 文档落地

- 产出：`run.sh` 固定 CWD、`vite.config.js` 端口联动、模块 README 第五章、前端 README 位置说明、旧方案作废标记、本方案实施记录。
- 验证：成功标准 5；6~9 的静态前置条件齐备，运行态命令已写入 README 交用户手动执行。

---

## 八、自测计划

### 场景 1：目录与仓库（已静态执行）

1. `find <源>/<目标> -type f | wc -l` 前后对账 → 期望均为 `11820`。
2. `ls src/views/*.vue | wc -l` → 期望 `6`。
3. `cd modules/agent-memory && git status --porcelain | wc -l` → 期望 `13`：既有 `10` 条未提交改动 + 本次 `3` 条（`README.md`、`run.sh`、`?? frontend/`）。
4. `git check-ignore -v` 对 `node_modules/.package-lock.json` 与 `dist/index.html` → 期望各自命中 `frontend/agent-memory-ui/.gitignore:2` 与 `:5`。
5. 验证：外层仓库 `git status --short` 无该工程文件；`D:/codehub/agent-memory` 不存在。

### 场景 2：静态语法与配置（已静态执行）

1. `bash -n run.sh` → 退出码 `0`。
2. `node --check vite.config.js` → 退出码 `0`（ESM 语法）。
3. `grep -n 'cd "$SCRIPT_DIR"' run.sh` → 有输出。
4. 验证：`modules/pom.xml` 与 `modules/agent-memory/pom.xml` 未改动（reactor 结构完整）。

### 场景 3：后端独立启动（**待用户手动执行**）

1. 仓库根执行 `./mvnw -pl modules/agent-memory/agent-memory-application -am package -DskipTests`。
2. 验证：退出码 `0`；jar 存在且 `> 100 MB`。
3. `cd modules/agent-memory/agent-memory-application && ./run.sh`。
4. 验证：日志 `Started AgentMemoryApplication`、`:9900 LISTENING`、无 `Database may be already in use`。
5. `curl --noproxy '*' -X POST http://127.0.0.1:9900/api/agent-memory/mcp -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'`。
6. 验证：HTTP `200`、响应含 `"result"`、`tools` 长度 `> 0`。
7. 验证：`agent-memory-application/data/agent-memory.mv.db` 存在且 mtime 更新（CWD 固定生效）。

> ⚠️ **语义缺陷声明**：`GET /api/agent-memory/mcp` 返回 200 **不等于**写入成功（该端点对 GET 也可能返回信封）。
> 要验证写入必须 `tools/call` 调 `memory_write` 后查 H2 行数或 `/api/ai/memory/**` 列表，**通过 ≠ 正确**。

### 场景 4：前端联调（**待用户手动执行**）

1. `cd modules/agent-memory/frontend/agent-memory-ui && npm install && npm run dev`。
2. 验证：`http://localhost:6060` HTTP `200`；页面 `/api/ai/memory/policy` 经 proxy 返回 `200`；控制台无 `4xx/5xx`。
3. 端口冲突时用 `AGENT_MEMORY_PORT=9901` 同时传给 `./run.sh` 与 `npm run dev`。
4. 验证：Vite 日志的 proxy target 为 `:9901`，页面接口仍 `200`。

---

## 九、已知延后项

- **运行态真机验收（成功标准 6~9）** —— 原因：按用户约定「代码修改后不代为执行 shell 命令或重启」，命令已写入模块 README 与本节，由用户手动执行后回填。
- **读侧 HTTP 化 / 契约客户端 jar** —— 原因：随平级拆解方案一并否决；模块留在 reactor 内，`framework-ai` 的编译期依赖保持现状。
- **主应用与独立应用共享同一 H2 文件** —— 原因：本期不搬数据、不改主应用装配；当前靠 CWD 不同天然隔离，若将来要合一需单独出方案。
- **两套前端页面归并** —— 原因：全量保留决策，属产品取舍。
- **`~/.workbuddy/mcp.json` 端口调整** —— 原因：全局配置且默认端口未变，无需调整；若将来默认端口改动再单独确认。
- **嵌套仓库未提交改动的提交** —— 原因：外部动作，需用户授权；本次不代为 commit。

---

## 十、实施顺序

1. 复核现状：确认平级目录未创建、`agent-memory-application` 已具备启动形态、前端工程自带 `.gitignore`。
2. 新建 `modules/agent-memory/frontend/` 并 `mv` 前端工程，逐一对账文件数与 git 忽略规则。
3. 修 `run.sh`（固定 CWD）与 `vite.config.js`（端口联动）。
4. 更新模块 README（定位说明、模块结构树、第五章运行方式）与前端 README（新位置、端口联动）。
5. 旧拆解方案标「已否决」，新建本方案并回填实施记录；同步两份 11-06 审计文档的路径引用。
6. 写入项目工作日志；运行态命令交用户手动执行。

---

## 十一、实施记录

- 实施日期：2026-11-06
- 实际改动清单：

| 文件 / 模块 | 改动 |
|---|---|
| `modules/agent-memory/frontend/` | **新建目录** |
| `modules/agent-memory/frontend/agent-memory-ui/` | 由 `frontend/agent-memory-ui/` 整体移入，11820 个文件零增减；源路径已不存在 |
| `modules/agent-memory/agent-memory-application/run.sh` | 在 `SCRIPT_DIR` 后加 `cd "$SCRIPT_DIR"`；更新第 3 条注释 |
| `modules/agent-memory/frontend/agent-memory-ui/vite.config.js` | proxy target 改为 `` `http://localhost:${backendPort}` ``，`backendPort = process.env.AGENT_MEMORY_PORT \|\| '9900'`；补端口联动注释 |
| `modules/agent-memory/README.md` | 第 55 行定位说明改写；第三节模块结构树修正并补 `frontend/`；新增第五节「独立运行」；原第五节顺延为第六节 |
| `modules/agent-memory/frontend/agent-memory-ui/README.md` | 标注新工程位置与模块定位；快速开始补充 `AGENT_MEMORY_PORT` 前后端联动示例 |
| `docs/plans/2026-11-06-agent-memory-extraction.md` | 标题加 `[已否决]`、状态改「已否决/作废」、文首加决策变更说明、第十一节改为「未实施且已否决」 |
| `docs/2026-11-06-agent-only-scope-audit.md` | 前端工程路径引用同步为新位置 |
| `docs/2026-11-06-fullstack-gap-audit.md` | 两处前端工程路径引用同步为新位置 |
| `docs/plans/2026-08-29-agent-memory-ui-standalone.md` | 加一行路径变更注记（历史方案，正文不改） |
| `.workbuddy/memory/2026-11-06.md` | 追加本次决策与实施记录 |

**未改动**：`modules/pom.xml`、`modules/agent-memory/pom.xml`、任一后端 Java 类、主应用 `application.yml`、
`~/.workbuddy/mcp.json`、`frontend/modules/agentmemory`、任何 git 提交、任何构建产物与数据文件。

### 与方案的偏差

| 原方案 | 实际做法 | 原因 |
|---|---|---|
| 新建 `D:/codehub/agent-memory` 平级项目、从 reactor 移除模块 | 完全不做，模块原地保留 | 用户 16:59 明确否决 |
| 需 `agent-memory-support` 承载 7 个框架类、解 `framework-ai` 读侧耦合 | 完全不做 | 留在同一 reactor 内，跨模块依赖本来就是本机 reactor 可见，无自洽问题 |
| 成功标准 6~9 要求真机启动与前端联调 | 仅完成静态核验（1~5），运行态命令写入 README 交用户手动执行 | 用户约定「代码修改后不代为执行 shell 命令或重启」；如实标注为待验收，未伪称已验证 |

### 真机验证结果

**已静态执行并通过**：

- 成功标准 1：目标 `package.json` 存在；源路径 `frontend/agent-memory-ui` 已消失；`D:/codehub/agent-memory` 未创建。
- 成功标准 2：移动前后 `find -type f | wc -l` 均为 `11820`；`src/views` 下 6 个 `.vue`。
- 成功标准 3：嵌套仓库 `git status --porcelain` 计数 `10` → `13`（新增 `M README.md`、`M agent-memory-application/run.sh`、`?? frontend/`）；`git check-ignore -v` 分别命中 `frontend/agent-memory-ui/.gitignore:2`（`node_modules/`）与 `:5`（`dist/`）；外层仓库状态无该工程文件。
- 成功标准 4：`modules/pom.xml` 与 `modules/agent-memory/pom.xml` 均未改动，reactor 结构完整。
- 成功标准 5：`run.sh` 含 `cd "$SCRIPT_DIR"`；`vite.config.js` 语法合法（静态复核）。

**未执行（待用户手动验收）**：成功标准 6、7、8、9 —— 未打包、未启动后端、未启动前端、未发 curl。
命令见模块 README 第五节与本文件第八节场景 3/4，验收后请回填本节。

### 过程中新发现的坑

- **`.gitignore` 双层规则**：根 `.gitignore:5` 是 `frontend/`、`:41` 是 `modules/agent-memory/`，所以前端工程移动前后**都不被外层仓库跟踪**；移入后归 `modules/agent-memory/.git` 这个嵌套仓库管。前端工程自带的 `.gitignore` 在子目录内同样对嵌套仓库生效，`node_modules/` 与 `dist/` 不会被误纳入版本控制 —— 若工程没有自带这份文件，移动后 `git status` 会多出上万条未跟踪项。
- **`run.sh` 的 CWD 陷阱**：脚本注释声称「数据落模块目录」，但它只用 `SCRIPT_DIR` 拼 jar 绝对路径、从未 `cd`，`./data/*` 实际跟随调用者 CWD。从仓库根执行 `./run.sh` 会把 H2 库建到仓库根 `data/`，与 `agent-application` 指向的 `./data/agent-memory` 撞车（后者报错形态是空库/`Database may be already in use`）。已在本次修复。
