# 切断 MemoryScorer 对 autoconfig 的依赖，修复 core→autoconfig 架构铁律违规

> 状态：已完成（标准 1~4 真机验证通过 · 标准 5 待后端启动后补验）
> 起草日：2026-11-06
> 关联模块：`modules/agent-memory/agent-memory-core`、`agent-application`（守卫测试）
> 规范依据：`docs/rules/PLAN_DOC_RULES.md`

---

## 一、置信度与剩余风险

- 当前置信度：95%
- 主要剩余风险：`MemoryScorer` 的 public 六参构造器**不做「权重和 = 1」校验**（该校验在 `AgentMemoryProperties.Recall` 的紧凑构造器里）。删除 `of(Recall)` 后，若将来有调用方绕过 `Recall`、直接 `new MemoryScorer(...)` 并传入错权重，服务将正常启动但打分全盘失真——暴露形式是「召回质量莫名变差」而非启动报错。

---

## 二、目标

本次要达成：

- 让 `governance.com.zimo.agent.memory.MemoryScorer` 不再依赖 `com.zimo.module.agentmemory.autoconfig` 下的任何类型，消除 `PluggableArchitectureTest.coreShouldNotDependOnAutoconfig` 的 7 条违规。
- 保持打分行为、默认值、校验语义三者完全不变。

本次明确不做（延后项见第九节）：

- 不移动 `AgentMemoryProperties` 的包位置（仍按 `AGENTS.md` 留在 `autoconfig`）。
- 不修改 `PluggableArchitectureTest` 的规则本身（不给自己开例外）。
- 不改打分公式、不引入向量召回、不动 `MemoryMerger` / `MemoryEvictor` 的口径。
- 不处理 `modules/agent-memory` 整个模块未纳入 git 的问题（见第九节）。

---

## 三、成功标准

本方案完成后，以下每一条都应能在真机上直接判定为真：

1. 执行 `mvnw -pl agent-application -am test` → 打开 `agent-application/target/surefire-reports/com.zimo.agentapplication.PluggableArchitectureTest.txt`，首行统计为 `Tests run: 3, Failures: 0, Errors: 0`（当前为 `Failures: 1`）。
2. 执行 `grep -rn "agentmemory\.autoconfig" --include=*.java modules/agent-memory/agent-memory-core/src/main/java | grep -v "/autoconfig/"` → 输出**行数 = 0**（当前为 1 行：`MemoryScorer.java:3` 的 import）。
3. 执行 `mvnw -pl agent-application -am test` → 控制台出现 `BUILD SUCCESS`（当前为 `BUILD FAILURE`，失败项目 `agent-application`）。
4. 执行 `mvnw -pl agent-application -am test` 后查看 `modules/agent-memory/agent-memory-core/target/surefire-reports/governance.com.zimo.agent.memory.MemoryScorerTest.txt` → `Tests run: 14, Failures: 0, Errors: 0`（用例数与改前 14 一致，不得减少）。
5. 启动后端后执行 `curl --noproxy "*" -s http://127.0.0.1:9900/api/plugins` → `data` 数组长度 = **9**，且 `pluginId` 含 `agentmemory`（确认上一份方案的 6 个插件未因本次改动回归）。

> 前置条件（不算成功标准）：`mvnw -pl modules/agent-memory/agent-memory-core -am test` 能编译通过。

---

## 四、参照行为（对标）

无（本方案只修本仓库内部的依赖方向，不引入外部对标）。

---

## 五、现状差距

### 能力缺失

- 无。这是依赖方向写错，不是能力缺失。

### 只是没接线

- `MemoryScorer` **已有** `public MemoryScorer(double, double, double, double, int, double)` 六参构造器（`MemoryScorer.java:68`），`of(Recall)` 只做解包转发，删除它零功能损失。
- 违规面极小：全模块非 `autoconfig` 包中只有 `MemoryScorer` 一个类引用 `AgentMemoryProperties`（已 grep 确认仅 1 个文件、1 行 import）。
- 生产调用点只有 1 处：`AgentMemoryAutoConfiguration:222` 的 `MemoryScorer.of(properties.recall())`。

### 不打算解决的问题

- `modules/agent-memory` 全模块在 git 中 0 个跟踪文件（从未提交），导致这条违规长期无人发现——不在本方案处理范围。

---

## 六、技术方向

