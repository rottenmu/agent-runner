# module-ai 技能 ZIP 导入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: 使用 `test-driven-development` 按任务逐项实施；如果用户明确选择多代理执行，每个任务使用独立代理并在任务间进行审查。

**Goal:** 在 `module-ai` 技能管理中增加安全的单技能 ZIP 导入能力，并将现有超长技能编辑代码拆为独立组件。

**Architecture:** 前端使用独立技能编辑抽屉和 ZIP 导入弹窗，父页面只负责页面编排与列表刷新。后端先通过内存 ZIP 结构校验器核对 EOCD、中央目录和本地条目，再由 Parser 和导入 Service 完成受限读取、严格清单解析与请求转换，最终复用 `AiAgentManagementService#createApiSkill`，不直接写 Mapper。

**Tech Stack:** Java 17、Spring Boot 3.4.5、Jackson、JUnit 5、Mockito、MyBatis-Plus、Vue 3、Element Plus、Axios、Node Test Runner、MySQL。

## 全局约束

- 一个 ZIP 只允许导入一个 API 技能。
- ZIP 必须恰好包含根目录 `skill.json`，不接受其他文件或目录。
- 压缩文件最大 `2 MB`，`skill.json` 解压后最大 `256 KB`，最多扫描 `16` 个 ZIP 条目。
- JSON `schemaVersion` 必须为 `1`，未知字段、错误类型和整数配置中的小数值必须拒绝。
- 导入清单不允许声明 `agentId`、`promptTemplateId`、`apiRegistryId`。
- 技能重名返回业务状态 `400`，不得覆盖。
- 文件或字段错误返回 `400`；资源超限返回 `413`；持久化或注册表故障返回 `500`。
- 不解压到磁盘，不记录清单全文、请求头或密钥。
- Servlet Multipart 默认单文件 `2 MB`、单请求 `3 MB`、内存阈值 `2 MB`，应用显式配置可覆盖；超限统一返回业务状态 `413`。
- 不新增数据库表、字段、SQL、SchemaInitializer 或本地数据库。
- Service、Controller Bean 使用唯一 public 构造器注入。
- 遵守 `docs/rules/BACKEND_JAVA_COMMENT_RULES.md` 和 `docs/rules/CODE_SIZE_RULES.md`。
- 当前工作区包含用户已有未提交改动；不得重置、覆盖、暂存或提交无关文件。
- 当前同一模块中仍有前序未提交改动。除非用户明确授权并先处理前序改动，否则实施中的“提交检查点”只做差异汇总，不执行 `git add` 或 `git commit`。

---

## 文件边界

### 后端新增

- `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skillimport/AiSkillImportManifest.java`
  - 定义严格、无环境绑定字段的 ZIP 清单结构。
- `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skillimport/AiSkillImportException.java`
  - 表示业务状态 `400` 或 `413` 的可预期导入错误。
- `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skillimport/AiSkillZipParser.java`
  - 只负责 ZIP 文件头、条目、解压上限和 JSON 严格解析。
- `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skillimport/AiSkillZipImportService.java`
  - 负责上传文件校验、清单转换和调用现有技能创建方法。
- `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/skillimport/AiSkillZipParserTest.java`
- `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/skillimport/AiSkillZipImportServiceTest.java`

### 后端修改

- `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/controller/AiSkillAdminController.java`
  - 增加 multipart 导入接口并注入导入 Service。
- `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/controller/AiSkillAdminControllerTest.java`
  - 增加导入契约测试并调整唯一构造器。
- `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiSkillAdminAutoConfiguration.java`
  - 装配 Parser、导入 Service 和新 Controller 依赖。
- `modules/module-ai/module-ai-autoconfig/src/test/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfigurationTest.java`
  - 验证新 Bean 和 Controller 构造链。

### 前端新增

- `frontend/modules/ai/src/components/AiSkillEditorDrawer.vue`
  - 承载现有技能新建、编辑表单及保存行为。
- `frontend/modules/ai/src/components/AiSkillImportDialog.vue`
  - 承载 ZIP 拖拽、文件校验和上传状态。
- `frontend/modules/ai/src/utils/http-error.js`
  - 统一提取后端错误消息。
- `frontend/modules/ai/src/assets/ai-agent-manage.css`
  - 承接现有页面样式，保持父子组件视觉一致。
- `frontend/modules/ai/tests/skill-zip-import-static.test.mjs`
  - 验证导入 API、弹窗和父页面接入契约。
- `frontend/modules/ai/tests/ai-agent-manage-size.test.mjs`
  - 防止拆分后的 Vue 文件再次超过有效代码上限。

### 前端修改

- `frontend/modules/ai/src/api/agent.js`
  - 新增 `importSkillZip(file)`。
- `frontend/modules/ai/src/views/AiAgentManage.vue`
  - 接入两个技能组件并移除已迁移逻辑。
