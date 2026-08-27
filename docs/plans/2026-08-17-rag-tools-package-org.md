# module-rag / module-tools Package 划分方案

> 状态：待审核（审核通过后执行）
> 日期：2026-08-17

## 1. 背景与目标

`module-rag` 32 个类、`module-tools` 31 个类目前大量平铺在根包，entity/mapper/
service 混杂，不利于按职责检索与边界约束。本次按仓库标准目录
（AGENTS.md 分层规则）与现有子包惯例，做**合理职责分包**：

- 只做目录结构（子包）调整，**不迁移包名**（com.zimo/com.zimo 不变）；
- 按实际职责建包，**不建空包**（配置属性类按仓库惯例留根包）；
- 保持 module-tools 已有的 `controller/govern/sdk/tool` 子包不动，只补
  entity/mapper/service 分层。

## 2. 现状盘点

### module-rag（com.zimo.module.rag，32 类平铺根包 + skill/autoconfig）

| 类别 | 类 | 数量 |
|---|---|---|
| Controller | RagController | 1 |
| Entity | RagKnowledgeBase/RagDocument/RagChunk/RagKbPermission/RagVersion/RagEvaluation/RagRetrieveLog | 7 |
| Mapper | 对应 7 个 Mapper 接口 | 7 |
| Service（具体类） | RagKbService/RagKbPermissionService/RagVersionService/RagAutoSyncService/RagEvaluationService/RagRetrieveLogService/RagRetrieveService/RagPipelineService/RagChatService | 9 |
| 处理管道组件 | DsFileParser/RagCleaner/RagChunker/RagEmbedder/RagReranker | 5 |
| Skill | skill/RagRetrieveSkill | 1 |
| 配置属性 | RagProperties | 1 |
| autoconfig | RagAutoConfiguration/RagControllerScanAutoConfiguration | 2 |

### module-tools（com.zimo.module.tools，根包 8 类 + 4 子包）

| 类别 | 类 | 位置 |
|---|---|---|
| Controller | ToolGovernanceController | controller/（已有） |
| Entity | ToolAgentPermission/ToolDefinition/ToolInvokeLog/ToolPlugin/ToolPythonScript | 根包（5） |
| Mapper | 对应 5 个 Mapper 接口 | 根包（5） |
| Service | ToolPluginService/ToolScriptService | 根包（2） |
| 治理层 | ToolGovernanceService/ToolGovernor/ToolRegistry/ToolExecutor/GenericHttpExecutor/PythonScriptExecutor | govern/（已有） |
| SDK | ToolSdk | sdk/（已有） |
| 内置工具 | CalcTool/CodeTool/EmailTool/FileTool/HttpTool/ScheduleTool/TableTool/TransformTool | tool/（已有） |
| 配置属性 | ToolsProperties | 根包 |
| autoconfig | ToolsAutoConfiguration/ToolsControllerScanAutoConfiguration | autoconfig/（已有） |

## 3. 目标结构

### module-rag

```text
com/zimo/module/rag/
├── RagProperties.java              # 配置属性（留根包，仓库惯例）
├── autoconfig/                     # 已有，不动
├── controller/
│   └── RagController.java
├── entity/                         # 7 个数据库实体
│   ├── RagKnowledgeBase.java  RagDocument.java  RagChunk.java
│   ├── RagKbPermission.java  RagVersion.java  RagEvaluation.java  RagRetrieveLog.java
├── mapper/                         # 7 个 Mapper 接口
│   ├── RagKnowledgeBaseMapper.java  RagDocumentMapper.java  RagChunkMapper.java
│   ├── RagKbPermissionMapper.java  RagVersionMapper.java
│   ├── RagEvaluationMapper.java  RagRetrieveLogMapper.java
├── service/                        # 9 个业务服务（仓库惯例保持具体类）
│   ├── RagKbService.java  RagKbPermissionService.java  RagVersionService.java
│   ├── RagAutoSyncService.java  RagEvaluationService.java  RagRetrieveLogService.java
│   ├── RagRetrieveService.java  RagPipelineService.java  RagChatService.java
├── pipeline/                       # 处理管道：解析/清洗/切片/向量化/重排
│   ├── DsFileParser.java  RagCleaner.java  RagChunker.java
│   ├── RagEmbedder.java  RagReranker.java
└── skill/
    └── RagRetrieveSkill.java       # 已有，不动
```

### module-tools

