# 仓库代理指南

这个文件保持简短，作为进入本仓库工作前的稳定入口。

## 项目概览

- `agent_runner` 是 Java 17 / Spring Boot 3.4.5 的 Maven 多模块项目。
- 后端模块：
  - `framework/` 放共享框架代码和自动配置。
  - `modules/` 放业务插件模块，例如 `module-sys`、`module-demo`、`module-manufacturing-pm`。
  - 业务插件通常拆成 `*-core` 和 `*-autoconfig`。
  - `agent-application/` 是 Spring Boot 主入口，负责组合框架和插件模块。
- 前端模块：
  - `frontend/web-shell/` 是主壳应用。
  - `frontend/modules/` 放源码级前端插件模块。
  - 前端插件通过主壳加载菜单和路由，不再按独立 qiankun 子应用组织。

## 后端工程架构要求

用户提供的标准 Spring Boot 分层目录必须结合本仓库的 Maven 多模块插件架构使用，不能把单体项目的启动类、全局配置和 `application.yml` 复制到每个业务模块。

### 模块职责映射

- `agent-application/` 对应应用组装层：只保留唯一正式启动类 `AgentApplication.java`、应用级配置、环境配置和全局入口；业务插件不得再新增 `*Application.java`。
- `framework/framework-common/` 对应跨模块 `common`、统一返回体、分页参数、错误码、通用异常和无业务归属的工具类。
- `framework/framework-autoconfig/` 对应跨模块 `config`：MyBatis-Plus、Jackson、Web MVC、异常处理、接口文档等全局能力应在这里统一配置，避免业务模块重复声明。
- `modules/module-<domain>/module-<domain>-core/` 放领域业务代码，包括 controller、dto、entity、exception、mapper、service、vo 和领域内工具。
- `modules/module-<domain>/module-<domain>-autoconfig/` 放插件自动装配、配置属性、Mapper 扫描、数据源接入、Bean 组装和数据库版本脚本。
- `agent-application` 通过依赖各 `*-autoconfig` 组装应用；业务模块通过 `PluginRegister` 声明插件身份。

### 标准目录

```text
agent_runner/
├── agent-application/
│   └── src/main/
│       ├── java/com/zimo/admin/
│       │   ├── AgentApplication.java
│       │   └── config/                  # 仅应用级配置；已有同职责类时不得重复创建
│       └── resources/
│           ├── application.yml
│           └── application-<profile>.yml
├── framework/
│   ├── framework-common/
│   │   └── src/main/java/com/zimo/framework/common/
│   │       ├── R.java                   # 统一返回结果体
│   │       ├── PageQuery.java           # 通用分页入参
│   │       ├── ErrorCode.java           # 返回码契约
│   │       └── BizException.java        # 通用业务异常
│   └── framework-autoconfig/
│       └── src/main/java/com/zimo/framework/autoconfig/
│           └── *AutoConfiguration.java  # 全局 Jackson、MVC、MP、异常等自动配置
└── modules/module-<domain>/
    ├── module-<domain>-core/
    │   └── src/main/
    │       ├── java/com/zimo/<domain>/
    │       │   ├── constant/             # 模块常量和枚举
    │       │   ├── controller/           # REST 控制器
    │       │   ├── dto/                  # 请求入参 DTO
    │       │   ├── entity/               # MyBatis-Plus 数据库实体
    │       │   ├── exception/            # 模块业务异常
    │       │   ├── mapper/               # MyBatis-Plus Mapper 接口
    │       │   ├── service/              # 业务接口
    │       │   │   └── impl/             # 业务接口实现
    │       │   ├── vo/                   # 接口出参 VO
    │       │   └── util/                 # 仅模块内部复用的无状态工具
    │       └── resources/
    │           └── mapper/<domain>/      # Mapper XML
    └── module-<domain>-autoconfig/
        └── src/main/
            ├── java/com/zimo/<domain>/autoconfig/
            │   ├── *AutoConfiguration.java
            │   └── *Properties.java
            └── resources/
                ├── META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
                └── db/module-<domain>/V<version>__<description>.sql
```

