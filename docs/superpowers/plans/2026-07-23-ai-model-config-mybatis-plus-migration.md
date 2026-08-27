# AI 模型配置 MyBatis-Plus 迁移实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将模型配置从 Spring JDBC 和启动自动建表迁移到 MyBatis-Plus，并保持现有 API 与业务行为不变。

**Architecture:** `AiModelConfigService` 继续依赖既有 `AiModelConfigRepository` 接口；新增独立 Mapper 和 MyBatis-Plus Repository 替换 JDBC 实现。模型配置自动配置只组装 Mapper、Repository、Service 和 Controller，不再执行 DDL。

**Tech Stack:** Java 17、Spring Boot 3.4.5、MyBatis-Plus 3.5.9、MySQL、JUnit 5、Mockito、AssertJ。

## Global Constraints

- 仅使用 MySQL，不引入 SQLite、H2、Flyway 或 Liquibase。
- Controller 和 Service Bean 使用单一 public 构造器注入。
- `/api/biz/ai/model-configs/**` 接口与 `R<T>` 响应保持不变。
- `api_key` 存储和响应脱敏行为保持不变。
- `ai_model_config` 不由应用创建或升级，DDL 由用户手工执行。
- 不修改无关工作区文件。

---

### Task 1: 建立模型配置 Mapper 和 JSON 字段映射

**Files:**
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/mapper/AiModelConfigMapper.java`
- Modify: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigEntity.java`
- Modify: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/mapper/AiManagementMapperContractTest.java`

**Interfaces:**
- Consumes: `AiModelConfigEntity`
- Produces: `AiModelConfigMapper extends BaseMapper<AiModelConfigEntity>`

- [ ] **Step 1: 写失败的 Mapper 契约测试**

```java
assertThat(BaseMapper.class).isAssignableFrom(AiModelConfigMapper.class);
assertThat(AiModelConfigEntity.class.getAnnotation(TableName.class).autoResultMap()).isTrue();
TableField tags = AiModelConfigEntity.class.getDeclaredField("tags").getAnnotation(TableField.class);
assertThat(tags.typeHandler()).isEqualTo(JacksonTypeHandler.class);
assertThat(AiModelConfigEntity.class.getDeclaredField("deleted").isAnnotationPresent(TableLogic.class))
        .isTrue();
```

- [ ] **Step 2: 运行测试并确认失败**

```bash
mvn -pl modules/module-ai/module-ai-core -am -Dtest=AiManagementMapperContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，因为 `AiModelConfigMapper` 尚不存在且实体未启用 JSON TypeHandler。

- [ ] **Step 3: 实现 Mapper 和实体映射**

```java
package com.zimo.module.ai.modelconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import modelconfig.com.zimo.module.ai.AiModelConfigEntity;

public interface AiModelConfigMapper extends BaseMapper<AiModelConfigEntity> {
}
```

实体关键注解：

```java
@TableName(value = "ai_model_config", autoResultMap = true)
public class AiModelConfigEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
}
```

- [ ] **Step 4: 使用 Step 2 命令验证 PASS**

---

### Task 2: 用 MyBatis-Plus Repository 替换 JDBC Repository

**Files:**
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/MybatisPlusAiModelConfigRepository.java`
- Create: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/modelconfig/MybatisPlusAiModelConfigRepositoryTest.java`
- Delete: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/JdbcAiModelConfigRepository.java`
- Delete: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/modelconfig/JdbcAiModelConfigRepositoryTest.java`

**Interfaces:**
- Consumes: `AiModelConfigMapper`
- Produces: `AiModelConfigRepository` 的 MyBatis-Plus 实现

- [ ] **Step 1: 写失败的 Repository 测试**