- `frontend/modules/ai/tests/skill-edit-delete-actions.test.mjs`
  - 将技能编辑断言指向新的编辑组件，保留行为契约。

---

### Task 1：严格技能清单与 ZIP Parser

**Files:**

- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skillimport/AiSkillImportManifest.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skillimport/AiSkillImportException.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skillimport/AiSkillZipParser.java`
- Test: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/skillimport/AiSkillZipParserTest.java`

**Interfaces:**

- Consumes: Spring Boot 提供的 `ObjectMapper`，输入为不超过 `2 MB` 的 ZIP 字节。
- Produces:
  - `public AiSkillZipParser(ObjectMapper objectMapper)`
  - `public AiSkillImportManifest parse(byte[] archive)`
  - `public static AiSkillImportException badRequest(String message)`
  - `public static AiSkillImportException tooLarge(String message)`
  - `public int getCode()`

- [ ] **Step 1：先写 Parser 失败与成功测试**

测试类必须包含以下具体用例：

```java
@Test
void 解析唯一根目录清单并应用默认值() {
    byte[] archive = zip(entry("skill.json", """
            {
              "schemaVersion": 1,
              "name": "quality_query",
              "description": "质量查询",
              "apiConfig": {
                "baseUrl": "https://api.example.com",
                "path": "/quality"
              }
            }
            """));

    AiSkillImportManifest manifest = parser.parse(archive);

    assertThat(manifest.name()).isEqualTo("quality_query");
    assertThat(manifest.readOnlyOrDefault()).isTrue();
    assertThat(manifest.apiConfig().enabledOrDefault()).isTrue();
    assertThat(manifest.apiConfig().methodOrDefault()).isEqualTo("POST");
    assertThat(manifest.apiConfig().timeoutMillisOrDefault()).isEqualTo(3000);
    assertThat(manifest.apiConfig().headersOrDefault()).isEmpty();
}
```

```java
@Test
void 拒绝额外目录或文件() {
    byte[] archive = zip(
            entry("skill.json", validManifest()),
            entry("README.md", "not allowed"));

    assertThatThrownBy(() -> parser.parse(archive))
            .isInstanceOf(AiSkillImportException.class)
            .hasMessage("ZIP 只能包含根目录 skill.json")
            .extracting("code")
            .isEqualTo(400);
}
```

其余用例必须分别命名并独立断言：

- `拒绝空字节和错误ZIP文件头`
- `拒绝缺少skillJson`
- `拒绝嵌套和包含点点的条目路径`
- `拒绝重复skillJson`
- `拒绝损坏或加密ZIP`
- `拒绝未知schemaVersion`
- `拒绝未知顶层字段`
- `拒绝apiConfig中的apiRegistryId`
- `拒绝错误字段类型`
- `拒绝超过256KB的skillJson`
- `拒绝超过16个条目`
- `支持UTF8中文描述`

测试辅助方法使用 `ZipOutputStream` 在内存创建 ZIP，不写临时文件：

```java
private static byte[] zip(Entry... entries) {
    try {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Entry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.content().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    } catch (IOException exception) {
        throw new UncheckedIOException(exception);
    }
}
```

- [ ] **Step 2：运行测试，确认因类型不存在而失败**

Run:

```text
mvn -pl modules/module-ai/module-ai-core -am -Dtest=AiSkillZipParserTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 编译失败，提示 `AiSkillZipParser`、`AiSkillImportManifest` 或 `AiSkillImportException` 不存在。

- [ ] **Step 3：实现导入清单和异常类型**

清单只暴露允许字段，并在访问方法中应用默认值：

```java
public record AiSkillImportManifest(
        int schemaVersion,
        String name,
        String description,
        Boolean readOnly,
        ApiConfig apiConfig) {

    public boolean readOnlyOrDefault() {
        return readOnly == null || readOnly;
    }

    public record ApiConfig(
            Boolean enabled,
            String baseUrl,
            String path,
            String method,
            Map<String, String> headers,
            Integer timeoutMillis) {

        public boolean enabledOrDefault() {
            return enabled == null || enabled;
        }

        public String methodOrDefault() {
            return method == null || method.isBlank() ? "POST" : method.trim();
        }

        public Map<String, String> headersOrDefault() {
            return headers == null ? Map.of() : Map.copyOf(headers);
        }

        public int timeoutMillisOrDefault() {
            return timeoutMillis == null ? 3000 : timeoutMillis;
        }
    }
}
```

异常类型只允许通过具名工厂创建：

```java
public final class AiSkillImportException extends RuntimeException {
    private final int code;

    private AiSkillImportException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static AiSkillImportException badRequest(String message) {
        return new AiSkillImportException(400, message);
    }

    public static AiSkillImportException tooLarge(String message) {
        return new AiSkillImportException(413, message);
    }

