# module-ai 技能管理适配层实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 在 `module-ai` 中新增平台业务路径 `/api/biz/ai/skills` 的技能管理适配接口，支持技能查询、创建、编辑、删除和配置更新。

**架构：** `module-ai-core` 新增轻量 `AiSkillAdminController`，只负责平台路径、`R<T>` 返回体和异常转换。技能创建、编辑、删除、API 配置保存、提示词绑定、删除后解除智能体绑定等业务逻辑继续复用 `ai-agent-spring-boot-starter` 的 `AiAgentManagementService`。

**技术栈：** Java 17、Spring Boot 3.4.5、Spring MVC、JUnit 5、Mockito、MockMvc、Maven。

## 全局约束

- 本仓库以后生成或修改的 `.md` 文档默认使用中文。
- 后端 Java 代码注释必须遵守 `docs/rules/BACKEND_JAVA_COMMENT_RULES.md`。
- 后端与通用代码行数规范必须遵守 `docs/rules/CODE_SIZE_RULES.md`。
- Service 层、Controller 层中所有需要注入 Bean 的类，均采用普通 public 构造器注入。
- Service 层、Controller 层 Bean 每个类只保留一个 public 构造器。
- 本任务不新增 SQLite、H2 或本地数据库配置。
- 不编辑 `target/`、`dist/` 等生成产物。
- 不修改前端主壳端口配置。
- 不让 `module-ai` 反向依赖 `module-sys`。

---

## 文件结构

- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/controller/AiSkillAdminController.java`
  - 平台业务路径技能管理适配 Controller。
  - 构造器注入 `AiAgentManagementService`。
  - 对外返回 `R<T>`。
  - 将 `IllegalArgumentException` 转换为 `BizException(400, message)`。
  - 删除返回 `false` 时转换为 `BizException(404, "API技能不存在")`。
- Create: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/controller/AiSkillAdminControllerTest.java`
- Create: `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiSkillAdminAutoConfiguration.java`
  - 在 `AiAgentManagementService` 存在时显式注册平台技能管理 Controller。
  - 避免 `module-ai` 单独自动装配时扫描到可选依赖 Controller。
- Update: `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfiguration.java`
  - 移除全包 `ComponentScan`，仅保留插件基础 Bean。
- Update: `modules/module-ai/module-ai-autoconfig/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  - 增加 `AiSkillAdminAutoConfiguration` 自动装配入口。
- Update: `modules/module-ai/module-ai-autoconfig/src/test/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfigurationTest.java`
  - 覆盖技能管理 Controller 的条件注册行为。
  - 使用 `MockMvcBuilders.standaloneSetup` 验证平台路径、返回体和服务转调。
  - 使用 Mockito mock `AiAgentManagementService`。
- Read-only: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/*`
  - 沿用 `AiManagedSkill`、`AiManagedSkillRequest`、`AiSkillApiConfigRequest`、`AiSkillPromptTemplateRequest`。

---

### Task 1: Controller 红灯测试

**Files:**
- Create: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/controller/AiSkillAdminControllerTest.java`
- Create: `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiSkillAdminAutoConfiguration.java`
  - 在 `AiAgentManagementService` 存在时显式注册平台技能管理 Controller。
  - 避免 `module-ai` 单独自动装配时扫描到可选依赖 Controller。
- Update: `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfiguration.java`
  - 移除全包 `ComponentScan`，仅保留插件基础 Bean。
- Update: `modules/module-ai/module-ai-autoconfig/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  - 增加 `AiSkillAdminAutoConfiguration` 自动装配入口。
- Update: `modules/module-ai/module-ai-autoconfig/src/test/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfigurationTest.java`
  - 覆盖技能管理 Controller 的条件注册行为。

**Interfaces:**
- Consumes:
  - `AiAgentManagementService#listSkills()`
  - `AiAgentManagementService#createApiSkill(AiManagedSkillRequest request)`
  - `AiAgentManagementService#updateApiSkill(String name, AiManagedSkillRequest request)`
  - `AiAgentManagementService#updateApiSkillConfig(String name, AiSkillApiConfigRequest request)`
  - `AiAgentManagementService#bindSkillPromptTemplate(String name, Long promptTemplateId)`
  - `AiAgentManagementService#deleteApiSkill(String name)`
