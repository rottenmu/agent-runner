# 工程架构精简 · P2（starter 更名归位）+ P3（仓库卫生）

日期：2026-09-10
前提：P1 已把 11 个 autoconfig 合并进 core，模块数 41→30，全量测试 695/0/0 通过。

## P2 · agent-spring-boot-starter → framework-ai（去名不副实）

### 动机
`agent-spring-boot-starter` 有 139 个类、包名 `com.zimo.starter.ai`，被 8 个业务模块反向依赖——
它是事实上的第二个 framework，不是"启动器"。且内部 `intent`(16 类)、`skill`(15)、`sandbox`(15)
与独立模块 `agent-intent-core`(16)、`module-tools-core`(29) 职责重叠。

### 动作
1. 目录移动：`modules/agent-spring-boot-starter` → `framework/framework-ai`（归入现有 `com.zimo.framework` 体系）。
2. 包名替换：全仓 `com.zimo.starter.ai` → `com.zimo.framework.ai`（脚本 `tmp/rename_pkg.py`，
   193 文件 / 631 处替换；排除 `docs/`、`.workbuddy/` 不篡改历史文档）。
3. parent 修正：framework-ai 的 parent `modules` → `framework`；framework 聚合 pom 新增 `<module>framework-ai</module>`。
4. artifactId：`agent-spring-boot-starter` → `framework-ai`，同步 8 个下游 core pom、根 pom `dependencyManagement`、`modules/pom.xml` 移除旧模块声明。
5. 配置属性前缀 `ai.agent.*` 保持不变（非 `com.zimo.starter` 字符串，不受影响）。

### 验证
- `./mvnw -B clean test` → **BUILD SUCCESS**，全量 0 failure / 0 error（framework-ai 作为第 8/30 模块构建）。
- 11 个 core 的 `AutoConfiguration.imports` 自动装配元数据随包名更新，运行时可被发现。

## P3 · 仓库卫生清理

| 项 | 处理 | 结果 |
|---|---|---|
| `tmp/` | 删除（`.gitignore` 已忽略的纯临时区，含 35M+ 废弃备份与日志） | 释放约 30M+ |
| 重构脚本 | 保留至 `docs/refactor/scripts/`（merge_autoconfig/fix_pom/fix_refs/rename_pkg/fix_p2_poms/drop_packaging_bom）+ `docs/refactor/pom-backup-p1/` | 可复现 |
| `framework/modules/module-packaging-bom` | 删除（死模块，仅含 target 残留，不在任何 pom 的 modules 列表） | 模块数不变 |
| 嵌套 git `modules/agent-memory/.git` | `.gitignore` 追加 `modules/agent-memory/` + `git rm -r --cached` 从父索引解除跟踪；保留其独立 `.git`（远程 `rottenmu/agent-memory`） | 消除双重版本管理 |

> 注：未处理"运行时占位空目录"（`data/plugins`、`db/mysql`、`db/local` 等）——它们是应用运行时需要的目录，非垃圾。
> 未处理 `frontend/` 逻辑分裂（根 `.gitignore` 第 5 行排除 `frontend/` 但目录仍被提交）——属原 P3 清单外，可单独立项。

## 最终状态
- Maven 模块数：**30**（P1 前 41）
- 构建：命令行 `clean test` 可复现、全绿
- 架构：`framework`（common/autoconfig/ai）为唯一框架层；业务模块反向依赖 framework 而非 starter
- 仓库：无嵌套 git 冲突、无废弃临时目录

## 遗留 → 已修复（同日）

### P2 暴露的真实缺陷：`AiMemoryService` 硬依赖

**问题**：`AiAgentAutoConfiguration.aiHarnessAgentFactory(...)` 把 `AiMemoryService` 声明为方法参数硬依赖，
但 `AiHarnessAgentFactory` 内部本就允许它为 `null`（`if (memoryService != null)` 才注册记忆读写工具）。
后果：装配侧要求 Bean 必须存在，而该 Bean 由 `module-agent-memory` 提供 —— 任何未引入 agent-memory
模块的应用，引用 framework-ai 后会在上下文启动阶段直接失败。

**修复**：
1. 参数改为 `ObjectProvider<AiMemoryService> memoryServiceProvider`，方法体内
   `memoryServiceProvider.getIfAvailable()` 取值（与同方法内 `storageServiceProvider` 写法一致）。
2. 关键验证手段：**移除 6 处为绕过硬依赖而添加的测试 mock**，若测试仍全绿即证明修复生效：
   - `module-feishu-core`：`FeishuAiChannelAutoConfigurationTest`、`FeishuAiSkillAutoConfigurationTest`（`withBean` mock）
   - `framework-ai`：`AiAgentAutoConfigurationTest`、`IntentAutoConfigurationTest`（`withBean` mock）
   - `framework-ai`：`A2aControllerTest`、`McpControllerTest`（`@MockBean AiMemoryService`；两控制器主代码并不依赖记忆服务）
   注：后两者是 `@WebMvcTest + ImportAutoConfiguration(AiAgentAutoConfiguration)`，同样会被该 Bean 创建失败波及。
3. 新增回归护栏 `AiAgentAutoConfigurationTest#createsHarnessAgentFactoryWithoutMemoryService`：
   断言"无 `AiMemoryService` Bean 时上下文 `hasNotFailed`、`doesNotHaveBean(AiMemoryService)`、工厂 Bean 仍存在"。

**结果**：`./mvnw -B clean test` → **BUILD SUCCESS，696 tests / 0 failure / 0 error**（较修复前 +1 条新回归测试）。

### 未做的更深解耦（已知限制）

`framework-ai` 对 `agent-memory-core` 仍是**非 optional** 的编译期依赖，且 `AiHarnessAgentFactory`
持有 `private final AiMemoryService` 字段。因此"类路径上完全没有 agent-memory-core"的场景
（例如下游用 `<exclusions>` 排除）仍会因类加载失败。彻底解耦需：
① `agent-memory-core` 标 `optional`；② 记忆相关 Bean 加 `@ConditionalOnClass`；
③ 在 `framework-common` 定义记忆读写端口接口，由 agent-memory 提供适配实现，使工厂不再编译期引用 `AiMemoryService`。
属架构级改造，未纳入本轮。
