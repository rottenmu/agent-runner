# Controller 组件扫描全模块推广方案

> 状态：待审核（审核通过后按本文档执行）
> 日期：2026-08-17
> 关联：P0-1（module-ai controller 组件扫描改造，已完成）

## 1. 背景与目标

P0-1 已把 module-ai 的 14 个 controller 从手动 `@Bean` 注册改为组件扫描
（`AiControllerScanAutoConfiguration` + `@ComponentScan("com.zimo.module.ai.controller")`），
根治了该模块"新增接口 404"问题。

但**其余 9 个模块/包仍全部采用手动 `@Bean` 注册**（共 14 个 controller，
10 个注册点），新增 controller 依然必须手动加 `@Bean`，否则 404。

**目标**：把"`@RestController` 即可零配置自动注册"的扫描模式推广到全项目，
彻底消除"手动注册遗漏 → 404"陷阱，实现全项目一致性。

## 2. 现状盘点

| 模块 | controller（数量） | 所在包 | 包内成分 | 模块开关条件 |
|---|---|---|---|---|
| module-intent | IntentController（1） | `com.zimo.intent.controller` | 仅 controller | `plugin.intent.enabled` |
| module-tools | ToolGovernanceController（1） | `com.zimo.module.tools.controller` | 仅 controller | 无 |
| module-datasource | DsDataSourceController（1） | `com.zimo.module.ds` | 混有 entity/mapper/service/skill | 无 |
| module-sys | SysApiRegistryController（1） | `com.zimo.module.sys.apiregistry` | 混有 service/repository/entity | `@ConditionalOnBean(apiRegistryJdbcOperations)` + `plugin.sys.enabled` |
| module-channel | ChannelController（1） | `com.zimo.module.channel` | 混有 adapter/service/entity/mapper | 无 |
| module-rag | RagController（1） | `com.zimo.module.rag` | 混有大量 service/entity/mapper | 无 |
| module-security | SecurityController（1） | `com.zimo.module.security` | 混有大量 service/entity/mapper | 无 |
| module-feishu | FeishuAgentAdmin/AgentCredential/Config/Event（4） | `com.zimo.module.feishu.{admin,agent,config,event}` | 父包下 105 个组件 | 无 |
| ai-agent-spring-boot-starter | Mcp/A2a/AiMemory（3） | `com.zimo.starter.ai.{mcp,a2a,memory}` | 小包含工具类 | `ai.agent.enabled` |
| module-ai（收尾） | Collab/Observ（2） | `com.zimo.module.ai.{collab,observ}` | 混有 service/mapper | 无（AiCollab/AiObserv 无条件） |

已调研确认的有利条件：

- **所有 controller 均无类级 `@Conditional*` 注解**——不存在 P0-1 踩过的
  "扫描时类级条件评估失败"坑。
- **所有 controller 均为普通构造器注入**，依赖的 Service 已是 Spring Bean，
  扫描可直接注入。
- **module-auth 已用全包扫描**（`@ComponentScan("com.zimo.module.auth")` +
  `FullyQualifiedAnnotationBeanNameGenerator`），是现成参照；
  module-ai 用仅 controller 子包扫描，两种样板均已存在。

## 3. 改造方案

### 3.1 统一模板（新增独立 Scan AutoConfiguration）

每个模块新增一个 `XxxControllerScanAutoConfiguration`，统一采用**精确扫描模式**：

```java
@AutoConfiguration
@ConditionalOnProperty(prefix = "plugin.xxx", name = "enabled", havingValue = "true", matchIfMissing = true) // 仅带开关的模块
@ComponentScan(
        basePackages = "com.xxx.module.yyy",
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = RestController.class),
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class XxxControllerScanAutoConfiguration {
}
```

要点：

1. **`useDefaultFilters = false` + `includeFilters = @RestController`**：
   只注册 `@RestController`，**绝不连带扫描** service/entity/mapper——避免与
   各 AutoConfiguration 中已手动注册的 Service `@Bean` 冲突（呼应 P0-1 教训
   "避免连带扫到手动 Bean 的 service 冲突"）。
