-- ============================================================================
-- agent-harness 图运行时 · 工具治理层建表脚本（MySQL 8.0）
--
-- 版本：V1.0.0
-- 日期：2026-10-20
-- 范围：M2（工具市场 + 编排审计）新增的两张表。
--       M1/M3/M4 未新增表：图运行实例复用既有 wf_workflow_run / wf_workflow_run_log，
--       挂起现场（pendingNodeId/pendingMeta/resumeToken）与冲突标记（conflictedNodes）
--       全部落在 wf_workflow_run.context_json 里，无需改表结构。
--
-- 与 Java 侧的关系：
--   ToolSchemaInitializer 的 MYSQL_TOOL_META / MYSQL_INVOKE_LOG 是**运行期幂等自举**（每次启动都跑），
--   本脚本是**可审计的版本化迁移**（生产按序执行一次）。两份内容的字段必须一致，
--   由 HarnessMysqlDdlParityTest 逐列比对锁定。
--
-- 约定：
--   1. 字符集统一 utf8mb4 / utf8mb4_general_ci（tool_id、tenant_id 可能含中文租户名）。
--   2. 不使用外键：tool_id 是逻辑标识，跨库/跨租户部署时不保证同库引用完整性。
--   3. 不建 (tenant_id, tool_id) 唯一索引：工具逻辑删除（deleted=1），删掉后同标识再注册
--      会与残留行冲突；唯一性由 ToolRegistryServiceImpl 注册时查重保证。
--   4. 时间列用 DATETIME 而非 TIMESTAMP：TIMESTAMP 受 2038 上限与会话时区影响，
--      而审计表要求长期留存。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- ai_tool_meta：工具目录（租户级）
-- 对应实体：com.zimo.module.ai.tool.ToolMeta
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_tool_meta (
  id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  tool_id             VARCHAR(128) NOT NULL               COMMENT '工具标识（租户内唯一，逻辑删除后需查重）',
  tenant_id           VARCHAR(64)  NOT NULL DEFAULT 'u001' COMMENT '租户标识',
  name                VARCHAR(128)                        COMMENT '工具名',
  description         VARCHAR(1024)                       COMMENT '工具描述（给模型看的说明）',
  provider_type       VARCHAR(32)                         COMMENT '提供方类型：local/mcp/sandbox',
  provider_ref        VARCHAR(255)                        COMMENT '提供方引用（本地类名/MCP server 名/sandbox 镜像等）',
  version             VARCHAR(32)                         COMMENT '工具版本',
  schema_json         TEXT                                COMMENT '入参 JSON Schema',
  required_permission VARCHAR(128)                        COMMENT '调用所需权限点',
  enabled             TINYINT      DEFAULT 1              COMMENT '是否启用：1 启用 / 0 停用',
  deleted             TINYINT      DEFAULT 0              COMMENT '逻辑删除：1 已删除 / 0 正常',
  create_time         DATETIME                            COMMENT '创建时间',
  update_time         DATETIME                            COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_tool_meta_tenant (tenant_id, tool_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci
  COMMENT = '工具市场目录（agent-harness）';

-- ----------------------------------------------------------------------------
-- ai_graph_tool_invoke_log：图编排内的工具调用审计
-- 对应实体：com.zimo.module.ai.tool.GraphToolInvokeLog
--
-- ⚠️ 与 module-tools 的 tool_invoke_log 是**两张不同的表**，不要合并：
--   tool_invoke_log 记「运行时治理」视角（权限/限流/熔断），无编排上下文；
--   本表记「编排视角」，带 run_id + node_id，用于回答「这次图运行的第几个节点调了什么工具」。
--   两者的写入入口是同一个 ToolGovernanceService（ToolBridge），旁路审计各写各的。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_graph_tool_invoke_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  tool_id     VARCHAR(128) NOT NULL               COMMENT '工具标识',
  tenant_id   VARCHAR(64)                         COMMENT '租户标识',
  user_id     VARCHAR(64)                         COMMENT '调用者标识',
  run_id      BIGINT                              COMMENT '图运行实例 ID（wf_workflow_run.id）',
  node_id     VARCHAR(128)                        COMMENT '发起调用的节点 ID',
  args_json   TEXT                                COMMENT '入参 JSON（超长截断）',
  result_json TEXT                                COMMENT '回执 JSON（超长截断）',
  status      VARCHAR(32)                         COMMENT '回执状态：success/denied/failed/guarded/conflict',
  error_msg   VARCHAR(1024)                       COMMENT '失败原因',
  latency_ms  BIGINT       DEFAULT 0              COMMENT '耗时（毫秒）',
  invoke_ts   BIGINT       NOT NULL               COMMENT '调用发生时间（epoch 毫秒）',
  PRIMARY KEY (id),
  KEY idx_graph_tool_log (tool_id, invoke_ts),
  KEY idx_graph_tool_log_run (run_id, node_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci
  COMMENT = '图编排工具调用审计（agent-harness）';

-- idx_graph_tool_log_run 是脚本额外提供的：Java 侧自举同样会 ensureIndex 建它，
-- 因为「按运行实例回查某次编排的全部工具调用」是最常用的排障查询，走不到 (tool_id, invoke_ts)。
-- 建表语句里的 KEY 只对**新建库**生效，已存在的库靠文件末尾的补丁段补。

-- ----------------------------------------------------------------------------
-- 补丁段：为已存在的库补 (run_id, node_id) 索引
-- MySQL 不支持 CREATE INDEX IF NOT EXISTS，用 information_schema 判存后动态执行。
-- ----------------------------------------------------------------------------
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'ai_graph_tool_invoke_log'
    AND index_name = 'idx_graph_tool_log_run'
);
SET @ddl := IF(@idx_exists = 0,
  'CREATE INDEX idx_graph_tool_log_run ON ai_graph_tool_invoke_log(run_id, node_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
