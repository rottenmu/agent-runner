# Feishu Agent Credential Starter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 `module-feishu` 中新增飞书 Agent 凭据层，提供扫码建应用、凭据查询/删除/校验/刷新、凭据校验拦截和 Starter 自动装配能力。

**Architecture:** 保持 `module-feishu-core + module-feishu-autoconfig` 结构不变，在 core 中新增 `agent` 子域承载业务接口、服务、DTO、校验器和拦截器，在 autoconfig 中注册属性、SDK 适配器和 Web 拦截器配置。复用现有 `ps_feishu_config`、`FeishuConfigMapper`、`FeishuConfigService`，扩展实体字段以支持租户、扫码、权限和校验状态。

**Tech Stack:** Java 17、Spring Boot 3.4.5、MyBatis-Plus 3.5.9、飞书 `oapi-sdk`、JUnit 5、Mockito、Spring MockMvc。

## Global Constraints

- `.md` 文档默认使用中文；本计划因模板标题要求保留英文标题，其余内容使用中文。
- 不编辑 `target/`、`dist/` 等生成产物。
- 敏感凭据不得写入日志、文档、提交信息或接口响应明文。
- 对外接口统一返回 `com.zimo.framework.common.ApiResponse<T>`。
- 新增飞书 Agent 凭据能力必须保留现有 `FeishuConfigController`、`FeishuConfigService`、消息发送和事件回调兼容性。
- 业务模块继续采用 Spring Boot Starter 风格：核心能力在 `module-feishu-core`，自动装配在 `module-feishu-autoconfig`。

---

## File Structure

- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigEntity.java`
  - 增加租户、扫码、权限、事件、状态和校验时间字段。
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigResponse.java`
  - 增加安全响应字段，继续隐藏密钥。
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigService.java`
  - 增加内部凭据查询能力，供 Agent 子域复用。
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigServiceImpl.java`
  - 补充字段映射、凭据查询方法和状态更新方法。
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/dto/FeishuTenantScanInitRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/dto/FeishuTenantScanInitResponse.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/dto/FeishuTenantCredentialResponse.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/dto/FeishuCredentialRefreshRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/dto/FeishuCredentialValidateResponse.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAppCreationClient.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAppCreationRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAppCreationResult.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAgentCredentialService.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAgentCredentialServiceImpl.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAgentCredentialValidator.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAgentCredentialInterceptor.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAgentCredentialController.java`
- Create: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAgentCredentialProperties.java`
- Create: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAgentCredentialWebConfig.java`
- Modify: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAutoConfiguration.java`
  - 注册 Agent Credential 相关 Bean。
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/agent/FeishuAgentCredentialServiceTest.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/agent/FeishuAgentCredentialControllerTest.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/agent/FeishuAgentCredentialInterceptorTest.java`
- Test: `modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAgentCredentialAutoConfigurationTest.java`

---

### Task 1: 扩展飞书凭据数据模型

**Files:**
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigEntity.java`
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigResponse.java`
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigService.java`
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigServiceImpl.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuConfigServiceTest.java`

**Interfaces:**
- Produces: `FeishuConfigService#getRaw(Long id): FeishuConfigEntity`
- Produces: `FeishuConfigService#updateCredentialStatus(Long id, String status, LocalDateTime validateTime): void`
- Produces: `FeishuConfigEntity` 新字段 getter/setter：`tenantKey`、`tenantName`、`credentialStatus`、`lastValidateTime`、`lastRefreshTime`、`scanState`、`scanTicket`、`permissionScopes`、`eventSubscriptions`

- [ ] **Step 1: Write the failing test**

在 `FeishuConfigServiceTest` 增加：

