# agent-harness 动态插件加载：运行时 jar 装载与插件贡献真正生效

> 状态：待评审
> 起草日：2026-09-15
> 关联模块：`framework/framework-ai`（`plugin` 包、`AiAgentService`、`AiHarnessAgentFactory`、`AiHarnessAgentRouter`、`AiHarnessAgentRegistry`、`AiAgentAutoConfiguration`）、`modules/agent-harness/agent-harness-core`（`AiPluginAdminController`）、`plugin-samples/demo-hello-plugin`（新增）、`frontend/agent-harness-ui`（M4，可选）
> 规范依据：`docs/rules/PLAN_DOC_RULES.md`

---

## 一、置信度与剩余风险

- 当前置信度：87%
- 主要剩余风险：`AiAgentService`（有效 469 / 500）与 `AiHarnessAgentRouter` 的「中间件 / 补丁快照化」需要在**几乎为零的行数余量**下完成，一旦溢出就必须抽新类并牵动 autoconfig 装配；它会以「M2 改完 `mvn clean verify` 报行数超限，或装配出现循环依赖」的形式暴露。

---

## 二、目标

本次要达成：

- 把已存在但**未真正接通**的动态插件链路接通：运行时装载 / 卸载插件 jar 后，插件贡献的能力（工具）、工具钩子、中间件、Profile 补丁在**不重启进程**的前提下真正参与运行。
- 装载结果可诊断：装载失败时接口能返回「为什么失败」（清单缺失 / 未实现 SPI / 依赖缺失 / 不在允许清单），而不是只返回 `loaded:false`。
- 插件可自带第三方依赖（`plugins/<jar名>/lib/*.jar`），不必把依赖 shade 进插件 jar。
- 提供一个**可构建、可投放、可验证**的样例插件模块，作为「怎么写插件」的活文档，同时作为端到端验证的载体。
- 卸载与热重载后，插件贡献**可逆撤销**，不会残留、不会重复累积。

本次明确不做（延后项见第九节）：

- 不做 parent-last 类隔离 / 插件版本依赖解析（与 SPI 类身份要求冲突，见 6.3 取舍）。
- 不做插件签名校验与权限沙箱。
- 不做「插件贡献 Spring Bean / REST Controller」（本期插件只贡献 AI 运行时能力）。
- 不做 sandbox 动态化（架构语义冲突，见 5.4）。

---

## 三、成功标准

本方案完成后，以下每一条都应能在真机上直接判定为真：

1. 执行 `mvn -pl plugin-samples/demo-hello-plugin package` → `plugin-samples/demo-hello-plugin/target/demo-hello-plugin.jar` 存在；`unzip -l` 输出同时满足：含 `META-INF/ai-plugin.properties`、含实现类 `.class`、**不含** `io/agentscope/**` 任何条目（证明 `provided` 生效）。
2. `POST /api/ai/plugins/upload`（表单文件 = demo jar）→ 响应 `loaded=true`，`pluginId` 等于 `demo-hello`，`version` 非空字符串；随后 `GET /api/ai/plugins` 返回列表中该条 `loaded=true`。
3. 造一个删掉 `META-INF/ai-plugin.properties` 的 jar 并上传 → 响应 `loaded=false`，且 `error` 字段包含「清单」二字；对比：改造前该接口只有 `loaded=false`，无任何原因字段。
4. **不重启后端进程**，先对一个 agent 发一条消息使其进入 registry 缓存，再上传 demo jar，再对**同一个 agent** 发下一条消息 → 第二条消息所在轮次的工具清单中出现 `hello_echo`（由插件贡献），且 `GET /api/ai/plugins` 请求前后 registry 缓存条目数在每次插件装载/卸载事件后归零。
5. **不重启后端进程**，上传注册了中间件的 demo jar → 对**已缓存过的同一个 agent key** 再发一条消息 → 服务端日志中出现 `sample-mw` 标记 ≥ 1 次（改造前为 0 次，因为中间件列表在 `AiAgentService` 构造时冻结）。
6. `POST /api/ai/plugins/unload`（`key=demo-hello-plugin`）→ 返回 `unloaded=true`；再对同一 agent 发消息 → 该轮工具清单中**不含** `hello_echo`，且 `DynamicPluginManager.dynamicCapabilities()` 中属于 demo 的能力数 = 0。
7. 保持后端进程运行，覆盖写 `data/plugins/demo-hello-plugin.jar`（把 `version` 改成 `v2`）→ 在 ≤ 2 × `ai.agent.plugin-watch-interval-ms` 内 `GET /api/ai/plugins` 显示该插件 `version=v2`，且 `dynamicCapabilities()` 中属于该插件的能力数**仍为 1**（不重复累积）。
8. 在 `data/plugins/demo-hello-plugin/lib/` 放入一个第三方依赖 jar（demo 插件在 `onLoad` 中 `Class.forName` 它的类）→ 上传返回 `loaded=true`；清空 `lib/` 后重新装载 → 返回 `loaded=false` 且 `error` 包含「依赖缺失」。
9. 将 `ai.agent.plugin-allowed-jars` 配为 `other.jar`（不含 demo jar 名）→ 上传 demo jar → 返回 `loaded=false` 且 `error` 包含「允许清单」。
10. 行数审计通过：`DynamicPluginManager` 有效行 ≤ 300（当前 220）；`AiAgentService` 有效行 ≤ 500（当前 469，**净增必须 ≤ 31**）；`AiPluginAdminController` 有效行 ≤ 400（当前 42）；新增的 `PluginRuntimeRefresher` / `LoadResult` 单文件有效行在对应类别上限内。

