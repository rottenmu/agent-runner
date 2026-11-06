# [已否决] 将 agent-memory 从 agent_runner 拆解为平级独立应用

> 🚫 状态：**已否决 / 作废**（2026-11-06 用户改决策；本方案**未实施，也不再实施**）
> 后继方案：`docs/plans/2026-11-06-agent-memory-inrepo-runnable.md`
> 起草日：2026-11-06
> 关联模块：`modules/agent-memory`、`framework/framework-ai`、`agent-application`、`frontend/modules/agentmemory`
> 规范依据：`docs/rules/PLAN_DOC_RULES.md`
> 前置决策：2026-11-06「全量保留，不删除任何模块」

---

> ⚠️ **决策变更（2026-11-06 16:59）**：用户明确「不这么拆解了」——`agent-memory` **仍作为
> `agent_runner` 工程中的一个模块**，但要求可以运行；`agent-memory-ui` 归入
> `modules/agent-memory/frontend/`。
>
> 因此本文件中「平级独立项目 `D:/codehub/agent-memory`」「D1~D4 拍板」「从 reactor 移除
> `<module>agent-memory</module>`」「删除源目录」「复制 `agent-memory-support`」「主应用排除
> `AgentMemoryAutoConfiguration`」等**全部作废，不得据此执行**；`D:/codehub/agent-memory` 也**从未创建**。
>
> 保留本文件是为了不丢调研结论（跨模块依赖面、读侧 9 处 import、双进程 H2 文件锁、嵌套 git 脏工作区），
> 这些事实在后继方案里仍然有效，请当作**历史资料**阅读。

## 零、评审要点（请先拍板这 4 项）

| # | 决策 | 选项 | 建议 |
|---|---|---|---|
| **D1** | `framework-ai` 仍编译期依赖 `agent-memory-core`，读侧怎么解 | **A 契约客户端 jar**（保持类名/签名，内部改 HTTP，主应用只换 pom 坐标）／**B 彻底 HTTP 化**（删 9 处 import、改 `AiHarnessAgentFactory` 9 处构造器、重写 11 个单测）／**C 仅搬目录 + 本地 `mvn install` 整个 core**（不是真独立） | **A**：改动面最小、可回滚、能力不丢；B 留作后续 M5 |
| **D2** | 独立应用监听端口 | `9901`／用户指定 | **9901**。主应用受 `SERVER__PORT` 环境变量覆盖（本机实测 58090），9900 是 yml 默认值，两者都不要占 |
| **D3** | H2 / Arrow 数据文件 | **停机复制到新项目 `data/`**／原地保留 + 绝对路径引用 | **复制**：`agent_runner/data/agent-memory.mv.db`（约 3 MB）、`data/olap/`、`data/agent-memory-files/` 一起带走，主应用不再持有 |
| **D4** | 前端与全局 MCP 指向 | 前端直连新端口／主应用加反向代理／两者 | **直连 + 改 `~/.workbuddy/mcp.json` 端口**；反向代理本期不做 |

> ⚠️ 名称无需改动：Maven `<artifactId>agent-memory</artifactId>`、`spring.application.name: agent-memory`、启动类 `AgentMemoryApplication` 都已对齐，只要目录叫 `D:/codehub/agent-memory` 即可。
> ⚠️ 别和 `D:/codehub/agent-memory-system` 混淆——那是设计稿目录（`DESIGN.md` / `index.html`），不是代码工程，本次不动它。

---

## 一、置信度与剩余风险

- 当前置信度：78%
- 主要剩余风险：`framework-ai` 的 9 处 main + 11 处 test import 与 `AiHarnessAgentFactory` 的 9 处构造器仍指向 `agent-memory-core` 的**进程内实现**；若只搬目录不改调用链，主应用要么编译失败，要么主应用与独立应用同时装配 `AgentMemoryAutoConfiguration` 去抢同一个 `./data/agent-memory.mv.db`（H2 文件锁 / 记忆静默丢失），暴露形式是「两个进程都起来了，但对话里记忆全空且只留 WARN 日志」。

---

## 二、目标

本次要达成：