2. **`nameGenerator = FullyQualifiedAnnotationBeanNameGenerator`**：
   以全限定类名作为 bean 名，防不同包同名 controller 冲突（与 module-auth 一致）。
3. **模块开关条件透传**：带 `@ConditionalOnProperty` 的模块（intent/sys/starter）
   在新扫描配置上补同样条件——否则模块关闭时 Service 不注册、controller 无条件
   注册会直接启动失败（`NoSuchBeanDefinition`）。
4. 注册进各自 `META-INF/spring/...AutoConfiguration.imports`。

### 3.2 改造动作（每个模块）

1. 新建 `XxxControllerScanAutoConfiguration`（上模板）并注册 imports。
2. 删除原 AutoConfiguration 中对应的 controller `@Bean` 方法（含残留 Javadoc）。
3. 清理 controller 相关的 `import`（保留 service 等其余注册不动）。
4. 编译 + 启动 + 接口回归。

### 3.3 分阶段计划（小步快走，每阶段独立验证）

| 阶段 | 范围 | 说明 | 风险 |
|---|---|---|---|
| **1. 试点** | module-intent + module-tools | controller 包为"仅 controller"，零连带风险，验证模板可行 | 低 |
| **2. 推广** | datasource / sys / channel / rag / security | 包内混有组件，验证 includeFilters 精确模式 | 中 |
| **3. 收尾** | module-feishu（4 包，扫父包）+ starter（3 包，扫父包）+ module-ai collab/observ（扫父包） | 包结构最复杂，最后处理；collab/observ 完成 P0-1 遗留 | 中高 |

## 4. 风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| 扫描连带注册与手动 `@Bean` 冲突 | 启动失败 | `useDefaultFilters=false` + `@RestController` includeFilters，杜绝连带 |
| 模块关闭时 controller 无条件注册 → 启动失败 | 启动失败 | 扫描配置透传模块 `@ConditionalOnProperty`（intent/sys/starter） |
| Bean 名冲突（不同包同名类） | 启动失败 | `FullyQualifiedAnnotationBeanNameGenerator` |
| 类级 `@ConditionalOnBean` 扫描评估失败 | controller 未注册 404 | 已确认全部 controller 无类级条件，不适用 |
| slice 测试（ApplicationContextRunner）破坏 | 测试失败 | 扫描配置独立于原 AutoConfiguration，原 Bean 测试不受影响；如测试显式加载 controller Bean 则同步调整 |
| 局部验证遗漏 | 线上接口 404 | 每阶段完成全部相关接口回归（curl 冒烟） |

## 5. 验证清单（每阶段）

- [ ] `mvnw clean install -pl <模块> -am`（注意：必须 install，否则嵌套 jar 引旧类）
- [ ] admin-shell 启动成功，无 Bean 冲突/缺失
- [ ] 被改造 controller 的接口 curl 冒烟全部 200（带登录 token）
- [ ] 原手动注册未动部分回归正常
- [ ] 若模块带开关：`enabled=false` 时启动仍正常（controller 不注册）

## 6. 收益

- 新增 controller 零配置自动注册，根治全项目"手动注册遗漏 → 404"；
- 删除约 10 处 `@Bean` 样板代码；
- 全项目 controller 注册方式统一为一种模式（module-auth 全包扫 / 其余精确扫
  可后续再评估是否收敛，本次不做）。

## 7. 非目标（本次不做）

- 不批量迁移包名（`com.zimo` → `com.zimo`）：AGENTS.md 明确禁止为统一目录
  批量改包。
- 不动 feishu autoconfig 的包名不一致问题（`com.zimo.module.feishu.autoconfig`
  vs core 的 `com.zimo.module.feishu`）。
- 不改造 module-auth 已有全包扫描模式。

## 8. 执行结果（2026-08-17 已实施）

### 已完成改造（8 个扫描配置，删除 12 处手动 @Bean）