    public int getCode() {
        return code;
    }
}
```

- [ ] **Step 4：实现流式 Parser**

实现常量：

```java
private static final int MAX_ENTRY_COUNT = 16;
private static final int MAX_MANIFEST_BYTES = 256 * 1024;
private static final String MANIFEST_NAME = "skill.json";
```

构造器必须复制 `ObjectMapper`，仅对导入清单开启严格模式，不修改全局 Mapper：

```java
public AiSkillZipParser(ObjectMapper objectMapper) {
    Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    ObjectMapper strictMapper = objectMapper.copy()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    this.reader = strictMapper.readerFor(AiSkillImportManifest.class);
}
```

`parse` 必须按以下固定顺序处理：

1. 校验 `archive` 非空且以 ZIP 本地文件头 `50 4B 03 04` 开始。
2. 在解压前校验 EOCD、中央目录、本地条目头和数据描述符；拒绝截断、篡改、多磁盘、ZIP64、加密和不支持的压缩算法。
3. 从中央目录读取条目数，超过 16 个立即抛出 `413`；条目不是唯一根目录 `skill.json` 时立即返回 `400`，不得解压额外条目。
4. 仅对已通过结构校验的唯一 `skill.json` 使用 UTF-8 `ZipInputStream`，以 `MAX_MANIFEST_BYTES + 1` 为上限读取，超过上限抛出 `413`。
5. 用严格 `ObjectReader` 解析，并禁用 `ACCEPT_FLOAT_AS_INT`；捕获 `JsonProcessingException` 后转换为固定的 `skill.json 格式不合法`，不得拼接 Jackson 原始异常消息。
6. 校验版本、必填文本、`apiConfig` 和正数超时。

读取限制使用独立私有方法，禁止调用无界 `readAllBytes()`：

```java
private byte[] readManifest(ZipInputStream input) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int total = 0;
    int read;
    while ((read = input.read(buffer)) != -1) {
        total += read;
        if (total > MAX_MANIFEST_BYTES) {
            throw AiSkillImportException.tooLarge("skill.json 解压后不能超过 256 KB");
        }
        output.write(buffer, 0, read);
    }
    return output.toByteArray();
}
```

- [ ] **Step 5：运行 Parser 测试并检查行数**

Run:

```text
mvn -pl modules/module-ai/module-ai-core -am -Dtest=AiSkillZipParserTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `AiSkillZipParserTest` 全部通过，Parser 单方法有效代码不超过 80 行。

- [ ] **Step 6：任务检查点**

运行：

```text
git diff --check -- modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skillimport modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/skillimport/AiSkillZipParserTest.java
```

Expected: 无空白错误。汇总 Task 1 文件，当前不暂存、不提交。

---

### Task 2：ZIP 导入 Service 与现有创建方法复用

**Files:**

- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skillimport/AiSkillZipImportService.java`
- Test: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/skillimport/AiSkillZipImportServiceTest.java`

**Interfaces:**

- Consumes:
  - `AiSkillZipParser#parse(byte[])`
  - `AiAgentManagementService#createApiSkill(AiManagedSkillRequest)`
- Produces:
  - `public AiSkillZipImportService(AiSkillZipParser parser, AiAgentManagementService managementService)`
  - `public AiManagedSkill importSkill(MultipartFile file)`

- [ ] **Step 1：先写导入编排测试**

有效导入测试必须捕获实际传给现有创建方法的请求：

```java
@Test
void 有效Zip转换后只调用一次现有创建方法() {
    AiAgentManagementService managementService = mock(AiAgentManagementService.class);
    AiSkillZipImportService service = new AiSkillZipImportService(realParser(), managementService);
    AiManagedSkill created = skill("quality_query");
    when(managementService.createApiSkill(any())).thenReturn(created);
    MockMultipartFile file = zipFile("quality-query.zip", validArchive());

    AiManagedSkill result = service.importSkill(file);

    ArgumentCaptor<AiManagedSkillRequest> captor = ArgumentCaptor.forClass(AiManagedSkillRequest.class);
    verify(managementService).createApiSkill(captor.capture());
    assertThat(result).isSameAs(created);
    assertThat(captor.getValue().getAgentId()).isNull();
    assertThat(captor.getValue().getPromptTemplateId()).isNull();
    assertThat(captor.getValue().getApiConfig().getApiRegistryId()).isNull();
}
```

其他独立用例：

- `拒绝空MultipartFile`
- `拒绝非zip扩展名`
- `拒绝超过2MB压缩文件并返回413`
- `Parser失败时不调用创建方法`
- `技能重名异常保持为IllegalArgumentException供Controller转换400`
- `持久化异常不包装为400或413`
- `注册表异常不包装为400或413`

- [ ] **Step 2：运行测试，确认 Service 不存在**