- 在 `D:/codehub/agent-memory` 建立与 `D:/codehub/agent_runner` **平级**的独立项目，名称 `agent-memory`，保留现有独立 git 仓库与 remote。
- 该项目**自洽**：自带 parent pom、自带所需框架类、自带 `application.yml`、自带 `data/`、自带启动脚本；不依赖 agent_runner 的 reactor 就能 `mvnw clean package` 并 `java -jar` 启动。
- agent_runner **不再包含** `modules/agent-memory` 目录，且不再把完整 `agent-memory-core` 打进自己的 fat jar。
- 两个应用**能同时运行**且端口不冲突；对话写回、L3 画像注入、前端记忆管理页、MCP 端点四项能力**全部保留且可用**。

本次明确不做（延后项见第九节）：

- 不改四层记忆（L0~L3）语义、存储方案（H2 MVStore + Arrow）、召回算法、治理规则。
- 不删 agent_runner 任何模块/工程，不改前端页面结构。
- 不做在线数据迁移、不停机切换；本期接受停机复制。
- 不代用户修改 `~/.workbuddy/mcp.json`（只给出改法，由用户确认后执行）。

---

## 三、成功标准

本方案完成后，以下每一条都应能在真机上直接判定为真：

1. 执行 `ls D:/codehub/agent-memory/pom.xml` 返回文件；执行 `ls D:/codehub/agent_runner/modules/agent-memory` 返回「No such file or directory」；两者父目录同为 `D:/codehub`。
2. 在 `D:/codehub/agent-memory` 执行 `mvnw clean package -DskipTests` → 退出码 `0`，产出 `agent-memory-application/target/agent-memory-application-1.0.0.jar` 且大小 `> 100 MB`（历史实测 155 MB）。
3. 以 `java --add-opens=java.base/java.nio=ALL-UNNAMED -jar <jar>` 启动独立应用 → `curl --noproxy '*' -X POST http://127.0.0.1:9901/api/agent-memory/mcp -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'` 返回 HTTP `200` 且响应体含 `"result"` 与 `tools` 数组，数组长度 `> 0`。
4. 在 `D:/codehub/agent_runner` 执行 `mvnw -pl agent-application -am package -DskipTests` → 退出码 `0`；解压主应用 fat jar 后 `BOOT-INF/lib/` 下**不含** `agent-memory-core-*.jar`（若采用 D1-A，允许存在 `agent-memory-client-*.jar`）。
5. 两个应用同时启动 → `netstat -ano | grep LISTENING` 中同时出现主应用端口与 `9901`，启动日志中 `Address already in use` 出现次数 `= 0`；独立应用日志中 H2 `Database may be already in use` 出现次数 `= 0`。
6. 主应用配置 `ai.chat-recorder.url` 指向 `http://127.0.0.1:9901/api/agent-memory/mcp` 后，发一次 `POST /api/biz/ai/chat` → `ChatTurnMemoryRecorder` 的 `consecutiveFailures` 保持 `0`、`droppedTurns` 不增长；独立应用侧 `data/agent-memory.mv.db` 的 mtime 在对话后更新。
7. 通过 MCP `memory_write` 写入一条 L3 画像，再发一次对话 → 主应用日志中 `personaFragment` 非空（或 `MemoryPromptMiddleware` 命中计数 `> 0`），且对话响应 HTTP `200`。
8. 打开 `frontend/modules/agentmemory` 的记忆列表页 → 网络面板中 `/api/ai/memory/**` 请求返回 HTTP `200`，列表记录数 `≥ 1`，控制台无 `4xx/5xx` 与 ReferenceError。
9. 搬迁前后 `agent-memory.mv.db` 的 md5 **一致**（停机复制，内容零改动）；`D:/codehub/agent-memory` 下 `git log --oneline -1` 输出与搬迁前嵌套仓库 HEAD 相同的提交哈希。

---

## 四、参照行为（对标）

| 参照项 | 它怎么做 | 我们要复刻到什么程度 |
|---|---|---|
| `docs/plans/2026-08-29-agent-memory-backend-standalone.md`（已落地） | 在同一仓库内新增 `agent-memory-application`，只装配记忆能力，无需 MySQL，单独 `java -jar` 启动；端口 9900、需 `unset SERVER__PORT`、需 `--add-opens`、启动类放 `com.zimo.agentmemory.app` 避开组件扫描 | **全部复刻**启动类/yml/排除了 RocksdbStorageAutoConfiguration 与 API 注册表、显式 `JdbcTemplate` Bean、启动脚本；**不复制**「两者不要同时运行」这个限制——本次正是要消除它 |
| 该方案第七节实施记录（2026-08-29 真机验证） | `/api/ai/memory/policy` 200、MCP `tools/list` + `memory_write` 成功、REST 写入且脱敏生效、ETL 全量重建 0 行 | 作为 M0 的**基线**，搬移后必须逐条重跑并达到同样结果 |