### 1. 删除 `MemoryScorer.of(Recall)`，改由 autoconfig 直接用公共构造器装配

职责边界：

- core 层（`governance`）只认识原始数值，不认识「配置对象」这一形态。
- autoconfig 层负责把 `AgentMemoryProperties.Recall` 解成原始数值再喂给 core——依赖方向变成 autoconfig → core，符合铁律。

关键接口：

```java
// core: governance.com.zimo.agent.memory.MemoryScorer（已有，不改签名）
public MemoryScorer(double matchWeight, double decayWeight, double importanceWeight,
                    double halfLifeDays, int topK, double minScore)
```

```java
// autoconfig: AgentMemoryAutoConfiguration#memoryScorer（改这一行）
AgentMemoryProperties.Recall recall = properties.recall();
MemoryScorer scorer = new MemoryScorer(
        recall.matchWeight(),
        recall.decayWeight(),
        recall.importanceWeight(),
        recall.halfLifeDays(),
        recall.topK(),
        recall.minScore());
```

取舍理由：

- **不新增 `RecallWeights` 值对象**：`MemoryScorer` 已有等价的 public 六参构造器，再加一个 record 只是把同样 6 个字段抄第二遍，徒增维护面。
- **不把 `AgentMemoryProperties` 移出 `autoconfig`**：`AGENTS.md` 明确要求 `*Properties` 放 `*-autoconfig`，且其余模块（tools/ds/rag）的 Properties 都在 autoconfig 下；为绕开一条守卫而制造全仓不一致，代价更大。
- **不改 `PluggableArchitectureTest` 开例外**：那会把「core 不得依赖 autoconfig」削成假守卫，正是仓库历史上「刻意不判 = 恒真守卫」的老毛病。

### 2. 测试侧默认值保持单一来源

`MemoryScorerTest` 目前用 `new AgentMemoryProperties.Recall(null, null, null, null)` 取默认值（依赖紧凑构造器的 null 兜底）。改为测试内 helper：

```java
private static MemoryScorer withDefaults() {
    AgentMemoryProperties.Recall recall = new AgentMemoryProperties.Recall(null, null, null, null);
    return new MemoryScorer(recall.matchWeight(), recall.decayWeight(), recall.importanceWeight(),
            recall.halfLifeDays(), recall.topK(), recall.minScore());
}
```

取舍理由：测试源集不受 ArchUnit 约束（`DO_NOT_INCLUDE_TESTS`），保留对 `Recall` 默认常量的引用可以避免「把 0.5/0.3/0.2 硬抄进测试后，配置默认值被改动而测试无感」。

### 3. 校验语义保持不变（明确声明）

| 校验项 | 校验位置 | 本次是否改动 |
| --- | --- | --- |
| 权重各项 0~1、非 null、和 = 1 | `AgentMemoryProperties.Recall` 紧凑构造器 + `validate` | **不改** |
| `halfLifeDays > 0`、`topK > 0` | `MemoryScorer` 构造器 | **不改** |

删除 `of(Recall)` 不减少任何校验——它本身不含校验逻辑。

---

## 七、里程碑

### M1. 切断依赖并回绿守卫测试

- 产出：`MemoryScorer` 去掉 autoconfig import 与 `of(Recall)`；`AgentMemoryAutoConfiguration` 改用构造器；`MemoryScorerTest` 改用 helper。
- 验证：第三节标准 1、2、3、4。

### M2. 回归插件清单并回填实施记录

- 产出：本文档第十一节。
- 验证：第三节标准 5。

---

## 八、自测计划

### 场景 1：架构守卫转绿

1. 在仓库根执行 `mvnw -pl agent-application -am test`。
2. 打开 `agent-application/target/surefire-reports/com.zimo.agentapplication.PluggableArchitectureTest.txt`。
3. 验证：
   - `Tests run: 3, Failures: 0, Errors: 0`；
   - 文件内不出现 `Architecture Violation`；
   - 不出现 `AgentMemoryProperties` 字样。

### 场景 2：依赖面归零

1. 执行 `grep -rn "agentmemory\.autoconfig" --include=*.java modules/agent-memory/agent-memory-core/src/main/java | grep -v "/autoconfig/"`。
2. 验证：输出行数 = 0（改前为 1）。

### 场景 3：打分行为未回归

