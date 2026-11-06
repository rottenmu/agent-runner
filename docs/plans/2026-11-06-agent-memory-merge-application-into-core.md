# 将 agent-memory-application 的代码与配置整合进 agent-memory-core

> 状态：已落地（静态核验完成；构建与运行态待用户手动验收）
> 起草日：2026-11-06
> 关联模块：`modules/agent-memory`（聚合）、`agent-memory-core`、`agent-memory-application`（退役）
> 上游依赖方：`framework/framework-ai`、`agent-application`
> 规范依据：`docs/rules/PLAN_DOC_RULES.md`
> 前置决策：① 11-06「全量保留，不删除任何模块」；② 11-06「不拆平级、留在 reactor 内、前端归位」（`2026-11-06-agent-memory-inrepo-runnable.md`）

---

## 一、置信度与剩余风险

- 当前置信度：86%（文件迁移、pom、脚本、组件扫描范围均已静态核实；**未执行构建与启动**，按用户约定不代为运行）
- 主要剩余风险：`spring-boot-maven-plugin` 加 `classifier=exec` 后，根 pom 继承的 `spring-boot-starter-parent:3.4.5` 的 pluginManagement 已绑定 id=`repackage` 的 execution，core 又显式声明了一个无 id（`default`）的 execution —— 若两者都执行 `repackage`，理论上可能出现二次打包告警。原 `agent-memory-application/pom.xml` 就是同样的写法且 2026-09-29 打包成功（149 MB jar 现存），故沿用；验收时以 `mvn package` 退出码与两个 jar 是否同时存在为准。

---

## 二、目标

本次要达成：

- 把 `agent-memory-application` 的**代码（启动类）与配置（yml）**整合进 `agent-memory-core`，core 同时具备「库」与「可独立运行应用」两种身份。
- `run.sh` 与 `data/` 随运行入口一起迁入 core，数据文件零丢失。
- 聚合 pom 只保留 `agent-memory-core` 一个子模块；`agent-memory-application` 目录**退役但不删除**（旧 jar 与回滚说明保留）。
- 不破坏 `framework-ai` / `agent-application` 对 core 的**编译期依赖**与**运行时配置**。

本次明确不做（延后项见第九节）：

- 不拆平级项目、不从 `modules/pom.xml` 移除 `agent-memory`、不改 `framework-ai` 的 9 处 main / 11 处 test import。
- 不改四层记忆语义、存储方案、召回算法、REST/MCP 端点。
- 不改默认端口 9900、不改 `~/.workbuddy/mcp.json`、不动 `frontend/` 任何文件、不动主应用配置与数据。
- 不删除 `agent-memory-application/` 目录及其 `target/` 旧 jar（用户上轮明确「不能删除」）。
- 不执行构建/启动命令、不提交 git。

---

## 三、成功标准

本方案完成后，以下每一条都应能在真机上直接判定为真：