---

## 五、现状差距

### 能力缺失

- **没有自洽的构建根**：`modules/agent-memory/pom.xml` 的 parent 是 `com.zimo:modules:1.0.0`，来自 agent_runner；`modules/pom.xml:27` 把它列入 reactor。搬出后 parent 会找不到。
- **跨模块依赖无来源**：`agent-memory-core/pom.xml` 依赖 `framework-common`、`module-datasource-storage`、`framework-autoconfig`、`agentscope-harness`。前三者属 agent_runner。
- **端口冲突**：`agent-memory-application/src/main/resources/application.yml:2` 写死 `port: 9900`，与 `agent-application` 默认端口相同；历史方案明确写了「两者不要同时运行」。
- **数据目录与 CWD 绑定**：`h2-url: jdbc:h2:file:./data/agent-memory;MODE=LEGACY`、`olap-arrow-data-file: ./data/olap/l0_log.arrow`、`file-base-dir: ./data/agent-memory-files`。主应用 `application.yml:181-191` 也有一份**指向同一文件**的 `agent-memory.*` 配置。
- **编译期读侧耦合**：`framework-ai` 有 9 处 main import（`AiHarnessAgentFactory`、`AiAgentService`、`AiChatRequest`、`OpenAiCompatibleChatClient`、`AiAgentAutoConfiguration`、`MemoryPromptMiddleware`、`PlanWritebackCoordinator` 等）+ 11 处 test import，指向 `com.zimo.module.agentmemory.{chat,engine,memory}`；`AiHarnessAgentFactory` 有 9 处构造器传 `AiMemoryService`，并在 `:424` 调 `AiMemoryAgentTool.register(toolkit, memoryService, tenantId)`。这些类内部依赖 `governance.*` / `storage.OltpMemoryRepository` / `model.*`，**不是薄 DTO，搬不干净**。
- **自动装配会双开**：`agent-memory-core` 的 `AutoConfiguration.imports` 含 `AgentMemoryAutoConfiguration` 与 `AgentMemorySyncAutoConfiguration`；任何引入该 jar 的 Spring Boot 应用都会装配整套记忆服务。
- **端点指向写死**：`MemoryMcpClient.java:186` 兜底 `http://127.0.0.1:9900/api/agent-memory/mcp`；`AuthProperties.java:26` 白名单含该路径；`frontend/modules/agentmemory/src/api/memory.js` 打 `/api/ai/memory/*`（走 web-shell 9900）；`frontend/agent-memory-ui/vite.config.js:11` proxy 到 9900；用户全局 `~/.workbuddy/mcp.json` 的端点也是 9900。

### 只是没接线

- **独立应用形态已存在且验证过**：`AgentMemoryApplication.java` + `application.yml` + `spring-boot-maven-plugin repackage` + `run.sh` 齐备，2026-08-29 端到端跑通，`target/agent-memory-application-1.0.0.jar` 已有（155 MB）。
- **写侧已经是 HTTP**：`ChatTurnMemoryRecorder → MemoryMcpClient → POST /api/agent-memory/mcp`，且 `ai.chat-recorder.url` 的配置注释明写「显式配置时才用它，例如指向另一台机器的记忆服务」——**解耦开关现成，只是当前留空**。
- **跨模块依赖面很小**：`agent-memory-core` 对 `framework-common` 只有 4 个类（`PluginRegister`、`AiEventPublisher`、`ConversationTurnEvent`、`FileStorageService`），对 `module-datasource-storage` 只有 3 个（`StorageContext`、`StorageProvider`、`StorageRouter`），对 `framework-autoconfig` **零 Java import**（仅 pom 依赖，且独立应用 yml 已关闭 api-registry / storage）。→ 只需一个精简 `agent-memory-support` 模块即可自洽。
- **git 已独立**：`modules/agent-memory/.git` 是完整仓库，remote `git@ssh.github.com:rottenmu/agent-memory.git`；外层 `agent_runner/.gitignore:41` 明确忽略 `modules/agent-memory/`，`git ls-files` 计数 `0` → 搬移**不影响外层仓库索引**。

### 不打算解决的问题