Run:

```text
mvn -pl modules/module-ai/module-ai-core -am -Dtest=AiSkillZipImportServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 编译失败，提示 `AiSkillZipImportService` 不存在。

- [ ] **Step 3：实现上传校验和请求转换**

Service 使用固定压缩大小限制：

```java
private static final long MAX_ARCHIVE_BYTES = 2L * 1024L * 1024L;
```

主流程保持短小：

```java
public AiManagedSkill importSkill(MultipartFile file) {
    validateUpload(file);
    AiSkillImportManifest manifest = parser.parse(readArchive(file));
    return managementService.createApiSkill(toCreateRequest(manifest));
}
```

文件校验：

```java
private void validateUpload(MultipartFile file) {
    if (file == null || file.isEmpty()) {
        throw AiSkillImportException.badRequest("请选择 ZIP 技能包");
    }
    String filename = file.getOriginalFilename();
    if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
        throw AiSkillImportException.badRequest("仅支持 .zip 技能包");
    }
    if (file.getSize() > MAX_ARCHIVE_BYTES) {
        throw AiSkillImportException.tooLarge("ZIP 技能包不能超过 2 MB");
    }
}
```

请求转换必须显式创建新的管理请求，不接收环境字段：

```java
private AiManagedSkillRequest toCreateRequest(AiSkillImportManifest manifest) {
    AiManagedSkillRequest request = new AiManagedSkillRequest();
    request.setName(manifest.name().trim());
    request.setDescription(manifest.description().trim());
    request.setReadOnly(manifest.readOnlyOrDefault());
    request.setAgentId(null);
    request.setPromptTemplateId(null);

    AiSkillApiConfigRequest api = new AiSkillApiConfigRequest();
    api.setApiRegistryId(null);
    api.setEnabled(manifest.apiConfig().enabledOrDefault());
    api.setBaseUrl(manifest.apiConfig().baseUrl().trim());
    api.setPath(manifest.apiConfig().path().trim());
    api.setMethod(manifest.apiConfig().methodOrDefault());
    api.setHeaders(manifest.apiConfig().headersOrDefault());
    api.setTimeoutMillis(manifest.apiConfig().timeoutMillisOrDefault());
    request.setApiConfig(api);
    return request;
}
```

`IOException` 只在读取上传内容时转换为 `400` 安全消息；`createApiSkill` 抛出的异常不得捕获或改写。

- [ ] **Step 4：运行 Service 与 Parser 测试**

Run:

```text
mvn -pl modules/module-ai/module-ai-core -am -Dtest=AiSkillZipParserTest,AiSkillZipImportServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 两个测试类全部通过；解析失败时 Mockito 验证 `never().createApiSkill(any())`。

- [ ] **Step 5：任务检查点**

运行 scoped `git diff --check`，汇总 Task 2 文件，不暂存、不提交。

---

### Task 3：技能导入 multipart Controller

**Files:**

- Modify: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/controller/AiSkillAdminController.java`
- Modify: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/controller/AiSkillAdminControllerTest.java`

**Interfaces:**

- Consumes: `AiSkillZipImportService#importSkill(MultipartFile)`
- Produces:
  - `POST /api/biz/ai/skills/import`
  - multipart 字段 `file`
  - `R<AiManagedSkill>`

- [ ] **Step 1：先更新 Controller 测试构造器并写导入测试**

所有测试统一通过一个辅助上下文创建 Controller：

```java
private static ControllerFixture fixture() {
    return new ControllerFixture(
            mock(AiAgentManagementService.class),
            mock(AiSkillZipImportService.class));
}

private record ControllerFixture(
        AiAgentManagementService managementService,
        AiSkillZipImportService importService) {

    AiSkillAdminController controller() {
        return new AiSkillAdminController(managementService, importService);
    }

    MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(controller()).build();
    }
}
```

成功契约：

```java
@Test
void 上传Zip时返回创建后的技能() throws Exception {
    ControllerFixture fixture = fixture();
    when(fixture.importService().importSkill(any())).thenReturn(skill("quality_query"));
    MockMultipartFile file = new MockMultipartFile(
            "file", "quality.zip", "application/zip", new byte[] {0x50, 0x4b, 0x03, 0x04});

    fixture.mockMvc().perform(multipart("/api/biz/ai/skills/import").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.name").value("quality_query"));

    verify(fixture.importService()).importSkill(any(MultipartFile.class));
}
```

异常契约使用直接调用断言业务码：

```java
@Test
void 导入格式异常转换为400业务异常() {
    ControllerFixture fixture = fixture();
    when(fixture.importService().importSkill(any()))
            .thenThrow(AiSkillImportException.badRequest("缺少 skill.json"));

    assertThatThrownBy(() -> fixture.controller().importSkill(mock(MultipartFile.class)))
            .isInstanceOf(BizException.class)
            .hasMessage("缺少 skill.json")
            .extracting("code")
            .isEqualTo(400);
}
```

