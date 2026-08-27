# 模块 admin-shell → agent-application 更名执行记录

> 状态：已完成
> 日期：2026-08-17
> 决策（用户确认）：新模块名 `agent-application`；模块名 + 包名同步改

## 改动清单

| 项 | 原 | 新 |
|---|---|---|
| 模块目录 | `admin-shell/` | `agent-application/` |
| 根 pom `<module>` | `admin-shell` | `agent-application` |
| pom `<artifactId>` | `admin-shell` | `agent-application` |
| Java 包名 | `com.zimo.admin` | `com.zimo.agentapplication` |
| 主类 | `AdminShellApplication` | `AgentApplication`（main + 测试引用同步） |
| jar 产物 | `admin-shell-1.0.0.jar` | `agent-application-1.0.0.jar` |
| `deploy/aliyun/Dockerfile.backend` | `COPY artifacts/backend/admin-shell.jar` | `agent-application.jar` |
| `.codex/skills/deploy-to-aliyun`（脚本/SKILL.md/tests） | `admin-shell` 目录与 jar 引用 | `agent-application` |
| `AGENTS.md` | `admin-shell`（4+ 处） | `agent-application` |
| `.idea`（compiler/encodings/workspace.xml） | module 名 admin-shell | agent-application |
| `AdminAuthBoundaryTest` 硬编码路径 | `admin-shell/src/main/java/com/xingju/admin` | `agent-application/src/main/java/com/xingju/agentapplication` |

## 保持原样

- `ApiRegistryOwnershipTest`：断言 `com.zimo.admin.apiregistry.*` 旧类已移除
  （语义与模块名无关，仍成立）；
- 历史方案文档（`docs/plans/2026-08-08-*`、`2026-08-15-*`）中的 `admin-shell`
  描述为当时决策记录，不改；
- `.codex/__pycache__/*.pyc` 编译缓存自动重建。

## 验证

- `mvnw clean install -pl agent-application -am` BUILD SUCCESS（产物
  `agent-application-1.0.0.jar`）；
- 启动成功，登录 OK，4 接口全 200；
- 全量 `mvnw verify` BUILD SUCCESS。