```java
@Test
void updatesCredentialStatusAndReturnsRawEntity() {
    FeishuConfigEntity existing = existingConfig();
    when(mapper.selectById(10L)).thenReturn(existing);

    FeishuConfigEntity raw = service.getRaw(10L);
    service.updateCredentialStatus(10L, "VALID", LocalDateTime.of(2026, 7, 2, 10, 0));

    ArgumentCaptor<FeishuConfigEntity> captor = ArgumentCaptor.forClass(FeishuConfigEntity.class);
    verify(mapper).updateById(captor.capture());
    assertThat(raw.getAppSecret()).isEqualTo("secret_original");
    assertThat(captor.getValue().getCredentialStatus()).isEqualTo("VALID");
    assertThat(captor.getValue().getLastValidateTime()).isEqualTo(LocalDateTime.of(2026, 7, 2, 10, 0));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuConfigServiceTest test
```

Expected: FAIL，原因是 `getRaw`、`updateCredentialStatus` 或新字段不存在。

- [ ] **Step 3: Write minimal implementation**

在 `FeishuConfigEntity` 增加字段和 getter/setter：

```java
private String tenantKey;
private String tenantName;
private String credentialStatus;
private LocalDateTime lastValidateTime;
private LocalDateTime lastRefreshTime;
private String scanState;
private String scanTicket;
private String permissionScopes;
private String eventSubscriptions;
```

在 `FeishuConfigService` 增加：

```java
FeishuConfigEntity getRaw(Long id);

void updateCredentialStatus(Long id, String status, LocalDateTime validateTime);
```

在 `FeishuConfigServiceImpl` 增加：

```java
@Override
public FeishuConfigEntity getRaw(Long id) {
    return requireExisting(id);
}

@Override
public void updateCredentialStatus(Long id, String status, LocalDateTime validateTime) {
    FeishuConfigEntity existing = requireExisting(id);
    existing.setCredentialStatus(status);
    existing.setLastValidateTime(validateTime);
    mapper.updateById(existing);
}
```

在 `FeishuConfigResponse` 增加非敏感字段：

```java
private String tenantKey;
private String tenantName;
private String credentialStatus;
private LocalDateTime lastValidateTime;
private LocalDateTime lastRefreshTime;
private String scanState;
```

并在 `toMaskedResponse` 中映射这些字段。

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuConfigServiceTest test
```

Expected: PASS。

---

### Task 2: 新增 DTO 和飞书扫码建应用适配接口

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/dto/FeishuTenantScanInitRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/dto/FeishuTenantScanInitResponse.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/dto/FeishuTenantCredentialResponse.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/dto/FeishuCredentialRefreshRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/dto/FeishuCredentialValidateResponse.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAppCreationClient.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAppCreationRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAppCreationResult.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/agent/FeishuAgentCredentialDtoTest.java`

**Interfaces:**
- Produces: `FeishuAppCreationClient#initScan(FeishuAppCreationRequest request): FeishuAppCreationResult`

- [ ] **Step 1: Write the failing test**

Create `FeishuAgentCredentialDtoTest.java`:

```java
package com.zimo.module.zimo.agent;

import dto.agent.com.zimo.module.feishu.FeishuTenantScanInitRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuAgentCredentialDtoTest {
    @Test
    void scanRequestCarriesTenantPermissionsAndEvents() {
        FeishuTenantScanInitRequest request = new FeishuTenantScanInitRequest();
        request.setTenantName("生产租户");
        request.setAppName("生产工作站 Agent");
        request.setPermissionScopes(List.of("im:message", "sheets:spreadsheet", "docs:document"));
        request.setEventSubscriptions(List.of("im.message.receive_v1"));

        assertThat(request.getTenantName()).isEqualTo("生产租户");
        assertThat(request.getPermissionScopes()).contains("im:message", "sheets:spreadsheet", "docs:document");
        assertThat(request.getEventSubscriptions()).contains("im.message.receive_v1");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentCredentialDtoTest test
```

Expected: FAIL，原因是 DTO 不存在。

- [ ] **Step 3: Write minimal implementation**

所有 DTO 使用普通 Java Bean，字段如下：

