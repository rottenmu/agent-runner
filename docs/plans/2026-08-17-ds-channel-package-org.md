# module-datasource / module-channel Package 划分方案

> 状态：待审核（审核通过后执行）
> 日期：2026-08-17

## 1. 背景与目标

延续 module-rag/module-tools 的分层治理，对 `module-datasource`（10 类）与
`module-channel`（17 类）做合理职责分包。原则同上轮：

- 只做目录结构（子包）调整，不迁移包名（com.zimo/com.zimo 不变）；
- 按实际职责建包，不建空包；无独立配置属性类时不留根包类；
- module-datasource 的 `skill/`、module-channel 的 `adapter/` 子包保留并完善。

## 2. 现状盘点

### module-datasource（com.zimo.module.ds）

| 类别 | 类 | 现状 |
|---|---|---|
| Controller | DsDataSourceController | 根包 |
| Entity | DsDataSource | 根包 |
| Mapper | DsDataSourceMapper | 根包 |
| Service | DsDataSourceService | 根包 |
| 连接器体系 | DsConnector（接口）/ DsConnectorFactory（工厂）/ DsConnectors（final 类，内含 7 个静态内部实现 Database/Erp/Document/Ocr/Web/Api/FileServer） | 根包 |
| Skill | skill/DsQuerySkill | 子包（已有） |
| autoconfig | DsAutoConfiguration / DsControllerScanAutoConfiguration | 子包（已有） |

### module-channel（com.zimo.module.channel）

| 类别 | 类 | 现状 |
|---|---|---|
| Controller | ChannelController | 根包 |
| Entity | ChanChannel / ChanConversation / ChanMessage | 根包 |
| Mapper | 对应 3 个 Mapper | 根包 |
| Service/路由 | ChannelService / ChannelRegistry / ChannelRouter / ConversationBridge | 根包 |
| 适配器 | ChannelAdapter（接口）/ DingtalkAdapter / HttpApiAdapter / QyRobotAdapter / WebhookChannelAdapter | 接口在根包，4 实现在 adapter/ |
| autoconfig | ChannelAutoConfiguration / ChannelControllerScanAutoConfiguration | 子包（已有） |

### module-feishu resources/module-feishu（用户引用）

仅 `application.yaml` 单文件（feishu 独立数据源配置，已在上轮治理中改为
SQLite 默认并消除明文凭据）。**纯配置资源、无 Java 代码，无需 package 划分**，
本次不动。

## 3. 目标结构

### module-datasource

```text
com/zimo/module/ds/
├── autoconfig/                  # 已有，不动
├── controller/
│   └── DsDataSourceController.java
├── entity/
│   └── DsDataSource.java
├── mapper/
│   └── DsDataSourceMapper.java
├── service/
│   └── DsDataSourceService.java
├── connector/                   # 连接器体系（接口 + 工厂 + 7 实现内聚）
│   ├── DsConnector.java  DsConnectorFactory.java  DsConnectors.java
└── skill/
    └── DsQuerySkill.java        # 已有，不动
```

### module-channel

```text
com/zimo/module/channel/
├── autoconfig/                  # 已有，不动
├── adapter/                     # 接口移入，与 4 实现内聚
│   ├── ChannelAdapter.java      # ← 从根包移入
│   ├── DingtalkAdapter.java  HttpApiAdapter.java
│   ├── QyRobotAdapter.java  WebhookChannelAdapter.java
├── controller/
│   └── ChannelController.java
├── entity/
│   ├── ChanChannel.java  ChanConversation.java  ChanMessage.java
├── mapper/
│   ├── ChanChannelMapper.java  ChanConversationMapper.java  ChanMessageMapper.java
└── service/
    ├── ChannelService.java  ChannelRegistry.java
    ├── ChannelRouter.java  ConversationBridge.java
```

## 4. 配套改动

| 文件 | 改动 |
|---|---|
| `DsAutoConfiguration` | `@MapperScan("com.zimo.module.ds")` → `"com.zimo.module.ds.mapper"`；@Bean 引用类补 import |
| `DsControllerScanAutoConfiguration` | `basePackages` → `com.zimo.module.ds.controller` |
| `ChannelAutoConfiguration` | `@MapperScan("com.zimo.module.channel")` → `"com.zimo.module.channel.mapper"`；import 更新 |
| `ChannelControllerScanAutoConfiguration` | `basePackages` → `com.zimo.module.channel.controller` |
| ChannelRegistry（引用 ChannelAdapter） | 补 `adapter.ChannelAdapter` import |

## 5. 风险与应对

| 风险 | 应对 |
|---|---|
| import 遗漏（同包引用移动后需补 import） | 沿用上轮脚本（先全局替换旧根包 import → 再补缺失），编译循环到绿 |
| 嵌套类误当顶层类（上轮踩坑） | 脚本只索引顶层类声明，人工复核 connector/ 内部实现 |
| MapperScan 漏改 → Mapper 不注册 | 明确列入配套改动清单 |

## 6. 验证清单

- [x] `./mvnw clean install -pl admin-shell -am` 编译通过
- [x] admin-shell 启动成功，无 Mapper/Bean 缺失
- [x] ds 接口回归：datasources / datasources/types 全部 200
- [x] channel 接口回归：types / channels 全部 200
- [x] 全量 `mvnw verify` BUILD SUCCESS
- [x] MCP 技能注册：24 工具（含 query_datasource）

## 7. 执行结果（2026-08-17 已实施）

### 完成内容

- **module-datasource**：8 个类从根包搬入
  `controller(1)/entity(1)/mapper(1)/service(1)/connector(3)`，根包清空；
  `connector/` 内聚连接器体系（DsConnector 接口 + DsConnectorFactory +
  DsConnectors 含 7 个内部实现）；
- **module-channel**：11 个类搬入
  `controller(1)/entity(3)/mapper(3)/service(4)`，**ChannelAdapter 接口移入
  `adapter/`** 与 4 实现内聚，根包清空；
- **module-feishu resources/module-feishu**：仅单文件 `application.yaml`
  （配置资源，无 Java 代码），未改动；
- **配套改动**：2 处 `@MapperScan` 收窄到 `.mapper`、2 处 controller 扫描
  收窄到 `.controller`；import 修复覆盖 18 个文件（脚本自动 + 手工修正）。

### 踩坑记录

- **嵌套类误补 import（复现上轮）**：`DsConnector.DsDataPreview` 是接口嵌套
  record，脚本正则误匹配生成不存在的 `connector.DsDataPreview` import →
  代码实际用 `DsConnector.DsDataPreview` 全限定名，删除误补 import 即解决；
- 两模块均无测试文件、无跨模块引用，重构面小；脚本流程
  （先替换旧根包 import → 再补缺失）已稳定复用。