判定口径说明：第 4、5、6、7 条都要求「不重启进程」，这是本方案与现状的分水岭 —— 现状下这些能力只有在**进程启动前**把 jar 放进目录才有效，而这恰恰是「动态」二字要求排除的路径。

---

## 四、参照行为（对标）

| 参照项 | 它怎么做 | 我们要复刻到什么程度 |
| --- | --- | --- |
| dsh / Cordis（`docs/plans/2026-08-25-plugin-hot-reload.md` 已对齐） | `ctx.on/emit` 事件模型 + 可逆注册 + 装载/卸载生命周期 | 复刻「装载即注册、卸载可逆、事件可订阅、jar 变更热重载」；**不复刻**其 DI 容器与调度 Fiber |
| Spring Boot 条件装配 | 启动期扫描 + `AutoConfiguration.imports` 声明 | 复刻「清单文件声明实现类」（我们用自己的 `META-INF/ai-plugin.properties`）；**不复刻**启动期固定，本轮要解决的正是它的反面 |
| OSGi | 模块生命周期（installed→resolved→started→stopped→unloaded）+ 类隔离 | 只复刻**生命周期四态语义**（装载 / 启动 / 停止 / 卸载）；**不复刻** parent-last 隔离与版本依赖解析（见第九节） |
| JDK `ServiceLoader` | `META-INF/services/<接口全名>` | 不采用。SPI 机制本身已用自研清单实现，因为需要在同一文件里携带 `plugin.class` 之外的信息；本轮不替换清单格式 |

---

## 五、现状差距

### 5.1 已具备（本方案不是从零开始）

| 组件 | 位置 | 现状 |
| --- | --- | --- |
| 插件 SPI | `framework/framework-ai/.../plugin/AiPlugin.java` | `id()` / `version()` / `onLoad(ctx)` / `onUnload()` 四方法，27 行 |
| 插件上下文 | `.../plugin/PluginContext.java` | 6 个注册入口：capability / listener / sandbox / profilePatch / middleware + `eventBus()` + `dataDir()` |
| 插件管理器 | `.../plugin/DynamicPluginManager.java` | 220 有效行：`loadJar` / `unload` / `list` / `scanAndReload` / `startWatcher` / `stopWatcher`，含 jar 指纹变更检测 |
| 事件总线 | `.../plugin/PluginEventBus.java` | `on(pluginId, eventType, handler)` / `emit` / `removeAll(pluginId)`，44 有效行 |
| Spring 装配 | `AiAgentAutoConfiguration#dynamicPluginManager` | 启动扫描 `ai.agent.plugin-dir`（默认 `data/plugins`）+ 可选 watcher（默认关，间隔 5000ms） |
| 管理接口 | `modules/.../plugin/AiPluginAdminController.java` | `GET /api/ai/plugins`、`POST /upload`、`POST /load`、`POST /unload`，42 有效行 |
| 测试 | `DynamicPluginManagerHotReloadTest` 等 | 热重载 5 例 + 可逆回滚 + 事件总线；测试期用 `JavaCompiler` 现编现打包插件 jar |
| 插件目录 | `data/plugins/` | 存在，当前为空 |

