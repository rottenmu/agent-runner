# P6 插件热重载（dsh A1 收尾：jar 变更自动 reload）

> 日期：2026-08-25
> 模块：`agent-spring-boot-starter`（DynamicPluginManager + DefaultPluginContext + AiAgentProperties + 装配）
> 目标：插件目录 jar 变更自动热重载——新增→加载、修改→卸载重载、删除→卸载，补齐 dsh A1「热插拔」最后语义。

## 1. 设计

### 1.1 指纹检测（DynamicPluginManager）

- `LoadedPlugin` 增加 `jarLastModified` / `jarSize` 字段（loadJar 时记录）
- `jarChanged(loaded, jar)`：mtime 或 size 任一变化 → 判定修改

### 1.2 扫描与动作（scanAndReload）

| 场景 | 动作 |
| --- | --- |
| jar 存在但未加载 | `loadJar`（新增加载） |
| jar 已加载且指纹变化 | `unload` + `loadJar`（替换重载） |
| 已加载但目录中已删除 | `unload`（自动卸载） |

`scanAndReload` 为 synchronized，扫描全程一致。

### 1.3 watch 线程（startWatcher / stopWatcher）

- daemon 线程按间隔轮询 `scanAndReload`，重复调用幂等
- `stopWatcher` 中断线程；`close()` 先停 watch 再卸载全部插件

### 1.4 配置（ai.agent.*）

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `plugin-watch-enabled` | false | 热重载开关（默认关，避免生产误触发 reload） |
| `plugin-watch-interval-ms` | 5000 | 轮询间隔 |

装配：`dynamicPluginManager` Bean 在启动扫描后按配置启动 watcher。

## 2. 关键修复（热重载暴露的既有 bug）

**注册台账分离**：`DefaultPluginContext` 内部 `registrations` 与 `LoadedPlugin.registrations` 是**两个独立列表**——loadJar 创建 context 时未共享台账，导致 `unload` 时 `removeRegistrations(loaded.registrations)` 为空列表，**插件贡献（能力/钩子/沙箱/补丁/中间件）永不清理**。P5 测试用 `registrationsForTest()` 绕过未暴露；热重载替换/卸载场景下能力残留（v2 重载后 capabilities size=2）。

修复：`DefaultPluginContext` 新增共享台账构造（`registrations` 与 LoadedPlugin 同一引用），loadJar 传入 `loaded.registrations`。

## 3. 测试

`DynamicPluginManagerHotReloadTest` 5 用例（端到端真实构建插件 jar：JavaCompiler 编译 + JarOutputStream 打包）：

| 用例 | 验证 |
| --- | --- |
| `newJarIsLoadedOnScan` | 新 jar → 自动加载（list + capabilities） |
| `modifiedJarIsReloadedOnScan` | 覆盖写 v2 → 替换重载（version=v2，能力无重复） |
| `removedJarIsUnloadedOnScan` | 卸载后删 jar → 状态清空 |
| `unchangedJarIsNotReloaded` | 同指纹再扫 → 不动 |
| `watcherStartStopIsIdempotent` | 启停幂等 |

回归：starter 全量 **186 通过，0 失败**（较 181 新增 5 个热重载用例）。

## 4. 关键坑

- **Windows 文件句柄**：已加载 jar 被 URLClassLoader 持有，直接删除失败 → 修改场景用覆盖写（同文件名），删除场景先 unload 释放句柄；`@AfterEach close()` 避免 tempDir 清理失败。
- **测试插件 jar 构建**：匿名内部类（`DemoPlugin$1.class`）必须随 jar 打包（全目录打包而非单 class）；`AiCapabilityProvider` 非函数式接口不能 lambda（name+contribute 两方法）；META-INF/ai-plugin.properties 必须入 jar（instantiatePlugin 依赖）。
- **surefire 编译 classpath**：`System.getProperty("java.class.path")` 在 surefire 下可用（编译测试插件源码）。

## 5. 结论

dsh A1「一切皆插件 + 热插拔」完整落地：类加载级插件 + 事件订阅（P5）+ 中间件贡献（P5）+ 可逆回滚（P5）+ **jar 热重载（P6）**。