1. `ls modules/agent-memory/agent-memory-core/src/main/java/com/zimo/agentmemory/app/AgentMemoryApplication.java` 与 `.../src/main/resources/application-agentmemory.yml` 均返回文件；core 的 `src/main/resources/` 根下**不存在** `application.yml`；原 `agent-memory-application/` 的**源码树**已无 `.java` 与 `.yml` 文件（`target/classes/application.yml` 是旧编译产物，不计、也不参与构建）。
2. `agent-memory-core/pom.xml` 含 `<classifier>exec</classifier>` 且 `<mainClass>` 指向 `com.zimo.agent.memory.AgentMemoryApplication`；`modules/agent-memory/pom.xml` 的 `<modules>` 只有 `<module>agent-memory-core</module>`；`agent-memory-application/pom.xml` 不存在（已改名 `pom.xml.retired`）。
3. 全仓 `@ComponentScan` 的 `basePackages` 均为具体子包（实测 `com.zimo.module.auth` / `com.zimo.module.sys` / `com.zimo.module.ai.controller` 等），**无 `com.zimo` 宽扫描** → 启动类不会被主应用误装配。
4. `bash -n modules/agent-memory/agent-memory-core/run.sh` 退出码 `0`；脚本内同时包含 `--spring.profiles.active=agentmemory`、`agent-memory-core-1.0.0-exec.jar`、`cd "$SCRIPT_DIR"`。
5. `agent-memory-core/data/` 下 4 个文件字节数与迁移前一致：`agent-memory.mv.db` 53248、`agent-memory.trace.db` 7525、`olap/l0_log.arrow` 2146、`olap/l0_log.arrow.cursor` 1。
6. 用户手动执行 `./mvnw -pl modules/agent-memory/agent-memory-core -am package -DskipTests` → 退出码 `0`；`target/` 下同时存在 `agent-memory-core-1.0.0.jar`（普通库 jar）与 `agent-memory-core-1.0.0-exec.jar`（`> 100 MB`）；`jar tf` 普通 jar 能列出 `com/zimo/module/agentmemory/memory/AiMemoryService.class`。
7. 用户手动执行 `cd modules/agent-memory/agent-memory-core && ./run.sh` → 日志出现 `Started AgentMemoryApplication`、激活 profile `agentmemory`、`:9900 LISTENING`、无 `Database may be already in use`；MCP `tools/list` 返回 HTTP `200` 且 `tools` 长度 `> 0`。
8. 用户手动启动主应用 `agent-application` → **编译通过**（证明 fat jar 没替换主构件）；启动日志中主应用端口不被改成 9900、`framework.api-registry` 未被关闭（证明 profile 配置没被自动加载）。
9. `agent-memory-application/target/agent-memory-application-1.0.0.jar` 仍存在（149 MB）；该目录下 `RETIRED.md` 与 `pom.xml.retired` 存在。

---

## 四、参照行为（对标）

| 参照项 | 它怎么做 | 我们要复刻到什么程度 |
|---|---|---|
| `docs/plans/2026-08-29-agent-memory-backend-standalone.md` | 独立模块只装配记忆能力，启动类放 `com.zimo.agentmemory.app` 避开组件扫描，`java -jar` 启动 | **全部复刻**启动类/配置/排除 RocksDB 与 api-registry/显式 `JdbcTemplate` Bean；本次只把承载模块换成 core |
| Spring Boot 官方 `spring-boot-maven-plugin` classifier 用法 | 库模块用 `<classifier>exec</classifier>`，主构件保持普通 jar，另产可执行 jar | **完整复刻**：这是「一个模块同时是库和可执行应用」的标准解法 |
| 仓库已踩坑：相对 `./data` 与 CWD 绑定（SQLite / H2） | 从不同目录启动读到空库 | **反向复刻**：`run.sh` 已 `cd "$SCRIPT_DIR"`，迁移后仍保持 |

---

## 五、现状差距

### 能力缺失

- `agent-memory-application` 与 `agent-memory-core` 是**两个模块但只有 2 个源码/配置文件**：application 里仅一个启动类 + 一个 `application.yml`，其余全靠 core 传递依赖，模块拆分收益低于维护成本。
- 直接把这两个文件按原名搬进 core 会**静默破坏主应用**，有两处：
  1. **fat jar 替换主构件**：core 被 `framework-ai`（pom `:53`）与 `agent-application`（pom `:64`）依赖。常规 `repackage` 会把主 jar 换成 `BOOT-INF/classes` 结构，依赖方编译期找不到 `com.zimo.module.agentmemory.*`，表现为整仓编译失败而非记忆功能异常。
  2. **classpath 配置污染**：`application.yml` 放在 core 的 resources 根，任何依赖 core 的 Spring Boot 应用都会自动加载它 —— `server.port: 9900`、`framework.api-registry.enabled=false`、`framework.storage.enabled=false` 会盖到主应用上，表现为「主应用端口莫名变了 / API 注册表失效」，很难归因。
- 模块 README 的结构树、构建命令、运行命令全部指向 `agent-memory-application`，整合后会失效。

### 只是没接线

