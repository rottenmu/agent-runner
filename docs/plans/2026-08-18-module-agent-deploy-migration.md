# agent-deploy-studio → module-agent-deploy 迁移方案

> 状态：待审核（审核通过后执行）
> 日期：2026-08-18
> 来源：`D:\codehub\agent-deploy-studio`（AgentScope Deploy Studio v1.0.0）

## 1. 现状对比

### agent-deploy-studio（33 个 Java 文件，3 模块）

| 模块 | 包 | 职责 |
|---|---|---|
| agentscope-deploy-api | `io.agentscope.deploy.api` | 配置模型（AgentDefinition/StoreConfig/CompactionConfig/FilesystemConfig/ObservabilityConfig/SkillRepositoryConfig/ToolResultEvictionConfig）+ DTO（AgentStatus/ApiResponse） |
| agentscope-deploy-core | `io.agentscope.deploy.core` | AgentFactory（HarnessAgent 装配）/AgentRegistry/AgentRuntime/HealthService/ChatService/SessionStore/StoreFactory（REDIS/MYSQL/ROCKSDB/DERBY/MEMORY 5 后端）/MetricsService/TraceStore/OtelSetup/SkillRepositoryFactory/EnvConfig/PlatformProperties |
| agentscope-deploy-server | `io.agentscope.deploy.server` | Spring Boot 启动 + 5 Controller（Agent/Chat/System/Trace）+ GlobalExceptionHandler |

依赖：`agentscope-harness 2.0.2` + extensions（redis/mysql/skill-mysql/skill-git/dashscope）。

### 当前项目 agent_runner

- `ai-agent-spring-boot-starter` 已集成 **agentscope-harness 2.0.0-RC3**，含 AiHarnessAgentFactory/Registry/Router/Memory 等——**与 deploy-studio 的 core 存在功能重叠**（HarnessAgent 装配、DistributedStore、MemoryConfig、CompactionConfig）；
- 模块模式：`module-<domain>` 拆 `*-core` + `*-autoconfig`，controller 由 `@ModuleControllerScan` 注册；
- Spring Boot 3.4.5（deploy-studio 为 3.3.5）。

### deploy-studio 差异化能力（当前项目没有的）

- **可观测性**：MetricsService + AgentMetricsMiddleware（指标）、OtelSetup + TraceMiddleware + TraceStore（OpenTelemetry 追踪）、HealthService；
- **会话服务**：ChatService + SessionStore（会话级状态管理）；
- **多存储后端**：StoreFactory 支持 REDIS/MYSQL/ROCKSDB/DERBY/MEMORY 5 种；
- **技能仓库**：SkillRepositoryFactory（mysql/git 两类仓库）；
- **Web 控制台**：5 个管理 Controller + frontend（Vue）。

## 2. 目标结构（对齐当前项目模式）

```text
modules/module-agent-deploy/
├── module-agent-deploy-core/
│   └── src/main/java/com/zimo/module/agentdeploy/
│       ├── config/      PlatformProperties / EnvConfig
│       ├── model/       AgentDefinition / StoreConfig / CompactionConfig / FilesystemConfig /
│       │                ObservabilityConfig / SkillRepositoryConfig / ToolResultEvictionConfig
│       ├── dto/         AgentStatus / ApiResponse
│       ├── factory/     AgentFactory / AgentRegistry / AgentRuntime
│       ├── store/       StoreFactory / RocksDbBaseStore / DerbyBaseStore / DerbyAgentStateStore
│       ├── chat/        ChatService / SessionStore
│       ├── metrics/     MetricsService / AgentMetricsMiddleware
│       ├── tracing/     OtelSetup / TraceMiddleware / TraceStore
│       ├── skill/       SkillRepositoryFactory
│       ├── health/      HealthService
│       └── web/         AgentController / ChatController / SystemController / TraceController /
│                        GlobalExceptionHandler（核心包，无 Spring 装配）
└── module-agent-deploy-autoconfig/
    └── src/main/java/com/zimo/module/agentdeploy/autoconfig/
        ├── AgentDeployAutoConfiguration（PlatformProperties 绑定 + Store/Metrics/Tracing/Session Bean）
        ├── AgentDeployControllerScanAutoConfiguration（@ModuleControllerScan 扫描 web/）
        └── resources/META-INF/spring/...AutoConfiguration.imports
```

- `agent-application/pom.xml` 添加 `module-agent-deploy-autoconfig` 依赖；
- **server 启动职责不迁移**（由 agent-application 承担，符合仓库架构）。

## 3. 关键决策（需确认）

### D1. 与现有 starter 的边界

| 选项 | 说明 | 取舍 |
|---|---|---|
| **A. 独立迁移（推荐）** | deploy-studio 能力整体迁入 module-agent-deploy，与 starter 并存；AgentFactory/Registry 在模块内独立（多 Agent 托管视角），starter 侧重运行时路由集成 | 改动直接、功能完整；两套 HarnessAgent 装配短期并存，后续可收敛 |
| B. 差异化合并 | 去除与 starter 重复的装配，只迁 Metrics/Tracing/Health/SessionStore/SkillRepo/Web，复用 starter 的 AgentFactory | 更优但需重写模块内部依赖（AgentRegistry 依赖 AgentFactory），改动大 |

### D2. AgentScope 版本统一（classpath 冲突硬约束）

当前 starter=**2.0.0-RC3**、deploy-studio=**2.0.2**，同一 artifact 双版本共存会冲突，**必须统一**：

| 选项 | 说明 | 风险 |
|---|---|---|
| **升级到 2.0.2（推荐）** | deploy-studio 已全量验证；RC3 为预发布版 | starter 基于 RC3 API，升级后需编译验证（若有 API 变更逐一适配） |
| 降到 RC3 | 风险反向转移给 deploy-studio 的扩展（redis/mysql 扩展可能要求 2.0.x） | 扩展兼容性未知 |