1. 执行 `mvnw -pl agent-application -am test`（会带跑 agent-memory 模块测试）。
2. 打开 `modules/agent-memory/agent-memory-core/target/surefire-reports/governance.com.zimo.agent.memory.MemoryScorerTest.txt`。
3. 验证：`Tests run: 14, Failures: 0, Errors: 0`。
4. 再打开 `AgentMemoryPropertiesTest.txt`，验证权重和 ≠ 1 的用例仍为通过状态（校验未被搬走）。

### 场景 4：插件清单未回归

1. 启动后端（9900）。
2. 执行 `curl --noproxy "*" -s http://127.0.0.1:9900/api/plugins`。
3. 验证：`data` 长度 = 9，`pluginId` 含 `agentmemory`。

### 验证手段的语义缺陷声明

- 场景 1、3 依赖 surefire 报告而非控制台输出：**报告文件可能来自上一轮构建**。执行前必须确认 `target/surefire-reports` 的时间戳晚于本次构建开始时间，否则「读到旧报告 = 假绿」。
- 场景 3 的用例数「14」只能证明数量未变，**不能单独证明打分公式未变**；真正的行为判据是这 14 个用例全绿，而它们覆盖了打分分解与边界。

---

## 九、已知延后项

- **`modules/agent-memory` 未纳入 git** —— 原因：该模块从未提交（`git ls-files` 计数 0），是否入库、以及入库时是否与其他未跟踪目录（如 `agent-harness-ui`）一并处理，需要单独决策，超出本次范围。
- **`MemoryScorer` 构造器不校验权重和** —— 原因：校验本应发生在配置绑定期（`Recall`）。若将来出现绕过配置的调用方，应补构造器校验而非依赖调用方自觉；当前唯一调用点就是配置装配，故本期不动，但已记录为已知风险。
- **`framework-ai` 两个沙箱测试依赖 `python3`** —— 原因：已在环境侧用硬链接修复（见 `2026-11-06-plugin-register-alignment.md` 第十一节），代码侧未做可移植性改造。

---

## 十、实施顺序

1. 改 `MemoryScorer`：删 `of(Recall)` 与 `import ...autoconfig.AgentMemoryProperties`，同步修正类内 javadoc 中指向 `AgentMemoryProperties` 的 `{@link}`。
2. 改 `AgentMemoryAutoConfiguration#memoryScorer`：改用六参构造器。
3. 改 `MemoryScorerTest`：2 处 `MemoryScorer.of(...)` 改为 helper / 构造器。
4. 跑 `mvnw -pl agent-application -am test`，验证场景 1~3。
5. 起后端验证场景 4。
6. 回填第十一节。

---

## 十一、实施记录

### 11.1 实际改动清单（3 个文件）

| # | 文件 | 改动 |
| --- | --- | --- |
| 1 | `modules/agent-memory/agent-memory-core/src/main/java/com/zimo/module/agentmemory/governance/MemoryScorer.java` | 删除 `import autoconfig.com.zimo.agent.memory.AgentMemoryProperties;`（原第 3 行）；删除 `public static MemoryScorer of(AgentMemoryProperties.Recall)` 整个方法（原 89~103 行，含其 javadoc）。**未改**六参构造器、未改任何打分公式。 |
| 2 | `modules/agent-memory/agent-memory-core/src/main/java/com/zimo/module/agentmemory/autoconfig/AgentMemoryAutoConfiguration.java` | `memoryScorer(AgentMemoryProperties)` 内 `MemoryScorer.of(properties.recall())` → 先取 `AgentMemoryProperties.Recall recall = properties.recall();`，再用六参构造器解包；补 2 行注释说明「解包留在 autoconfig」的理由。日志行原样保留。 |
| 3 | `modules/agent-memory/agent-memory-core/src/test/java/com/zimo/module/agentmemory/governance/MemoryScorerTest.java` | 新增 `private static MemoryScorer fromRecall(AgentMemoryProperties.Recall)` helper（含 javadoc 说明为何测试侧仍引用 `Recall`）；`defaultScorer()` 与 `readsParametersFromConfiguration()` 两处由 `MemoryScorer.of(...)` 改为 `fromRecall(...)`。 |

### 11.2 与方案的偏差（3 处，均为收敛性调整）

