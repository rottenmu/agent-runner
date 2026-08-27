# 包名 com.xingju → com.zimo 统一执行记录

> 状态：已完成
> 日期：2026-08-17
> 范围：全部 Java 包名与资源引用（259 个源码文件）

## 改动内容

### 目录迁移（7 个 com/xingju 目录 → com/zimo）

| 模块 | 原包 | 新包 |
|---|---|---|
| ai-agent-spring-boot-starter（main+test） | `com.xingju.starter.ai.*` | `com.zimo.starter.ai.*` |
| module-feishu-autoconfig（main+test） | `com.xingju.module.feishu.autoconfig` | `com.zimo.module.feishu.autoconfig`（**与 feishu-core 一致，08-08 遗留的包名不一致问题一并解决**） |
| agent-application（main+test） | `com.xingju.agentapplication` | `com.zimo.agentapplication` |
| framework-autoconfig（test） | `com.xingju.framework.*` 测试包 | 合并入 `com.zimo.framework.*` |

### 引用替换（259 个文件）

- 全部 `com.xingju` → `com.zimo`：Java（package 声明/import/全限定名）、
  AutoConfiguration.imports、spring.factories、properties、yml、json、md、xml；
- 跨模块 import（module-ai/tools/rag/auth/sys/feishu-core 等引用 starter 类）自动跟随；
- AGENTS.md 目录模板 `com/xingju/` → `com/zimo/`；
- 清理历史遗留的空 `com/xingju` 目录树（无文件纯空壳）；
- `AdminAuthBoundaryTest` 硬编码路径 `com/xingju/agentapplication` → `com/zimo/agentapplication`
  （注意：斜杠路径形式需单独替换，点分替换不覆盖）。

### 保持原样

- 根 pom `<groupId>com.xingju</groupId>`（用户仅要求改包名，未要求改 groupId）；
- `AuthBoundaryContractTest` 断言旧路径 `com/xingju/...` 不存在（语义正确，不能改）；
- 历史方案文档与 tmp 临时脚本（MySQL 时代 `xingju_db`/`production_db` 为服务器实际值）；
- `.codex` 部署技能无包名引用。

## 验证

- `mvnw clean install -pl agent-application -am` 一次构建通过；
- 启动成功，登录 OK，8 接口全 200，MCP 24 工具正常；
- 全量 `mvnw verify` BUILD SUCCESS（修复 1 处测试路径后）。
