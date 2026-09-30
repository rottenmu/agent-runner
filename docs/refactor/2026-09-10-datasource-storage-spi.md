# 数据库存储抽象下沉 agent-datasource（module-datasource-storage）

> 日期：2026-09-10
> 目标：把「存储后端 SPI + 路由」这套通用能力从 agent-memory 私有实现，下沉为 agent-datasource 下的**零依赖通用模块**，供任意模块复用。

---

## 1. 背景与动机

改造前，存储引擎插拔能力（`StorageContext` / `OltpStorageProvider` / `OlapStorageProvider` / 路由回退逻辑）**只存在于 agent-memory-core 内部**：

```
com.zimo.module.agentmemory.storage.spi.StorageContext
com.zimo.module.agentmemory.storage.spi.OltpStorageProvider
com.zimo.module.agentmemory.storage.spi.OlapStorageProvider
com.zimo.module.agentmemory.storage.MemoryStorageFactory   ← 内含手写路由 + 回退
```

问题：

| 问题 | 说明 |
| --- | --- |
| 不可复用 | 其他模块（datasource / 业务模块）想接多引擎，只能复制粘贴同一套样板 |
| 概念错位 | SPI 是通用存储范式，却挂在「记忆」这个业务语义包下 |
| 路由逻辑重复 | `MemoryStorageFactory` 里 OLTP / OLAP 两段回退逻辑几乎逐行重复 |
| 已知缺陷 D5 | 回退默认引擎时，传给 Provider 的仍是**未解析的原始引擎名**（`duckdb`），Provider 拿到的 `context.engine()` 与实际实现不符 |

---

## 2. 方案：零依赖子模块承载抽象

关键约束是**不能把重依赖带进消费方**。`module-datasource-core` 携带 mybatis-plus / poi-ooxml / pdfbox / tess4j / jsoup / commons-net / mysql-connector 七重依赖，且其 AutoConfiguration 无条件加载（要求 MyBatis 存在）。若把 SPI 放进它，agent-memory-application（9900 独立服务）会被迫连带整个数据源全家桶。

因此新建**同级零依赖子模块**：

```
modules/agent-datasource/
├── module-datasource-storage/     ← 新增（本模块）
│   ├── pom.xml                    parent=module-datasource，仅 JDK + slf4j-api
│   └── src/main/java/com/zimo/module/ds/storage/
│       ├── StorageContext.java    装配上下文（record）
│       ├── StorageProvider.java   存储后端 SPI（泛型）
│       └── StorageRouter.java     按名路由 + 回退（无状态工具类）
└── module-datasource-core/        重依赖数据源实现（不参与本抽象）
```

模块数 30 → **31**。

### 2.1 包名与语义

| 类 | 职责 |
| --- | --- |
| `StorageContext` | record：`engine` / `jdbcUrl` / `dataFilePath` / `dataSource` / `options`，外加 `dataFile()`、`withEngine()` 便捷方法。用 `options` + 可为 null 的字段覆盖 JDBC 类、列式文件类、需额外参数的引擎三类形态 |
| `StorageProvider<T>` | 泛型 SPI：`String engine()` + `T create(StorageContext)`。`T` 是该后端产出的仓储类型 |
| `StorageRouter` | 静态 `route(providers, configuredEngine, defaultEngine, context, kind)`：配置空 → 默认；未注册 → 告警并回退默认（**并把实际生效引擎名写回 context**）；全无 → `IllegalStateException` |

`withEngine()` 的存在正是 D5 的根治点：回退路径上 Provider 收到的是**真实生效**的引擎名。

---

## 3. 破环：AiSkill 下沉 framework-common

改造前存在循环：

```
module-datasource-core → framework-ai → agent-memory-core → module-datasource-*
```

`framework-ai` 的 skill 相关类型（`AiSkill` / `AiSkillResult`）被 `module-datasource-core` 引用，而 skill 本身与 AI 框架无耦合。处理：

- 将 `AiSkill.java`、`AiSkillResult.java` 从 `framework/framework-ai/.../skill/` 迁至
  `framework/framework-common/src/main/java/com/zimo/framework/common/skill/`；
- 全仓 **33 个文件** import 改写；
- `module-datasource-core/pom.xml` 移除 `framework-ai` 依赖，循环解除。

---

## 4. agent-memory 侧的落地改造

`agent-memory` 是**独立 git 仓库**（remote `rottenmu/agent-memory`），由 `modules/pom.xml` 聚合进 reactor。

### 4.1 删除私有 SPI

