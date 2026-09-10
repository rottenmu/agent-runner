# autoconfig 模块合并进 core — 重构记录

日期：2026-09-10
范围：11 个业务 autoconfig 模块 → 各自 core 模块
结果：Maven 模块 **41 → 30**，`./mvnw -B test` **BUILD SUCCESS**，695 个测试全绿

---

## 1. 为什么要合并

11 个 autoconfig 模块中，半数只装 1–2 个模板类（`XxxAutoConfiguration` + `XxxControllerScanAutoConfiguration`），
却各自占用一个 Maven 模块、一个 pom、一个 jar。11 个模块合计仅 97 个文件，属过度拆分。

| 模块 | Java 类数 |
|---|---|
| agent-trace-autoconfig | 1 |
| rag-autoconfig / agent-intent-autoconfig / module-datasource-autoconfig / module-tools-autoconfig / module-security-autoconfig | 各 2 |
| 其余 5 个 | 5–30 |

## 2. 执行步骤

| 阶段 | 动作 | 脚本 |
|---|---|---|
| 1 | 平移 97 个文件（含 25 个测试、module-sys 的 `spring.factories`、feishu 的 `application.yaml`）到各 core | `tmp/merge_autoconfig.py` |
| 2 | autoconfig pom 依赖去重合并进 core pom（排除自引用） | `tmp/merge_autoconfig.py pom` |
| 3 | 修正缩进、改写 3 处幽灵依赖、删除 1 处重复依赖 | `tmp/fix_pom.py` |
| 4 | 11 个聚合 pom 移除 autoconfig 模块声明；13 处外部引用改指 core；根 pom 删 9 个死条目 | `tmp/fix_refs.py` |
| 5 | 删除 11 个已清空的 autoconfig 目录 | — |

pom 备份位于 `tmp/pom-backup/`（含根 pom）。

## 3. 验证结果

| 项 | 结果 |
|---|---|
| `./mvnw -B validate` | 30 模块全部解析成功 |
| `./mvnw -B clean compile` | BUILD SUCCESS |
| `./mvnw -B test-compile` | BUILD SUCCESS |
| `./mvnw -B test` | BUILD SUCCESS，695 tests / 0 failure / 0 error |
| `AutoConfiguration.imports` 产物 | 11 个 core 全部产出，共 27 条配置类 |

## 4. 顺带修掉的历史债

命令行构建此前从未跑通（`module-intent-autoconfig` 依赖不存在的 `module-intent-core`），
因此以下问题长期隐藏，本次一并修复：

### P0 · 幽灵 artifactId
| 位置 | 声明 | 实际 |
|---|---|---|
| `agent-intent-autoconfig` | `module-intent-core` | `agent-intent-core` |
| `agent-memory-autoconfig` | `module-agent-memory-core` | `agent-memory-core` |
| `agent-harness-autoconfig`（目录名） | artifactId 实为 `module-ai-autoconfig` | `agent-application/pom.xml` 引用的正是后者 |

### 测试中的硬编码旧路径
- `AuthAutoConfigurationSourceTest`：`modules/module-auth/...` → 实际目录是 `agent-auth`（该测试从未真正通过）
- `SysStarterDependencyContractTest`、`PermissionUsageDocumentationTest`：`module-sys-autoconfig/...` → core 路径

### 断言与代码不同步
- `AuthPropertiesTest`：`excludePaths` 缺 `/api/agent-memory/mcp`（代码有、测试没有）
- `AsyncLogSyncTaskTest` / `TrajectoryRecorderTest`：`L0RawLog` 已扩为 10 参（新增 `source`），测试仍用 9 参；
  另有两个接口不存在的方法桩（`listRawLogsBySource` / `listAllPersonas`）被删除

### 上下文缺 Bean
- feishu 两个测试：`AiAgentAutoConfiguration.aiHarnessAgentFactory` 硬依赖 `AiMemoryService`，
  而 `AgentMemoryAutoConfiguration` 不在测试的显式配置列表里 → 按 starter 自身测试约定补 mock bean

### 配置缺默认值
- `agent-application/application.yml`：`archive-app-token: ${FEISHU_ARCHIVE_APP_TOKEN}` 缺 `:`，
  环境变量未设时占位符解析抛异常 → 改为 `${FEISHU_ARCHIVE_APP_TOKEN:}`

## 5. 遗留项（未处理）

1. **`aiHarnessAgentFactory` 应改为 `ObjectProvider<AiMemoryService>`** — `AiHarnessAgentFactory` 内部本就允许
   `memoryService == null`（注释：为 null 时不注册记忆读写工具），但 Bean 方法参数是硬依赖。
   这意味着**任何不含 agent-memory 的应用引用 starter 都会启动失败**。属 P2 starter 重构范围，未动。
2. P3 仓库卫生：嵌套 git（`modules/agent-memory/.git`）、`hs_err_pid17376.log`、`tmp/` 下 35M 废弃备份、
   `framework/modules/module-packaging-bom` 空壳。
3. P2 starter 更名归位（139 类 / 包名 `com.zimo.starter.ai` / 被 8 个业务模块反向依赖）与 intent 去重。