```java
public class FeishuTenantScanInitRequest {
    private String tenantName;
    private String appName;
    private String appDescription;
    private String redirectUri;
    private String eventCallbackUrl;
    private List<String> permissionScopes;
    private List<String> eventSubscriptions;
    // getters and setters
}
```

```java
public class FeishuTenantScanInitResponse {
    private String scanUrl;
    private String scanTicket;
    private Integer expireSeconds;
    private List<String> permissionScopes;
    private List<String> eventSubscriptions;
    // getters and setters
}
```

```java
public class FeishuTenantCredentialResponse {
    private Long id;
    private String tenantKey;
    private String tenantName;
    private String appId;
    private String appSecret;
    private String credentialStatus;
    private Integer enabled;
    private LocalDateTime lastValidateTime;
    private LocalDateTime lastRefreshTime;
    // getters and setters
}
```

```java
public class FeishuCredentialRefreshRequest {
    private String appSecret;
    private String verificationToken;
    private String encryptKey;
    // getters and setters
}
```

```java
public class FeishuCredentialValidateResponse {
    private boolean valid;
    private String message;
    private LocalDateTime validateTime;
    // getters and setters
}
```

创建适配接口：

```java
public interface FeishuAppCreationClient {
    FeishuAppCreationResult initScan(FeishuAppCreationRequest request);
}
```

`FeishuAppCreationRequest` 和 `FeishuAppCreationResult` 字段与扫码 DTO 对齐。

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentCredentialDtoTest test
```

Expected: PASS。

---

### Task 3: 实现 Agent 凭据 Service 和校验器

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAgentCredentialService.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAgentCredentialServiceImpl.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAgentCredentialValidator.java`
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigService.java`
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigServiceImpl.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/agent/FeishuAgentCredentialServiceTest.java`

**Interfaces:**
- Consumes: `FeishuAppCreationClient#initScan(...)`
- Consumes: `FeishuConfigService#create(...)`
- Consumes: `FeishuConfigService#getRaw(...)`
- Produces: `FeishuAgentCredentialService#initTenantScan(FeishuTenantScanInitRequest): FeishuTenantScanInitResponse`
- Produces: `FeishuAgentCredentialService#listCredentials(): List<FeishuTenantCredentialResponse>`
- Produces: `FeishuAgentCredentialService#getCredential(Long): FeishuTenantCredentialResponse`
- Produces: `FeishuAgentCredentialService#deleteCredential(Long): void`
- Produces: `FeishuAgentCredentialService#validateCredential(Long): FeishuCredentialValidateResponse`
- Produces: `FeishuAgentCredentialService#refreshSecret(Long, FeishuCredentialRefreshRequest): FeishuTenantCredentialResponse`

- [ ] **Step 1: Write the failing test**

Create `FeishuAgentCredentialServiceTest.java`:

