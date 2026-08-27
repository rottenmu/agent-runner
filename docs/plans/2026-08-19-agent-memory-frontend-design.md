# module-agent-memory 前端页面设计方案

> 依据：`module-agent-memory` 后端接口（OLTP CRUD + OLAP 分析）
> 日期：2026-08-19 | 状态：待评审

## 一、后端接口盘点（前端数据来源）

### 1. 记忆管理 OLTP（`/api/ai/memory`，运行时 CRUD）
| 方法 | 路径 | 参数 | 返回 | 用途 |
|---|---|---|---|---|
| GET | `/session/{sessionId}` | tenantId, limit, offset | 会话变量列表 | 会话级变量查询 |
| POST | `/session/{sessionId}` | key, value | 记录+sensitiveMasked | 写入会话变量 |
| DELETE | `/session/{sessionId}` | key | {deleted} | 删除会话变量 |
| GET | `/user/{userId}` | tenantId, category, limit, offset | 用户记忆列表 | 画像/偏好/习惯/历史 |
| POST | `/user/{userId}` | category, content | 记录+sensitiveMasked | 写入用户记忆 |
| DELETE | `/user/{userId}` | id(可空) | {deleted} | 单删/全清用户记忆 |
| GET | `/global` | tenantId, limit, offset | 全局记忆列表 | 全局键值 |
| POST | `/global` | key, content | 记录+sensitiveMasked | 写入全局记忆 |
| DELETE | `/global` | key(可空) | {deleted} | 单删/全清全局记忆 |
| GET | `/policy` | — | whitelist/sensitiveRules | 记忆策略（只读展示） |

### 2. 记忆分析 OLAP（`/api/agent-memory/analytics`，只读报表）
| 方法 | 路径 | 参数 | 返回 | 用途 |
|---|---|---|---|---|
| GET | `/session-stats` | — | session_id/message_count/total_tokens/active_duration_ms | 会话聚合 |
| GET | `/user-activity` | userId, days(≤90) | day/message_count | 用户行为时序 |
| GET | `/distillation-stats` | — | role/event_count | L0 事件构成（蒸馏质量） |
| GET | `/trace` | traceId | 事件链 | 溯源定位 |
| GET | `/query` | sql | 结果集 | 自定义 OLAP SQL |

## 二、页面架构

### 前端插件模块（遵循仓库 frontend/modules 模式）
```text
frontend/modules/module-agent-memory/
├── index.ts                 # 插件注册（菜单/路由）
├── views/
│   ├── dashboard/index.vue  # 记忆总览（OLAP 报表）
│   ├── user/index.vue       # 用户长期记忆（L3 画像）
│   ├── session/index.vue    # 会话记忆（L1 变量 + L2 场景）
│   ├── global/index.vue     # 全局记忆
│   ├── trace/index.vue      # 溯源分析（L0）
│   └── policy/index.vue     # 记忆策略（只读）
└── api/memory.ts            # 接口封装
```

### 菜单结构
```
记忆管理
├── 记忆总览      /agent-memory/dashboard
├── 用户记忆      /agent-memory/user
├── 会话记忆      /agent-memory/session
├── 全局记忆      /agent-memory/global
├── 溯源分析      /agent-memory/trace
└── 记忆策略      /agent-memory/policy
```

## 三、页面功能设计

### 1. 记忆总览（Dashboard）——OLAP 分析主入口
- **指标卡行**：会话数（session-stats 长度）/ 消息总量（message_count 求和）/ token 消耗（total_tokens 求和）/ L0 事件量（distillation-stats event_count 求和）
- **用户行为时序图**：user-activity?days=7/30 折线（ECharts，x=day，y=message_count）
- **会话活跃排行表**：session-stats 表格（消息数/token/活跃时长，token 降序 Top 10）
- **蒸馏构成环形图**：distillation-stats role 分布（user/assistant/tool/system）
- 刷新按钮 + 最后同步时间提示（ETL 每分钟增量，数据存在延迟）

### 2. 用户记忆（L3 画像）
- 顶部：用户搜索框（userId）+ 类别 Tab 过滤（全部/persona/preference/history/custom，映射后端 category）
- 记忆卡片列表：内容 + 类别标签 + 更新时间 + 脱敏标识（sensitiveMasked=true 显示"已脱敏"角标）
- 操作：新增（弹窗选类别 + 内容）、删除（单条/清空确认）、分页（limit/offset）
- 新增写入后清空并刷新列表

### 3. 会话记忆（L1 变量）
- 会话 ID 输入 → 变量键值表格（key/value/时间）
- 新增/删除会话变量；支持分页

### 4. 全局记忆
- 键值表格 + 新增/删除/清空；同会话记忆交互

### 5. 溯源分析（L0 事件链）
- traceId 输入（可从用户/会话记忆列表复制）→ 时间线事件链（角色着色：user=蓝/assistant=绿/tool=橙/system=灰）
- 展示 content 全文 + 时间戳 + token
- 与记忆 CRUD 联动：从记忆详情"溯源"按钮跳转本页并带入 traceId

### 6. 记忆策略（只读）
- 白名单状态卡片（whitelistEnabled + 类别 chips）
- 敏感过滤开关 + 规则列表（phone/id_card/bank_card/email/api_key/bearer_token/private_ip）

## 四、数据流与状态

```text
Vue 组件 → api/memory.ts（axios） → 后端 Controller → Service → 仓储
状态：Pinia store（memoryStore：用户记忆列表/分页/筛选条件缓存）
交互约束：
- OLAP 报表接口有 ETL 延迟（≤1min），总览页展示"数据同步时间"
- DELETE /user 与 /global 的 id/key 可空 = 全清，需二次确认
- 脱敏字段 sensitiveMasked=true 时 UI 置灰标注，禁止原值回显
```

## 五、实现建议
1. 技术栈：Vue3 + Element Plus + ECharts + Pinia（仓库既有）
2. 分页统一：limit/offset 封装成公共分页组件（复用仓库 PageQuery 惯例）
3. 脱敏提示：列表展示已脱敏内容 + 角标，编辑时提示"敏感字段已按策略隐藏"
4. 原型：随附单文件 HTML 原型（模拟数据）供评审
