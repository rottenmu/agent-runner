-- ============================================================================
-- agent-harness 可观测层 · 会话事件日志建表脚本（MySQL 8.0）
--
-- 版本：V1.0.1
-- 日期：2026-10-20
-- 对应服务：com.zimo.module.ai.observ.SessionEventLogServiceImpl（append-only 事件流）
--
-- 为什么需要这个脚本：
--   该表的 Java 自举语句长期是 SQLite 专用写法——`INTEGER PRIMARY KEY AUTOINCREMENT`
--   与 `CLOB` 在 MySQL 上分别是**语法错**和**未知类型**，而建表在 @PostConstruct 里执行，
--   于是切 MySQL 会直接把应用启动打断。Java 侧已补方言分支，本脚本是同一结构的可审计版本。
--
-- 表语义：一次会话调用（trace）产生一条 BEGIN + N 条 STEP + 一条 END，
--   seq 从 0 单调递增、事件只追加不修改，回放与 Token 计量都派生自该流。
-- ============================================================================

CREATE TABLE IF NOT EXISTS observ_session_event (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（追加顺序）',
  trace_id    VARCHAR(64)  NOT NULL               COMMENT '链路 ID',
  session_id  VARCHAR(128)                        COMMENT '会话 ID',
  agent_name  VARCHAR(128)                        COMMENT '智能体名',
  seq         INT          NOT NULL DEFAULT 0     COMMENT '链路内序号（0 起，单调递增）',
  event_type  VARCHAR(16)  NOT NULL               COMMENT '事件类型：BEGIN/STEP/END',
  step_type   VARCHAR(32)                         COMMENT '步骤类型：意图路由/工具调用等',
  name        VARCHAR(255)                        COMMENT '事件名',
  input_text  TEXT                                COMMENT '输入摘要（超 4000 字截断）',
  output_text TEXT                                COMMENT '输出摘要（超 4000 字截断）',
  status      VARCHAR(32)                         COMMENT '状态：success/failed 等',
  tokens      INT          DEFAULT 0              COMMENT 'Token 计量',
  latency_ms  BIGINT       DEFAULT 0              COMMENT '耗时（毫秒）',
  event_ts    BIGINT       NOT NULL               COMMENT '事件时间（epoch 毫秒）',
  PRIMARY KEY (id),
  KEY idx_obs_evt_trace (trace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci
  COMMENT = '会话事件日志（append-only，agent-harness 可观测）';