- L2 场景层空转、OLAP(Arrow) 只写不读、RAG embedding 永久熔断等历史功能缺陷——属功能补齐，与拆解无关，见 `docs/2026-11-06-agent-only-scope-audit.md` §3。
- `agent-memory-ui`(6060) 与主壳 `agentmemory` 6 个同名页面以哪套为准——上一轮已列为待确认，本期不裁决。

---

## 六、技术方向

### 1. 目录与构建独立（必做，无争议）

职责：把 `modules/agent-memory/` 整体移到 `D:/codehub/agent-memory/`，改父 pom 为独立坐标 `com.zimo:agent-memory:1.0.0`（`packaging=pom`），子模块 `agent-memory-core` / `agent-memory-application` 保持。

关键结构：

```text
D:/codehub/
├── agent_runner/                 # 不再有 modules/agent-memory
└── agent-memory/                 # 新平级项目，沿用现有嵌套 .git
    ├── pom.xml                   # 独立 parent，自带 properties / dependencyManagement
    ├── agent-memory-support/     # 新增：7 个框架类（见方向 2）
    ├── agent-memory-core/
    ├── agent-memory-application/ # 启动类 + yml + run.sh
    └── data/                     # H2 / Arrow / MEMORY.md（D3 复制）
```

取舍理由：不改成 Spring Cloud 微服务、不引入注册中心——现有 MCP/REST 已足够，加中间件只会放大故障面。

### 2. 跨模块依赖自洽：`agent-memory-support`

职责：承载从 agent_runner 复制过来的 7 个类，包名**保持不变**（`com.zimo.framework.common.*` / `com.zimo.module.ds.storage.*`），避免改 core 的 7 处 import。

关键接口：`PluginRegister`、`AiEventPublisher`、`ConversationTurnEvent`、`FileStorageService`、`StorageContext`、`StorageProvider`、`StorageRouter`。

取舍理由：相比「复制整个 framework-common / framework-autoconfig / ds.storage 三个模块」，7 个类更可控；相比「依赖 agent_runner 的本地 Maven 仓库产物」，它**没有构建顺序耦合**，符合「单独作为一个应用」。**风险**：这 7 个类内部可能还有传递依赖，M0 编译实验要按报错补齐闭包；`framework-autoconfig` 因零 Java import，可直接从 pom 移除。

### 3. 读侧解耦（D1，需拍板）

| 方案 | 职责边界 | 改动面 | 取舍 |
|---|---|---|---|
| **A 契约客户端 jar**（推荐） | 独立项目额外产出 `agent-memory-client`，**保留** `com.zimo.module.agentmemory.{chat,engine,memory}` 的包名与公开签名，内部实现改为调用独立应用 REST/MCP；独立应用新增「召回片段 / 会话压缩 / 记忆工具」3 组端点 | `framework-ai/pom.xml:53`、`agent-application/pom.xml:64` 换坐标；`framework-ai` 9 处 import 与 `AiHarnessAgentFactory` 9 处构造器**不动**；11 个单测多数可保留 | 改动最小、可回滚；代价是两项目间有 `mvn install` 构建顺序，且「同名类、不同实现」容易误读，必须在 client README 写明 |
| **B 彻底 HTTP 化** | 删除全部 `com.zimo.module.agentmemory.*` import，framework-ai 内新增 REST 客户端 | 9 处 main + 11 处 test import、9 处构造器、`AiMemoryAgentTool.register` 调用点、11 个单测重写 | 零编译期耦合最干净，但工作量大、回归面广；建议留作 M5 |
| **C 仅搬目录 + 本地 install 整个 core** | agent_runner 通过本地仓库依赖完整 core | 仅改 pom 与目录 | **不推荐**：主应用仍会把 `AgentMemoryAutoConfiguration` 装进自己进程，与独立应用抢同一 H2 文件，不满足「单独作为一个应用」 |

**无论选哪个，都要做**：主应用排除 `AgentMemoryAutoConfiguration` / `AgentMemorySyncAutoConfiguration`（`spring.autoconfigure.exclude`），并清掉 `agent-application/src/main/resources/application.yml:180-191` 的 `agent-memory.*` 段 —— 保证 H2 文件只被独立应用打开。

### 4. 端口、端点与数据路径

职责：独立应用 `server.port: 9901`（D2），主应用保持现状；`ai.chat-recorder.url` 显式指向 `http://127.0.0.1:9901/api/agent-memory/mcp`，不再依赖 9900 兜底推导。