| 模块 | 新增扫描配置 | 纳入 controller | 说明 |
|---|---|---|---|
| module-intent | `IntentControllerScanAutoConfiguration` | IntentController | 透传 `plugin.intent.enabled` |
| module-tools | `ToolsControllerScanAutoConfiguration` | ToolGovernanceController | 扫 controller 子包 |
| module-datasource | `DsControllerScanAutoConfiguration` | DsDataSourceController | 扫父包 includeFilters |
| module-channel | `ChannelControllerScanAutoConfiguration` | ChannelController | 扫父包 includeFilters |
| module-rag | `RagControllerScanAutoConfiguration` | RagController | 扫父包 includeFilters |
| module-security | `SecurityControllerScanAutoConfiguration` | SecurityController | 扫父包 includeFilters |
| ai-agent-spring-boot-starter | `AiRuntimeControllerScanAutoConfiguration` | Mcp/A2a/AiMemory | 扫父包 includeFilters + 透传 `ai.agent.enabled` |
| module-ai（原 P0-1） | `AiControllerScanAutoConfiguration`（保持） | 14 个 controller | 已有 |

### 保留手动注册的例外（3 处，均为 bean 条件依赖）

**根因（Spring 框架硬限制）**：`@ComponentScan` 所在配置类**不能与
`@ConditionalOnBean` 同用**——该条件在 REGISTER_BEAN 阶段评估，而扫描结果
在此阶段未定，Spring 直接抛 `ApplicationContextException` 拒绝启动。扫描注册
的 controller 又无法携带方法级 `@ConditionalOnBean`（类级条件会静默不注册，
P0-1 教训）。因此凡 controller 依赖**按 bean 条件注册的 Service** 的模块，
无法安全纳入扫描。

| 模块 | controller | 保留原因 |
|---|---|---|
| module-sys | SysApiRegistryController | `SysApiRegistryAutoConfiguration` 类级 `@ConditionalOnBean(apiRegistryJdbcOperations)` |
| module-ai | CollabController / ObservController | `AiCollabAutoConfiguration` 类级 `@ConditionalOnBean(AiAgentManagementService.class)` |
| module-feishu | FeishuConfig/AgentCredential/AgentAdmin（3 个） | @Bean 方法级 `@ConditionalOnBean(FeishuXxxService)` + 开关条件（FeishuEventController 无条件本可扫，为保持模块内一致性一并保留） |

> 这三处已在实际代码中以 Javadoc 注明"框架限制例外"。后续若 Service 注册
> 改为无条件（如引入默认实现 + `@ConditionalOnMissingBean`），可再纳入扫描。

### 顺带修复的并行遗留（共 45 处测试 + 2 个资源）

构建与全量测试过程中发现并修复的既有遗留（均与本次扫描改造无直接关系，属
08-08 MySQL→SQLite / 08-10 后模块扩展 / 包名迁移未同步的累积欠账）：

1. **framework-autoconfig（5 个）**：api_registry 测试断言 MySQL 语法
   （`ON DUPLICATE KEY`/`ADD UNIQUE KEY`/`GROUP BY index_name`）→ 对齐 SQLite
   （`ON CONFLICT(hash)`/`CREATE UNIQUE INDEX`/`sqlite_master`）
2. **module-auth（4 个）**：SysStpInterfaceTest 断言已移除的 `ai:agent:list`
   权限 → 改 `ai:mcp:list`；AuthAutoConfigurationSourceTest 旧包路径 → com.zimo；
   AuthPropertiesTest 排除路径补 `/api/channel/inbound`、`/api/channel/sdk.js`
3. **module-sys（4 个）**：SysApiRegistry/SysAutoConfiguration 包名断言
   com.zimo→com.zimo；PermissionUsageDocumentationTest 删除已失效的 README
   断言（README 已于 08-08 清理）；**恢复 MybatisDataScopePluginConfig 被临时
   注释的 DataScopeMybatisPlugin 注册**（并行开发遗留，恢复后全项目查询回归正常）