- Produces: 失败的 Controller 契约测试，证明 `/api/biz/ai/skills` 适配层尚不存在。

- [x] **Step 1: Write the failing test**

Create `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/controller/AiSkillAdminControllerTest.java`:

```java
package com.zimo.module.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zimo.framework.common.BizException;
import com.zimo.starter.ai.management.AiAgentManagementService;
import com.zimo.starter.ai.management.AiManagedSkill;
import com.zimo.starter.ai.management.AiManagedSkillRequest;
import com.zimo.starter.ai.management.AiSkillApiConfigRequest;
import com.zimo.starter.ai.management.AiSkillApiConfigResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestController;

class AiSkillAdminControllerTest {

    @Test
    void listsSkillsWithPlatformResponseBody() throws Exception {
        AiAgentManagementService service = mock(AiAgentManagementService.class);
        when(service.listSkills()).thenReturn(List.of(skill("remote_quality_check")));

        mockMvc(service).perform(get("/api/biz/ai/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data[0].name").value("remote_quality_check"))
                .andExpect(jsonPath("$.data[0].source").value("api"))
                .andExpect(jsonPath("$.data[0].apiConfig.apiRegistryId").value(1001));
    }

    @Test
    void createsApiSkillThroughManagementService() throws Exception {
        AiAgentManagementService service = mock(AiAgentManagementService.class);
        when(service.createApiSkill(any(AiManagedSkillRequest.class))).thenReturn(skill("remote_quality_check"));

        mockMvc(service).perform(post("/api/biz/ai/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(skillPayload("remote_quality_check")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("remote_quality_check"));

        verify(service).createApiSkill(any(AiManagedSkillRequest.class));
    }

    @Test
    void updatesApiSkillThroughManagementService() throws Exception {
        AiAgentManagementService service = mock(AiAgentManagementService.class);
        when(service.updateApiSkill(eq("remote_quality_check"), any(AiManagedSkillRequest.class)))
                .thenReturn(skill("remote_quality_check"));

        mockMvc(service).perform(put("/api/biz/ai/skills/remote_quality_check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(skillPayload("remote_quality_check")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("remote_quality_check"));

        verify(service).updateApiSkill(eq("remote_quality_check"), any(AiManagedSkillRequest.class));
    }

    @Test
    void updatesApiConfigThroughManagementService() throws Exception {
        AiAgentManagementService service = mock(AiAgentManagementService.class);
        when(service.updateApiSkillConfig(eq("remote_quality_check"), any(AiSkillApiConfigRequest.class)))
                .thenReturn(skill("remote_quality_check"));

        mockMvc(service).perform(put("/api/biz/ai/skills/remote_quality_check/api-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "apiRegistryId": 1001,
                                  "enabled": true,
                                  "baseUrl": "https://api.example.com",
                                  "path": "/quality",
                                  "method": "POST",
                                  "headers": {},
                                  "timeoutMillis": 3000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.apiConfig.path").value("/quality"));

        verify(service).updateApiSkillConfig(eq("remote_quality_check"), any(AiSkillApiConfigRequest.class));
    }

    @Test
    void bindsPromptTemplateThroughManagementService() throws Exception {
        AiAgentManagementService service = mock(AiAgentManagementService.class);
        when(service.bindSkillPromptTemplate("remote_quality_check", 88L)).thenReturn(skill("remote_quality_check"));

        mockMvc(service).perform(put("/api/biz/ai/skills/remote_quality_check/prompt-template")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptTemplateId\":88}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(service).bindSkillPromptTemplate("remote_quality_check", 88L);
    }

    @Test
    void deletesApiSkillThroughManagementService() throws Exception {
        AiAgentManagementService service = mock(AiAgentManagementService.class);
        when(service.deleteApiSkill("remote_quality_check")).thenReturn(true);

        mockMvc(service).perform(delete("/api/biz/ai/skills/remote_quality_check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(service).deleteApiSkill("remote_quality_check");
    }

    @Test
    void convertsMissingDeleteResultToBusinessException() {
        AiAgentManagementService service = mock(AiAgentManagementService.class);
        when(service.deleteApiSkill("missing_skill")).thenReturn(false);
        AiSkillAdminController controller = new AiSkillAdminController(service);

        assertThatThrownBy(() -> controller.delete("missing_skill"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(404);
    }

    @Test
    void convertsStarterIllegalArgumentToBusinessException() {
        AiAgentManagementService service = mock(AiAgentManagementService.class);
        when(service.updateApiSkill(eq("summarize"), any(AiManagedSkillRequest.class)))
                .thenThrow(new IllegalArgumentException("内置技能不可编辑"));
        AiSkillAdminController controller = new AiSkillAdminController(service);

        assertThatThrownBy(() -> controller.update("summarize", new AiManagedSkillRequest()))
                .isInstanceOf(BizException.class)
                .hasMessage("内置技能不可编辑")
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    void declaresRestControllerContract() {
        assertThat(AiSkillAdminController.class.isAnnotationPresent(RestController.class)).isTrue();
    }

    private static MockMvc mockMvc(AiAgentManagementService service) {
        return MockMvcBuilders.standaloneSetup(new AiSkillAdminController(service)).build();
    }

    private static AiManagedSkill skill(String name) {
        AiSkillApiConfigResponse apiConfig = new AiSkillApiConfigResponse(
                true,
                "https://api.example.com",
                "/quality",
                "POST",
                Map.of(),
                3000,
                1001L);
        return new AiManagedSkill(name, "质检查询", true, 0, null, apiConfig, "api", true, null);
    }

    private static String skillPayload(String name) {
        return """
                {
                  "name": "%s",
                  "description": "质检查询",
                  "readOnly": true,
                  "apiConfig": {
                    "apiRegistryId": 1001,
                    "enabled": true,
                    "baseUrl": "https://api.example.com",
                    "path": "/quality",
                    "method": "POST",
                    "headers": {},
                    "timeoutMillis": 3000
                  }
                }
                """.formatted(name);
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl modules/module-ai/module-ai-core -am -Dtest=AiSkillAdminControllerTest -DfailIfNoTests=false test
```