结论：**「能加载 jar」这半件事已经做完了**。本轮要补的是「加载了之后功能真的生效」以及「失败了能说清原因」。

### 5.2 能力缺失

- **A1 · 装载失败不可诊断**。`loadJar` 捕获 `Exception` 后只 `log.warn` 并返回 `null`，`PluginInfo` 结构里没有任何失败信息，controller 只能回 `loaded:false`。用户把 jar 拖进去、接口说没装上，但「为什么」只存在于服务端日志里。插件是用户自己放的文件，错误必须回到用户手里。
- **A2 · 插件不能自带第三方依赖**。`loadJar` 构造的 `URLClassLoader` 的 URL 数组只有 jar 自身，父加载器是应用加载器。插件若引用宿主没有的库（如 `com.google.gson`）会在 `onLoad` 触发 `NoClassDefFoundError`，且被 A1 吞掉，表现为「莫名其妙没装上」。
- **A3 · 没有可投放的样例插件**。`DynamicPluginManagerHotReloadTest` 内部的 jar 是测试运行期用 `JavaCompiler` 现编的临时产物，进程结束即消失。仓库里不存在一个用户能 `mvn package` 拿到、能直接扔进 `data/plugins/` 的插件产物，也没有「怎么写插件」的可编译示例。
- **A4 · `registerSandbox` / `registerProfilePatch` 的接口承诺与运行时兑现不一致**（详见 5.4）。
- **A5 · 无来源审计与准入开关**。装载 / 卸载只有一条 info 日志，没有允许清单，也没有在任何地方声明「加载 jar 等于把任意代码注入本进程」。现在 `POST /upload` 是一个**无门槛的远程代码执行入口**。

### 5.3 只是没接线（成本更低，但是本轮「动态」的核心）

- **B1 · 中间件快照被冻结，运行时装上的插件中间件永远不生效**。
  `AiAgentAutoConfiguration` 在构造 `AiAgentService` Bean 时，把 `pluginManager.dynamicMiddlewares()` **一次性**并进一个列表并传入；`AiAgentService` 把它存为 `final List<AiAgentMiddleware> middlewares`（第 52 行赋值，第 118 行落地），每轮在构建 `MiddlewareChain` 时读的是**这个冻结列表**。后果：只有「进程启动前就躺在插件目录里」的插件中间件有效；运行中上传的插件，其中间件注册进 `DynamicPluginManager.middlewares` 后**再也没有人来读**。
- **B2 · agent 实例缓存冻结插件能力**。
  `AiAgentService` 的请求路径是 `registry.withAgent(...)` → `AiHarnessAgentRegistry.getOrCreate()` 按 key 命中缓存；而插件能力（`AiCapabilityProvider`）与工具钩子（`ToolExecutionListener`）只在 `AiHarnessAgentFactory.create()` 里读取，即**缓存未命中时**。后果：运行时装上的能力对**已缓存的 agent** 无效，要等 LRU 淘汰或有人显式调 `invalidate(tenantId, agentId)`。
- **B3 · `ProfilePatch` 同样被冻结**。`AiHarnessAgentRouter`（第 21 / 64 / 82 行）在构造时接收**固定的** `List<AiProfilePatchProvider> patches`，与 B1 同构。
- **B4 · 管理接口硬编码插件目录**。`AiPluginAdminController` 三处写死 `Path.of("data/plugins")`，而管理器用的是 `ai.agent.plugin-dir`。把配置改到别的目录后，管理器从新目录加载、上传接口仍往旧目录写，且 `load` / `unload` 找不到文件。

### 5.4 不打算解决的问题（写清原因，避免被当成遗漏）