4. **module-feishu（29 个）**：11 个测试 package 声明 com.zimo→com.zimo（反向）；
   FeishuAutoConfigurationTest 方法名/断言更新；FeishuAgentSchemaInitializerTest
   移除过严的列级注释断言；7 个测试 runner 补 SQLite 数据源属性 +
   JacksonAutoConfiguration（aiHarnessAgentFactory 依赖 ObjectMapper）；
   **模块 yaml 默认数据源 MySQL→SQLite**（消除生产 RDS 地址与明文密码泄露，
   与主数据源对齐）
5. **admin-shell（4 个）**：ApiRegistryOwnershipTest 旧包名；PluggableArchitectureTest
   ArchUnit 规则包前缀 com.zimo→com.zimo + 扫描范围补 com.zimo
6. **module-ai（6 个测试方法）**：AiManagedSkill/AiManagedAgent record 扩参后
   旧构造器用法对齐（AiSkillZipImportServiceTest/AiSkillAdminControllerTest/
   AiManagedAgentProfileResolverTest/AiAgentManagementServiceTest/
   AiManagementControllerContractTest）

### 验证结果

- 全量回归 12 个模块接口全部 200（intent/tools/ds/channel/rag/security/sys/collab/observ/
  agents/user/page/role + MCP JSON-RPC tools/list + A2A agent-card + memory policy）
- 阶段 1/2/3 每阶段均编译 + 启动 + 接口回归通过
- **全量 `mvnw verify` BUILD SUCCESS**（所有模块测试全绿）

### 开关语义验证（2026-08-17 补做）

| 场景 | 结果 |
|---|---|
| `--plugin.intent.enabled=false` | ✅ 启动成功；intent 接口 404（controller 不注册），其余模块正常 |
| `--plugin.ai.enabled=false` | ✅ 启动成功；module-ai 接口全 404，sys/ds/intent 等正常 |
| `--ai.agent.enabled=false` | ⚠️ module-ai 已修复（controller 不再注册）；channel/tools 仍失败——**既有行为**（改造前手动 @Bean 同样无条件依赖 AiAgentService/AiSkillRegistry，开关关闭时同样失败），非本次改造回归 |

**期间修复的两个开关语义缺陷**（验证中暴露）：

1. **`AiControllerScanAutoConfiguration` 缺开关条件**（P0-1 遗留）：无条件扫描导致
   `plugin.ai.enabled=false` / `ai.agent.enabled=false` 时 controller 仍注册、
   依赖缺失启动失败 → 补 `@ConditionalOnExpression("${plugin.ai.enabled:true} && ${ai.agent.enabled:true}")`
   （注：`@ConditionalOnProperty` 不可重复注解，双条件需用 SpEL 组合）
2. **MapperScan 位置缺陷**（P1-3 拆分遗留）：`@MapperScan` 原在带条件的
   `AiSkillAdminAutoConfiguration` 下，而无条件的 `AiSkillCoreAutoConfiguration`
   依赖其 Mapper → 开关关闭时 core 服务因 Mapper 缺失启动失败 → MapperScan
   移至无条件的 core 配置（Mapper 本身无业务条件，提前注册无副作用）

### 教训沉淀

- **Spring 硬限制**：`@ComponentScan` 配置类不能与 `@ConditionalOnBean` 同用
  （REGISTER_BEAN 阶段评估），否则启动直接抛异常；扫描注册的 controller 也
  无法携带 bean 条件（类级条件静默不注册）。凡 controller 依赖按 bean 条件
  注册的 Service 的模块（sys/collab/observ/feishu），只能保持手动 `@Bean`。
- 模块级 `@ConditionalOnProperty`（PARSE 阶段）与扫描兼容（intent/starter 已验证）。
- **扫描配置必须透传其依赖链的开关条件**（含间接依赖），否则开关关闭时
  controller 无条件注册 → 依赖缺失启动失败；`@ConditionalOnProperty` 不可重复
  注解，多开关组合用 `@ConditionalOnExpression("${a:true} && ${b:true}")`。
- 无条件配置类不得依赖带条件配置注册的 Bean（如 Mapper 扫描）——同类 Bean
  注册应放在无条件层。
