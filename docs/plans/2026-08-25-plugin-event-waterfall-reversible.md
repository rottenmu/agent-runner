# P5 插件事件级瀑布 + 可逆注册回滚（对齐 dsh A1）

> 日期：2026-08-25
> 模块：`agent-spring-boot-starter`（plugin 包 + AiAgentService + AiAgentAutoConfiguration）
> 目标：补齐 dsh A1 最后差距——插件从「类加载级贡献」升级为「事件级订阅 + 可逆注册回滚」，对齐 Cordis 事件模型（`ctx.emit / ctx.on`）。

## 1. 背景

P4 前插件机制为 `AiPlugin` SPI + URLClassLoader：可插拔、有卸载移除，但**无事件订阅**（插件无法响应运行时 turn/工具事件）、**无中间件贡献**（不能参与 around 瀑布）。dsh A1 的核心是「一切皆插件 + 事件级订阅 + 注册可逆」。本次补齐三件事：

1. **PluginEventBus**：插件订阅运行时事件（turn 生命周期 / 工具流水线 / 插件装载卸载）
2. **插件中间件贡献**：插件可通过 `PluginContext.registerMiddleware` 加入 around 瀑布链
3. **可逆回滚增强**：卸载时撤销中间件注册 + 事件订阅（按插件 id 整体撤销）

## 2. 设计

### 2.1 PluginEventBus（`plugin/` 包）

| 方法 | 说明 |
| --- | --- |
| `on(pluginId, eventType, handler)` | 订阅（记录插件归属） |
| `emit(eventType, payload)` | 同步派发；任一 handler 返回 false 即短路（吞没） |
| `removeAll(pluginId)` | 撤销某插件全部订阅（卸载回滚） |
| `subscriberCount / totalSubscriptions` | 观测 |

事件类型常量：`PLUGIN_LOADED / PLUGIN_UNLOADED / AGENT_TURN_BEGIN / AGENT_TURN_END / TOOL_PRE / TOOL_POST`。

### 2.2 插件中间件贡献

- `PluginContext` 新增 `registerMiddleware(AiAgentMiddleware)` 与 `eventBus()`
- `DynamicPluginManager` 新增 `middlewares` 集合 + `addMiddleware` + `dynamicMiddlewares()` 快照
- `AiAgentAutoConfiguration.aiAgentService` 装配时合并 `pluginManager.dynamicMiddlewares()` 进中间件链

### 2.3 事件接线

- `AiAgentService` 新增可空 `PluginEventBus` 字段 + 14 参重载构造（旧 13 参委托传 null，兼容既有调用方）；`invokeHarness` 在链首派发 `AGENT_TURN_BEGIN`、链尾派发 `AGENT_TURN_END`（含 result.kind）
- `DynamicPluginManager.loadJar` 派发 `PLUGIN_LOADED`；`unload` 先 `removeAll(pluginId)` 撤销订阅再派发 `PLUGIN_UNLOADED`

### 2.4 可逆回滚

- 既有 `LoadedPlugin.registrations` 台账扩展覆盖中间件（`removeRegistrations` 增加 `AiAgentMiddleware` 分支）
- 事件订阅按 pluginId 台账撤销（`eventBus.removeAll`），与注册回滚协同

## 3. 测试

| 测试类 | 用例数 | 覆盖点 |
| --- | --- | --- |
| `PluginEventBusTest` | 6 | 派发/短路/类型过滤/卸载撤销/防御性拷贝/无订阅 |
| `DynamicPluginManagerReversibleTest` | 3 | 中间件注册+订阅/卸载回滚/中间件链生效（[wrapped] 包裹） |

回归：starter 全量 **181 通过，0 失败**（较 172 新增 9 个插件用例）；feishu 模块编译通过（AiAgentService 旧构造兼容）。

## 4. 关键坑

- **`AiAgentService` 构造扩展选重载而非改签名**：新增 14 参构造（+PluginEventBus），旧 13 参委托传 null，避免 module-feishu 等下游 6 处测试连锁改动。
- **测试台账重复注册**：onLoad 已通过 ctx 注册中间件，测试又手动 addMiddleware 同一实例导致 List 重复 → 简化测试直接验证 ctx 台账回滚。

## 5. 结论

dsh A1-A11 全部能力对标落地完毕。剩余可选增强：本机 Claude Code CLI 桥（用户已排除）、插件热重载（jar 变更自动 reload，可基于现有 loadJar/unload 扩展）。