- **动态 `SandboxBackend` 消费**。`SandboxBackend` 在宿主是**单例 Bean**（`AiAgentAutoConfiguration#localSandboxBackend`，`@ConditionalOnMissingBean`，被 `AgentHookBridge` 单点注入），而 `PluginContext#registerSandbox` 提供的是**列表式注册**。这是模型冲突而非漏接线：要让多个插件沙箱共存，得先定义「优先级 / 多后端路由 / 同名冲突」语义，属于独立决策，不应塞进本轮。本轮只做**诚实化**：在 `PluginContext#registerSandbox` 与 `DynamicPluginManager.dynamicSandboxes()` 的 Javadoc 上明确标注「当前无消费点，注册后运行时不生效（预留）」，避免插件作者误判。
- **类隔离与依赖解析**。见 6.3 取舍。
- **插件签名与权限沙箱**。Java 17 已移除 `SecurityManager`，低成本方案不存在，需独立设计（见第九节）。

---

## 六、技术方向

### 6.1 装载结果结构化（对应 A1）

职责：

- 让每一次装载尝试都携带一个**可直接展示给用户的中文失败原因**，不依赖服务端日志。
- 把「jar 名 / 插件 id / 版本 / 是否成功 / 失败原因」收敛到一个返回结构里，供 REST 与后续前端复用。

关键接口 / 数据结构：

```java
/** 一次装载尝试的结果（成功或失败都返回它，不再用 null 表示失败）。 */
public record LoadResult(
        boolean ok,
        String jarName,
        String pluginId,   // 失败时可为 jar 名
        String version,    // 失败时为 null
        String error       // 失败时为一行中文原因；成功时为 null
) {}

// 管理器
public synchronized LoadResult loadJar(Path jar);
public synchronized List<PluginInfo> list();          // 签名与语义不变

// 失败归类（私有静态方法，把异常翻译成用户能看懂的一句话）
private static String describeFailure(Throwable t);
```

`describeFailure` 的归类表（判定顺序从上到下）：

| 触发条件 | 返回文案 |
| --- | --- |
| `IllegalStateException` 且 message 含 `ai-plugin.properties` | 插件清单 META-INF/ai-plugin.properties 缺失 |
| message 含 `plugin.class` | 插件清单缺少 plugin.class |
| message 含 `未实现 AiPlugin` | 插件类未实现 AiPlugin：`<类名>` |
| `NoClassDefFoundError` / `ClassNotFoundException` | 插件依赖缺失：`<类名>`（请放到 `plugins/<jar名>/lib/` 或 shade 进插件） |
| `NoSuchMethodException` | 插件类缺少无参构造 |
| `InvocationTargetException` | 插件装载回调抛错：`<cause 类名>: <message>` |
| 其余 | `<异常类名>: <message>` |

取舍：不引入自定义异常体系，也不把异常对象往接口外抛。理由：插件失败是**用户可自助修复**的类别（改清单、补依赖、换 jar），所以需要的是「一句能照做的话」，不是堆栈；堆栈仍写日志供开发排查。

`loadJar` 返回类型从 `PluginInfo`/`null` 变为 `LoadResult` 是**破坏性变更**，调用点仅三处：`AiPluginAdminController`（upload / load）、`AiAgentAutoConfiguration` 启动扫描、两个既有测试。启动扫描处只关心成败（记日志），两个测试改为断言 `LoadResult.ok()`。

### 6.2 贡献实时生效：失效广播 + 惰性重建（对应 B1 / B2 / B3）

职责：

- 让「插件集合发生变化」成为一个**全局可观察的事实**，缓存层据此决定何时丢弃旧实例。
- 让中间件 / 补丁这两处**构造期快照**改为**使用时取快照**。

关键接口 / 数据结构：