```java
@Test
void findBuildsFiltersAndReturnsMapperRows() {
    AiModelConfigMapper mapper = mock(AiModelConfigMapper.class);
    when(mapper.selectList(any())).thenReturn(List.of(entity(1L)));

    List<AiModelConfigEntity> rows = repository(mapper)
            .find(new AiModelConfigQuery("prod", "bailian", "enabled", "qwen"));

    assertThat(rows).extracting(AiModelConfigEntity::getId).containsExactly(1L);
    verify(mapper).selectList(any());
}

@Test
void insertSetsTimestampsAndDelegatesToMapper() {
    AiModelConfigMapper mapper = mock(AiModelConfigMapper.class);
    AiModelConfigEntity entity = entity(null);

    repository(mapper).insert(entity);

    assertThat(entity.getCreatedAt()).isNotNull();
    assertThat(entity.getUpdatedAt()).isEqualTo(entity.getCreatedAt());
    verify(mapper).insert(entity);
}

@Test
void logicalDeleteUsesMybatisPlusLogicalDelete() {
    AiModelConfigMapper mapper = mock(AiModelConfigMapper.class);
    when(mapper.deleteById(9L)).thenReturn(1);

    assertThat(repository(mapper).logicalDelete(9L)).isTrue();
}
```

- [ ] **Step 2: 运行测试并确认失败**

```bash
mvn -pl modules/module-ai/module-ai-core -am -Dtest=MybatisPlusAiModelConfigRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，因为 `MybatisPlusAiModelConfigRepository` 尚不存在。

- [ ] **Step 3: 实现查询和写入**

```java
AiModelConfigQuery criteria = query == null
        ? new AiModelConfigQuery(null, null, null, null)
        : query;
LambdaQueryWrapper<AiModelConfigEntity> wrapper = Wrappers.lambdaQuery();
if (StringUtils.hasText(criteria.env())) {
    wrapper.eq(AiModelConfigEntity::getEnv, criteria.env().trim());
}
if (StringUtils.hasText(criteria.provider())) {
    wrapper.eq(AiModelConfigEntity::getProvider, criteria.provider().trim());
}
if ("enabled".equalsIgnoreCase(criteria.status())) {
    wrapper.eq(AiModelConfigEntity::isEnabled, true);
} else if ("disabled".equalsIgnoreCase(criteria.status())) {
    wrapper.eq(AiModelConfigEntity::isEnabled, false);
}
if (StringUtils.hasText(criteria.keyword())) {
    String keyword = criteria.keyword().trim();
    wrapper.and(condition -> condition
            .like(AiModelConfigEntity::getConfigName, keyword)
            .or().like(AiModelConfigEntity::getModelId, keyword)
            .or().like(AiModelConfigEntity::getDescription, keyword));
}
wrapper.orderByDesc(AiModelConfigEntity::getUpdatedAt)
        .orderByDesc(AiModelConfigEntity::getId);
return mapper.selectList(wrapper);
```

新增时补齐创建、更新时间后调用 `mapper.insert`；更新调用 `mapper.updateById`；删除调用 `mapper.deleteById`。测试结果使用 Lambda Update Wrapper：

```java
mapper.update(null, Wrappers.<AiModelConfigEntity>lambdaUpdate()
        .eq(AiModelConfigEntity::getId, id)
        .set(AiModelConfigEntity::getLastTestStatus, response.status())
        .set(AiModelConfigEntity::getLastTestLatency, response.latency())
        .set(AiModelConfigEntity::getLastTestMessage, response.message())
        .set(AiModelConfigEntity::getLastTestedAt, response.testedAt())
        .set(AiModelConfigEntity::getUpdatedAt, LocalDateTime.now()));
```

删除 JDBC 实现及对应测试。

- [ ] **Step 4: 验证 Repository 和 Service**

```bash
mvn -pl modules/module-ai/module-ai-core -am -Dtest=MybatisPlusAiModelConfigRepositoryTest,AiModelConfigServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS。

---

### Task 3: 移除自动建表与 JDBC 自动配置