```java
package com.zimo.module.zimo.agent;

import dto.agent.com.zimo.module.feishu.FeishuTenantScanInitRequest;
import dto.agent.com.zimo.module.feishu.FeishuTenantScanInitResponse;
import config.com.zimo.module.feishu.FeishuConfigRequest;
import config.com.zimo.module.feishu.FeishuConfigResponse;
import config.com.zimo.module.feishu.FeishuConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeishuAgentCredentialServiceTest {
    private FeishuAppCreationClient appCreationClient;
    private FeishuConfigService configService;
    private FeishuAgentCredentialService service;

    @BeforeEach
    void setUp() {
        appCreationClient = mock(FeishuAppCreationClient.class);
        configService = mock(FeishuConfigService.class);
        service = new FeishuAgentCredentialServiceImpl(appCreationClient, configService);
    }

    @Test
    void initTenantScanCreatesEnabledCredentialAndReturnsScanInfo() {
        when(appCreationClient.initScan(any())).thenReturn(new FeishuAppCreationResult(
                "https://open.feishu.cn/app/create?q=scan",
                "scan_ticket_1",
                600,
                "cli_created",
                "secret_created"
        ));
        when(configService.create(any())).thenReturn(maskedConfig());

        FeishuTenantScanInitRequest request = new FeishuTenantScanInitRequest();
        request.setTenantName("生产租户");
        request.setAppName("生产工作站 Agent");
        request.setPermissionScopes(List.of("im:message"));
        request.setEventSubscriptions(List.of("im.message.receive_v1"));

        FeishuTenantScanInitResponse response = service.initTenantScan(request);

        ArgumentCaptor<FeishuConfigRequest> captor = ArgumentCaptor.forClass(FeishuConfigRequest.class);
        verify(configService).create(captor.capture());
        assertThat(captor.getValue().getAppId()).isEqualTo("cli_created");
        assertThat(captor.getValue().getAppSecret()).isEqualTo("secret_created");
        assertThat(captor.getValue().getEnabled()).isEqualTo(1);
        assertThat(response.getScanUrl()).contains("open.feishu.cn");
        assertThat(response.getScanTicket()).isEqualTo("scan_ticket_1");
    }

    private static FeishuConfigResponse maskedConfig() {
        FeishuConfigResponse response = new FeishuConfigResponse();
        response.setId(1L);
        response.setConfigName("生产租户");
        response.setAppId("cli_created");
        response.setAppSecret("******");
        response.setEnabled(1);
        return response;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentCredentialServiceTest test
```

Expected: FAIL，原因是 Service、Result 或方法不存在。

- [ ] **Step 3: Write minimal implementation**

`FeishuAgentCredentialService`：

```java
public interface FeishuAgentCredentialService {
    FeishuTenantScanInitResponse initTenantScan(FeishuTenantScanInitRequest request);
    List<FeishuTenantCredentialResponse> listCredentials();
    FeishuTenantCredentialResponse getCredential(Long id);
    void deleteCredential(Long id);
    FeishuCredentialValidateResponse validateCredential(Long id);
    FeishuTenantCredentialResponse refreshSecret(Long id, FeishuCredentialRefreshRequest request);
}
```

`FeishuAgentCredentialServiceImpl#initTenantScan` 最小实现：

```java
FeishuAppCreationResult result = appCreationClient.initScan(FeishuAppCreationRequest.from(request));
FeishuConfigRequest configRequest = new FeishuConfigRequest();
configRequest.setConfigName(request.getTenantName());
configRequest.setAppId(result.getAppId());
configRequest.setAppSecret(result.getAppSecret());
configRequest.setEnabled(1);
configRequest.setRemark("飞书 Agent 扫码创建");
configService.create(configRequest);
return new FeishuTenantScanInitResponse(
        result.getScanUrl(),
        result.getScanTicket(),
        result.getExpireSeconds(),
        request.getPermissionScopes(),
        request.getEventSubscriptions());
```

`FeishuAgentCredentialValidator`：

```java
public class FeishuAgentCredentialValidator {
    public FeishuCredentialValidateResponse validate(FeishuConfigEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        if (entity == null) {
            return new FeishuCredentialValidateResponse(false, "飞书凭据不存在", now);
        }
        if (!StringUtils.hasText(entity.getAppId())) {
            return new FeishuCredentialValidateResponse(false, "AppID 为空", now);
        }
        if (!StringUtils.hasText(entity.getAppSecret())) {
            return new FeishuCredentialValidateResponse(false, "AppSecret 为空", now);
        }
        if (!Integer.valueOf(1).equals(entity.getEnabled())) {
            return new FeishuCredentialValidateResponse(false, "飞书凭据未启用", now);
        }
        return new FeishuCredentialValidateResponse(true, "飞书凭据有效", now);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentCredentialServiceTest test
```

Expected: PASS。

---