1. **测试 DisplayName 顺带改名**：`"MemoryScorer.of 读取的 topK / minScore / halfLife 与配置一致"` → `"Recall 的 topK / minScore / halfLife 经装配后与配置一致"`。原因：`of` 已不存在，保留旧名会让后来者按名索骥找到一个不存在的方法。方案未写这条，属必要修正。
2. **javadoc 措辞避开 `MemoryScorer.of(` 字面量**：helper 注释初稿写成 `{@code MemoryScorer.of(Recall)}`，导致全仓 `grep "MemoryScorer\.of("` 仍命中 1 行（虽只是注释）。改为「从 Recall 直接造打分器的静态工厂」，使 grep 审计结果严格为 0，避免后续用 grep 判定「是否删净」时出现假阳性。
3. **未新增 `RecallWeights` 值对象**（方案已如此决定，此处记录为执行确认，非偏差）。

### 11.3 静态验证结果（已执行）

| 判据 | 结果 |
| --- | --- |
| `grep -rn "MemoryScorer\.of(" --include=*.java .` | **0 处**（改前 3 处：1 生产 + 2 测试） |
| `grep -rn "agentmemory\.autoconfig" .../src/main/java \| grep -v "/autoconfig/"` | **0 行**（改前 1 行） |
| `grep "AgentMemoryProperties" MemoryScorer.java` | **无残留**（含 javadoc 也无） |
| 生产侧 `new MemoryScorer(` 唯一调用点 | `AgentMemoryAutoConfiguration:225`，即预期的唯一装配点 |
| 三个文件花括号平衡 | 46/46、118/118、22/22，均平衡 |

### 11.4 真机验证结果（2026-11-06 14:27~14:28 执行）

用户手动执行 `mvnw -pl agent-application -am test` → **BUILD SUCCESS**。报告时间戳 `2026-11-06 14:27:59 ~ 14:28:12`，晚于改前基线，**排除「读到旧报告」的假绿可能**。

| 标准 | 报告/命令 | 改前基线 | 实测 | 结论 |
| --- | --- | --- | --- | --- |
| 1 | `PluggableArchitectureTest.txt` | `Failures: 1` | `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` | **通过** |
| 2 | `grep agentmemory\.autoconfig`（排除 autoconfig 包） | 1 行 | 0 行 | **通过** |
| 3 | 控制台 | `BUILD FAILURE` | `BUILD SUCCESS` | **通过** |
| 4 | `MemoryScorerTest.txt` | `Tests run: 14, Failures: 0` | `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` | **通过（用例数未减少）** |
| 5 | `curl /api/plugins` | 未测 | **未验证**：检查时 9900 未监听（后端未启动），`curl` 返回 `http_code=000` / exit 7 | **待验证** |

补充汇总（用于确认「没有靠跳过凑绿」）：

| 范围 | 合计 |
| --- | --- |
| `agent-application` 全部 7 个测试类 | Tests 14 / Failures 0 / Errors 0 / **Skipped 0** |
| `agent-memory-core` 全部测试类 | Tests 326 / Failures 0 / Errors 0 / **Skipped 0** |
| `framework-ai` 沙箱 5 个测试类 | Tests 27 / Failures 0 / Errors 0（`python3` 硬链接已生效，`LocalSandboxBackendTest` 6/6、`SandboxSmokeTest` 3/3 全绿） |
| 跨三个模块 `grep "<<< FAILURE\|<<< ERROR"` | 无任何失败报告 |

> **Skipped = 0 是本轮单独核对的一项**：`Skipped` 非零会让「Failures: 0」变成掩盖问题的假绿，所以逐模块汇总时把它一并打出来。

**标准 5 的执行方式（待补）**：

```bat
mvnw -pl agent-application -am spring-boot:run
curl --noproxy "*" -s http://127.0.0.1:9900/api/plugins
```

判据：`data` 数组长度 = 9，`pluginId` 集合 = `{ai, feishu, sys, workflow, datasource, rag, tools, agentmemory, agent-trace-ui}`，`order` 升序。**注意**：这 9 条只证明 Bean 注册成功，不证明前端能加载（见 `2026-11-06-plugin-register-alignment.md` 同款声明）。

### 11.5 过程中新发现的坑

- **`modules/agent-memory` 整个模块在 git 中仍是 0 个跟踪文件**：`git status` 看不到本次三处改动的 diff，`git stash` / `git diff` 均无法用于回滚或对照。若需要留痕，只能靠本文档记录 + 手动备份。这条与第九节延后项同一根因，此处再次实证。

---

<!-- 实施完成后回填。 -->
