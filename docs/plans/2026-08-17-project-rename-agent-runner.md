# 项目名 production_studio → agent_runner 统一方案

> 状态：待审核（审核通过后执行）
> 日期：2026-08-17

## 1. 目标

项目已从 production-studio 迁至 agent_runner 目录，将残留的 `production_studio` /
`production-studio` 标识统一为 `agent_runner`，避免工程标识不一致。

## 2. 引用清单（已全量盘点）

### 2.1 项目本体（必改）

| 文件 | 现状 | 改后 |
|---|---|---|
| `pom.xml`（根） | `<artifactId>production-studio</artifactId>` / `<name>production-studio</name>` | `agent-runner` |
| `admin-shell/pom.xml` `framework/pom.xml` `modules/pom.xml` | parent `<artifactId>production-studio</artifactId>` | `agent-runner` |
| `admin-shell/src/main/resources/application.yml` | `jdbc:sqlite:./data/production_studio.db` | `./data/agent_runner.db` |
| `data/production_studio.db` | 实际 SQLite 库文件 | **重命名** `data/agent_runner.db`（否则改 URL 后建空库丢数据） |
| `README.md` | 项目名 production-studio（2 处） | agent_runner |
| `AGENTS.md` | 项目名 production-studio（2 处） | agent_runner |
| `frontend/modules/ai/src/model-config/modelConfigStore.js` | localStorage key `production-studio:ai-model-config:v1` | `agent_runner:ai-model-config:v1` |

### 2.2 部署基础设施（待确认）

| 文件 | 现状 | 建议 |
|---|---|---|
| `deploy/aliyun/docker-compose.yml` | compose name / container_name / image / 卷默认路径 `/opt/app/production_studio/shared/...` / 网络名 | 本地标识（name/container/image/网络）改 agent-runner；**服务器路径 `/opt/app/production_studio` 保持**（远端目录不在仓库控制内，改后挂载会失败；已可通过 `PRODUCTION_STUDIO_LOG_DIR/DATA_DIR` 环境变量覆盖） |
| `.codex/skills/deploy-to-aliyun/` | 技能描述与脚本中的 `/opt/app/production_studio` 路径 | 建议保持（绑定远程服务器实际目录） |

### 2.3 历史记录（不改）

- `.workbuddy/memory/*.md`、`.agentscope/workspace/**`：历史日志与智能体记忆，记录当时事实，保持原样。

## 3. 执行步骤

1. 重命名 `data/production_studio.db` → `data/agent_runner.db`（先停后端）；
2. 改 `application.yml` JDBC URL；
3. 改根 pom + 3 个子 pom 的 artifactId；
4. 改 README.md / AGENTS.md / frontend key；
5. 部署配置按确认后的范围处理；
6. 验证：`mvnw clean install -pl admin-shell -am` + 启动 + 登录/数据查询（确认旧数据仍在）。

## 4. 风险与应对

| 风险 | 应对 |
|---|---|
| db 文件未同步重命名 → 启动建空库丢数据 | 文件重命名与 yml 改动捆绑，验证时确认原数据（用户/角色表）仍在 |

## 5. 执行结果（2026-08-17 已实施，范围=项目本体 + compose 标识）

- `data/production_studio.db` → `data/agent_runner.db`（已重命名，数据完整保留）；
- `application.yml` JDBC URL → `jdbc:sqlite:./data/agent_runner.db`；
- 根 pom + admin-shell/framework/modules 子 pom：`<artifactId>/<name>` → `agent-runner`；
- README.md / AGENTS.md：项目名 → `agent_runner`（各 2 处）；
- frontend localStorage key → `agent_runner:ai-model-config:v1`；
- docker-compose：compose name / container_name / image / 网络标识 → `agent-runner`；
  服务器路径 `/opt/app/production_studio/shared/...` 与 `PRODUCTION_STUDIO_RELEASE`
  变量名保持（远端目录与 deploy 技能未动，用户确认范围）；
- 历史记录（.workbuddy/memory、.agentscope）保持原样；
- 验证：`mvnw clean install` BUILD SUCCESS（新 artifactId）；启动成功；
  登录 + 用户列表 total=1（admin 在）证明数据未丢；抽查 4 接口全 200；
  全量 `mvnw verify` BUILD SUCCESS。
| artifactId 变更 → 本地仓库旧路径残留 | 重新 install 即可（本地仓库旧 artifact 无害） |
| 部署配置与服务器不一致 | 服务器路径保持 production_studio（见 2.2） |