另写：

- `导入超限异常转换为413业务异常`
- `缺少file字段时进入Service并转换为400业务异常`
- `技能重名转换为400业务异常`
- `持久化RuntimeException不被Controller捕获以便全局返回500`
- `导入方法声明multipart路由和file参数`

- [ ] **Step 2：运行测试，确认 Controller 尚无导入接口**

Run:

```text
mvn -pl modules/module-ai/module-ai-core -am -Dtest=AiSkillAdminControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 编译失败或导入接口契约失败。

- [ ] **Step 3：实现唯一构造器和导入端点**

Controller 保持一个 public 构造器：

```java
public AiSkillAdminController(
        AiAgentManagementService managementService,
        AiSkillZipImportService importService) {
    this.managementService = Objects.requireNonNull(managementService, "managementService must not be null");
    this.importService = Objects.requireNonNull(importService, "importService must not be null");
}
```

新增端点：

```java
@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public R<AiManagedSkill> importSkill(
        @RequestPart(value = "file", required = false) MultipartFile file) {
    try {
        return R.ok(importService.importSkill(file));
    } catch (AiSkillImportException exception) {
        throw new BizException(exception.getCode(), exception.getMessage());
    } catch (IllegalArgumentException exception) {
        throw badRequest(exception);
    }
}
```

不得增加捕获 `RuntimeException` 或 `Exception` 的分支，确保非预期故障进入全局 `500`。

- [ ] **Step 4：运行 Controller 与导入测试**

Run:

```text
mvn -pl modules/module-ai/module-ai-core -am -Dtest=AiSkillAdminControllerTest,AiSkillZipParserTest,AiSkillZipImportServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 全部通过。

- [ ] **Step 5：任务检查点**

检查 Controller 有效代码不超过 400 行、方法不超过 80 行；运行 scoped `git diff --check`，不暂存、不提交。

---

### Task 4：自动配置导入组件

**Files:**

- Modify: `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiSkillAdminAutoConfiguration.java`
- Modify: `modules/module-ai/module-ai-autoconfig/src/test/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfigurationTest.java`

**Interfaces:**

- Consumes: `ObjectMapper`、`AiAgentManagementService`
- Produces: `AiSkillZipParser`、`AiSkillZipImportService` 和带新依赖的 `AiSkillAdminController`

- [ ] **Step 1：先写自动配置失败测试**

直接工厂测试：

```java
@Test
void createsSkillImportBeansAndController() {
    AiSkillAdminAutoConfiguration configuration = new AiSkillAdminAutoConfiguration();
    ObjectMapper objectMapper = new ObjectMapper();
    AiAgentManagementService managementService = mock(AiAgentManagementService.class);

    AiSkillZipParser parser = configuration.aiSkillZipParser(objectMapper);
    AiSkillZipImportService importService =
            configuration.aiSkillZipImportService(parser, managementService);

    assertThat(parser).isNotNull();
    assertThat(importService).isNotNull();
    assertThat(configuration.aiSkillAdminController(managementService, importService))
            .isInstanceOf(AiSkillAdminController.class);
}
```

更新原 `createsAllManagementControllersFromModuleServices`，不得继续调用旧的单参数
`aiSkillAdminController(managementService)`。

- [ ] **Step 2：运行测试，确认新 Bean 方法不存在**

Run:

```text
mvn -pl modules/module-ai/module-ai-autoconfig -am -Dtest=AiModuleAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 编译失败，提示新 Bean 方法或 Controller 构造参数不存在。

- [ ] **Step 3：增加三个条件 Bean**

```java
@Bean
@ConditionalOnMissingBean
public AiSkillZipParser aiSkillZipParser(ObjectMapper objectMapper) {
    return new AiSkillZipParser(objectMapper);
}

@Bean
@ConditionalOnMissingBean
public AiSkillZipImportService aiSkillZipImportService(
        AiSkillZipParser parser,
        AiAgentManagementService service) {
    return new AiSkillZipImportService(parser, service);
}