Expected: FAIL at compilation because `controller.com.zimo.module.ai.AiSkillAdminController` does not exist.

---

### Task 2: 实现平台路径技能适配 Controller

**Files:**
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/controller/AiSkillAdminController.java`
- Test: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/controller/AiSkillAdminControllerTest.java`

**Interfaces:**
- Consumes:
  - `R.ok(T data)`
  - `R.ok()`
  - `BizException(int code, String message)`
  - `AiAgentManagementService` 的技能管理方法
- Produces:
  - `public R<List<AiManagedSkill>> list()`
  - `public R<AiManagedSkill> create(AiManagedSkillRequest request)`
  - `public R<AiManagedSkill> update(String name, AiManagedSkillRequest request)`
  - `public R<AiManagedSkill> updateApiConfig(String name, AiSkillApiConfigRequest request)`
  - `public R<AiManagedSkill> bindPromptTemplate(String name, AiSkillPromptTemplateRequest request)`
  - `public R<Void> delete(String name)`

- [x] **Step 1: Write minimal implementation**

Create `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/controller/AiSkillAdminController.java`:

```java
package com.zimo.module.ai.controller;

import com.zimo.framework.common.BizException;
import com.zimo.framework.common.ApiResponse;
import com.zimo.starter.ai.management.AiAgentManagementService;
import com.zimo.starter.ai.management.AiManagedSkill;
import com.zimo.starter.ai.management.AiManagedSkillRequest;
import com.zimo.starter.ai.management.AiSkillApiConfigRequest;
import com.zimo.starter.ai.management.AiSkillPromptTemplateRequest;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 模块技能管理适配接口。
 *
 * <p>本控制器面向平台业务路径 {@code /api/biz/ai/skills}，只负责统一返回体和模块边界适配。
 * 技能创建、编辑、删除、API 配置保存和绑定解除逻辑均委托给
 * {@link AiAgentManagementService}。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
@RestController
@RequestMapping("/api/biz/ai/skills")
public class AiSkillAdminController {

    private final AiAgentManagementService managementService;

    /**
     * 创建 AI 技能管理适配控制器。
     *
     * @param managementService AI starter 技能管理服务，不允许为 {@code null}
     * @throws NullPointerException 当管理服务为 {@code null} 时抛出
     */
    public AiSkillAdminController(AiAgentManagementService managementService) {
        this.managementService = Objects.requireNonNull(managementService, "managementService must not be null");
    }

    /**
     * 查询当前已注册的 AI 技能列表。
     *
     * @return 统一返回体，数据为技能列表
     */
    @GetMapping
    public R<List<AiManagedSkill>> list() {
        return R.ok(managementService.listSkills());
    }

    /**
     * 创建自定义 API 技能。
     *
     * @param request 技能创建请求
     * @return 统一返回体，数据为创建后的技能
     */
    @PostMapping
    public R<AiManagedSkill> create(@RequestBody AiManagedSkillRequest request) {
        return call(() -> managementService.createApiSkill(request));
    }

    /**
     * 编辑自定义 API 技能。
     *
     * @param name 技能名称
     * @param request 技能编辑请求
     * @return 统一返回体，数据为编辑后的技能
     */
    @PutMapping("/{name}")
    public R<AiManagedSkill> update(
            @PathVariable String name,
            @RequestBody AiManagedSkillRequest request) {
        return call(() -> managementService.updateApiSkill(name, request));
    }

    /**
     * 更新自定义 API 技能的远程调用配置。
     *
     * @param name 技能名称
     * @param request API 调用配置
     * @return 统一返回体，数据为更新后的技能
     */
    @PutMapping("/{name}/api-config")
    public R<AiManagedSkill> updateApiConfig(
            @PathVariable String name,
            @RequestBody AiSkillApiConfigRequest request) {
        return call(() -> managementService.updateApiSkillConfig(name, request));
    }

    /**
     * 绑定或清空技能提示词模板。
     *
     * @param name 技能名称
     * @param request 提示词模板绑定请求
     * @return 统一返回体，数据为更新后的技能
     */
    @PutMapping("/{name}/prompt-template")
    public R<AiManagedSkill> bindPromptTemplate(
            @PathVariable String name,
            @RequestBody AiSkillPromptTemplateRequest request) {
        Long promptTemplateId = request == null ? null : request.getPromptTemplateId();
        return call(() -> {
            AiManagedSkill skill = managementService.bindSkillPromptTemplate(name, promptTemplateId);
            if (skill == null) {
                throw new BizException(404, "技能不存在或未注册");
            }
            return skill;
        });
    }

    /**
     * 删除自定义 API 技能。
     *
     * @param name 技能名称
     * @return 不携带业务数据的统一成功返回体
     */
    @DeleteMapping("/{name}")
    public R<Void> delete(@PathVariable String name) {
        return call(() -> {
            if (!managementService.deleteApiSkill(name)) {
                throw new BizException(404, "API技能不存在");
            }
            return R.ok();
        });
    }

    private <T> R<T> call(ControllerAction<R<T>> action) {
        try {
            return action.execute();
        } catch (IllegalArgumentException exception) {
            throw new BizException(400, message(exception, "请求参数不合法"));
        }
    }

    private <T> R<T> call(ServiceAction<T> action) {
        return call(() -> R.ok(action.execute()));
    }

    private String message(RuntimeException exception, String fallback) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? fallback
                : exception.getMessage();
    }

    @FunctionalInterface
    private interface ServiceAction<T> {
        T execute();
    }

    @FunctionalInterface
    private interface ControllerAction<T> {
        T execute();
    }
}
```