- core 的 pom **本就已有** `spring-boot-starter-web` / `spring-boot-starter-jdbc`，application 的 pom 只是重复声明，无需搬依赖。
- 根 pom 继承 `spring-boot-starter-parent:3.4.5` → `spring-boot-maven-plugin` 版本由 pluginManagement 提供，core 里无需写 `<version>`。
- 启动类包名 `com.zimo.agentmemory.app` 与 core 业务包 `com.zimo.module.agentmemory` 不同树，迁移后**包名不变即可**，无需改任何 import。
- 主应用启动类在 `com.zimo.agentapplication`，组件扫描只覆盖该包及其子包；全仓其余 `@ComponentScan` 也都指定了具体子包 → 启动类不会被误扫（已逐一核实）。
- core 的测试里**没有** `@SpringBootTest` / `@ContextConfiguration`，不会因新增启动类而改变测试上下文查找结果。
- `agent-memory-application` **无任何模块依赖它**（全仓 grep 仅历史文档提及），从 reactor 移除是安全的。

### 不打算解决的问题

- 主应用与独立应用共享同一 H2 文件导致的互锁：当前靠 CWD 不同天然隔离，本期不改主应用装配。
- `agent-memory-ui` 与主壳 `agentmemory` 6 个同名页面的归并：全量保留决策，不裁决。
- 退役目录的彻底删除：用户上轮明确不能删，保留 `target/` 旧 jar 与回滚说明。

---

## 六、技术方向

### 1. 文件迁移（physically 合并）

职责：4 项移入 core，**启动类包名不变**，配置文件改名。

| 原路径 | 新路径 | 关键决策 |
|---|---|---|
| `application/src/main/java/com/zimo/agentmemory/app/AgentMemoryApplication.java` | `core/src/main/java/com/zimo/agentmemory/app/AgentMemoryApplication.java` | 包路径逐字不变，避免组件扫描冲突与 import 改动 |
| `application/src/main/resources/application.yml` | `core/src/main/resources/application-agentmemory.yml` | **改名**：profile 专用文件只在激活 `agentmemory` 时加载，依赖方零感知 |
| `application/run.sh` | `core/run.sh` | 改 jar 名、加 `--spring.profiles.active=agentmemory`、CWD 落到 core |
| `application/data/` | `core/data/` | 4 个数据文件原样随迁 |

取舍理由：不用 `spring.config.additional-location` 外置配置（会失去单 jar 可运行），也不用「只在 exec jar 里打 yml」的复杂 assembly（构建脆弱）。profile 专用文件是 Spring Boot 原生机制，改动最小。

### 2. core 同时是库与应用（classifier）

职责：`agent-memory-core/pom.xml` 加 `spring-boot-maven-plugin`：

```xml
<configuration>
    <mainClass>com.zimo.agent.memory.AgentMemoryApplication</mainClass>
    <classifier>exec</classifier>
</configuration>
```

产物：

```text
agent-memory-core-1.0.0.jar       ← 主构件，普通库 jar（framework-ai / agent-application 用）
agent-memory-core-1.0.0-exec.jar  ← 附属构件，可执行 fat jar（run.sh 用）
```

取舍理由：相比「再拆一个薄启动模块」（等于回到两个模块）、「core 只打 fat jar」（破坏依赖方）、「用 `spring-boot:run` 启动」（无单 jar 交付），classifier 是唯一同时满足「一个模块」与「库可被依赖」的做法。代价是 `mvn package` 每次多产一个约 150 MB 的 jar。

### 3. 退役而非删除

职责：`agent-memory-application/pom.xml` → `pom.xml.retired`；新增 `RETIRED.md`（去向表、classifier 原因、回滚 4 步）；清空源码空目录；**保留 `target/` 旧 jar**；聚合 pom 的 `<modules>` 只剩 core。

取舍理由：符合用户「不能删除」的红线，且保留一份可对照的历史构建产物与回滚路径。`pom.xml.retired` 不会被 Maven 识别，目录也不在 reactor 中，不会被误构建。