| 文件 | 处置 |
| --- | --- |
| `agentmemory/storage/spi/StorageContext.java` | 删除，改用 `com.zimo.module.ds.storage.StorageContext` |
| `agentmemory/storage/spi/OltpStorageProvider.java` | 删除，改用 `StorageProvider<OltpMemoryRepository>` |
| `agentmemory/storage/spi/OlapStorageProvider.java` | 删除，改用 `StorageProvider<OlapAnalyticsRepository>` |

> 因 agent-memory 为独立仓库、文件不在父仓索引，只能用普通删除（`git rm` 不可用）。

### 4.2 Provider 改为通用接口实现

```java
// H2OltpStorageProvider：消费 dataSource
public class H2OltpStorageProvider implements StorageProvider<OltpMemoryRepository> {
    @Override public String engine() { return "h2"; }
    @Override public OltpMemoryRepository create(StorageContext context) {
        if (context.dataSource() == null) { throw new IllegalStateException("..."); }
        return new H2OltpMemoryRepository(context.dataSource());
    }
}

// ArrowOlapStorageProvider：纯文件型，消费 dataFile()
public class ArrowOlapStorageProvider implements StorageProvider<OlapAnalyticsRepository> {
    @Override public String engine() { return "arrow"; }
    @Override public OlapAnalyticsRepository create(StorageContext context) {
        return new ArrowOlapAnalyticsRepository(context.dataFile());
    }
}
```

一个上下文同时喂 JDBC 型与文件型两类引擎，Provider 各取所需——这是 `StorageContext` 字段设计的验证点。

### 4.3 装配层直接路由，移除 MemoryStorageFactory

工厂类最初的形态是「自带路由逻辑」。§4.2 把路由下沉到 `StorageRouter` 后，`MemoryStorageFactory` 只剩一层
**方法名映射**（`createOltp` → `route(..., "OLTP")`），属于纯转发壳。经确认外部引用仅装配层与自身测试后，
**直接删除该类**，装配层改为直接调用 `StorageRouter.route(...)`：

```java
@Bean
public OltpMemoryRepository oltpMemoryRepository(
        List<StorageProvider<OltpMemoryRepository>> oltpProviders,
        StorageContext memoryStorageContext,
        AgentMemoryProperties properties) {
    return StorageRouter.route(oltpProviders, properties.oltpEngine(),
            AgentMemoryProperties.DEFAULT_OLTP_ENGINE, memoryStorageContext, "OLTP");
}

@Bean
public OlapAnalyticsRepository olapAnalyticsRepository(
        List<StorageProvider<OlapAnalyticsRepository>> olapProviders,
        StorageContext memoryStorageContext,
        AgentMemoryProperties properties) {
    return StorageRouter.route(olapProviders, properties.olapEngine(),
            AgentMemoryProperties.DEFAULT_OLAP_ENGINE, memoryStorageContext, "OLAP");
}
```

默认引擎常量随之**归位到真正消费它的配置类**：

| 常量 | 迁移前 | 迁移后 |
| --- | --- | --- |
| `DEFAULT_OLTP_ENGINE` = `"h2"` | `MemoryStorageFactory` | `AgentMemoryProperties` |
| `DEFAULT_OLAP_ENGINE` = `"arrow"` | `MemoryStorageFactory` | `AgentMemoryProperties` |

顺带**消除了一处字面量重复**：`AgentMemoryProperties` 紧凑构造函数里原本硬编码 `oltpEngine = "h2"` /
`olapEngine = "arrow"`，与工厂里的常量各写一份；现在统一引用常量，单一事实源。

删除清单：

| 文件 | 处置 |
| --- | --- |
| `agentmemory/storage/MemoryStorageFactory.java` | **删除** |
| `agentmemory/storage/MemoryStorageFactoryTest.java` | 更名 `StorageRoutingTest.java`，改测 `StorageRouter` 路由 |

### 4.4 装配变更（AgentMemoryAutoConfiguration）

```java
@Bean
public StorageProvider<OltpMemoryRepository> h2OltpStorageProvider() { ... }

@Bean
public StorageProvider<OlapAnalyticsRepository> arrowOlapStorageProvider() { ... }

@Bean
public OltpMemoryRepository oltpMemoryRepository(
        List<StorageProvider<OltpMemoryRepository>> oltpProviders,
        StorageContext memoryStorageContext, AgentMemoryProperties properties) { ... }

@Bean
public OlapAnalyticsRepository olapAnalyticsRepository(
        List<StorageProvider<OlapAnalyticsRepository>> olapProviders,
        StorageContext memoryStorageContext, AgentMemoryProperties properties) { ... }
```

