# agent-memory 后端独立启动方案

- 日期：2026-08-29
- 状态：待审核
- 背景：agent-memory-ui（:6060）已独立运行,现需后端可脱离 agent-application 主应用单独启动

## 一、现状与目标

现状：`agent-memory-autoconfig` 被 `agent-application` 依赖,启动主应用会同时加载 sys/auth/ai/feishu/rag 等全部业务模块,且 api-registry 等框架能力依赖 MySQL。

目标：新增**独立可执行应用模块**,只装配 agent-memory 四层记忆能力（REST `/api/ai/memory`、MCP `/api/agent-memory/mcp`、分析 `/api/agent-memory/analytics`、ETL 调度）,无需 MySQL,单独 `java -jar` 即可运行,供 agent-memory-ui 直连。

## 二、模块结构

```
modules/agent-memory/
├── agent-memory-core/                 # 已有
├── agent-memory-autoconfig/           # 已有
└── agent-memory-application/          # 新增：独立可执行应用
    ├── pom.xml                        # 仅依赖 agent-memory-autoconfig + spring-boot-maven-plugin
    └── src/main/
        ├── java/com/zimo/module/agentmemory/
        │   └── AgentMemoryApplication.java   # @SpringBootApplication 启动类
        └── resources/
            └── application.yml        # 端口 9900（与 UI proxy 一致）、H2/Arrow 数据路径
```

## 三、关键设计

| 项 | 方案 |
|---|---|
| 启动类 | `AgentMemoryApplication`（唯一,位于 application 模块,非业务插件） |
| 自动装配 | 依赖 `agent-memory-autoconfig` → 其 `AutoConfiguration.imports` 自动加载四层记忆 + ETL 调度 |
| 排除项 | `spring.autoconfigure.exclude` 排除 `RocksdbStorageAutoConfiguration`（避免多余 RocksDB 初始化）;`framework.api-registry.enabled=false` 关闭 API 注册表（避免 MySQL 依赖）;`framework.storage.enabled=false` 记忆文件走纯本地文件模式（MemoryFileService 对 FileStorageService 为 optional） |
| 端口 | 9900（与 agent-memory-ui vite proxy 一致,零配置直连） |
| 数据目录 | `./data/agent-memory`（H2 文件）+ `./data/olap/l0_log.arrow`（沿用默认,沿用 agent-memory H2 豁免） |
| 启动命令 | `mvn -pl modules/agent-memory/agent-memory-application -am spring-boot:run` 或打包后 `java -jar` |

## 四、与主应用的关系

- 互不影响：主应用 `agent-application` 照常工作；独立模块只在需要单独部署时使用。
- 端口冲突注意：两者**不要同时运行**（默认都占 9900）；独立模块端口可经 `--server.port` 覆盖。

## 五、实施步骤

1. 新增 `agent-memory-application` 模块（pom + 启动类 + application.yml + 根 pom 注册）
2. `mvn -pl modules/agent-memory/agent-memory-application -am compile` 验证编译
3. `spring-boot:run` 启动,冒烟：`/api/ai/memory/policy`、`/api/agent-memory/analytics/session-stats`、MCP `tools/list`
4. 与 agent-memory-ui（:6060）联调验证 CRUD

## 六、风险与对策

| 风险 | 对策 |
|---|---|
| framework-autoconfig 的 ApiRegistry 默认 matchIfMissing=true 会激活 | 显式 `framework.api-registry.enabled=false` |
| FileStorageService 缺失时 MemoryFileService 行为 | 已设计 optional,走纯文件模式（userData 本地 MEMORY.md） |
| 根 pom 需注册新模块 | 在 `modules/agent-memory/pom.xml` 的 modules 列表追加 |

## 七、实施记录（2026-08-29 已落地并启动）

- ✅ 新增 `modules/agent-memory/agent-memory-application/`：pom（spring-boot-maven-plugin repackage）、`AgentMemoryApplication`（包 `com.zimo.agentmemory.app`，刻意避开 `com.zimo.module.agentmemory` 组件扫描冲突）、application.yml（端口 9900、关闭 api-registry/RocksDB、H2/Arrow/MEMORY.md 数据路径）
- ✅ 父 pom 注册新模块；`mvnw package -DskipTests` 构建出可执行 jar（155MB）
- ✅ **关键修复**：core 中 AiMemoryController/MemoryArchService 等带组件注解且由 autoconfig @Bean 注册，启动类必须放独立包避免重复装配
- ✅ **关键修复**：`MemoryArchRepository` 依赖 `JdbcTemplate`，主应用由 MySQL 自动配置提供，独立应用需在启动类显式声明绑定 `agentMemoryDataSource` 的 JdbcTemplate bean
- ✅ **踩坑根因**：环境变量 `SERVER__PORT=0` 被 Spring relaxed binding 映射为 `server.port=0`（随机端口），覆盖 application.yml 的 9900 → 启动脚本 `env -u SERVER__PORT` 清除
- ✅ **踩坑根因**：Arrow 内存访问需 `--add-opens=java.base/java.nio=ALL-UNNAMED`（ETL 全量重建时 MemoryUtil 初始化失败，异常被捕获不影响启动但 OLAP 数据无法落盘）
- ✅ 启动脚本 `run.sh`（unset SERVER__PORT + add-opens + Windows 路径转换）
- ✅ 端到端验证通过：`/api/ai/memory/policy` 200、`/api/agent-memory/mcp` 200（tools/list + memory_write 成功）、REST user memory 写入成功且敏感脱敏生效（138****5678、sensitiveMasked=true）、ETL 全量重建 0 行正常
- 当前服务运行于 http://localhost:9900，与 agent-memory-ui（:6060）直连