---

## 七、里程碑

### M1. 迁移 + pom 改造 + 退役

- 产出：core 持有启动类/profile 配置/run.sh/data；pom 带 classifier；聚合 pom 单模块；退役目录标注完成。
- 验证：成功标准 1~5、9。

### M2. 文档同步 + 静态核验

- 产出：模块 README 结构树与第五节运行方式重写；新方案文档与实施记录；上游方案加后续变更注记；工作日志。
- 验证：成功标准 1~5、9 复核；6~8 的命令写入 README 交用户手动执行。

---

## 八、自测计划

### 场景 1：迁移完整性与 git 安全（已静态执行）

1. `ls` 四项新路径；`find core/src/main/resources -maxdepth 1 -name 'application.yml'` → 期望无输出。
2. `find agent-memory-application -name '*.java' -o -name '*.yml'` → 期望无输出。
3. 对 `core/data/` 四个文件 `stat -c %s` → 期望 53248 / 7525 / 2146 / 1，与迁移前一致。
4. `grep -c 'agent-memory-application' modules/agent-memory/pom.xml` → 期望 `1`（仅注释）。
5. 验证：`agent-memory-application/target/*.jar` 存在；嵌套仓库 `git status --porcelain` 不出现上万条未跟踪项。

### 场景 2：静态语法与扫描范围（已静态执行）

1. `bash -n core/run.sh` → 退出码 `0`。
2. `grep` 检查脚本含 `--spring.profiles.active=agentmemory`、`-exec.jar`、`cd "$SCRIPT_DIR"`。
3. `grep -rn '@ComponentScan' --include='*.java'` → 逐条确认 basePackages 非 `com.zimo` 宽扫描。
4. 验证：`framework-ai` 与 `agent-application` 的 pom 仍声明依赖 `agent-memory-core`，坐标未变。

### 场景 3：构建与「库身份」验证（**待用户手动执行**）

1. 仓库根执行 `./mvnw -pl modules/agent-memory/agent-memory-core -am package -DskipTests`。
2. 验证：退出码 `0`；两个 jar 同时存在；`jar tf agent-memory-core-1.0.0.jar | grep 'com/zimo/module/agentmemory/memory/AiMemoryService.class'` 有输出。
3. 若出现二次 repackage 告警 → 删除 core pom 中 `<executions>` 块，只保留 `<configuration>`（依赖 parent 的 pluginManagement execution）后重跑。
4. 再执行 `./mvnw -pl agent-application -am package -DskipTests` → 退出码 `0`（证明依赖方可编译）。

### 场景 4：双身份运行验证（**待用户手动执行**）

1. `cd modules/agent-memory/agent-memory-core && ./run.sh`。
2. 验证：日志 `Started AgentMemoryApplication`、`The following 1 profile is active: "agentmemory"`、`:9900 LISTENING`、无 H2 锁警告。
3. `curl --noproxy '*' -X POST http://127.0.0.1:9900/api/agent-memory/mcp -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'` → HTTP `200`、`tools` 长度 `> 0`。
4. 另起主应用 `python start-backend.py`（仓库根）→ 编译通过、端口仍为 `SERVER__PORT` 指定值、API 注册表未关闭。
5. 验证：`core/data/agent-memory.mv.db` mtime 更新；主应用 `data/` 未被独立应用改写。

> ⚠️ **语义缺陷声明**：`GET /api/agent-memory/mcp` 返回 200 **不等于**写入成功（该端点对 GET 也可能返回信封）。
> 要验证写入必须 `tools/call` 调 `memory_write` 后查 H2 行数或 `/api/ai/memory/**` 列表，**通过 ≠ 正确**。

---

## 九、已知延后项