关键配置：

```text
agent-memory/application.yml : server.port = 9901
agent-memory/data/          : agent-memory.mv.db / olap/ / agent-memory-files/
agent_runner/application.yml: ai.chat-recorder.url = http://127.0.0.1:9901/api/agent-memory/mcp
                              spring.autoconfigure.exclude = 两个 AgentMemory*AutoConfiguration
~/.workbuddy/mcp.json       : 端点端口 9900 → 9901（由用户确认后修改）
```

取舍理由：不做反向代理，避免主应用再次成为记忆链路的单点；代价是前端 `memory.js` 的 `/api/ai/memory/*` 与 `agent-memory-ui` 的 proxy 需要改目标（D4）。

### 5. git 与未提交改动（**红线前置**）

`modules/agent-memory` 嵌套仓库当前有 **6 个已修改 + 4 个未跟踪**文件（含此前实施的 `MemoryScorer`、`AgentMemoryPluginRegister`、`PersonaFragmentComposer` 等）。搬移前必须先 `git add` + `git commit`（或 `git stash`）并在新路径 `git status` 复核为空，否则这些改动会随目录移动变成「新仓库里的脏工作区」甚至丢失。

外层仓库无需 `git rm`（`.gitignore:41` 已忽略、`git ls-files` 计数 0）；搬移后把 `.gitignore` 里那条 `modules/agent-memory/` 悬空规则一并清理。

---

## 七、里程碑

### M0. 拍板 + 验证性实验

- 产出：D1~D4 结论；复制 7 个类到临时目录编译，确认传递依赖闭包；确认 H2 文件在无主应用时能被独立应用独占打开。
- 验证：独立应用按 08-29 记录重跑 4 个端点，全部 HTTP 200；主应用**不启动**时 `data/agent-memory.mv.db` 无锁冲突。

### M1. 物理拆分 + 独立构建

- 产出：`D:/codehub/agent-memory` 自洽工程；agent_runner 移除目录与 reactor 注册；嵌套 git 改动已提交。
- 验证：成功标准 1、2、4、9。

### M2. 读侧解耦（按 D1 结论）

- 产出：主应用不再装配记忆服务、不再持有 H2 文件；对话读侧经 HTTP 拿到片段。
- 验证：成功标准 4、5、7。

### M3. 双应用联调

- 产出：写回 URL、前端目标、MCP 令牌、全局 mcp.json 端口全部对齐。
- 验证：成功标准 3、5、6、8。

### M4. 收尾

- 产出：两项目 README / 启动脚本 / 数据目录说明；本文件回填第十一节。
- 验证：成功标准 1~9 逐条复跑一次。

---

## 八、自测计划

### 场景 1：独立构建与启动

1. 在 `D:/codehub/agent-memory` 执行 `mvnw clean package -DskipTests`。
2. 验证：退出码 `0`；jar 存在且 `> 100 MB`；日志无 `Could not resolve dependencies`。
3. 执行 `env -u SERVER__PORT java --add-opens=java.base/java.nio=ALL-UNNAMED -jar agent-memory-application-1.0.0.jar`（`run.sh` 已封装）。
4. 验证：日志出现 `Started AgentMemoryApplication`；`netstat` 出现 `9901 LISTENING`；无 `Database may be already in use`、无 `Address already in use`。
5. `curl --noproxy '*' -X POST http://127.0.0.1:9901/api/agent-memory/mcp -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'`。
6. 验证：HTTP `200`，响应含 `"result"`，`tools` 长度 `> 0`。

### 场景 2：双应用并行（本期核心，历史方案未覆盖）

1. 保持独立应用运行，在仓库根执行 `python start-backend.py` 启动主应用。
2. 验证：两个端口同时 LISTENING；主应用日志中 `AgentMemoryAutoConfiguration` 装配次数 `= 0`（用 actuator/日志检索确认）。
3. 执行 `ls -la D:/codehub/agent_runner/data/agent-memory.mv.db*` → 期望：主应用不再新建/改写该文件。
4. 验证：独立应用日志无 H2 锁警告；`curl` 两个端口各自返回本应用的健康检查。

### 场景 3：对话写回与记忆注入