### 分层规则

- 根包名使用仓库现有 `com.zimo...` 命名；新模块按 `com.zimo.<domain>` 组织。已有模块包名只做增量延续，不为统一目录而批量改包。
- controller 只负责协议转换、参数校验和调用 service，不直接操作 Mapper、数据库或 OSS。
- dto 只承载请求参数，vo 只承载对外响应，entity 只映射数据库表；禁止用一个类型同时承担三种职责。
- service 定义业务边界，`service.impl` 实现业务编排；简单且仓库已有明确模式的领域服务可以保持具体类，但不得把业务逻辑堆入 controller。
- mapper 使用 MyBatis-Plus；复杂 SQL 放 `src/main/resources/mapper/<domain>/`，Java 接口与 XML namespace 必须一致。
- 跨模块通用的 Result、分页、错误码、异常和工具优先复用 `framework-common`，业务模块不得重复创建 `ResultUtil`、`PageUtil` 等同职责封装。
- Cors、Jackson、MyBatis-Plus、Knife4j/OpenAPI、WebMvc 和全局异常处理属于应用或框架级配置；新增前先复用现有配置，不允许每个模块各写一套。
- 数据库结构变更使用模块化、可审计的 MySQL 版本脚本 `V<version>__<description>.sql`，禁止新增不可追踪的通用 `db/init.sql` 覆盖生产结构。
- 环境配置集中在 `agent-application/src/main/resources/application*.yml`，模块默认值可放模块命名空间资源；敏感值必须通过环境变量注入。
- 目录按实际职责创建，禁止为了匹配树形模板创建空包、空类或重复配置。
- 每个 Maven 子模块维护自己的 `pom.xml`；项目总体说明放根 `README.md`，复杂业务模块可补充模块级中文 README。
## 工作原则

- 共享能力放在 `framework/`，业务插件放在 `modules/`，应用组装放在 `agent-application/`。
- 保持插件边界清晰：插件身份由 `PluginRegister` 声明，插件启用由 `*-autoconfig` 完成。
- 不编辑 `target/`、`dist/` 等生成产物。
- 环境配置按敏感信息处理。除非用户明确要求，不要把凭据从配置文件复制到文档、日志、提交信息或聊天回复中。
- 用户已要求：本仓库以后生成或修改的 `.md` 文档默认使用中文；除非用户明确要求其他语言。
- 用户已要求：后续涉及方案、架构、实施计划或较大改动时，优先先写入 `.md` 文档；必须等待用户审核确认文档后，再按该文档进入执行阶段。
- 用户已要求：本仓库禁止使用 SQLite、H2 等本地数据库；运行、开发、验证和新增数据结构只能面向 MySQL。**唯一例外**：`module-agent-memory` 的智能体四层记忆（L0~L3）与 OLAP 离线分析，经用户 2026-08-18 明确批准使用嵌入式 H2 MVStore（OLTP）+ Arrow/Calcite（OLAP，纯 Java 无 JNI）作为进程内记忆存储，禁止其他模块或用途复用该例外。
- 后端 Java 代码注释必须遵守 `docs/rules/BACKEND_JAVA_COMMENT_RULES.md`。
- 后端与通用代码行数规范必须遵守 `docs/rules/CODE_SIZE_RULES.md`。
- Service 层、Controller 层中所有需要注入 Bean 的类，均采用普通 public 构造器注入；不要使用字段注入、setter 注入或测试专用注入构造器。
- Service 层、Controller 层 Bean 每个类只保留一个 public 构造器；测试需要控制时间、随机数等可变因素时，优先使用可覆写方法或独立 helper，不要新增第二个构造器。

## 常用命令

- 后端完整验证：`mvn clean verify`
- 后端主应用打包：`mvn -pl agent-application -am package`
- 后端本地运行：`mvn -pl agent-application -am spring-boot:run`
- 前端主壳构建：
  - `cd frontend/web-shell && npm install && npm run build`
- 前端开发服务：
  - 在对应前端目录内执行 `npm run dev`