```java
// 1) 注册表新增：失效全部（与既有 invalidate(tenantId, agentId) 同族，不关闭注册表本身）
public void invalidateAll();     // 实现 = removeAndRetire(所有现存 key)，不置 closed

// 2) 新增装配组件：把插件生命周期事件翻译成缓存失效
class PluginRuntimeRefresher implements AutoCloseable {
    PluginRuntimeRefresher(DynamicPluginManager manager,
                           ObjectProvider<AiHarnessAgentRegistry> registryProvider);
    // 订阅 PLUGIN_LOADED / PLUGIN_UNLOADED → registryProvider.getIfAvailable().invalidateAll()
}

// 3) AiAgentService：中间件改为「每轮取一次快照」
private List<AiAgentMiddleware> middlewareSnapshot() {
    List<AiAgentMiddleware> merged = new ArrayList<>(staticMiddlewares);
    if (pluginManager != null) {
        merged.addAll(pluginManager.dynamicMiddlewares());
    }
    return merged;
}
// 构建 MiddlewareChain 处由 `AiAgentMiddleware.buildChain(middlewares, terminal)`
// 改为 `AiAgentMiddleware.buildChain(middlewareSnapshot(), terminal)`

// 4) AiHarnessAgentRouter：ProfilePatch 改法同构（构造期 List → 使用时合并动态）
```

**为什么这个设计是安全的**（这是本方案置信度的关键依据，已核对源码）：`AiHarnessAgentRegistry.AgentEntry.retire()` 只置 `retired = true`，并且**仅在 `activeUses == 0` 时才真正 `agent.close()`**；`acquire()` 在 `retired` 时返回 `false`，新请求不会复用被退休的实例。因此 `invalidateAll()` 对**正在执行的 turn 是安全的**：运行中的 turn 持有租约，会照常跑完并返回结果，只是结束后实例被关闭；下一个请求走 `entryFor` → `factory.create()` 重建（带上新插件贡献）。

**为什么不做「热替换正在运行的 agent」**：运行中的 turn 持有 `toolkit` / agent 引用，中途替换会导致同一轮里前几步用旧工具、后几步用新工具，行为无法解释。选定「失效 + 下一轮重建」，代价是新插件在**下一个 turn** 才生效 —— 这个代价是可接受的，且可被第 4 条成功标准判定。

**装配顺序与循环依赖的处理**：`factory` 依赖 `pluginManager`（`AiHarnessAgentFactory` 第 71 / 204 行），`registry` 依赖 `factory`，所以 `pluginManager` 的 Bean 方法里拿不到 `registry`。因此 `PluginRuntimeRefresher` 作为**独立的 `@Bean`**，通过 `ObjectProvider<AiHarnessAgentRegistry>` **延迟**取注册表；启动期插件扫描发生在 `pluginManager` Bean 创建过程中，此时 refresher 尚未订阅、注册表也还不存在，属正常（那时还没有任何 agent 实例）。为避免 watcher 线程抢跑导致的极小窗口漏失效，refresher 在 `ApplicationReadyEvent` 上补做一次 `invalidateAll()`。

**事件短路风险**：`PluginEventBus.emit` 在任一订阅者返回 `false` 时短路。refresher 在**启动期（任何插件装载之前）**订阅，位于订阅列表首位，因此插件无法通过吞没 `plugin.loaded` 事件来阻止缓存失效。此约束需在 refresher 的注释里写明。

取舍：**为什么不直接让 `AiAgentService` 每轮都重建 agent** —— 会丢掉 `AiHarnessAgentRegistry` 的 LRU 复用价值，而 `factory.create()`（模型、工具集、技能注册）不是免费操作；**为什么复用 `PluginEventBus` 而不是新增一套监听接口** —— 事件总线已具备「按类型订阅 + 按插件 id 可撤销」语义，新增并行机制会让「插件发生变化」这件事有两个真相源。

### 6.3 插件自带依赖（对应 A2）

职责：

- 让插件能带宿主没有的第三方库，且**不污染宿主 classpath**。

关键接口 / 数据结构：

```java
// loadJar 内构造加载器时，URL 数组 = [插件 jar] + [同名配套目录下的 lib/*.jar]
// 约定：data/plugins/demo-hello-plugin.jar 的配套依赖目录为
//       data/plugins/demo-hello-plugin/lib/*.jar   （目录名 = jar 名去掉 .jar）
private URL[] buildClasspath(Path jar);
```

取舍：**明确不做 parent-last（子优先）隔离，父加载器必须保持为应用加载器**。原因是硬约束而非偏好：若插件自己的加载器优先加载 `AiPlugin`，那么 `AiPlugin.class.isAssignableFrom(clazz)` 会因「同名类被两个加载器各自加载 ⇒ 两个不同 Class 对象」而判定失败，插件永远装不上。共享宿主 SPI 的类身份是这套机制成立的前提。因此：