Bean 声明为带泛型的接口类型，Spring 依 `ResolvableType` 精确分派到对应集合，OLTP / OLAP 两类引擎互不串扰。

### 4.5 依赖声明

`agent-memory-core/pom.xml` 新增（显式版本，保持独立仓库不依赖父 pom 的 dependencyManagement 的既有约定）：

```xml
<dependency>
    <groupId>com.zimo</groupId>
    <artifactId>module-datasource-storage</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## 5. 验证

| 项 | 结果 |
| --- | --- |
| `module-datasource-storage` 编译安装 | SUCCESS |
| `agent-memory-core` 编译（40 main + 8 test 源文件） | SUCCESS |
| `StorageRoutingTest`（原 MemoryStorageFactoryTest） | **6/6 通过** |
| `agent-memory-core` 全量测试 | **26/26 通过** |
| **全仓 reactor 编译（31/31 模块）** | **BUILD SUCCESS** |
| **全仓测试** | **703 tests, 0 failures, 0 errors** |

新增回归用例 `fallbackPassesResolvedEngineNameToProvider`：配置 `duckdb`（未注册）时，捕获 Provider 收到的 `context.engine()`，断言为 `h2` 而非 `duckdb`。

> 验证插曲：首次全仓测试在 `module-feishu-core` 出现 88 例 `Could not initialize plugin: MockMaker`
> （根因 `Could not initialize inline Byte Buddy mock maker`）。排查确认是**宿主机内存耗尽**所致——
> 32GB 物理内存仅剩 1.6GB、页面文件提交上限触顶，ByteBuddy 自附加（self-attach）拿不到 JVM 资源。
> 待内存释放后原命令重跑即 **697/697 全绿**，与本次改造无关。


---

## 6. 收益与后续

**收益**

1. 通用能力归位：存储 SPI 落在 `agent-datasource`，任何模块可（零重依赖地）复用；
2. 破环完成：`module-datasource-core` 不再依赖 `framework-ai`；
3. 路由逻辑单点：回退/告警/异常语义只有一份 `StorageRouter`；
4. 缺陷 D5 根治；
5. 消费方依赖零膨胀：新模块只有 slf4j-api。

**改动文件清单**

| 文件 | 动作 |
| --- | --- |
| `modules/agent-datasource/module-datasource-storage/pom.xml` | 新增 |
| `…/ds/storage/StorageContext.java` | 新增 |
| `…/ds/storage/StorageProvider.java` | 新增 |
| `…/ds/storage/StorageRouter.java` | 新增 |
| `modules/agent-datasource/pom.xml` | 注册子模块 |
| `pom.xml`（根） | dependencyManagement 增补 |
| `agent-memory-core/…/storage/spi/StorageContext.java` | 删除 |
| `agent-memory-core/…/storage/spi/OltpStorageProvider.java` | 删除 |
| `agent-memory-core/…/storage/spi/OlapStorageProvider.java` | 删除 |
| `agent-memory-core/…/storage/spi/H2OltpStorageProvider.java` | 改实现通用 SPI |
| `agent-memory-core/…/storage/spi/ArrowOlapStorageProvider.java` | 改实现通用 SPI |
| `agent-memory-core/…/storage/MemoryStorageFactory.java` | **删除**（路由壳，职责并入 StorageRouter） |
| `agent-memory-core/…/autoconfig/AgentMemoryAutoConfiguration.java` | Bean 泛型化 + 直调 StorageRouter |
| `agent-memory-core/…/autoconfig/AgentMemoryProperties.java` | 收编 `DEFAULT_OLTP/OLAP_ENGINE` 常量，消除字面量重复 |
| `agent-memory-core/src/test/…/MemoryStorageFactoryTest.java` | 更名 `StorageRoutingTest.java`，改测 StorageRouter（4→6 例） |
| `agent-memory-core/pom.xml` | 新增依赖 |
| `framework/framework-ai/…/skill/AiSkill.java`、`AiSkillResult.java` | 迁至 framework-common |

**后续可做**

- `module-datasource-core` 内若也存在多引擎路由需求，可直接复用同一 SPI；
- `StorageRouter` 目前是静态工具类，若未来需要「运行时动态注册/热切换引擎」可改为实例 Bean（持有注册表 + 支持 reload）；
- agent-memory 为独立 git 仓库，本次改动需在该仓库单独提交（remote `rottenmu/agent-memory`，SSH 443）。