```text
com/zimo/module/tools/
├── ToolsProperties.java            # 配置属性（留根包）
├── autoconfig/                     # 已有，不动
├── controller/                     # 已有
├── entity/                         # 5 个数据库实体
│   ├── ToolAgentPermission.java  ToolDefinition.java  ToolInvokeLog.java
│   ├── ToolPlugin.java  ToolPythonScript.java
├── mapper/                         # 5 个 Mapper 接口
│   ├── ToolAgentPermissionMapper.java  ToolDefinitionMapper.java
│   ├── ToolInvokeLogMapper.java  ToolPluginMapper.java  ToolPythonScriptMapper.java
├── service/                        # 2 个业务服务
│   ├── ToolPluginService.java  ToolScriptService.java
├── govern/                         # 已有（治理层）
├── sdk/                            # 已有
└── tool/                           # 已有（8 个内置工具）
```

## 4. 配套改动

| 文件 | 改动 |
|---|---|
| `RagAutoConfiguration` | `@MapperScan("com.zimo.module.rag")` → `"com.zimo.module.rag.mapper"`；@Bean 方法引用类补 import |
| `RagControllerScanAutoConfiguration` | `basePackages` `com.zimo.module.rag` → `com.zimo.module.rag.controller`（收窄，includeFilters 语义不变） |
| `ToolsAutoConfiguration` | `@MapperScan("com.zimo.module.tools")` → `"com.zimo.module.tools.mapper"`；import 更新 |
| `ToolsControllerScanAutoConfiguration` | 已扫 controller 包，**不动** |
| module-ai `TestRunnerService.java` / `AiObservAutoConfiguration.java` | 2 处 `com.zimo.module.rag.RagRetrieveService` 引用 → `com.zimo.module.rag.service.RagRetrieveService` |
| 全部移动类 | package 声明更新 + 同模块互引补 import |

## 5. 执行方式（约 60 个类，用脚本批量搬移 + 手工修正 import）

1. 创建目标子包目录，`git mv`（无 git 则 `mv`）移动源文件；
2. 批量更新每个移动类的 `package` 声明；
3. 编译报错驱动修正 import（`mvnw clean install -pl <模块> -am` 反复到绿）；
4. 更新 4 处扫描配置 + 2 处跨模块引用；
5. 验证：admin-shell 启动 + rag/tools 接口回归 + 全量 `mvnw verify`。

## 6. 风险与应对

| 风险 | 应对 |
|---|---|
| import 遗漏（同包引用移动后需补 import） | 编译报错逐类修正（IDE 批量修复不可用，用 grep + 编译循环） |
| MapperScan 路径漏改 → Mapper 不注册 → 启动失败 | 明确列入配套改动清单 |
| 扫描包范围改错 → controller 404 | RagControllerScan 收窄到 controller 包后回归验证 |
| 跨模块引用遗漏 | 已盘点仅 module-ai 2 处 |

## 7. 验证清单

- [x] `./mvnw clean install -pl admin-shell -am` 编译通过
- [x] admin-shell 启动成功，无 Mapper/Bean 缺失
- [x] rag 接口回归：documents/knowledge-bases/tags 全部 200
- [x] tools 接口回归：governance/plugins/scripts 全部 200
- [x] 全量 `mvnw verify` BUILD SUCCESS
- [x] MCP 技能注册：24 工具（含 rag_retrieve / query_datasource）

## 8. 执行结果（2026-08-17 已实施）

### 完成内容

- **module-rag**：29 个类从根包搬入 `controller(1)/entity(7)/mapper(7)/service(9)/pipeline(5)`，
  根包仅保留 `RagProperties`（配置属性，仓库惯例）；
- **module-tools**：12 个类搬入 `entity(5)/mapper(5)/service(2)`，根包仅保留
  `ToolsProperties`；已有 `controller/govern/sdk/tool` 子包不动；
- **配套改动**：
  - `RagAutoConfiguration` `@MapperScan` → `com.zimo.module.rag.mapper`
  - `ToolsAutoConfiguration` `@MapperScan` → `com.zimo.module.tools.mapper`
  - `RagControllerScanAutoConfiguration` `basePackages` 收窄 → `com.zimo.module.rag.controller`
  - module-ai 2 处 `RagRetrieveService` 引用 → `com.zimo.module.rag.service.RagRetrieveService`
- import 修复：脚本自动补齐 26 个移动文件 + 9 个既有子包文件，手工修正 4 处
  （嵌套类 import 误补删除、RagRetrieveSkill 补 import）

### 踩坑记录

- **脚本误补嵌套类 import**：`RagChunker.Chunk`/`DsFileParser.DocumentContent`/
  `RagReranker.ScoredChunk` 是嵌套 record，被脚本当顶层类生成不存在的
  `pipeline.Chunk` 等 import → 编译报错后手工删除（实际代码用全限定名）；
- **旧 import 简单类名遮蔽**：修复脚本按"简单类名已 import"判断会漏掉旧根包
  import（`import com.zimo.module.tools.ToolPlugin` 与现有类同名）→ 需先全局
  替换旧根包 import 为子包，再补缺失 import；
- 两模块均无测试文件与 Mapper XML，跨模块引用仅 module-ai 2 处，重构面可控。