- 插件引用宿主 SPI（`AiPlugin` / `PluginContext` / `AiCapabilityProvider` / `Toolkit` 等）时，Maven 依赖必须用 `provided`，保证 jar 内不含这些类；
- 插件若引用了与宿主**版本冲突**的同名库，必须自行 shade 改包名，本轮不做自动隔离（见第九节）。

### 6.4 样例插件模块（对应 A3）

职责：

- 提供一个真实、可构建、可投放的插件产物，作为端到端验证的载体与「怎么写插件」的活文档。
- 一次覆盖三条动态路径（能力 / 工具钩子 / 中间件），使第 4、5、6 条成功标准有可观测的判定物。

关键接口 / 数据结构：

```text
plugin-samples/demo-hello-plugin/
├── pom.xml                      # 依赖 framework-ai，scope=provided
└── src/main/
    ├── java/com/zimo/sample/plugin/hello/
    │   ├── DemoHelloPlugin.java        # implements AiPlugin：id=demo-hello，version=v1
    │   ├── HelloEchoCapability.java    # AiCapabilityProvider：向 Toolkit 贡献工具 hello_echo
    │   └── SampleTimingMiddleware.java # AiAgentMiddleware：每轮打印 sample-mw 标记
    └── resources/META-INF/ai-plugin.properties   # plugin.class=com.zimo.sample.plugin.hello.DemoHelloPlugin
```

- 根 `pom.xml` 的 `<modules>` 增加 `plugin-samples`，`plugin-samples/pom.xml` 聚合 demo 模块。
- 构建产物：`mvn -pl plugin-samples/demo-hello-plugin package` → `target/demo-hello-plugin.jar`，直接复制到 `data/plugins/` 即可被装载。
- demo 插件在 `onLoad` 中订阅 `PLUGIN_LOADED` 事件并打印一行标记，用于验证事件总线对插件开放。

取舍：**为什么把它纳入根 pom 聚合**（而不是放个脚本单独构建）：纳入聚合后 `mvn clean verify` 会持续编译它，插件 SPI 一旦变更会立刻在此处编译失败 —— 这是防止「样例腐坏」最便宜的手段；代价是全量构建多一个极小模块。备选方案（不聚合、用脚本单独打包）已列入第九节延后项。

### 6.5 准入开关与审计（对应 A5）

职责：

- 把「无门槛的远程代码执行入口」收敛为「可配置准入 + 事后可追溯」。

关键接口 / 数据结构：

```java
// AiAgentProperties 新增（沿用既有 ai.agent.* 命名空间）
//   ai.agent.plugin-allowed-jars   默认 "*"，逗号分隔的文件名或 *（表示不限制）
public boolean isPluginJarAllowed(String jarFileName);

// 装载 / 卸载日志统一携带：jar 名、插件 id、版本、文件大小、mtime
log.info("插件已装载: {} v{} (jar={}, size={}B)", id, version, jarName, jarSize);
```

取舍：**不把默认值改成「禁止加载」**。理由有两条：其一，改默认值会破坏既有热重载测试与「把 jar 丢进目录即生效」的既有行为，属于无必要的行为破坏；其二，真正的风险不来自默认值而来自「谁能调这个接口」。因此本方向只提供**收紧手段**（允许清单）与**追溯手段**（结构化日志），并在文档与本方案的「不打算解决的问题」中明确声明风险，不假装用配置解决了一个权限问题。

---

## 七、里程碑

### M1. 装载可诊断 + 目录一致性

- 产出：`LoadResult` 结构 + `describeFailure` 归类 + `AiPluginAdminController` 改用配置目录（A1 / B4）。
- 验证方式：成功标准第 3 条（坏 jar 拿到含「清单」的原因）；`data/plugins` 与自定义 `ai.agent.plugin-dir` 两个配置下 upload / load / unload 均能找到文件。

### M2. 贡献实时生效（本轮核心）

- 产出：`AiHarnessAgentRegistry.invalidateAll()` + `PluginRuntimeRefresher` + `AiAgentService` 中间件快照化 + `AiHarnessAgentRouter` 补丁快照化（B1 / B2 / B3）。
- 验证方式：成功标准第 4、5、6 条（不重启进程，装载后下一轮工具生效、中间件生效，卸载后失效）。

