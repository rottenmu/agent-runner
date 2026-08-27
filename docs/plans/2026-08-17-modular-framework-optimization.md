# 模块化框架冗余分析与优化建议

> 状态：建议稿（待评审，未执行）
> 日期：2026-08-17
> 范围：工程架构层（模块划分 / 装配模式 / 重复实现 / 文档规范）

## 1. 架构现状快照

- **3 层结构**：`framework/`（common 零依赖契约 + autoconfig 装配）
  `modules/`（10 个业务模块：ai/auth/channel/datasource/feishu/intent/rag/security/sys/tools
  + ai-agent-spring-boot-starter）`agent-application/`（启动壳，依赖 10 个模块）；
- 每个业务模块 = `*-core`（业务）+ `*-autoconfig`（装配），共 **31 个 AutoConfiguration**；
- Controller 注册：**8 个组件扫描** + 4 处手动 @Bean（AiCollab/AiObserv/Feishu/SysApiRegistry，
  均为 @ConditionalOnBean 框架限制例外）。

## 2. 冗余点与建议（按优先级）

### P0-1. ControllerScanAutoConfiguration 样板类重复（8 个）

每个模块一个近乎相同的类：

```java
@AutoConfiguration
@ConditionalOnProperty(prefix = "plugin.xxx", ...)      // 部分模块
@ComponentScan(basePackages = "com.zimo.module.xxx",
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = ANNOTATION, classes = RestController.class),
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
```

**建议**：framework-autoconfig 提供 `@ModuleControllerScan(basePackage = "...")`
元注解 + `ImportBeanDefinitionRegistrar`（基于 ClassPathScanningCandidateComponentProvider，
内部固定 useDefaultFilters=false/includeFilters=RestController/FQCN 生成器，支持
`@ConditionalOnProperty` 组合）。各模块 8 个重复类收敛为一行注解，消除约 150 行样板。

> 注：`@ComponentScan` 无法参数化包名，必须用 Registrar 动态注册，
> 且不能与 `@ConditionalOnBean` 同用（REGISTER_BEAN 硬限制，沿用现状约束）。

### P0-2. module-ai 装配碎片化（8 个 AutoConfiguration）

| 类 | 现状 | 建议 |
|---|---|---|
| AiSkillMultipartWebAutoConfiguration | 仅 1 个 multipart resolver | **并入 AiSkillCoreAutoConfiguration**（无独立条件，纯属过度拆分） |
| AiModuleAutoConfiguration / AiSkillAdmin / AiSkillCore | 条件粒度合理（P1-3 设计） | 保留 |
| AiCollab / AiObserv / AiWorkflow / AiModelConfig | 功能域拆分合理 | 保留 |

### P0-3. 文档与规范残留

- `modules/module-feishu/` 下 2 个散落 MD（FEISHU_AGENT_DEBUG_GUIDE.md、
  FEISHU_JAVA_AGENTSCOPE_INTEGRATION.md）→ 移到 `docs/`；
- AGENTS.md 仍引用**已删除**的 `agents/`（Python AgentScope sidecar）与
  目录树中残留描述 → 更新为现状。

### P1-1. intent 双体系命名混淆（两个 IntentAutoConfiguration）

- `module-intent`（`com.zimo.intent`）：IntentRecognitionService **意图识别路由**
  （`plugin.intent.enabled`）；
- `ai-agent-spring-boot-starter`（`com.zimo.starter.ai.intent`）：IntentAwareSkillRouter
  **技能意图准入**（`ai.intent.enabled`）——职责不同（一个识别用户意图、一个
  在技能执行前做意图校验），但类名与包名重叠，认知成本高。

**建议**：starter 侧组件重命名为 `SkillIntent*`（或 `intent-gate` 语义）并
在 Javadoc 明确边界；或在文档中固化两体系分工说明（低成本方案）。

### P1-2. core 分层治理延续（第二批）

已完成 rag/tools/ds/channel；**security/intent/sys/ai 仍平铺**（entity/mapper/service
混根包或混 management 包）。建议沿用既有脚本流程分批治理，与
P1-1 的 intent 包名统一（`com.zimo.intent` → `com.zimo.module.intent`，与其他
模块对齐）一并处理。