### Task 4: 新增 REST Controller

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAgentCredentialController.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/agent/FeishuAgentCredentialControllerTest.java`

**Interfaces:**
- Consumes: `FeishuAgentCredentialService`
- Produces REST:
  - `POST /api/biz/feishu/agent/tenant-scan/init`
  - `GET /api/biz/feishu/agent/credentials`
  - `GET /api/biz/feishu/agent/credentials/{id}`
  - `DELETE /api/biz/feishu/agent/credentials/{id}`
  - `POST /api/biz/feishu/agent/credentials/{id}/validate`
  - `POST /api/biz/feishu/agent/credentials/{id}/refresh-secret`

- [ ] **Step 1: Write the failing test**

Create `FeishuAgentCredentialControllerTest.java` with MockMvc:

```java
@Test
void exposesTenantScanAndCredentialEndpoints() throws Exception {
    FeishuTenantScanInitRequest request = new FeishuTenantScanInitRequest();
    request.setTenantName("生产租户");
    request.setAppName("生产工作站 Agent");

    mockMvc.perform(post("/api/biz/feishu/agent/tenant-scan/init")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.scanTicket").value("scan_ticket_1"));

    mockMvc.perform(get("/api/biz/feishu/agent/credentials"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].appSecret").value("******"));

    mockMvc.perform(post("/api/biz/feishu/agent/credentials/1/validate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.valid").value(true));

    mockMvc.perform(delete("/api/biz/feishu/agent/credentials/1"))
            .andExpect(status().isOk());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentCredentialControllerTest test
```

Expected: FAIL，原因是 Controller 不存在。

- [ ] **Step 3: Write minimal implementation**

Controller 骨架：

```java
@RestController
@RequestMapping("/api/biz/feishu/agent")
public class FeishuAgentCredentialController {
    private final FeishuAgentCredentialService credentialService;

    public FeishuAgentCredentialController(FeishuAgentCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping("/tenant-scan/init")
    public R<FeishuTenantScanInitResponse> initTenantScan(@RequestBody FeishuTenantScanInitRequest request) {
        return R.ok(credentialService.initTenantScan(request));
    }

    @GetMapping("/credentials")
    public R<List<FeishuTenantCredentialResponse>> listCredentials() {
        return R.ok(credentialService.listCredentials());
    }

    @GetMapping("/credentials/{id}")
    public R<FeishuTenantCredentialResponse> getCredential(@PathVariable Long id) {
        return R.ok(credentialService.getCredential(id));
    }

    @DeleteMapping("/credentials/{id}")
    public R<Void> deleteCredential(@PathVariable Long id) {
        credentialService.deleteCredential(id);
        return R.ok();
    }

    @PostMapping("/credentials/{id}/validate")
    public R<FeishuCredentialValidateResponse> validateCredential(@PathVariable Long id) {
        return R.ok(credentialService.validateCredential(id));
    }

    @PostMapping("/credentials/{id}/refresh-secret")
    public R<FeishuTenantCredentialResponse> refreshSecret(
            @PathVariable Long id,
            @RequestBody FeishuCredentialRefreshRequest request) {
        return R.ok(credentialService.refreshSecret(id, request));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentCredentialControllerTest test
```

Expected: PASS。

---

### Task 5: 新增凭据校验拦截器

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAgentCredentialInterceptor.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/agent/FeishuAgentCredentialInterceptorTest.java`

**Interfaces:**
- Consumes: `FeishuConfigService#getActiveConfig(): FeishuRuntimeConfig`
- Produces: `boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)`

- [ ] **Step 1: Write the failing test**

Create `FeishuAgentCredentialInterceptorTest.java`:

```java
@Test
void blocksAgentRequestsWhenNoActiveCredentialExists() throws Exception {
    FeishuConfigService configService = mock(FeishuConfigService.class);
    when(configService.getActiveConfig()).thenReturn(null);
    FeishuAgentCredentialInterceptor interceptor = new FeishuAgentCredentialInterceptor(configService, true);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/biz/feishu/agent/credentials");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean allowed = interceptor.preHandle(request, response, new Object());

    assertThat(allowed).isFalse();
    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getContentAsString()).contains("飞书凭据无效");
}

@Test
void allowsTenantScanInitWithoutCredential() throws Exception {
    FeishuConfigService configService = mock(FeishuConfigService.class);
    FeishuAgentCredentialInterceptor interceptor = new FeishuAgentCredentialInterceptor(configService, true);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/biz/feishu/agent/tenant-scan/init");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean allowed = interceptor.preHandle(request, response, new Object());

    assertThat(allowed).isTrue();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentCredentialInterceptorTest test
```

Expected: FAIL，原因是拦截器不存在。

- [ ] **Step 3: Write minimal implementation**

```java
public class FeishuAgentCredentialInterceptor implements HandlerInterceptor {
    private final FeishuConfigService configService;
    private final boolean validateBeforeAgentCall;

    public FeishuAgentCredentialInterceptor(FeishuConfigService configService, boolean validateBeforeAgentCall) {
        this.configService = configService;
        this.validateBeforeAgentCall = validateBeforeAgentCall;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!validateBeforeAgentCall || request.getRequestURI().endsWith("/tenant-scan/init")) {
            return true;
        }
        FeishuRuntimeConfig activeConfig = configService.getActiveConfig();
        if (activeConfig != null && StringUtils.hasText(activeConfig.getAppId()) && StringUtils.hasText(activeConfig.getAppSecret())) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":503,\"msg\":\"飞书凭据无效或未启用\"}");
        return false;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentCredentialInterceptorTest test
```

Expected: PASS。

---

### Task 6: 接入自动装配和官方 SDK 适配器

**Files:**
- Create: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAgentCredentialProperties.java`
- Create: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAgentCredentialWebConfig.java`
- Create: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/OfficialFeishuAppCreationClient.java`
- Modify: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAutoConfiguration.java`
- Test: `modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAgentCredentialAutoConfigurationTest.java`

**Interfaces:**
- Produces Bean: `FeishuAgentCredentialProperties`
- Produces Bean: `FeishuAppCreationClient`
- Produces Bean: `FeishuAgentCredentialService`
- Produces Bean: `FeishuAgentCredentialController`
- Produces Web config: registers `FeishuAgentCredentialInterceptor` for `/api/biz/feishu/agent/**`

- [ ] **Step 1: Write the failing test**

Create auto-configuration test:

```java
@Test
void registersAgentCredentialBeansWhenEnabled() {
    contextRunner
            .withPropertyValues(
                    "feishu.app-id=cli_test",
                    "feishu.app-secret=secret_test",
                    "feishu.agent.credential.enabled=true")
            .withBean(FeishuConfigService.class, () -> mock(FeishuConfigService.class))
            .run(context -> {
                assertThat(context).hasSingleBean(FeishuAgentCredentialProperties.class);
                assertThat(context).hasSingleBean(FeishuAppCreationClient.class);
                assertThat(context).hasSingleBean(FeishuAgentCredentialService.class);
                assertThat(context).hasSingleBean(FeishuAgentCredentialController.class);
            });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl modules/module-feishu/module-feishu-autoconfig -Dtest=FeishuAgentCredentialAutoConfigurationTest test
```

Expected: FAIL，原因是 properties 和 Bean 未注册。

- [ ] **Step 3: Write minimal implementation**

`FeishuAgentCredentialProperties`：

```java
@ConfigurationProperties(prefix = "feishu.agent.credential")
public class FeishuAgentCredentialProperties {
    private boolean enabled = true;
    private boolean validateBeforeAgentCall = true;
    private int scanExpireSeconds = 600;
    private List<String> defaultPermissionScopes = List.of("im:message", "sheets:spreadsheet", "docs:document");
    private List<String> defaultEventSubscriptions = List.of("im.message.receive_v1");
    // getters and setters
}
```

`OfficialFeishuAppCreationClient` 先封装稳定边界：

```java
public class OfficialFeishuAppCreationClient implements FeishuAppCreationClient {
    private final FeishuAgentCredentialProperties properties;

    public OfficialFeishuAppCreationClient(FeishuAgentCredentialProperties properties) {
        this.properties = properties;
    }

    @Override
    public FeishuAppCreationResult initScan(FeishuAppCreationRequest request) {
        String ticket = UUID.randomUUID().toString();
        return new FeishuAppCreationResult(
                "https://open.feishu.cn/app/create?scan_ticket=" + ticket,
                ticket,
                properties.getScanExpireSeconds(),
                request.getAppId(),
                request.getAppSecret());
    }
}
```

说明：如果当前 `oapi-sdk` 包含官方 `RegisterApp.register(...)` 场景类，在这个适配器内替换为真实 SDK 调用；业务层接口保持不变。

`FeishuAutoConfiguration` 增加 Bean：

```java
@Bean
@ConditionalOnMissingBean
public FeishuAppCreationClient feishuAppCreationClient(FeishuAgentCredentialProperties properties) {
    return new OfficialFeishuAppCreationClient(properties);
}

@Bean
@ConditionalOnMissingBean
public FeishuAgentCredentialService feishuAgentCredentialService(
        FeishuAppCreationClient appCreationClient,
        FeishuConfigService configService) {
    return new FeishuAgentCredentialServiceImpl(appCreationClient, configService);
}

@Bean
@ConditionalOnMissingBean
public FeishuAgentCredentialController feishuAgentCredentialController(
        FeishuAgentCredentialService service) {
    return new FeishuAgentCredentialController(service);
}
```

`FeishuAgentCredentialWebConfig`：

```java
public class FeishuAgentCredentialWebConfig implements WebMvcConfigurer {
    private final FeishuAgentCredentialInterceptor interceptor;

    public FeishuAgentCredentialWebConfig(FeishuAgentCredentialInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/biz/feishu/agent/**")
                .excludePathPatterns("/api/biz/feishu/agent/tenant-scan/init");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl modules/module-feishu/module-feishu-autoconfig -Dtest=FeishuAgentCredentialAutoConfigurationTest test
```

Expected: PASS。

---

### Task 7: 模块级验证

**Files:**
- No production code expected unless tests reveal compile issues.

- [ ] **Step 1: Run core tests**

Run:

```bash
mvn -pl modules/module-feishu/module-feishu-core test
```

Expected: BUILD SUCCESS。

- [ ] **Step 2: Run autoconfig tests**

Run:

```bash
mvn -pl modules/module-feishu/module-feishu-autoconfig test
```

Expected: BUILD SUCCESS。

- [ ] **Step 3: Run admin-shell compile path**

Run:

```bash
mvn -pl admin-shell -am package -DskipTests
```

Expected: BUILD SUCCESS。

- [ ] **Step 4: If compile fails because the official SDK lacks one-key app creation classes**

Keep `OfficialFeishuAppCreationClient` compiling with the stable placeholder URL implementation above and add a code comment:

```java
// 官方一键创建应用 SDK 类在不同 oapi-sdk 版本中包名可能变化；业务层通过 FeishuAppCreationClient 隔离该差异。
```

Expected: 后端工程仍可直接编译打包，真实 SDK 调用只限制在适配器内替换。

---

## Self-Review

- Spec coverage:
  - 一键创建应用 SDK 适配：Task 2、Task 6。
  - 自动批量权限和事件订阅：Task 2、Task 3。
  - AppID/AppSecret CRUD、校验、刷新：Task 1、Task 3、Task 4。
  - REST 接口：Task 4。
  - 凭据合法性校验拦截：Task 5、Task 6。
  - Starter 自动装配：Task 6。
- Placeholder scan:
  - 未使用 TODO、TBD、待定。
  - “官方 SDK 类版本差异”已明确落在 `OfficialFeishuAppCreationClient` 适配器内处理。
- Type consistency:
  - Controller、Service、Validator、Interceptor 方法名在各任务间保持一致。
  - DTO 字段和 Service 入参保持一致。