- [x] **Step 2: Run focused test**

Run:

```bash
mvn -pl modules/module-ai/module-ai-core -am -Dtest=AiSkillAdminControllerTest -DfailIfNoTests=false test
```

Expected: PASS.

- [x] **Step 3: Check code size and comments**

Run:

```bash
powershell -Command "(Get-Content -Encoding UTF8 modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/controller/AiSkillAdminController.java | Measure-Object -Line).Lines"
```

Expected: Controller is below 400 lines and methods are below 80 effective code lines.

---

### Task 3: 模块自动装配与集成验证

**Files:**
- Update: `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfiguration.java`
- Create: `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiSkillAdminAutoConfiguration.java`
- Update: `modules/module-ai/module-ai-autoconfig/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Update: `modules/module-ai/module-ai-autoconfig/src/test/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfigurationTest.java`

**Interfaces:**
- Consumes: `AiAgentManagementService`
- Produces: `AiSkillAdminController` 的条件自动装配。

- [x] **Step 1: 写入自动装配边界测试**

验证 `AiAgentManagementService` 缺失时不注册 `AiSkillAdminController`，存在时注册平台技能管理 Controller。

- [x] **Step 2: 调整自动装配结构**

`AiModuleAutoConfiguration` 仅注册插件基础 Bean；`AiSkillAdminAutoConfiguration` 在 `AiAgentAutoConfiguration` 之后生效，并通过 `@ConditionalOnBean(AiAgentManagementService.class)` 注册 Controller。

- [x] **Step 3: 运行 module-ai 测试**

Run:

```bash
mvn -q -pl modules/module-ai/module-ai-core,modules/module-ai/module-ai-autoconfig -am "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS for `module-ai-core` and `module-ai-autoconfig` tests.