### P2-1. MapperScan 与 ControllerScan 合并为模块级元注解

若 P0-1 落地，可进一步提供 `@ModuleAutoConfiguration(basePackage, property)`
组合注解（@MapperScan + @ComponentScan + @ConditionalOnProperty），每个模块
装配配置收敛为 1-2 行。**收益边际较小**（MapperScan 每模块仅一次），可延后。

## 3. 不建议动的部分（现状合理）

- framework-common / framework-autoconfig 拆分（零依赖契约层合理）；
- 各模块 Properties 类（每模块一个，职责单一）；
- AutoConfiguration.imports 手动维护（Boot 3 官方推荐，生成器收益低）；
- 4 处手动 @Bean controller 例外（框架硬限制，无法扫描化，已 Javadoc 注明）。

## 4. 预期收益

- 消除 8 个扫描样板类（~150 行）+ 1 个碎片配置类；
- 消除两套 intent 的认知混淆；文档与目录规范对齐；
- 分层治理扩展至全部 core 模块（与既有 4 模块模式统一）。

## 5. 执行方式（待选）

1. 只做 P0（框架化注解 + multipart 合并 + 文档清理）——小步、低风险；
2. P0 + P1（含 intent 收敛 + 分层第二批）——中等改动，分两次提交；
3. 全部。

## 6. 执行结果（2026-08-17 已实施，范围 = P0 + P1）

### P0-1 完成：@ModuleControllerScan 框架化
- framework-autoconfig 新增 `ModuleControllerScan` 元注解 + `ModuleControllerScanRegistrar`
  （ClassPathScanningCandidateComponentProvider，固定 useDefaultFilters=false /
  includeFilters=@RestController / 全限定类名 bean 名）；
- **8 个模块扫描类收敛**：Intent/Channel/Ds/Rag/Security/Tools/Ai/AiRuntime 的
  @ComponentScan 块（各 ~6 行）替换为一行注解；各 autoconfig 模块补充
  framework-autoconfig 依赖（7 个 pom）；
- 条件保留：Intent（plugin.intent.enabled）、Ai（双开关 @ConditionalOnExpression）、
  AiRuntime（ai.agent.enabled）。

### P0-2 修正：multipart 配置保留
- 复核 `AiSkillMultipartWebAutoConfiguration` 具有独立 web 条件
  （@ConditionalOnWebApplication + before MultipartAutoConfiguration 排序），
  拆分合理，**不合并**（原建议误判）。

### P0-3 完成：文档清理
- `modules/module-feishu/` 2 个散落 MD → `docs/feishu/`；
- AGENTS.md：删除已移除的 agents/ Python sidecar 描述、AdminShellApplication →
  AgentApplication。

### P1-1 完成：intent 双体系边界文档化
- 两个 `IntentAutoConfiguration` 的 Javadoc 均补充体系边界说明（module-intent
  识别路由 vs starter 技能准入，开关独立、勿合并）；不改代码（避免动敏感链路）。

### P1-2 完成：分层第二批
- **module-security**：24 个平铺类 → controller(1)/entity(8)/mapper(8)/service(7)，
  根包清空；@MapperScan 收窄到 .mapper；@ModuleControllerScan 收窄到 .controller；
- **module-intent**：13 个平铺类 → model(3)/service(5)/parser(4)，根包仅留
  IntentProperties（配置属性惯例）；无 Mapper，不套 entity/mapper 模板；
- module-sys 已分层（跳过）；module-ai management 包（entity/service 混合）
  留待后续批次。

### 验证
- 全链路 `clean install` BUILD SUCCESS；启动 10/10 接口全 200；MCP 24 工具；
  全量 `mvnw verify` BUILD SUCCESS。

### 遗留（后续批次）
- module-ai `management` 包 entity/service 拆分（30+ 类核心链路，风险高，单独评估）；
- 若后续新增模块：直接使用 `@ModuleControllerScan`，不再手写扫描样板。