@Bean
@ConditionalOnMissingBean
public AiSkillAdminController aiSkillAdminController(
        AiAgentManagementService service,
        AiSkillZipImportService importService) {
    return new AiSkillAdminController(service, importService);
}
```

保留现有 `@ConditionalOnProperty`、`@ConditionalOnBean(AiSkillRegistry.class)` 和 MapperScan 条件，不改变插件启用语义。

- [ ] **Step 4：运行 module-ai 后端 Reactor**

Run:

```text
mvn -pl modules/module-ai/module-ai-autoconfig -am test
```

Expected: module-ai-core、starter 和 module-ai-autoconfig 相关测试全部通过。

- [ ] **Step 5：任务检查点**

运行 scoped `git diff --check`，确认没有新增数据库初始化代码，不暂存、不提交。

---

### Task 5：前端导入 API、错误工具和拖拽弹窗

**Files:**

- Modify: `frontend/modules/ai/src/api/agent.js`
- Create: `frontend/modules/ai/src/utils/http-error.js`
- Create: `frontend/modules/ai/src/components/AiSkillImportDialog.vue`
- Create: `frontend/modules/ai/tests/skill-zip-import-static.test.mjs`

**Interfaces:**

- Consumes: `POST /api/biz/ai/skills/import`
- Produces:
  - `importSkillZip(file): Promise<R<AiManagedSkill>>`
  - `resolveErrorMessage(error, fallback): string`
  - `AiSkillImportDialog` props `modelValue`
  - emits `update:modelValue`、`imported`

- [ ] **Step 1：先写前端静态契约测试**

测试读取 API、弹窗和父页面源码，至少包含：

```js
test('导入 API 必须使用 FormData 的 file 字段', () => {
  assert.match(apiSource, /export function importSkillZip\(file\)/)
  assert.match(apiSource, /const formData = new FormData\(\)/)
  assert.match(apiSource, /formData\.append\('file', file\)/)
  assert.match(apiSource, /request\.post\('\/biz\/ai\/skills\/import', formData\)/)
})

test('导入弹窗必须单文件手工提交', () => {
  assert.match(dialogSource, /<el-upload/)
  assert.match(dialogSource, /:auto-upload="false"/)
  assert.match(dialogSource, /:limit="1"/)
  assert.match(dialogSource, /accept="\.zip"/)
  assert.match(dialogSource, /开始导入/)
  assert.match(dialogSource, /await importSkillZip\(selectedFile\.value\)/)
  assert.match(dialogSource, /emit\('imported'/)
})
```

另断言：

- 前端常量为 `2 * 1024 * 1024`。
- 非 `.zip` 和超过 2 MB 时清空选择并提示。
- 上传期间按钮使用 `loading` 且禁用重复提交。
- catch 分支调用 `resolveErrorMessage(error, '导入技能失败')`。
- 成功分支清空文件并关闭弹窗。

- [ ] **Step 2：运行测试，确认文件和接口不存在**

Run:

```text
node --test frontend/modules/ai/tests/skill-zip-import-static.test.mjs
```

Expected: FAIL，提示导入组件或 `importSkillZip` 不存在。

- [ ] **Step 3：实现 API 和错误工具**

`agent.js`：

```js
export function importSkillZip(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/biz/ai/skills/import', formData)
}
```

不要设置 `Content-Type`，让 Axios 自动生成 multipart boundary。

`http-error.js`：

```js
export function resolveErrorMessage(error, fallback) {
  return error?.response?.data?.message
    || error?.response?.data?.msg
    || error?.message
    || fallback
}
```

- [ ] **Step 4：实现 `AiSkillImportDialog.vue`**

组件状态：

```js
const ZIP_MAX_BYTES = 2 * 1024 * 1024
const props = defineProps({ modelValue: { type: Boolean, default: false } })
const emit = defineEmits(['update:modelValue', 'imported'])
const uploadRef = ref()
const fileList = ref([])
const selectedFile = ref(null)
const importing = ref(false)
```

文件校验函数必须同时校验扩展名和大小：

```js
function acceptFile(file) {
  const name = String(file?.name || '').toLowerCase()
  if (!name.endsWith('.zip')) {
    ElMessage.error('仅支持 .zip 技能包')
    return false
  }
  if (Number(file.size || 0) > ZIP_MAX_BYTES) {
    ElMessage.error('ZIP 技能包不能超过 2 MB')
    return false
  }
  return true
}
```

提交函数：

```js
async function submitImport() {
  if (!selectedFile.value || importing.value) return
  importing.value = true
  try {
    const result = await importSkillZip(selectedFile.value)
    clearSelection()
    emit('update:modelValue', false)
    emit('imported', result?.data || result)
    ElMessage.success('技能导入成功')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '导入技能失败'))
  } finally {
    importing.value = false
  }
}
```

`handleExceed(files)` 使用 `uploadRef.clearFiles()` 和 `uploadRef.handleStart(files[0])` 替换旧文件；关闭弹窗时仅在非上传状态下清空选择。

- [ ] **Step 5：运行导入静态测试**

Run:

```text
node --test frontend/modules/ai/tests/skill-zip-import-static.test.mjs
```

Expected: API、单文件、大小、手工提交、成功和失败契约全部通过。

- [ ] **Step 6：任务检查点**

确认 `AiSkillImportDialog.vue` 有效代码不超过 300 行、函数不超过 50 行；运行 scoped `git diff --check`，不暂存、不提交。

---

### Task 6：拆分技能编辑抽屉并接入导入弹窗

**Files:**

- Create: `frontend/modules/ai/src/components/AiSkillEditorDrawer.vue`
- Create: `frontend/modules/ai/src/assets/ai-agent-manage.css`
- Modify: `frontend/modules/ai/src/views/AiAgentManage.vue`
- Modify: `frontend/modules/ai/tests/skill-edit-delete-actions.test.mjs`
- Modify: `frontend/modules/ai/tests/skill-zip-import-static.test.mjs`
- Create: `frontend/modules/ai/tests/ai-agent-manage-size.test.mjs`

**Interfaces:**

- `AiSkillEditorDrawer`
  - props: `modelValue: boolean`、`skill: object|null`、`promptTemplates: array`
  - emits: `update:modelValue`、`saved`
- `AiAgentManage.vue`
  - `openCreateSkill()` 传入空技能。
  - `openEditSkill(skill)` 传入现有技能。
  - `handleSkillSaved()` 和 `handleSkillImported()` 调用 `refreshSkills()`。

- [ ] **Step 1：先调整现有技能编辑测试目标**

在 `skill-edit-delete-actions.test.mjs` 增加：

```js
const editorSource = readFileSync(
  join(moduleRoot, 'src', 'components', 'AiSkillEditorDrawer.vue'),
  'utf8'
)
const skillUiSource = `${pageSource}\n${editorSource}`
```

将技能保存、名称禁用、API Registry 选择和保存错误的断言改为检查 `skillUiSource` 或 `editorSource`；
删除技能、引用提示和本地智能体绑定清理仍检查 `pageSource`。

在 `skill-zip-import-static.test.mjs` 断言父页面：

```js
assert.match(pageSource, /导入技能/)
assert.match(pageSource, /<AiSkillImportDialog/)
assert.match(pageSource, /@imported="handleSkillImported"/)
assert.match(pageSource, /async function handleSkillImported\(\)/)
assert.match(pageSource, /await refreshSkills\(\)/)
```

- [ ] **Step 2：写代码规模失败测试**

`ai-agent-manage-size.test.mjs` 使用确定的有效行统计：

```js
function effectiveLines(source) {
  return source.split(/\apiResponse?\n/).filter(line => {
    const value = line.trim()
    return value
      && !value.startsWith('//')
      && !value.startsWith('<!--')
      && !value.startsWith('*')
      && !/^[{}]$/.test(value)
  }).length
}