- **构建与运行态真机验收（成功标准 6~8）** —— 原因：按用户约定「代码修改后不代为执行 shell 命令或重启」，命令已写入模块 README 第五节与本节，由用户手动执行后回填。
- **删除 `agent-memory-application/` 退役目录及其 149 MB 旧 jar** —— 原因：用户 11-06 明确「不能删除」；待其确认产物已可重建后再清理。
- **读侧 HTTP 化 / 契约客户端 jar** —— 原因：随平级拆解方案一并否决，模块留在 reactor 内，编译期依赖保持。
- **历史文档中 `agent-memory-application` 的路径引用** —— 原因：08-29 / 09-15 / 09-17 / 09-29 等方案与代码说明属历史快照，正文不改；当前有效的 README、11-06 两份方案已同步。
- **`~/.workbuddy/mcp.json` 端口调整** —— 原因：默认端口仍为 9900，无需调整。

---

## 十、实施顺序

1. 核实依赖面：`agent-memory-application` 无人依赖、core 被 framework-ai / agent-application 依赖、根 pom parent 提供插件版本、全仓无宽组件扫描。
2. `mv` 四项到 core（配置文件改名），启动类与 run.sh 内文同步更新。
3. core pom 加 `classifier=exec` 插件；聚合 pom 移除 application 模块。
4. 退役目录：`pom.xml` → `pom.xml.retired`、清空源码空目录、写 `RETIRED.md`、保留 `target/`。
5. 更新模块 README（定位说明、2.2 标题、结构树、第五节）、上游方案注记、本方案实施记录、工作日志。
6. 静态核验；构建与运行命令交用户手动执行。

---

## 十一、实施记录

- 实施日期：2026-11-06
- 实际改动清单：

| 文件 / 模块 | 改动 |
|---|---|
| `agent-memory-core/src/main/java/com/zimo/agentmemory/app/AgentMemoryApplication.java` | 由 application 模块整体移入，**包名与类名不变**；javadoc 更新 jar 名、profile 参数与合并说明 |
| `agent-memory-core/src/main/resources/application-agentmemory.yml` | 由 `application.yml` 移入并**改名**；头部加 8 行注释说明为何不能叫 `application.yml` 及启动必带 profile |
| `agent-memory-core/run.sh` | 由 application 模块移入；jar 改为 `agent-memory-core-1.0.0-exec.jar`；新增 `--spring.profiles.active=agentmemory`；注释与打包提示更新 |
| `agent-memory-core/data/` | 4 个 H2/Arrow 数据文件由 `application/data/` 原样随迁 |
| `agent-memory-core/pom.xml` | 新增 `spring-boot-maven-plugin`（`mainClass` + `<classifier>exec</classifier>`），附 7 行说明为何必须用 classifier |
| `modules/agent-memory/pom.xml` | `<modules>` 移除 `agent-memory-application`，只留 core；加注释 |
| `agent-memory-application/pom.xml` | 改名为 `pom.xml.retired`（不再被 Maven 识别） |
| `agent-memory-application/RETIRED.md` | **新建**：去向表、classifier 原因、保留内容、4 步回滚 |
| `agent-memory-application/src/` | 源码文件迁出后清空空目录树；`target/` 旧 149 MB jar **保留** |
| `modules/agent-memory/README.md` | 定位说明、2.2 标题、模块结构树、第五节运行方式（含两个设计警告）全部重写 |
| `docs/plans/2026-11-06-agent-memory-inrepo-runnable.md` | 文首加「后续变更」注记，声明成功标准 4 与相关描述已被取代 |
| `docs/plans/2026-11-06-agent-memory-merge-application-into-core.md` | **新建**本方案 |
| `.workbuddy/memory/2026-11-06.md`、`MEMORY.md` | 追加决策与实施记录 |

**未改动**：`modules/pom.xml`、任一后端业务 Java 类、`framework-ai` / `agent-application` 的 pom 与代码、
主应用 `application.yml`、`frontend/` 任何文件、`~/.workbuddy/mcp.json`、任何 git 提交、任何数据文件内容。

### 与方案的偏差

