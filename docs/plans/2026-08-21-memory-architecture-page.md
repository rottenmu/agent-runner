# 记忆分层架构页面实施方案

> 基于用户提供的设计图：在"智能体管理 → 记忆管理"新增第七个 Tab"记忆架构"
> 用途：管理 SOUL（AI 身份）与 USER（认知档案），千人千面 AI 系统
> 日期：2026-08-21

## 一、页面设计要点

```
┌─────────────────────────────────────────────────────────────┐
│ 记忆 分层架构              管理 SOUL（AI 身份）与 USER（认知档案）  [+ 新建配置] │
├─────────────────────────────────────────────────────────────┤
│ ┌────┐  ┌────┐  ┌────┐  ┌────┐  ┌────┐                       │
│ │117 │  │113 │  │2721│  │120k│  │113 │   ← 5 统计卡（图标+数值+标签） │
│ │SOUL│  │USER│  │今日│  │累计│  │已提│                       │
│ │配置│  │档案│  │对话│  │对话│  │取 │                       │
│ └────┘  └────┘  └────┘  └────┘  └────┘                       │
│ [搜索用户 ID 或描述...] [刷新]    [SOUL 配置 | USER 档案]      │
│ ┌──────────────────────────────────────────────────────────┐│
│ │ 用户 │ 核心摘要 │ 背景 │ 版本 │ 来源 │ 操作                ││
│ │ ...  │ ...      │ ...  │ v1   │ 自动 │ 编辑/提取/删除       ││
│ └──────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

## 二、接口设计（8 个 REST，agent-memory 域 `/api/agent-memory/arch`）

| # | 方法 | 路径 | 说明 |
|---|---|---|---|
| 1 | GET | `/stats` | 5 个统计（soulConfigCount / userProfileCount / todayDialogues / totalDialogues / extracted） |
| 2 | GET | `/configs?type=&q=&page=&size=` | 分页查询（type=SOUL/USER，q 关键词） |
| 3 | GET | `/configs/{id}` | 详情（编辑回显） |
| 4 | POST | `/configs` | 新建（type/name/background/content/source） |
| 5 | PUT | `/configs/{id}` | 更新（核心摘要/背景/source） |
| 6 | DELETE | `/configs/{id}` | 删除 |
| 7 | POST | `/configs/{id}/extract` | 触发提取（已提取计数 +1；本次简化为直接 +1 并改 source=自动） |
| 8 | GET | `/dialogues/today?userId=&type=` | 今日对话数（基于 L0/OLAP）—— 实际并入 stats |

## 三、数据模型（H2 新增 1 表，模块内）

```sql
CREATE TABLE memory_arch_config (
  id VARCHAR(32) PRIMARY KEY,           -- 16 hex
  type VARCHAR(16) NOT NULL,            -- SOUL / USER
  name VARCHAR(128) NOT NULL,           -- USER 档案=用户标识；SOUL 配置=角色名
  summary CLOB,                         -- 核心摘要（表格列）
  background CLOB,                     -- 背景
  content CLOB,                         -- 完整描述
  source VARCHAR(8) NOT NULL,           -- 自动 / 手动
  version INT DEFAULT 1,
  updated_ts BIGINT NOT NULL
)
CREATE INDEX idx_arch_type ON memory_arch_config(type, updated_ts DESC)
```

## 四、统计实现

- `soulConfigCount / userProfileCount` → `SELECT COUNT(*) FROM memory_arch_config WHERE type=?`
- `todayDialogues` → L0 消息数 `> 今天 0 点`（`SELECT COUNT(*) FROM l0_raw_log WHERE ts >= ?`）
- `totalDialogues` → L0 总数（同理全表）
- `extracted` → `COUNT(type='USER' AND source='自动')` 或独立字段

> 注：实际生产可从 OLAP 宽表（Arrow）取，H2 实时聚合足够 demo。

## 五、前端页面（agentmemory 插件 + 7th Tab）

`MemoryArchitecture.vue`：
- 5 张 `el-card`（彩色边框/图标/数值/标签，紫色调主色）
- 搜索 `el-input` + 刷新 `el-button`
- 切换 `el-radio-group`（SOUL 配置 / USER 档案）
- 表格 `el-table`（用户/核心摘要/背景/版本/来源/操作），`el-tooltip` + `show-overflow-tooltip` 展示截断
- 来源用 `el-tag`（自动 绿色 / 手动 橙色）
- 操作：编辑 `el-button`（link type=primary）+ 提取（purple）+ 删除（danger）
- 新建配置：右上角按钮 → `el-dialog` 表单
- 路由：记忆管理菜单第 7 个 Tab "记忆架构"（`/agent-memory?tab=arch`）

## 六、分步实施清单

- [ ] **后端**
  - [ ] 1. `MemoryArchConfig` 实体 + H2 建表（auto-init 块追加）
  - [ ] 2. `MemoryArchRepository`（JdbcTemplate）
  - [ ] 3. `MemoryArchService`（CRUD + stats）
  - [ ] 4. `MemoryArchController`（8 个 REST）
  - [ ] 5. autoconfig 装配 + 路由权限
  - [ ] 6. 模块编译 + 单测
- [ ] **前端**
  - [ ] 7. `api/agentMemory.js` 加 8 个 API
  - [ ] 8. `MemoryArchitecture.vue` 完整页面
  - [ ] 9. 记忆管理菜单加 7th Tab（`ai/menus.js`）
  - [ ] 10. 前端 build
- [ ] **端到端**
  - [ ] 11. 重启后端 → 初始化种子数据（1 条 SOUL + 几条 USER 模拟图片）
  - [ ] 12. 访问 `/agent-memory?tab=arch` 验证 5 卡片/搜索/切换/CRUD
  - [ ] 13. 写 docs/plans/2026-08-21-memory-architecture-page.md

## 七、边界与回滚

- 统计基于 H2 实时聚合（轻量），如需 OLAP 加速可后续从 Arrow 宽表读
- 数据为新表独立于 L1/L3 person，不影响既有记忆
- 页面独立 Tab 出错不影响其他 6 Tab