### D3. 前端 Web 控制台

| 选项 | 说明 |
|---|---|
| A. 本期不迁（推荐） | 先交付后端 module-agent-deploy（可观测/会话/多存储能力），前端控制台后续作为前端插件模块（frontend/modules/agent-deploy/）迁移 |
| B. 一并迁移 | 工作量 +1 前端插件模块 |

### D4. 密钥与环境

- deploy-studio `env.properties` 中密钥（dashscope/redis/mysql）**不复制**；改造为 `application.yml` 环境变量注入（`${DASHSCOPE_API_KEY:}`）方式；
- 存储默认走当前项目 RocksDB（`framework` 已有 RocksdbFileStorageService），deploy-studio 的 StoreFactory 作为可选后端保留（Redis/MySQL 需额外配置）。

## 4. 执行步骤

1. 创建 `module-agent-deploy` 两子模块（pom：agentscope-harness 2.0.2 + 扩展依赖 + framework-common/autoconfig）；
2. 搬移 33 个文件 → 目标包结构（`io.agentscope.deploy.*` → `com.zimo.module.agentdeploy.*`）；
3. 适配：ApiResponse 复用 framework-common？`@ModuleControllerScan` 替换 server 的扫描；PlatformProperties 前缀对齐；
4. 版本统一 2.0.2，编译循环修错（含 starter 兼容验证）；
5. `agent-application` 加依赖 + 配置（存储/模型/密钥环境变量）；
6. 验证：构建 + 启动 + 管理接口回归（agents/health/traces/metrics）+ 全量 verify。

## 5. 验证清单

- [ ] `mvnw clean install -pl agent-application -am` 通过（含 starter 2.0.2 兼容）
- [ ] 启动成功，module-agent-deploy 管理接口全 200
- [ ] 全量 `mvnw verify` BUILD SUCCESS

## 6. 执行结果（2026-08-18 已完成，用户确认：A 独立迁移 + 升级 2.0.2 + 前端一并迁移）

### 版本升级（2.0.0-RC3 → 2.0.2）

- 根 pom `agentscope.version` → 2.0.2；
- **RC3 API 变更适配**：`DashScopeChatModel`/`OpenAIChatModel` 从
  `io.agentscope.core.model` 移入 `agentscope-extensions-model-dashscope`（2.0.2
  无 openai 扩展，`OpenAIChatModel` 已移除）→ starter 增加 dashscope 扩展依赖，
  OpenAI 兼容分支统一改用 `DashScopeChatModel`（dashscope 客户端即 OpenAI 兼容
  协议，baseUrl 可指向自建 vLLM）；main + test 同步；
- starter 测试全绿。

### module-agent-deploy 模块（32 个 Java 文件）

```text
modules/module-agent-deploy/
├── module-agent-deploy-core/      # 业务（包 com.zimo.module.agentdeploy）
│   ├── model/dto/                 # AgentDefinition/StoreConfig/CompactionConfig/... + AgentStatus
│   ├── config/                    # PlatformProperties / EnvConfig（env.properties/环境变量，默认 MEMORY 存储）
│   ├── factory 语义根包           # AgentFactory / AgentRegistry / AgentRuntime / HealthService
│   ├── store/                     # StoreFactory（REDIS/MYSQL/ROCKSDB/DERBY/MEMORY）/RocksDbBaseStore/Derby*
│   ├── chat/                      # ChatService / SessionStore
│   ├── metrics/ tracing/ skill/   # MetricsService / OtelSetup / SkillRepositoryFactory
│   └── web/                       # 5 Controller + GlobalExceptionHandler
└── module-agent-deploy-autoconfig/
    ├── AgentDeployAutoConfiguration（核心 Bean + X-Admin-Token 过滤器 + 停机钩子 + OTel 初始化）
    ├── AgentDeployControllerScanAutoConfiguration（@ModuleControllerScan 扫描 web/）
    └── AutoConfiguration.imports
```

- 包名 `io.agentscope.deploy.*` → `com.zimo.module.agentdeploy.*`；
- pom：agentscope 扩展（redis/mysql/skill-mysql/skill-git/dashscope）+ jedis/HikariCP/
  mysql-connector/rocksdbjni/derby/opentelemetry/reactor，版本管理在模块父 pom；
- agent-application 添加依赖。

### 集成与鉴权

- `/api/v1/**` 加入 auth 拦截器 exclude（deploy 管理域由模块自身 X-Admin-Token
  鉴权，`agentDeployAdminAuthFilter`）；AuthPropertiesTest 同步；
- **前端控制台**：deploy-studio 的 Web 控制台为单文件 Vue CDN 页面
  （index.html + vue/echarts vendor）→ 迁移至
  `agent-application/src/main/resources/static/console/`，`/console/` 同源访问，
  API 走 `/api/v1`；
- deploy-studio `env.properties` 明文密钥未复制（走环境变量注入）。

### 验证

- `mvnw clean install -pl agent-application -am` BUILD SUCCESS；
- 启动后：`/api/v1/system/status`（UP）/`/api/v1/agents`（[]）正常；
  `/console/` 页面 200、vendor 完整下载；既有接口（ai/rag/sys/intent）全 200；
- 全量 `mvnw verify` BUILD SUCCESS（修复 AuthPropertiesTest 断言后）。

### 遗留/注意

- starter 与 module-agent-deploy 的 HarnessAgent 装配并存（用户确认 A 方案），
  后续可收敛重叠；
- module-agent-deploy 存储默认 MEMORY；Redis/MySQL 后端需配置 env.properties
  或环境变量；
- 管理接口默认不启用 token 鉴权（admin.token 为空），需生产环境显式配置。