test('技能管理页面与子组件满足代码规模上限', () => {
  assert.ok(effectiveLines(pageSource) <= 500)
  assert.ok(effectiveLines(editorSource) <= 300)
  assert.ok(effectiveLines(importSource) <= 300)
})
```

- [ ] **Step 3：运行前端测试，确认抽屉不存在且页面超限**

Run:

```text
node --test frontend/modules/ai/tests/skill-edit-delete-actions.test.mjs frontend/modules/ai/tests/skill-zip-import-static.test.mjs frontend/modules/ai/tests/ai-agent-manage-size.test.mjs
```

Expected: FAIL，提示编辑组件不存在或 `AiAgentManage.vue` 超过 500 行。

- [ ] **Step 4：迁移现有技能编辑抽屉**

`AiSkillEditorDrawer.vue` 必须迁移而不重写以下现有行为：

- `defaultSkillForm`
- 表单重置和编辑态回填
- `AiApiRegistrySelect` 与 `mergeRegistryApiConfig`
- 请求头 JSON 解析
- `canSubmitSkill`
- `createSkill` / `updateSkill`
- 编辑态技能名称禁用
- 保存 loading 和错误提示

组件通过 watch 在打开或 `skill` 变化时重置：

```js
watch(
  () => [props.modelValue, props.skill],
  ([visible]) => {
    if (visible) resetSkillForm(props.skill)
  },
  { immediate: true }
)
```

保存成功后：

```js
emit('saved', saved)
emit('update:modelValue', false)
ElMessage.success('保存成功')
```

父页面删除已迁移的表单状态、API Registry 状态、请求头状态和 `saveSkill`，避免保留两套实现。

- [ ] **Step 5：接入两个技能组件**

父页面新增状态：

```js
const skillDrawerVisible = ref(false)
const skillImportVisible = ref(false)
const editingSkill = ref(null)
```

操作：

```js
function openCreateSkill() {
  editingSkill.value = null
  skillDrawerVisible.value = true
}

function openEditSkill(skill) {
  editingSkill.value = skill
  skillDrawerVisible.value = true
}

async function handleSkillSaved() {
  await refreshSkills()
}

async function handleSkillImported() {
  await refreshSkills()
}
```

模板按钮顺序固定：

```vue
<el-button v-if="activeTab === 'skills'" type="primary" :icon="Plus" @click="openCreateSkill">
  新建技能