1. 确认主应用 `ai.chat-recorder.url` 已指向 9901、`token` 与独立应用 `mcp-token` 一致或两端都留空。
2. `curl --noproxy '*' -X POST http://127.0.0.1:<主应用端口>/api/biz/ai/chat -d '<合法对话体>'`。
3. 验证：HTTP `200`；主应用日志 `ChatTurnMemoryRecorder` 无新增 WARN，`consecutiveFailures=0`；独立应用 `data/agent-memory.mv.db` mtime 更新。
4. 用 MCP `tools/call` 调 `memory_write` 写入一条 L3 画像。
5. 再发一次对话，验证：主应用日志 `personaFragment` 非空或命中计数 `> 0`，响应 HTTP `200`。

> ⚠️ **语义缺陷声明**：`GET /api/agent-memory/mcp` 返回 200 **不等于**写入成功（该端点对 GET 也可能返回信封）。必须用 `tools/call` 后查 H2 行数或 `/api/ai/memory/**` 列表，`通过 ≠ 正确`。

### 场景 4：前端与全局 MCP

1. 起 `frontend/web-shell`（16600）与 `frontend/agent-memory-ui`（6060），打开记忆管理列表页。
2. 验证：`/api/ai/memory/**` 请求 HTTP `200`、记录数 `≥ 1`、控制台无 `4xx/5xx`、无 ReferenceError。
3. 在 WorkBuddy 侧调用一次 `memory_search` / `memory_write`。
4. 验证：返回 `sensitiveMasked` 字段且无连接错误；独立应用日志出现对应请求。

---

## 九、已知延后项

- **D1-B 彻底 HTTP 化** —— 原因：改动 9 处 import + 9 处构造器 + 11 个单测，回归面过大；先用 A 拿到可回滚的独立形态，M5 再收敛。
- **主应用反向代理记忆端点** —— 原因：会让主应用重新成为记忆链路单点，与「独立应用」目标相悖；本期前端直连新端口。
- **`frontend/agent-memory-ui` 与主壳 `agentmemory` 重复页面归并** —— 原因：上轮决策要求全部保留，归并属产品取舍，不在拆解范围。
- **在线数据迁移 / 不停机切换** —— 原因：H2 文件库不支持多进程写，本期接受停机复制。
- **`~/.workbuddy/mcp.json` 的实际改写** —— 原因：属用户全局配置、且端点变更影响其他会话；只在方案给出改法，由用户确认后执行。
- **`agent-memory` 独立仓库推送到 remote** —— 原因：外部动作，需用户明确授权，本期只做本地搬移。

---

## 十、实施顺序

1. **用户拍板 D1~D4**；未拍板不进入执行。
2. 处理嵌套仓库 6 M + 4 ?? 未提交改动（提交或 stash），记录当前 HEAD 哈希。
3. M0 验证性实验：7 个类传递依赖闭包、H2 独占、08-29 四个端点基线复跑。
4. 复制（非剪切）`modules/agent-memory/` 到 `D:/codehub/agent-memory/`，保留 `.git`；先不删源目录。
5. 改造新项目 pom（独立 parent + `agent-memory-support` + 去掉 `framework-autoconfig`）、yml（9901）、`run.sh`、复制 `data/`。
6. 独立项目 `mvnw clean package` 通过并跑通场景 1。
7. 按 D1 实施读侧解耦，改主应用两处 pom + `application.yml`（排除自动装配、清 `agent-memory.*`、设 `chat-recorder.url`）。
8. 主应用 `mvnw -pl agent-application -am package -DskipTests` 通过；跑场景 2、3、4。
9. 确认全部成功标准后，才删除 `agent_runner/modules/agent-memory/` 源目录，并从 `modules/pom.xml:27` 移除 `<module>agent-memory</module>`、清理根 `.gitignore:41` 悬空规则。
10. 回填本文件第十一节。

> 每步可回滚：第 4 步用复制而非移动，第 9 步之前源目录始终完整；任一验证失败只需停止删除源目录即可回到现状。

---

## 十一、实施记录

**未实施，且已否决**。本方案按 `AGENTS.md` 要求先落文档等待评审；评审阶段用户改变决策（见文首「决策变更」），
故从未进入执行：未创建 `D:/codehub/agent-memory`、未搬移任何代码、未改主应用 pom/yml、未动嵌套 git 提交、
未修改 `~/.workbuddy/mcp.json`。实际执行内容见 `docs/plans/2026-11-06-agent-memory-inrepo-runnable.md`。