**Files:**
- Modify: `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiModelConfigAutoConfiguration.java`
- Modify: `modules/module-ai/module-ai-autoconfig/src/test/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfigurationTest.java`
- Modify: `modules/module-ai/module-ai-autoconfig/src/test/java/com/xingju/module/ai/autoconfig/AiManagementSchemaOwnershipTest.java`
- Delete: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigSchemaInitializer.java`
- Delete: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/modelconfig/AiModelConfigSchemaInitializerTest.java`
- Modify: `modules/module-ai/module-ai-core/pom.xml`

**Interfaces:**
- Consumes: `AiModelConfigMapper`、`MybatisPlusAiModelConfigRepository`
- Produces: 不执行 DDL 的模型配置 Bean 装配

- [ ] **Step 1: 修改测试并确认旧实现下失败**

```java
@Test
void createsModelConfigBeansFromMybatisPlusMapper() {
    AiModelConfigAutoConfiguration configuration = new AiModelConfigAutoConfiguration();
    AiModelConfigMapper mapper = mock(AiModelConfigMapper.class);

    AiModelConfigRepository repository = configuration.aiModelConfigRepository(mapper);

    assertThat(repository).isInstanceOf(MybatisPlusAiModelConfigRepository.class);
    assertThat(configuration.aiModelConfigService(repository))
            .isInstanceOf(AiModelConfigService.class);
}
```

源码所有权测试同时断言：

```java
assertThat(modelInitializer).doesNotExist();
assertThat(Files.readString(modelAutoConfiguration))
        .doesNotContain("JdbcTemplate")
        .doesNotContain("JdbcOperations")
        .doesNotContain("initMethod = \"initialize\"");
```

- [ ] **Step 2: 运行测试并确认失败**

```bash
mvn -pl modules/module-ai/module-ai-autoconfig -am -Dtest=AiManagementSchemaOwnershipTest,AiModuleAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，因为 JDBC Bean 和模型初始化器仍存在。

- [ ] **Step 3: 修改自动配置并清理依赖**

```java
@AutoConfiguration(after = AiModuleAutoConfiguration.class)
@ConditionalOnProperty(prefix = "plugin.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(DataSource.class)
@MapperScan(basePackageClasses = AiModelConfigMapper.class)
public class AiModelConfigAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AiModelConfigRepository.class)
    public AiModelConfigRepository aiModelConfigRepository(AiModelConfigMapper mapper) {
        return new MybatisPlusAiModelConfigRepository(mapper);
    }
}
```

保留现有 Service、Controller Bean，删除 JDBC 和初始化器 Bean。删除初始化器类、测试，以及 `module-ai-core/pom.xml` 中的 `spring-jdbc` 依赖。

- [ ] **Step 4: 使用 Step 2 命令验证 PASS**

---

### Task 4: 完整验证与 SQL 交付

**Files:**
- Verify: `modules/module-ai/**`
- Verify: `frontend/modules/ai/**`

**Interfaces:**
- Consumes: 完整模型配置 MyBatis-Plus 实现
- Produces: 验证结果和手工 MySQL 建表 SQL

- [ ] **Step 1: 运行后端测试**

```bash
mvn -pl modules/module-ai/module-ai-autoconfig -am test
```

Expected: BUILD SUCCESS。

- [ ] **Step 2: 运行 AI 前端测试**

```bash
node --test frontend/modules/ai/tests/*.test.mjs
```

Expected: 全部 PASS。

- [ ] **Step 3: 检查残留**

```bash
rg -n "JdbcAiModelConfigRepository|AiModelConfigSchemaInitializer|JdbcTemplate|JdbcOperations|CREATE TABLE IF NOT EXISTS ai_model_config" modules/module-ai --glob "!**/target/**"
```

Expected: 无匹配。

- [ ] **Step 4: 检查格式与工作区**

```bash
git diff --check
git status --short
```

Expected: 无空白错误；不修改或回滚无关文件。

- [ ] **Step 5: 交付完整 `ai_model_config` MySQL 建表 SQL**

SQL 包含自增主键、模型与推理参数、JSON `tags`、连接测试结果、逻辑删除、时间字段，以及 `(env, provider)`、`enabled`、`(deleted, updated_at)` 索引。