### M3. 样例插件 + 依赖目录

- 产出：`plugin-samples/demo-hello-plugin` 模块 + `buildClasspath` 支持 `lib/*.jar`（A2 / A3）。
- 验证方式：成功标准第 1、2、7、8 条（可构建、可投放、热重载升版不累积、lib 依赖可加载）。

### M4. 前端插件管理页（可选，可裁剪）

- 产出：`frontend/agent-harness-ui` 新增 `/plugins` 页面：列表（id / version / jar / 状态 / 失败原因）+ 上传 jar + 装载 + 卸载。行数按前端规范控制（页面 ≤ 500、公共组件 ≤ 300、弹窗 ≤ 200），沿用既有 `useXxx` composable 抽状态的做法（参考 `useMcpTest.js`）。
- 验证方式：页面上传 demo jar → 列表出现 `demo-hello v1`；卸载 → 该条消失；上传坏 jar → 页面展示失败原因文案。
- 说明：若你只要后端能力，此里程碑可整体裁剪，直接用 REST 接口操作。

---

## 八、自测计划

### 场景 A · 构建样例插件

1. 执行 `mvn -pl plugin-samples/demo-hello-plugin -am package`。
2. 确认产物存在：`plugin-samples/demo-hello-plugin/target/demo-hello-plugin.jar`。
3. 执行 `unzip -l plugin-samples/demo-hello-plugin/target/demo-hello-plugin.jar`。
4. 验证：
   - 输出含 `META-INF/ai-plugin.properties`。
   - 输出含 `com/zimo/sample/plugin/hello/DemoHelloPlugin.class`。
   - 输出**不含** `io/agentscope/` 与 `com/zimo/framework/ai/plugin/`（证明 `provided` 生效，SPI 类未被打进插件）。

### 场景 B · 装载并在不重启的前提下生效

1. 启动后端（9900），确认 `GET /api/ai/plugins` 返回 `[]`。
2. 通过页面或 REST 对一个 agent 发一条消息，使该 agent 进入 registry 缓存。
3. `POST /api/ai/plugins/upload` 上传 `demo-hello-plugin.jar`。
4. 对**同一个 agent** 再发一条消息。
5. 验证：
   - 第 3 步响应 `loaded=true`、`pluginId=demo-hello`、`version` 非空。
   - 服务端日志出现 `插件已装载: demo-hello` 与 `sample-mw` 标记（≥ 1 次）。
   - 第 4 步该轮工具清单出现 `hello_echo`。
   - 第 4 步**未**重启进程（进程 PID 与第 1 步一致）。

### 场景 C · 失败诊断

1. 复制 demo jar，用 `zip -d` 删掉 `META-INF/ai-plugin.properties`，改名 `broken.jar`。
2. 上传 `broken.jar`。
3. 验证：响应 `loaded=false` 且 `error` 包含「清单」。

### 场景 D · 卸载可逆

1. 在场景 B 的进程内执行 `POST /api/ai/plugins/unload`，`key=demo-hello-plugin`。
2. 对同一 agent 再发一条消息。
3. 验证：
   - 响应 `unloaded=true`。
   - 该轮工具清单不含 `hello_echo`。
   - 日志中 `sample-mw` 标记**不再新增**（计数与卸载前持平）。

### 场景 E · 热重载升版不累积

1. 打开 `ai.agent.plugin-watch-enabled=true`、`ai.agent.plugin-watch-interval-ms=2000`，重启后端。
2. 覆盖写 `data/plugins/demo-hello-plugin.jar`（源码 `version()` 改为 `v2` 后重新 `mvn package`，同文件名覆盖）。
3. 等待 ≥ 2 个轮询周期（≥ 4 秒）。
4. 验证：
   - `GET /api/ai/plugins` 中该插件 `version=v2`。
   - `dynamicCapabilities()` 中属于 demo 的能力数 = 1（识别不到内部 API 时，退化为观察日志中只出现一次 `插件已装载: demo-hello v2` 且无「已加载」重复提示）。
5. 删除 `data/plugins/demo-hello-plugin.jar`。
6. 验证：≥ 2 个周期后 `GET /api/ai/plugins` 返回空列表。