| 原方案 | 实际做法 | 原因 |
|---|---|---|
| 「整合」通常意味着删除原子模块目录 | **退役但保留目录**（`pom.xml.retired` + `RETIRED.md` + 旧 jar） | 用户 11-06 明确「不能删除」；保留零风险回滚路径 |
| 配置文件按原名 `application.yml` 搬移 | 改名为 `application-agentmemory.yml` | 原名会被所有依赖 core 的应用自动加载，污染主应用配置；这是实施中识别出的硬约束 |
| `spring-boot-maven-plugin` 常规 repackage | 加 `<classifier>exec</classifier>` | 否则 fat jar 替换主构件，`framework-ai` / `agent-application` 编译失败 |
| 成功标准 6~8 要求真机构建与双应用启动 | 仅完成静态核验（1~5、9），命令写入 README 交用户手动执行 | 用户约定「代码修改后不代为执行 shell 命令或重启」；如实标注为待验收，未伪称已验证 |

### 真机验证结果

**已静态执行并通过**：

- 成功标准 1：启动类与 `application-agentmemory.yml` 均在 core；core resources 根下无 `application.yml`；原 application **源码树** `.java`/`.yml` 残留数 `0`（`target/classes/application.yml` 为保留的旧编译产物，不计）。
- 成功标准 2：core pom 含 classifier 与正确 mainClass；聚合 pom 只剩一个 module；`pom.xml` 已改名为 `.retired`。
- 成功标准 3：全仓 `@ComponentScan` 逐条核实，`AuthAutoConfiguration` = `com.zimo.module.auth`、`SysAutoConfiguration` = `com.zimo.module.sys`、`AiControllerScanAutoConfiguration` = `com.zimo.module.ai.controller` 等，无 `com.zimo` 宽扫描；主应用启动类在 `com.zimo.agentapplication`。core 测试无 `@SpringBootTest`。
- 成功标准 4：`bash -n core/run.sh` 退出码 `0`；脚本含 profile 参数、`-exec.jar`、`cd "$SCRIPT_DIR"`。
- 成功标准 5：4 个数据文件字节数 53248 / 7525 / 2146 / 1，与迁移前一致。
- 成功标准 9：`agent-memory-application/target/agent-memory-application-1.0.0.jar`（149 MB）保留；`RETIRED.md` 与 `pom.xml.retired` 存在。

**未执行（待用户手动验收）**：成功标准 6、7、8 —— 未打包、未启动独立应用、未启动主应用、未发 curl。
命令见模块 README 第五节与本文件第八节场景 3/4，验收后请回填本节。

### 过程中新发现的坑

- **「一个模块同时是库 + 可执行应用」不能靠直接加 repackage 实现**：根 pom 继承 `spring-boot-starter-parent`，插件默认把主构件 repackage 成 fat jar；依赖方拿到的是 `BOOT-INF/classes` 布局，编译期 `com.zimo.module.agentmemory.*` 全部不可见。必须用 `classifier=exec` 把可执行产物变成附属构件。
- **classpath 里的 `application.yml` 是全局的**：不只是「主应用自己的」配置文件会被读，任何依赖 jar 根目录的 `application.yml` 都会参与 ConfigData 加载。把独立应用配置塞进库 jar 会静默改掉主应用端口与开关，且现象归因困难。改名成 `application-{profile}.yml` + 启动显式激活是最小成本解法。
- **Windows Git Bash 的 `rmdir` 对多参数/同命令逐级删除存在目录缓存**：同一条命令里删父目录会报 `Directory not empty`，但用 `&&` 串成独立命令逐层删即可成功（清理退役目录空源码树时复现）。
- **⚠️ XML 注释里不能出现连续双连字符 `--`**：在 `agent-memory-core/pom.xml` 的说明注释中写了
  `--spring.profiles.active=agentmemory`，`xml.etree.ElementTree` 直接报
  `ParseError: not well-formed (invalid token): line 102, column 54` —— Maven 同样会拒读这个 pom，
  整个 reactor 当场构建失败。改成「显式激活 agentmemory profile」后三个 pom 全部解析通过。
  凡是往 pom 注释里贴命令行参数，都要先检查 `--`。
