# AI 模型配置管理后端与前端对接 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `module-ai-core` 新增 MySQL 持久化模型配置管理后端，并把 AI 前端模型配置页面改为后端 API 驱动。

**Architecture:** 后端在 `module-ai-core` 新增 `modelconfig` 包，按 Entity/Query/Request/Response/Repository/Service/SchemaInitializer/Controller 分层；`module-ai-autoconfig` 根据 `plugin.ai.enabled=true` 和 `DataSource` 自动注册 JDBC、Schema、Repository、Service、Controller。前端新增 `src/api/model-config.js`，页面继续保留模板和交互，但列表、保存、删除、复制、测试、导入、导出均调用 `/biz/ai/model-configs`。

**Tech Stack:** Java 17、Spring Boot 3.4.5、Spring JDBC、MySQL、JUnit 5、Mockito、MockMvc、Vue 3、Element Plus、Axios、Node.js `node:test`、Vite。

## Global Constraints

- 设计依据：`docs/superpowers/specs/2026-07-23-ai-model-config-backend-frontend-design.md`。
- 后端真实路径固定为 `/api/biz/ai/model-configs`。
- 前端调用路径固定为 `/biz/ai/model-configs`，因为 `frontend/web-shell/src/api/request.js` 的 `baseURL` 是 `/api`。
- MySQL 表名固定为 `ai_model_config`。
- 使用 `CREATE TABLE IF NOT EXISTS ai_model_config`，启动时只确保表存在，不覆盖已有数据。
- 不使用 SQLite、H2 或 Flyway。
- 不修改前端开发端口 `15200`。
- 不创建独立前端应用。
- 所有响应和导出都不得返回 `apiKey` 明文，只返回 `apiKeyMasked`。
- 编辑接口中 `apiKey` 为空或 `***` 时保留原密钥。
- 本期连接测试做后端模拟校验，不真实调用阿里云百炼。
- 后端 Service、Controller 使用普通 public 构造器注入，每个 Bean 类只保留一个 public 构造器。
- 后端 Java 注释遵守 `docs/rules/BACKEND_JAVA_COMMENT_RULES.md`。
- 代码行数遵守 `docs/rules/CODE_SIZE_RULES.md`。
- 不编辑 `target/`、`dist/` 等生成产物。

---

## File Structure

- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigEntity.java`
  - 持久化实体，包含数据库字段和 getter/setter。
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigQuery.java`
  - 查询条件 record：`env`、`provider`、`status`、`keyword`。
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigRequest.java`
  - 新建/编辑请求体。
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigResponse.java`
  - 前端响应体，不包含 `apiKey` 明文。
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigTestResponse.java`
  - 测试连接响应。
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigImportRequest.java`
  - 批量导入请求。
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigImportResponse.java`
  - 批量导入结果。
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigRepository.java`
  - JDBC Repository，构造器接收 `JdbcOperations`。
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigService.java`
  - 业务服务，封装校验、脱敏、密钥保留、复制、测试、导入导出。
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigSchemaInitializer.java`
  - MySQL Schema 初始化器。
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/controller/AiModelConfigController.java`
  - REST Controller。
- Modify: `modules/module-ai/module-ai-core/pom.xml`
  - 增加 `spring-jdbc` 依赖。
- Create: `modules/module-ai/module-ai-core/API_MODEL_CONFIG_SPEC.md`
  - 中文 API 文档。
- Create: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/modelconfig/AiModelConfigServiceTest.java`
- Create: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/modelconfig/AiModelConfigSchemaInitializerTest.java`
- Create: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/modelconfig/AiModelConfigRepositoryTest.java`
- Create: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/controller/AiModelConfigControllerTest.java`
- Create: `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiModelConfigAutoConfiguration.java`
- Modify: `modules/module-ai/module-ai-autoconfig/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `modules/module-ai/module-ai-autoconfig/src/test/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfigurationTest.java`
- Create: `frontend/modules/ai/src/api/model-config.js`
- Modify: `frontend/modules/ai/src/views/AiModelConfigManage.vue`
- Modify: `frontend/modules/ai/tests/model-config-static.test.mjs`

---

### Task 1: 后端 Service 契约测试

