# 动态插件机制设计说明

> 日期：2026-08-22 ｜ 模块：agent-spring-boot-starter / agent-ai ｜ 状态：✅ 已落地并端到端验证
> 目标：为 Agent 运行时提供 **运行时类加载插件**（安装/卸载/热恢复），对标 DeepSeek Harness（Cordis）插件树语义。

## 一、背景与选型

| 方案 | 说明 | 结论 |
|---|---|---|
| A. 轻量技能热插拔 | 复用 AiSkillRegistry + 事件总线，纯配置级 | 技能体系已具备，未重复建设 |
| **B. 动态类加载插件** ✅ | `URLClassLoader` 动态加载 `data/plugins/*.jar`，实现 `AiPlugin` SPI | **本次实施** |
| C. 引入插件框架 | PF4J / Spring Plugin（依赖隔离、热部署） | 留作演进（当前单机单 ClassLoader 够用） |

## 二、架构

```text
data/plugins/*.jar（含 META-INF/ai-plugin.properties: plugin.class=xxx）
        │ URLClassLoader（子优先，SPI 接口由宿主提供）
        ▼
DynamicPluginManager ── 注册快照 ──► DynamicCapabilities / DynamicListeners / Sandbox / Patches
        │ loadJar / unload / list / close
        ▼
AiPlugin.onLoad(PluginContext)          AiPlugin.onUnload()
        │ registerCapability / registerListener / registerSandbox / registerProfilePatch
        ▼
AiHarnessAgentFactory.create(profile) ── 合并动态能力进 Toolkit / 动态钩子进链
        ▼
HarnessAgent（下次会话重建即生效，无需重启）
```

### 2.1 SPI 定义

| 接口 | 职责 |
|---|---|
| `AiPlugin` | 插件生命周期：`id()` / `version()` / `onLoad(ctx)` / `onUnload()` |
| `PluginContext` | onLoad 中注册能力：能力提供方 / 工具钩子 / 沙箱 / Profile 补丁 + `dataDir()` 专属目录 |
| `DynamicPluginManager` | 加载/卸载/列表/关闭；动态注册集合快照（卸载自动移除） |

### 2.2 装配与消费

- `AiAgentProperties.pluginDir`（默认 `data/plugins`）；autoconfig 启动时扫描目录自动装载
- `AiHarnessAgentFactory` 8 参构造（+ pluginManager 可空）：`create()` 合并动态能力进 Toolkit；`listenersChain()` 合并动态钩子
- 管理 REST `/api/ai/plugins`：`list` / `upload`（上传 jar 热加载）/ `load` / `unload`

## 三、插件开发三步

```java
// 1. 实现 AiPlugin
public class MyPlugin implements AiPlugin {
    public String id() { return "my-plugin"; }
    public String version() { return "1.0.0"; }
    public void onLoad(PluginContext ctx) {
        ctx.registerCapability(new AiCapabilityProvider() { /* 注册工具 */ });
    }
    public void onUnload() { }   // 撤销注册由管理器自动处理
}
```

```properties
# 2. 清单 META-INF/ai-plugin.properties
plugin.class=com.example.MyPlugin
```

```bash
# 3. 打包放入目录（或 REST 上传）
jar cf my-plugin-1.0.0.jar com META-INF && cp my-plugin-1.0.0.jar data/plugins/
```

## 四、关键机制与约束

| 机制 | 说明 |
|---|---|
| 类加载隔离 | 每插件独立 URLClassLoader（子优先），卸载即 close，防止类泄漏 |
| 清单发现 | `META-INF/ai-plugin.properties` 的 `plugin.class` 反射实例化（需无参构造） |
| 注册快照 | 动态集合与静态 Bean 分离，factory 装配时合并；卸载即时从快照移除 |
| 生命周期 | `@Bean(destroyMethod="close")` → 应用关闭时卸载全部插件 |
| 生效时机 | 能力/钩子对**新创建的 harness** 生效；存量 agent 会话不受影响 |

## 五、验证结果（端到端，demo-plugin-1.0.0.jar）

```
1. 启动自动装载  → 日志"插件已装载: demo-plugin v1.0.0" ✓
2. GET  /api/ai/plugins   → [{"id":"demo-plugin","version":"1.0.0","loaded":true}] ✓
3. POST /unload?key=demo-plugin-1.0.0 → {"unloaded":true}（onUnload + ClassLoader 关闭）✓
4. POST /load?jar=demo-plugin-1.0.0.jar → {"loaded":true}（热恢复）✓
```

## 六、演进方向（后续可选）

- 方案 C（PF4J）：多版本共存、依赖隔离、声明式依赖
- 插件市场：远程 jar 仓库 + 版本校验（SHA256）+ 签名
- 插件管理前端页（list/upload/unload 可视化）
- 插件权限：沙箱策略按插件授予（registerSandbox 已有挂点）
