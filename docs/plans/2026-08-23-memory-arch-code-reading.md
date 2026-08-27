# 记忆架构代码阅读分析（记忆管理菜单）

> 日期：2026-08-23 ｜ 范围：前端 MemoryArchitecture.vue + 后端 memoryarch 包

## 一、功能定位

记忆管理菜单下的「记忆架构」页面：管理**结构化画像配置**（SOUL 灵魂/人格 + USER 用户画像），
支持 CRUD + 提取标记 + 5 维统计。与 L0-L3 记忆金字塔是两套并存体系（本页是画像配置表，非金字塔展示）。

## 二、后端（memoryarch 包）

### 数据模型 `MemoryArchConfig`（record）
```
id / type(SOUL|USER) / name / summary(核心摘要) / background(背景)
content(完整内容) / source(来源：手动|自动) / version / updatedTs
```
表：`memory_arch_config`

### 分层
| 层 | 类 | 职责 |
|---|---|---|
| Controller | `MemoryArchController`（autoconfig，90 行） | `/api/agent-memory/arch`：stats / configs CRUD / configs/{id}/extract / 批量删 |
| Service | `MemoryArchService`（98 行） | 5 维统计 + CRUD + extract |
| Repository | `MemoryArchRepository`（142 行） | 建表 + countByType/countExtracted + list(type,q,page,size) |

### 5 维统计（stats）
`soulConfigCount`（SOUL 条数）/ `userProfileCount`（USER 条数）/ `todayDialogues`（今日 L0 消息）/ `totalDialogues`（总 L0 消息）/ `extracted`（已提取条数）

## 三、前端（MemoryArchitecture.vue，330 行）

- **顶部**：新建按钮 + 5 统计卡（灵魂配置/用户画像/今日对话/总对话/已提取）
- **工具栏**：类型筛选 + 关键词搜索 + 刷新
- **表格**：用户(name) / 核心摘要 / 背景 / 版本 / 来源 / 操作（编辑/提取/删除）
- **对话框**：新建/编辑表单（类型/名称/核心摘要/背景/完整内容/来源）

## 四、发现的问题（3 项）

| # | 级别 | 问题 | 说明 |
|---|---|---|---|
| 1 | **P1** | **页面未挂载路由** | `routes.js` 只有 dashboard/user/session/global/trace/policy 6 个路由，**无 arch 路由**；菜单/Tabs 均无引用——MemoryArchitecture.vue 是 **dead code，页面访问不到** |
| 2 | **P2** | **提取是假实现** | `extract()` 仅把 `source` 改为"自动"（计数+1），**未真正调用 LLM 从对话/记忆中提取画像** |
| 3 | **P3** | **版本不递增** | `update()` 沿用 `exist.version()`，未 version+1（画像配置的版本号失去意义） |

## 五、建议

1. **P1**：routes.js 加 `/agent-memory/arch` 路由（指向 MemoryArchitecture.vue）+ 菜单项（若需页面可见）
2. **P2**：extract 接入真实提取——从该用户 L1 记忆聚合生成 summary/background（可复用 L3 Persona 提取逻辑）
3. **P3**：update 时 `exist.version() + 1`