</el-button>
<el-button v-if="activeTab === 'skills'" @click="skillImportVisible = true">
  导入技能
</el-button>
```

页面底部接入：

```vue
<AiSkillEditorDrawer
  v-model="skillDrawerVisible"
  :skill="editingSkill"
  :prompt-templates="skillPromptTemplates"
  @saved="handleSkillSaved"
/>
<AiSkillImportDialog
  v-model="skillImportVisible"
  @imported="handleSkillImported"
/>
```

- [ ] **Step 6：迁移页面样式并满足行数**

将现有 `<style scoped>` 内容移动到 `src/assets/ai-agent-manage.css`，页面改为：

```vue
<style src="../assets/ai-agent-manage.css"></style>
```

使用页面现有的模块化 class 名，不增加通用元素选择器。因为技能抽屉已成为子组件，样式改为非 scoped 后必须检查选择器均以
`.agent-admin`、`.agent-card`、`.skill-card` 或 `.agent-form` 等模块前缀开始，避免污染其他插件页面。

- [ ] **Step 7：运行 AI 前端全部静态测试**

Run:

```text
node --test frontend/modules/ai/tests/*.test.mjs
```

Expected: 导入、新建、编辑、删除、提示词、模型配置、飞书绑定等静态测试全部通过，三个 Vue 文件满足代码规模断言。

- [ ] **Step 8：运行主壳构建**

Run:

```text
npm run build
```

Workdir: `frontend/web-shell`

Expected: Vite build 成功；无组件解析、CSS 路径或导入错误。

- [ ] **Step 9：任务检查点**

运行 scoped `git diff --check`，人工核对技能新建/编辑视觉结构未丢失，不暂存、不提交。

---

### Task 7：完整回归、安全检查与交付

**Files:**

- Review: `docs/superpowers/specs/2026-07-27-module-ai-skill-zip-import-design.md`
- Review: 本计划列出的所有新增和修改文件。

**Interfaces:**

- Produces: 已验证的 `/api/biz/ai/skills/import`、拖拽导入 UI 和未回归的原技能管理功能。

- [ ] **Step 1：运行完整 module-ai Reactor**

Run:

```text
mvn -pl modules/module-ai/module-ai-autoconfig -am test
```

Expected: Reactor Success，starter、module-ai-core 和 module-ai-autoconfig 测试全部通过。

- [ ] **Step 2：运行 AI 前端测试与构建**

Run:

```text
node --test frontend/modules/ai/tests/*.test.mjs
```

Expected: 全部 PASS。

Run:

```text
npm run build
```

Workdir: `frontend/web-shell`

Expected: 构建成功。

- [ ] **Step 3：执行安全静态检查**

Run:

```text
rg -n "readAllBytes|Files\\.write|Files\\.copy|createTemp|requestHeaders|Authorization" modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skillimport
```

Expected:

- 不出现无界 `readAllBytes`。
- 不出现文件系统写入或临时文件创建。
- 不出现记录清单、请求头或 Authorization 值的日志语句。

Run:

```text
rg -n "agentId|promptTemplateId|apiRegistryId" modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skillimport/AiSkillImportManifest.java
```

Expected: 无匹配，证明导入清单类型不接受环境字段。

- [ ] **Step 4：检查数据库边界**

Run:

```text
git diff --name-only -- modules/module-ai | rg -i "sql|schema|initializer|migration"
```

Expected: 无新增数据库脚本、迁移或初始化器。

- [ ] **Step 5：检查代码规模和差异**

Run:

```text
node --test frontend/modules/ai/tests/ai-agent-manage-size.test.mjs
git diff --check
git status --short
```

Expected:

- 页面和组件代码规模测试通过。
- `git diff --check` 无错误。
- `git status` 中用户已有无关改动仍存在且未被修改或删除。

- [ ] **Step 6：只读代码审查**

按以下清单审查：

- ZIP 炸弹、路径穿越、额外条目和错误 JSON 是否在调用创建方法前被拒绝。
- Parser 是否没有日志泄露请求头。
- 导入 Service 是否只调用现有 `createApiSkill`，没有直写仓储。
- Controller 是否只转换 `AiSkillImportException` 和 `IllegalArgumentException`，未吞掉 `500`。
- 前端失败后是否保留文件，成功后是否刷新列表。
- 技能编辑拆分后是否仍支持 API Registry、请求头、提示词模板和名称不可修改。

发现问题时先增加失败测试，再做最小修复，并重新运行对应任务测试。

- [ ] **Step 7：交付检查点**

汇总：

- 新增接口和 `skill.json` 示例。
- `400`、`413`、`500` 行为。
- 后端测试、前端测试和构建结果。
- 无数据库变更。
- 当前未提交状态和未触碰的无关改动。

未经用户明确授权，不执行暂存或提交。
