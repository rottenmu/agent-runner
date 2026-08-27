# AI 模型配置 MyBatis-Plus 迁移设计

## 背景

`module-ai` 中的智能体、技能和提示词模板已经使用 MyBatis-Plus，并由用户手工执行 MySQL 建表 SQL。模型配置目前仍使用 `JdbcTemplate`、手写 SQL Repository 和启动时自动建表，导致同一模块存在两套持久化方案。

本次改造将模型配置统一迁移到 MyBatis-Plus，并删除模块内所有模型配置自动建表行为。已有管理接口、业务行为和数据库表名保持不变。

## 目标

- 模型配置使用 MyBatis-Plus `BaseMapper` 持久化。
- 保留现有 `AiModelConfigRepository` 接口，避免 Controller 和 Service 感知 ORM 变化。
- 删除 `JdbcAiModelConfigRepository` 和 `AiModelConfigSchemaInitializer`。
- 删除模型配置专用的 `JdbcOperations`、`JdbcTemplate` Bean。
- 删除 `module-ai-core` 不再使用的 `spring-jdbc` 依赖。
- `ai_model_config` 由用户手工执行 MySQL SQL 创建或升级。
- 保持 `/api/biz/ai/model-configs/**` 接口及响应结构不变。

## 非目标

- 不调整模型配置页面交互。
- 不修改模型连接测试协议。
- 不修改 API Key 的现有存储和响应脱敏规则。
- 不调整智能体、技能和提示词模板持久化代码。
- 不引入 Flyway、Liquibase、H2 或 SQLite。

## 方案

### Mapper

新增 `AiModelConfigMapper`，继承：

```java
BaseMapper<AiModelConfigEntity>
```

`AiModelConfigEntity` 继续映射 `ai_model_config`，自增主键和 `deleted` 逻辑删除字段沿用现有 MyBatis-Plus 注解。`tags` 使用 `JacksonTypeHandler` 完成 MySQL JSON 与 `List<String>` 的转换，并为实体启用 `autoResultMap`。

### Repository

新增 `MybatisPlusAiModelConfigRepository`，实现现有 `AiModelConfigRepository`：

- 列表查询使用 `LambdaQueryWrapper`。
- 支持 `env`、`provider`、启停状态和关键词筛选。
- 列表按 `updated_at DESC, id DESC` 排序。
- 新增使用 `BaseMapper.insert`。
- 编辑使用 `BaseMapper.updateById`。
- 删除使用 `BaseMapper.deleteById`，由 `@TableLogic` 转换为逻辑删除。
- 连接测试结果使用 MyBatis-Plus Update Wrapper 更新。
- `tags` 由 MyBatis-Plus `JacksonTypeHandler` 在 JSON 和 `List<String>` 之间转换。

Repository 继续负责数据库实体读写，Service 继续负责业务校验、API Key 脱敏、复制、导入导出和连接测试。

### 自动配置

`AiModelConfigAutoConfiguration` 保持独立：

- 保留 `plugin.ai.enabled` 条件。
- 保留数据源存在条件。
- 删除 `JdbcOperations` Bean。
- 删除 `AiModelConfigSchemaInitializer` Bean。
- 注入 `AiModelConfigMapper` 和 `ObjectMapper` 创建 MyBatis-Plus Repository。
- Service 和 Controller 注册方式保持不变。

Mapper 扫描范围将覆盖模型配置 Mapper，避免依赖手工 `@Mapper` 注册。

### 数据库管理

应用启动时不再创建或修改 `ai_model_config`。部署前必须由用户手工执行 MySQL 建表 SQL。

本次不改变表名和字段定义，因此已有 `ai_model_config` 数据无需迁移。若表尚不存在，需要先执行交付的完整建表 SQL。

## 错误处理

- 表不存在、字段不匹配等数据库异常由现有全局异常处理链处理。
- Repository 不吞掉 MyBatis-Plus 数据访问异常。
- 查询不存在的记录仍由 Service 转换为现有 `BizException`。
- API Key JSON 或标签解析异常继续沿用当前业务语义。

## 测试

采用测试驱动方式实施：

1. 新增 Mapper 契约测试，先验证缺少 `AiModelConfigMapper`。
2. 新增 MyBatis-Plus Repository 测试，覆盖查询条件、新增、编辑、逻辑删除和测试结果更新。
3. 修改自动配置测试，先验证不再注册 JDBC 和初始化器 Bean。
4. 新增源码契约测试，防止模型配置自动建表器被重新引入。
5. 运行 `module-ai-core`、`module-ai-autoconfig` 及其依赖测试。
6. 运行 AI 前端静态测试，确认接口契约未变化。

## 验收标准

- `module-ai` 中不存在 `JdbcAiModelConfigRepository`。
- `module-ai` 中不存在 `AiModelConfigSchemaInitializer`。
- `module-ai` 中不存在模型配置专用 `JdbcTemplate` 或 `JdbcOperations`。
- `module-ai-core` 不再依赖 `spring-jdbc`。
- `AiModelConfigMapper` 继承 `BaseMapper<AiModelConfigEntity>`。
- 模型配置 Repository 使用 MyBatis-Plus，并保持现有接口功能。
- 启动过程不执行 `CREATE TABLE IF NOT EXISTS ai_model_config`。
- 后端和前端相关测试全部通过。
- 向用户提供可手工执行的完整 MySQL 建表 SQL。