**Files:**
- Create: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/modelconfig/AiModelConfigServiceTest.java`

**Interfaces:**
- Consumes: planned `AiModelConfigService`, `AiModelConfigRepository`, DTO classes.
- Produces: Service 业务规则红灯测试，后续实现必须满足这些契约。

- [ ] **Step 1: Write the failing service tests**

Create `AiModelConfigServiceTest.java` with these test cases:

```java
package com.zimo.module.ai.modelconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zimo.framework.common.BizException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiModelConfigServiceTest {

    @Test
    void 创建配置时校验必填字段并保存后返回脱敏密钥() {
        RecordingRepository repository = new RecordingRepository();
        AiModelConfigService service = new AiModelConfigService(repository);
        AiModelConfigRequest request = validRequest("Qwen Plus", "sk-1234567890");

        AiModelConfigResponse response = service.create(request);

        assertThat(repository.saved).hasSize(1);
        assertThat(repository.saved.get(0).getApiKey()).isEqualTo("sk-1234567890");
        assertThat(response.configName()).isEqualTo("Qwen Plus");
        assertThat(response.apiKeyMasked()).isEqualTo("sk-1****7890");
    }

    @Test
    void 创建配置时缺少密钥返回400业务异常() {
        AiModelConfigService service = new AiModelConfigService(new RecordingRepository());
        AiModelConfigRequest request = validRequest("Qwen Plus", "");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BizException.class)
                .hasMessage("API Key 必填")
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    void 编辑配置时空密钥保留原密钥() {
        RecordingRepository repository = new RecordingRepository();
        repository.rows.add(entity(9L, "旧配置", "sk-original-key"));
        AiModelConfigService service = new AiModelConfigService(repository);
        AiModelConfigRequest request = validRequest("新配置", "");

        AiModelConfigResponse response = service.update(9L, request);

        assertThat(repository.updated.get(0).getApiKey()).isEqualTo("sk-original-key");
        assertThat(response.configName()).isEqualTo("新配置");
        assertThat(response.apiKeyMasked()).isEqualTo("sk-o****-key");
    }

    @Test
    void 编辑配置时三星密钥占位符保留原密钥() {
        RecordingRepository repository = new RecordingRepository();
        repository.rows.add(entity(9L, "旧配置", "sk-original-key"));
        AiModelConfigService service = new AiModelConfigService(repository);
        AiModelConfigRequest request = validRequest("新配置", "***");

        service.update(9L, request);

        assertThat(repository.updated.get(0).getApiKey()).isEqualTo("sk-original-key");
    }

    @Test
    void 复制配置时重置测试结果并追加副本名称() {
        RecordingRepository repository = new RecordingRepository();
        AiModelConfigEntity source = entity(7L, "主配置", "sk-source-key");
        source.setLastTestStatus("success");
        source.setLastTestLatency(88);
        repository.rows.add(source);
        AiModelConfigService service = new AiModelConfigService(repository);

        AiModelConfigResponse response = service.copy(7L);

        assertThat(repository.saved.get(0).getConfigName()).isEqualTo("主配置 副本");
        assertThat(repository.saved.get(0).getLastTestStatus()).isEqualTo("untested");
        assertThat(response.lastTestStatus()).isEqualTo("untested");
    }

    @Test
    void 测试连接时更新测试状态() {
        RecordingRepository repository = new RecordingRepository();
        repository.rows.add(entity(11L, "测试配置", "sk-test-key"));
        AiModelConfigService service = new AiModelConfigService(repository);

        AiModelConfigTestResponse response = service.testConnection(11L);

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.latency()).isGreaterThan(0);
        assertThat(repository.testUpdatedIds).containsExactly(11L);
    }

    @Test
    void 导出配置时仍然不返回明文密钥() {
        RecordingRepository repository = new RecordingRepository();
        repository.rows.add(entity(1L, "导出配置", "sk-export-key"));
        AiModelConfigService service = new AiModelConfigService(repository);

        List<AiModelConfigResponse> exported = service.export(new AiModelConfigQuery("dev", null, null, null));

        assertThat(exported).hasSize(1);
        assertThat(exported.get(0).apiKeyMasked()).isEqualTo("sk-e****-key");
    }

    private static AiModelConfigRequest validRequest(String name, String apiKey) {
        return new AiModelConfigRequest(name, "描述", "bailian",
                "https://dashscope.aliyuncs.com/compatible-mode/v1", apiKey, "qwen-plus",
                "dev", true, new BigDecimal("0.70"), new BigDecimal("0.80"),
                4096, List.of("百炼"));
    }

    private static AiModelConfigEntity entity(Long id, String name, String apiKey) {
        AiModelConfigEntity entity = new AiModelConfigEntity();
        entity.setId(id);
        entity.setConfigName(name);
        entity.setDescription("描述");
        entity.setProvider("bailian");
        entity.setEndpoint("https://dashscope.aliyuncs.com/compatible-mode/v1");
        entity.setApiKey(apiKey);
        entity.setModelId("qwen-plus");
        entity.setEnv("dev");
        entity.setEnabled(true);
        entity.setTemperature(new BigDecimal("0.70"));
        entity.setTopP(new BigDecimal("0.80"));
        entity.setMaxTokens(4096);
        entity.setTags(List.of("百炼"));
        entity.setLastTestStatus("untested");
        entity.setLastTestLatency(0);
        entity.setLastTestMessage("尚未测试");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private static final class RecordingRepository implements AiModelConfigRepository {
        private final List<AiModelConfigEntity> rows = new ArrayList<>();
        private final List<AiModelConfigEntity> saved = new ArrayList<>();
        private final List<AiModelConfigEntity> updated = new ArrayList<>();
        private final List<Long> testUpdatedIds = new ArrayList<>();

        @Override
        public List<AiModelConfigEntity> find(AiModelConfigQuery query) {
            return rows;
        }

        @Override
        public Optional<AiModelConfigEntity> findById(long id) {
            return rows.stream().filter(row -> row.getId() == id).findFirst();
        }

        @Override
        public AiModelConfigEntity insert(AiModelConfigEntity entity) {
            entity.setId((long) (rows.size() + saved.size() + 1));
            saved.add(entity);
            rows.add(entity);
            return entity;
        }

        @Override
        public AiModelConfigEntity update(AiModelConfigEntity entity) {
            updated.add(entity);
            return entity;
        }

        @Override
        public boolean logicalDelete(long id) {
            return rows.removeIf(row -> row.getId() == id);
        }

        @Override
        public void updateTestResult(long id, AiModelConfigTestResponse response) {
            testUpdatedIds.add(id);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl modules/module-ai/module-ai-core -Dtest=AiModelConfigServiceTest test
```

Expected: FAIL because `AiModelConfigService`, DTOs, and Repository contract do not exist.

---

### Task 2: 后端 DTO、Entity、Service 实现

**Files:**
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigEntity.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigQuery.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigRequest.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigResponse.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigTestResponse.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigImportRequest.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigImportResponse.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigRepository.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigService.java`
- Test: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/modelconfig/AiModelConfigServiceTest.java`

**Interfaces:**
- Consumes: Task 1 tests.
- Produces:
  - `AiModelConfigService(AiModelConfigRepository repository)`
  - `List<AiModelConfigResponse> list(AiModelConfigQuery query)`
  - `AiModelConfigResponse get(long id)`
  - `AiModelConfigResponse create(AiModelConfigRequest request)`
  - `AiModelConfigResponse update(long id, AiModelConfigRequest request)`
  - `void delete(long id)`
  - `AiModelConfigResponse copy(long id)`
  - `AiModelConfigTestResponse testConnection(long id)`
  - `AiModelConfigImportResponse importConfigs(AiModelConfigImportRequest request)`
  - `List<AiModelConfigResponse> export(AiModelConfigQuery query)`

- [ ] **Step 1: Implement DTO and repository contract**

Create the DTO records with these exact component names:

```java
public record AiModelConfigQuery(String env, String provider, String status, String keyword) {}

public record AiModelConfigRequest(
        String configName,
        String description,
        String provider,
        String endpoint,
        String apiKey,
        String modelId,
        String env,
        Boolean enabled,
        BigDecimal temperature,
        BigDecimal topP,
        Integer maxTokens,
        List<String> tags) {}

public record AiModelConfigResponse(
        Long id,
        String configName,
        String description,
        String provider,
        String endpoint,
        String apiKeyMasked,
        String modelId,
        String env,
        boolean enabled,
        BigDecimal temperature,
        BigDecimal topP,
        int maxTokens,
        List<String> tags,
        String lastTestStatus,
        int lastTestLatency,
        String lastTestMessage,
        LocalDateTime lastTestedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}

public record AiModelConfigTestResponse(
        String status,
        int latency,
        String message,
        LocalDateTime testedAt) {}

public record AiModelConfigImportRequest(List<AiModelConfigRequest> configs) {}

public record AiModelConfigImportResponse(int successCount, int skippedCount) {}
```

Create `AiModelConfigRepository` as an interface so tests can use an in-memory fake:

```java
public interface AiModelConfigRepository {
    List<AiModelConfigEntity> find(AiModelConfigQuery query);
    Optional<AiModelConfigEntity> findById(long id);
    AiModelConfigEntity insert(AiModelConfigEntity entity);
    AiModelConfigEntity update(AiModelConfigEntity entity);
    boolean logicalDelete(long id);
    void updateTestResult(long id, AiModelConfigTestResponse response);
}
```

- [ ] **Step 2: Implement entity**

Create `AiModelConfigEntity` as a plain JavaBean with fields matching the database columns:

```java
private Long id;
private String configName;
private String description;
private String provider;
private String endpoint;
private String apiKey;
private String modelId;
private String env;
private boolean enabled;
private BigDecimal temperature;
private BigDecimal topP;
private int maxTokens;
private List<String> tags = List.of();
private String lastTestStatus;
private int lastTestLatency;
private String lastTestMessage;
private LocalDateTime lastTestedAt;
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
```

Add public getters and setters for every field. Keep the class as data-only; do not add service behavior to the entity.

- [ ] **Step 3: Implement service business rules**

Create `AiModelConfigService` with one public constructor:

```java
public AiModelConfigService(AiModelConfigRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
}
```

Implement these helper rules exactly:

```java
private String maskApiKey(String value) {
    if (!StringUtils.hasText(value)) {
        return "";
    }
    if (value.length() <= 8) {
        return "***";
    }
    return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
}

private boolean shouldKeepOriginalApiKey(String apiKey) {
    return !StringUtils.hasText(apiKey) || "***".equals(apiKey.trim());
}

private BizException badRequest(String message) {
    return new BizException(400, message);
}

private BizException notFound() {
    return new BizException(404, "模型配置不存在");
}
```

Validation must enforce:

```java
configName required, <= 128
provider one of bailian/custom
endpoint required, <= 512
apiKey required on create
modelId required, <= 128
env one of dev/staging/prod
temperature 0..1
topP 0..1
maxTokens 256..8192
tags size <= 20 and each tag length <= 32
```

Mapping to response must never expose raw `apiKey`; only set `apiKeyMasked`.

- [ ] **Step 4: Run service tests**

Run:

```bash
mvn -pl modules/module-ai/module-ai-core -Dtest=AiModelConfigServiceTest test
```

Expected: PASS.

---

### Task 3: Schema 初始化与 JDBC Repository

**Files:**
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/AiModelConfigSchemaInitializer.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/modelconfig/JdbcAiModelConfigRepository.java`
- Create: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/modelconfig/AiModelConfigSchemaInitializerTest.java`
- Create: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/modelconfig/AiModelConfigRepositoryTest.java`
- Modify: `modules/module-ai/module-ai-core/pom.xml`

**Interfaces:**
- Consumes: `AiModelConfigRepository`, `AiModelConfigEntity`, DTOs from Task 2.
- Produces:
  - `AiModelConfigSchemaInitializer(JdbcOperations jdbcOperations)`
  - `void initialize()`
  - `JdbcAiModelConfigRepository(JdbcOperations jdbcOperations, ObjectMapper objectMapper)`

- [ ] **Step 1: Add Spring JDBC dependency**

Modify `module-ai-core/pom.xml` dependencies:

```xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-jdbc</artifactId>
</dependency>
```

- [ ] **Step 2: Write schema initializer test**

Create `AiModelConfigSchemaInitializerTest.java`:

```java
class AiModelConfigSchemaInitializerTest {

    @Test
    void initializeCreatesMysqlModelConfigTableIfMissing() {
        RecordingJdbcOperations jdbc = new RecordingJdbcOperations();
        AiModelConfigSchemaInitializer initializer = new AiModelConfigSchemaInitializer(jdbc);

        initializer.initialize();

        assertThat(jdbc.sql).contains("CREATE TABLE IF NOT EXISTS ai_model_config");
        assertThat(jdbc.sql).contains("api_key VARCHAR(1024)");
        assertThat(jdbc.sql).contains("tags_json JSON");
        assertThat(jdbc.sql).contains("is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0");
    }
}
```

Use Mockito for this test:

```java
JdbcOperations jdbc = mock(JdbcOperations.class);
new AiModelConfigSchemaInitializer(jdbc).initialize();
verify(jdbc).execute(argThat(sql -> sql.contains("CREATE TABLE IF NOT EXISTS ai_model_config")));
```

- [ ] **Step 3: Implement schema initializer**

Create `AiModelConfigSchemaInitializer`:

```java
public class AiModelConfigSchemaInitializer {
    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS ai_model_config (
              id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
              config_name VARCHAR(128) NOT NULL COMMENT '配置名称',
              description VARCHAR(512) NOT NULL DEFAULT '' COMMENT '描述',
              provider VARCHAR(32) NOT NULL COMMENT '提供商',
              endpoint VARCHAR(512) NOT NULL COMMENT 'API Endpoint',
              api_key VARCHAR(1024) NOT NULL COMMENT 'API Key',
              model_id VARCHAR(128) NOT NULL COMMENT '模型 ID',
              env VARCHAR(32) NOT NULL COMMENT '环境',
              enabled TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否启用',
              temperature DECIMAL(4,2) NOT NULL DEFAULT 0.70 COMMENT '温度参数',
              top_p DECIMAL(4,2) NOT NULL DEFAULT 0.80 COMMENT 'Top P 参数',
              max_tokens INT NOT NULL DEFAULT 4096 COMMENT '最大 token 数',
              tags_json JSON DEFAULT NULL COMMENT '标签数组',
              last_test_status VARCHAR(32) NOT NULL DEFAULT 'untested' COMMENT '测试状态',
              last_test_latency INT NOT NULL DEFAULT 0 COMMENT '测试耗时毫秒',
              last_test_message VARCHAR(512) NOT NULL DEFAULT '尚未测试' COMMENT '测试结果说明',
              last_tested_at DATETIME DEFAULT NULL COMMENT '最近测试时间',
              is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除',
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
              PRIMARY KEY (id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 模型配置表'
            """;
}
```

- [ ] **Step 4: Write repository contract tests**

Create `AiModelConfigRepositoryTest.java` with Mockito verification:

```java
@Test
void findQueryAlwaysFiltersLogicalDeletedRows() {
    JdbcOperations jdbc = mock(JdbcOperations.class);
    ObjectMapper objectMapper = new ObjectMapper();
    JdbcAiModelConfigRepository repository = new JdbcAiModelConfigRepository(jdbc, objectMapper);
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

    repository.find(new AiModelConfigQuery("dev", "bailian", "enabled", "qwen"));

    verify(jdbc).query(argThat(sql ->
            sql.contains("FROM ai_model_config")
                    && sql.contains("is_deleted = 0")
                    && sql.contains("config_name LIKE ?")
                    && sql.contains("model_id LIKE ?")),
            any(RowMapper.class),
            any(Object[].class));
}

@Test
void logicalDeleteUpdatesDeleteFlagOnly() {
    JdbcOperations jdbc = mock(JdbcOperations.class);
    when(jdbc.update(anyString(), eq(8L))).thenReturn(1);
    JdbcAiModelConfigRepository repository = new JdbcAiModelConfigRepository(jdbc, new ObjectMapper());

    assertThat(repository.logicalDelete(8L)).isTrue();

    verify(jdbc).update(argThat(sql -> sql.contains("SET is_deleted = 1")), eq(8L));
}
```

- [ ] **Step 5: Implement `JdbcAiModelConfigRepository`**

Implement SQL methods:

```java
find(AiModelConfigQuery query)
findById(long id)
insert(AiModelConfigEntity entity)
update(AiModelConfigEntity entity)
logicalDelete(long id)
updateTestResult(long id, AiModelConfigTestResponse response)
```

Rules:

- All reads include `is_deleted = 0`.
- `status=enabled` maps to `enabled = 1`.
- `status=disabled` maps to `enabled = 0`.
- Keyword filters `(config_name LIKE ? OR model_id LIKE ?)`.
- Tags are serialized with `ObjectMapper.writeValueAsString(tags)`.
- Invalid tags JSON maps to `List.of()`.

- [ ] **Step 6: Run repository and schema tests**

Run:

```bash
mvn -pl modules/module-ai/module-ai-core -Dtest=AiModelConfigSchemaInitializerTest,AiModelConfigRepositoryTest test
```

Expected: PASS.

---

### Task 4: Controller 与 API 文档

**Files:**
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/controller/AiModelConfigController.java`
- Create: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/controller/AiModelConfigControllerTest.java`
- Create: `modules/module-ai/module-ai-core/API_MODEL_CONFIG_SPEC.md`

**Interfaces:**
- Consumes: `AiModelConfigService` methods from Task 2.
- Produces: REST API contract and API documentation.

- [ ] **Step 1: Write controller tests**

Create `AiModelConfigControllerTest.java` with MockMvc standalone setup:

```java
@Test
void 查询配置列表返回统一响应体() throws Exception {
    AiModelConfigService service = mock(AiModelConfigService.class);
    when(service.list(any(AiModelConfigQuery.class))).thenReturn(List.of(response(1L)));

    mockMvc(service).perform(get("/api/biz/ai/model-configs")
                    .param("env", "dev")
                    .param("provider", "bailian")
                    .param("status", "enabled")
                    .param("keyword", "qwen"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.msg").value("success"))
            .andExpect(jsonPath("$.data[0].apiKeyMasked").value("sk-a****-key"));

    verify(service).list(new AiModelConfigQuery("dev", "bailian", "enabled", "qwen"));
}

@Test
void 创建配置转调Service() throws Exception {
    AiModelConfigService service = mock(AiModelConfigService.class);
    when(service.create(any(AiModelConfigRequest.class))).thenReturn(response(2L));

    mockMvc(service).perform(post("/api/biz/ai/model-configs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(2));

    verify(service).create(any(AiModelConfigRequest.class));
}

@Test
void 声明平台路径契约() {
    RequestMapping mapping = AiModelConfigController.class.getAnnotation(RequestMapping.class);
    assertThat(mapping.value()).containsExactly("/api/biz/ai/model-configs");
}
```

Add these companion tests in the same file:

```java
@Test
void 编辑配置转调Service() throws Exception {
    AiModelConfigService service = mock(AiModelConfigService.class);
    when(service.update(eq(3L), any(AiModelConfigRequest.class))).thenReturn(response(3L));

    mockMvc(service).perform(put("/api/biz/ai/model-configs/3")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(3));

    verify(service).update(eq(3L), any(AiModelConfigRequest.class));
}

@Test
void 删除配置转调Service并返回空数据() throws Exception {
    AiModelConfigService service = mock(AiModelConfigService.class);

    mockMvc(service).perform(delete("/api/biz/ai/model-configs/4"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").isEmpty());

    verify(service).delete(4L);
}

@Test
void 复制配置转调Service() throws Exception {
    AiModelConfigService service = mock(AiModelConfigService.class);
    when(service.copy(5L)).thenReturn(response(5L));

    mockMvc(service).perform(post("/api/biz/ai/model-configs/5/copy"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(5));

    verify(service).copy(5L);
}

@Test
void 测试连接转调Service() throws Exception {
    AiModelConfigService service = mock(AiModelConfigService.class);
    when(service.testConnection(6L)).thenReturn(new AiModelConfigTestResponse(
            "success", 120, "模拟连接成功", LocalDateTime.parse("2026-07-23T12:00:00")));

    mockMvc(service).perform(post("/api/biz/ai/model-configs/6/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("success"));

    verify(service).testConnection(6L);
}

@Test
void 导入配置转调Service() throws Exception {
    AiModelConfigService service = mock(AiModelConfigService.class);
    when(service.importConfigs(any(AiModelConfigImportRequest.class)))
            .thenReturn(new AiModelConfigImportResponse(1, 0));

    mockMvc(service).perform(post("/api/biz/ai/model-configs/import")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"configs\":[" + validJson() + "]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.successCount").value(1));

    verify(service).importConfigs(any(AiModelConfigImportRequest.class));
}

@Test
void 导出配置转调Service() throws Exception {
    AiModelConfigService service = mock(AiModelConfigService.class);
    when(service.export(new AiModelConfigQuery("prod", null, null, null)))
            .thenReturn(List.of(response(7L)));

    mockMvc(service).perform(get("/api/biz/ai/model-configs/export").param("env", "prod"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(7));

    verify(service).export(new AiModelConfigQuery("prod", null, null, null));
}
```

- [ ] **Step 2: Implement controller**

Create `AiModelConfigController` with:

```java
@RestController
@RequestMapping("/api/biz/ai/model-configs")
public class AiModelConfigController {
    private final AiModelConfigService service;

    public AiModelConfigController(AiModelConfigService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @GetMapping
    public R<List<AiModelConfigResponse>> list(AiModelConfigQuery query) {
        return R.ok(service.list(query));
    }

    @GetMapping("/{id}")
    public R<AiModelConfigResponse> get(@PathVariable long id) {
        return R.ok(service.get(id));
    }

    @PostMapping
    public R<AiModelConfigResponse> create(@RequestBody AiModelConfigRequest request) {
        return R.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public R<AiModelConfigResponse> update(@PathVariable long id, @RequestBody AiModelConfigRequest request) {
        return R.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable long id) {
        service.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/copy")
    public R<AiModelConfigResponse> copy(@PathVariable long id) {
        return R.ok(service.copy(id));
    }

    @PostMapping("/{id}/test")
    public R<AiModelConfigTestResponse> test(@PathVariable long id) {
        return R.ok(service.testConnection(id));
    }

    @PostMapping("/import")
    public R<AiModelConfigImportResponse> importConfigs(@RequestBody AiModelConfigImportRequest request) {
        return R.ok(service.importConfigs(request));
    }

    @GetMapping("/export")
    public R<List<AiModelConfigResponse>> export(AiModelConfigQuery query) {
        return R.ok(service.export(query));
    }
}
```

- [ ] **Step 3: Generate API markdown documentation**

Create `modules/module-ai/module-ai-core/API_MODEL_CONFIG_SPEC.md` in Chinese. Include:

```markdown
# AI 模型配置管理 API 响应规范

## 统一返回体

成功响应：

```json
{
  "code": 200,
  "msg": "success",
  "data": {}
}
```

## 前端调用说明

后端真实路径以 `/api/biz/ai/model-configs` 开头。前端统一 request 的 baseURL 是 `/api`，因此前端调用路径以 `/biz/ai/model-configs` 开头。

## API Key 规则

- 请求可以提交 `apiKey`。
- 响应永不返回明文 `apiKey`。
- 编辑时 `apiKey` 为空或 `***` 表示保留原密钥。
```

Document every endpoint with method, path, request example, response example.

- [ ] **Step 4: Run controller tests**

Run:

```bash
mvn -pl modules/module-ai/module-ai-core -Dtest=AiModelConfigControllerTest test
```

Expected: PASS.

---

### Task 5: 自动装配集成

**Files:**
- Create: `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiModelConfigAutoConfiguration.java`
- Modify: `modules/module-ai/module-ai-autoconfig/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `modules/module-ai/module-ai-autoconfig/src/test/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfigurationTest.java`

**Interfaces:**
- Consumes: core classes from Tasks 2-4.
- Produces: model config beans registered when AI plugin and `DataSource` are available.

- [ ] **Step 1: Extend auto-configuration tests**

Add imports to `AiModuleAutoConfigurationTest`:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import controller.com.zimo.module.ai.AiModelConfigController;
import modelconfig.com.zimo.module.ai.AiModelConfigRepository;
import com.zimo.module.ai.modelconfig.AiModelConfigSchemaInitializer;
import modelconfig.com.zimo.module.ai.AiModelConfigService;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
```

Add `AiModelConfigAutoConfiguration.class` to the `AutoConfigurations.of(...)` list.

Add tests:

```java
@Test
void registersModelConfigBeansWhenDataSourceExists() {
    contextRunner
            .withBean(DataSource.class, DriverManagerDataSource::new)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .run(context -> {
                assertThat(context).hasBean("aiModelConfigJdbcOperations");
                assertThat(context).hasSingleBean(AiModelConfigSchemaInitializer.class);
                assertThat(context).hasSingleBean(AiModelConfigRepository.class);
                assertThat(context).hasSingleBean(AiModelConfigService.class);
                assertThat(context).hasSingleBean(AiModelConfigController.class);
            });
}

@Test
void skipsModelConfigBeansWhenDataSourceMissing() {
    contextRunner.run(context -> {
        assertThat(context).doesNotHaveBean(AiModelConfigSchemaInitializer.class);
        assertThat(context).doesNotHaveBean(AiModelConfigRepository.class);
        assertThat(context).doesNotHaveBean(AiModelConfigService.class);
        assertThat(context).doesNotHaveBean(AiModelConfigController.class);
    });
}
```

- [ ] **Step 2: Implement auto-configuration**

Create `AiModelConfigAutoConfiguration`:

```java
@AutoConfiguration
@ConditionalOnProperty(prefix = "plugin.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(DataSource.class)
public class AiModelConfigAutoConfiguration {

    @Bean(name = "aiModelConfigJdbcOperations")
    @ConditionalOnMissingBean
    public JdbcOperations aiModelConfigJdbcOperations(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(initMethod = "initialize")
    @ConditionalOnMissingBean
    public AiModelConfigSchemaInitializer aiModelConfigSchemaInitializer(
            @Qualifier("aiModelConfigJdbcOperations") JdbcOperations jdbcOperations) {
        return new AiModelConfigSchemaInitializer(jdbcOperations);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiModelConfigRepository aiModelConfigRepository(
            @Qualifier("aiModelConfigJdbcOperations") JdbcOperations jdbcOperations,
            ObjectMapper objectMapper) {
        return new JdbcAiModelConfigRepository(jdbcOperations, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiModelConfigService aiModelConfigService(AiModelConfigRepository repository) {
        return new AiModelConfigService(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiModelConfigController aiModelConfigController(AiModelConfigService service) {
        return new AiModelConfigController(service);
    }
}
```

- [ ] **Step 3: Register auto-configuration import**

Append this line to `AutoConfiguration.imports`:

```text
com.zimo.module.ai.autoconfig.AiModelConfigAutoConfiguration
```

- [ ] **Step 4: Run autoconfig tests**

Run:

```bash
mvn -pl modules/module-ai/module-ai-autoconfig -Dtest=AiModuleAutoConfigurationTest test
```

Expected: PASS.

---

### Task 6: 前端 API 客户端与静态契约

**Files:**
- Create: `frontend/modules/ai/src/api/model-config.js`
- Modify: `frontend/modules/ai/tests/model-config-static.test.mjs`

**Interfaces:**
- Consumes: backend endpoint paths from Tasks 4-5.
- Produces:
  - `listModelConfigs(params)`
  - `getModelConfig(id)`
  - `createModelConfig(data)`
  - `updateModelConfig(id, data)`
  - `deleteModelConfig(id)`
  - `copyModelConfig(id)`
  - `testModelConfig(id)`
  - `importModelConfigs(data)`
  - `exportModelConfigs(params)`

- [ ] **Step 1: Update frontend static test first**

Modify `model-config-static.test.mjs` to read `src/api/model-config.js` and assert:

```js
const apiSource = readModuleFile('src', 'api', 'model-config.js')

for (const fnName of [
  'listModelConfigs',
  'getModelConfig',
  'createModelConfig',
  'updateModelConfig',
  'deleteModelConfig',
  'copyModelConfig',
  'testModelConfig',
  'importModelConfigs',
  'exportModelConfigs'
]) {
  assert.match(apiSource, new RegExp(`export function ${fnName}\\b`), `API 应导出 ${fnName}`)
}

assert.match(apiSource, /\/biz\/ai\/model-configs/, '前端必须调用 /biz/ai/model-configs')
assert.doesNotMatch(apiSource, /\/api\/biz\/ai\/model-configs/, '前端 request 已有 /api baseURL，不能重复写 /api')
assert.doesNotMatch(page, /loadModelConfigState/, '页面不应再使用 localStorage 主存储加载')
assert.doesNotMatch(page, /saveModelConfigState/, '页面不应再使用 localStorage 主存储保存')
assert.doesNotMatch(page, /runModelConnectionTest/, '连接测试必须改为后端接口')
```

- [ ] **Step 2: Run frontend test to verify it fails**

Run:

```bash
node --test frontend/modules/ai/tests/model-config-static.test.mjs
```

Expected: FAIL because `src/api/model-config.js` does not exist and page still uses local storage helpers.

- [ ] **Step 3: Implement frontend API client**

Create `frontend/modules/ai/src/api/model-config.js`:

```js
import request from '../../../../web-shell/src/api/request'

const baseUrl = '/biz/ai/model-configs'

export function listModelConfigs(params = {}) {
  return request.get(baseUrl, { params })
}

export function getModelConfig(id) {
  return request.get(`${baseUrl}/${id}`)
}

export function createModelConfig(data) {
  return request.post(baseUrl, data)
}

export function updateModelConfig(id, data) {
  return request.put(`${baseUrl}/${id}`, data)
}

export function deleteModelConfig(id) {
  return request.delete(`${baseUrl}/${id}`)
}

export function copyModelConfig(id) {
  return request.post(`${baseUrl}/${id}/copy`)
}

export function testModelConfig(id) {
  return request.post(`${baseUrl}/${id}/test`)
}

export function importModelConfigs(data) {
  return request.post(`${baseUrl}/import`, data)
}

export function exportModelConfigs(params = {}) {
  return request.get(`${baseUrl}/export`, { params })
}
```

- [ ] **Step 4: Run frontend static test**

Run:

```bash
node --test frontend/modules/ai/tests/model-config-static.test.mjs
```

Expected: still FAIL until page is refactored in Task 7.

---

### Task 7: 前端页面切换为后端 API 驱动

**Files:**
- Modify: `frontend/modules/ai/src/views/AiModelConfigManage.vue`
- Test: `frontend/modules/ai/tests/model-config-static.test.mjs`

**Interfaces:**
- Consumes: API client functions from Task 6.
- Produces: API-driven model config page.

- [ ] **Step 1: Replace local storage imports**

Remove these imports from `AiModelConfigManage.vue`:

```js
loadModelConfigState
saveModelConfigState
upsertConfig
removeConfig
duplicateConfig
filterConfigs
runModelConnectionTest
```

Add imports:

```js
import {
  copyModelConfig,
  createModelConfig,
  deleteModelConfig,
  exportModelConfigs,
  importModelConfigs,
  listModelConfigs,
  testModelConfig,
  updateModelConfig
} from '../api/model-config.js'
```

- [ ] **Step 2: Replace state shape**

Replace:

```js
const state = reactive(loadModelConfigState())
const filteredConfigs = computed(() => filterConfigs(state.configs, filters))
```

With:

```js
const configs = ref([])
const loading = ref(false)
const filteredConfigs = computed(() => configs.value)
```

Add:

```js
async function loadConfigs() {
  loading.value = true
  try {
    const response = await listModelConfigs(filters)
    configs.value = response.data || []
  } finally {
    loading.value = false
  }
}
```

Bind table loading:

```vue
<el-table v-loading="loading" :data="filteredConfigs" border empty-text="当前环境暂无模型配置">
```

- [ ] **Step 3: Replace CRUD operations**

Implement:

```js
async function saveConfig() {
  const errors = validateConfig(form)
  if (errors.length) return ElMessage.warning(errors[0])
  const payload = buildPayload()
  if (editingId.value) await updateModelConfig(editingId.value, payload)
  else await createModelConfig(payload)
  drawerVisible.value = false
  ElMessage.success('模型配置已保存')
  await loadConfigs()
}

function buildPayload() {
  return {
    configName: form.configName || form.name,
    description: form.description,
    provider: form.provider,
    endpoint: form.endpoint,
    apiKey: form.apiKey,
    modelId: form.modelId,
    env: form.env,
    enabled: form.enabled,
    temperature: form.temperature,
    topP: form.topP,
    maxTokens: form.maxTokens,
    tags: [...(form.tags || [])]
  }
}
```

Update page references from `row.name` to `row.configName` where backend response is used.

- [ ] **Step 4: Replace delete/copy/test/import/export**

Use backend calls:

```js
async function deleteConfig(config) {
  try {
    await ElMessageBox.confirm(`确认删除模型配置「${config.configName}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await deleteModelConfig(config.id)
  ElMessage.success('模型配置已删除')
  await loadConfigs()
}

async function copyConfig(config) {
  await copyModelConfig(config.id)
  ElMessage.success('已复制配置')
  await loadConfigs()
}

async function testConfig(config) {
  testingId.value = config.id
  try {
    const response = await testModelConfig(config.id)
    showTestMessage(response.data)
    await loadConfigs()
  } finally {
    testingId.value = ''
  }
}
```

Import:

```js
const imported = normalizeImportedConfigs(JSON.parse(await file.text()), filters.env)
const response = await importModelConfigs({ configs: imported.map(toBackendRequest) })
ElMessage.success(`已导入 ${response.data.successCount} 条配置，跳过 ${response.data.skippedCount} 条`)
await loadConfigs()
```

Export:

```js
const response = await exportModelConfigs(filters)
const url = createExportUrl(response.data || [])
```

- [ ] **Step 5: Load data on mount and filters change**

Add:

```js
onMounted(() => {
  window.addEventListener('keydown', handleShortcut)
  loadConfigs()
})

watch(filters, loadConfigs, { deep: true })
```

Import `watch` from Vue.

- [ ] **Step 6: Run frontend tests**

Run:

```bash
node --test frontend/modules/ai/tests/*.test.mjs
```

Expected: PASS.

---

### Task 8: 全量验证与交付检查

**Files:**
- Read all changed files from previous tasks.

**Interfaces:**
- Consumes: complete backend and frontend integration.
- Produces: verified deliverable.

- [ ] **Step 1: Run module-ai-core tests**

Run:

```bash
mvn -pl modules/module-ai/module-ai-core -am test
```

Expected: PASS.

- [ ] **Step 2: Run module-ai-autoconfig tests**

Run:

```bash
mvn -pl modules/module-ai/module-ai-autoconfig -am test
```

Expected: PASS.

- [ ] **Step 3: Run frontend AI tests**

Run:

```bash
node --test frontend/modules/ai/tests/*.test.mjs
```

Expected: PASS.

- [ ] **Step 4: Build web shell**

Run:

```bash
cd frontend/web-shell
npm run build
```

Expected: PASS. Existing Rollup chunk warnings are acceptable if no new errors appear.

- [ ] **Step 5: Inspect paths and secrets**

Run:

```bash
rg -n "/api/biz/ai/model-configs|apiKey\\)|apiKey:|api_key|console\\.log" frontend/modules/ai modules/module-ai/module-ai-core
```

Expected:

- Frontend uses `/biz/ai/model-configs`, not `/api/biz/ai/model-configs`.
- Backend may contain `api_key` in SQL and `apiKey` in request/entity only.
- Response/documentation does not include real API Key examples.
- No debug `console.log` for secrets.

- [ ] **Step 6: Inspect working tree**

Run:

```bash
git status --short
```

Expected: Changed files are limited to this feature plus pre-existing unrelated dirty files. Do not revert unrelated user changes.