- [x] **Step 4: Run AI starter management regression tests**

Run:

```bash
mvn -q -pl modules/ai-agent-spring-boot-starter "-Dtest=AiAgentManagementControllerTest,AiAgentManagementServiceTest" "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS. Existing `/api/ai/skills` behavior remains unchanged.

---

### Task 4: 最终审查与差异确认

**Files:**
- Review: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/controller/AiSkillAdminController.java`
- Review: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/controller/AiSkillAdminControllerTest.java`
- Review: `docs/superpowers/specs/2026-07-23-module-ai-skill-admin-actions-design.md`
- Review: `docs/superpowers/plans/2026-07-23-module-ai-skill-admin-actions.md`

**Interfaces:**
- Consumes: completed implementation and tests.
- Produces: review-ready diff with no unrelated changes.

- [x] **Step 1: Run diff check**

Run:

```bash
git diff --check -- modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/controller/AiSkillAdminController.java modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/controller/AiSkillAdminControllerTest.java docs/superpowers/specs/2026-07-23-module-ai-skill-admin-actions-design.md docs/superpowers/plans/2026-07-23-module-ai-skill-admin-actions.md
```

Expected: No whitespace errors. LF/CRLF warnings are acceptable.

- [x] **Step 2: Review changed files**

Run:

```bash
git diff -- modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/controller/AiSkillAdminController.java modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/controller/AiSkillAdminControllerTest.java docs/superpowers/specs/2026-07-23-module-ai-skill-admin-actions-design.md docs/superpowers/plans/2026-07-23-module-ai-skill-admin-actions.md
```

Expected: Diff only contains the `module-ai`技能适配 Controller、测试和中文文档。

- [x] **Step 3: Request code review**

Ask an independent reviewer to check:

```text
请只读审查 module-ai 新增的 AiSkillAdminController 和测试。
重点检查：
1. 是否复用 AiAgentManagementService，没有复制业务逻辑。
2. 是否统一返回 R<T>。
3. 是否没有引入 module-sys 依赖。
4. 删除不存在技能、内置技能编辑删除的错误转换是否清晰。
5. 是否不影响已有 /api/ai/skills。
```

Expected: Critical 0，Important 0。

---

## 自检

- Spec coverage: `/api/biz/ai/skills` 查询、创建、编辑、删除、API 配置、提示词绑定、`R<T>` 返回体、错误转换、内置技能保护都已映射到任务。
- Placeholder scan: 计划未包含 TBD、TODO、待定或未定义接口。
- Type consistency: `AiManagedSkill`、`AiManagedSkillRequest`、`AiSkillApiConfigRequest`、`AiSkillPromptTemplateRequest`、`AiAgentManagementService` 均来自现有 starter 包。
- Scope check: 本计划只新增 `module-ai` 后端适配层，不改前端、不改数据库、不迁移 starter 管理接口。

## 实际执行结果

- 已新增 `AiSkillAdminController`，对外暴露 `/api/biz/ai/skills` 平台路径接口。
- 已新增 `AiSkillAdminAutoConfiguration`，在 `AiAgentManagementService` 存在时按需注册 Controller。
- 已移除 `AiModuleAutoConfiguration` 的全包组件扫描，避免可选管理服务缺失时启动失败。
- 已补充 Controller 行为测试和自动装配边界测试。
- 已完成代码审查：Critical 0；Important 1 项已修复。