### 场景 F · 自带依赖与准入

1. 制造一个第三方依赖 jar（例如用 JDK 自带的 `java.util.zip` 之外的一个自建类打成 `dep-lib.jar`），放入 `data/plugins/demo-hello-plugin/lib/`。
2. 重新上传 demo jar，验证 `loaded=true`。
3. 清空 `lib/` 目录，重新上传，验证 `loaded=false` 且 `error` 含「依赖缺失」。
4. 配置 `ai.agent.plugin-allowed-jars=other.jar`，重启后端，上传 demo jar。
5. 验证：`loaded=false` 且 `error` 含「允许清单」。
6. 恢复 `ai.agent.plugin-allowed-jars=*`。

### 验证手段的语义缺陷（必须声明）

- 成功标准第 4、5、6 条对「工具清单中出现 `hello_echo`」的判定，依赖服务端日志/事件的可读性，而**不是**模型是否真的调用了该工具。模型是否调用受模型自身决策影响，不能作为判定依据 —— 因此判定口径定为「工具被注册进 Toolkit」，即由插件在 `contribute()` 中打印的注册标记 + `dynamicCapabilities()` 的计数共同佐证。**通过 ≠ 模型一定会用它**，两者是不同层面的结论。
- 场景 E 第 4 条若无法从外部读取 `dynamicCapabilities()`，退化为日志口径（不重复「已加载」提示）。这是**弱化判定**：它只能证明「没有重复装载」，不能证明「集合内无残留」。执行时需在实施记录里如实标注采用了哪种口径。

---

## 九、已知延后项

- **parent-last 类隔离与版本依赖解析** —— 原因：与「SPI 类身份必须共享」的前提直接冲突，需要定制「SPI 包 parent-first + 其余 parent-last」的双亲委派策略，并配套版本冲突诊断；工作量与本轮目标（装载 + 生效）不成比例，且收益有限（插件与宿主库冲突时 shade 是更通用的解法）。
- **插件签名校验与权限沙箱** —— 原因：Java 17 已移除 `SecurityManager`，无低成本等价物；真正的隔离需要独立进程或 WASM/容器级沙箱，属于独立课题。
- **插件贡献 Spring Bean / REST Controller** —— 原因：需要 Spring 子容器与父子生命周期管理（Bean 注册、路由挂载、优雅卸载），是另一层能力；本期插件只贡献 AI 运行时能力（工具 / 钩子 / 中间件 / 补丁）。
- **动态 `SandboxBackend` 消费** —— 原因见 5.4：单例 Bean 与列表注册的模型冲突，需先定义优先级与冲突语义。
- **样例插件不纳入根 pom 聚合的替代方案** —— 原因：纳入聚合虽让全量构建多一个模块，但能保证样例不腐坏；若后续认为构建耗时不可接受，可改为 `plugin-samples/build.sh` 单独构建，届时需同步在本文件回填偏差。
- **插件 jar 的 sha256 指纹审计** —— 原因：每次装载需完整读取文件，而现有 size + mtime 指纹已能满足热重载变更检测；审计需求出现时再加。

---

## 十、实施顺序

1. **M1**：`LoadResult` + `describeFailure`（`DynamicPluginManager`）→ 改三处调用点 → `AiPluginAdminController` 改用配置目录注入。
2. **M2**：`AiHarnessAgentRegistry.invalidateAll()` → `PluginRuntimeRefresher` + autoconfig 装配 → `AiAgentService` 中间件快照化 → `AiHarnessAgentRouter` 补丁快照化。**先量行数再动手**（`AiAgentService` 余量仅 31 行）。
3. **M3**：`buildClasspath` 支持 `lib/*.jar` → 新建 `plugin-samples/demo-hello-plugin` + 根 pom 聚合 → 构建产物。
4. **M4（可选）**：前端插件管理页。
5. 执行第八节全部场景，回填第十一节。
6. 回归：`mvn clean verify`；重启前后端服务后用真机流程复跑成功标准 2 / 4 / 5 / 6 / 7。

---

## 十一、实施记录

> 待实施后回填（实际改动清单、与方案的偏差及原因、真机验证结果、过程中新发现的坑）。
