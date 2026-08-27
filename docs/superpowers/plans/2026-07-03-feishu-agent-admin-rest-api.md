# 飞书 Agent 管理调试 Rest 接口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 `module-feishu` 增加一组统一的 `/api/biz/feishu/admin/**` 管理调试接口，覆盖扫码初始化、连接状态、CLI 表格写入调试、消息日志分页和机器人启停。

**Architecture:** 在 `module-feishu-core` 新增 `admin` 包，Controller 只负责权限校验、入参接收和统一返回，业务编排放到 `FeishuAgentAdminService`。在 `module-feishu-autoconfig` 新增 `FeishuAdminProperties` 并通过 `FeishuAutoConfiguration` 按 `feishu.admin.enabled` 条件装配管理接口能力。

**Tech Stack:** Java 17, Spring Boot 3.4.5, Maven, MyBatis Plus, JUnit 5, Mockito, AssertJ, `com.zimo.framework.common.ApiResponse`。

## Global Constraints

- 后端模块保持插件边界：核心业务放 `modules/module-feishu/module-feishu-core`，自动装配放 `modules/module-feishu/module-feishu-autoconfig`。
- 管理接口统一路径前缀为 `/api/biz/feishu/admin`，返回体统一使用 `R<T>`。
- 不新增数据库表，不改动已有凭据层、Channel SDK 层、CLI 执行层对外契约。
- 不在接口响应、日志和测试断言中暴露 `AppSecret`、`VerificationToken`、`EncryptKey` 明文。
- 管理 token 通过请求头 `X-Feishu-Admin-Token` 和配置项 `feishu.admin.api-token` 校验；配置为空时允许本地调试访问。
- 本仓库新增或修改的 `.md` 文档使用中文。
- 不编辑 `target/`、`dist/` 等生成产物。

---

## 文件结构

- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/admin/FeishuAdminPermissionGuard.java`
  - 轻量管理接口权限校验，只依赖 `apiToken` 字符串。
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/admin/FeishuAgentAdminService.java`
  - 统一编排凭据层、Channel SDK 层、CLI 执行层、日志服务和配置服务。
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/admin/FeishuAgentAdminController.java`
  - 暴露 `/api/biz/feishu/admin/**` Rest 接口。
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/admin/dto/*.java`
  - 管理接口入参与响应 DTO。
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/log/FeishuMessageLogService.java`
  - 增加分页查询方法，Mapper 缺失或日志关闭时返回空分页。
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigService.java`
  - 增加当前启用配置摘要查询和停用能力。
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigServiceImpl.java`
  - 实现启用配置摘要查询和停用当前配置。
- Create: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAdminProperties.java`
  - 绑定 `feishu.admin` 配置。
- Modify: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAutoConfiguration.java`
  - 条件装配管理接口相关 Bean。
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/admin/*Test.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/log/FeishuMessageLogServiceTest.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuConfigServiceImplTest.java`
- Test: `modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAdminAutoConfigurationTest.java`

---

### Task 1: 管理接口权限 Guard 与配置属性

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/admin/FeishuAdminPermissionGuard.java`
- Create: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/admin/FeishuAdminPermissionGuardTest.java`
- Create: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAdminProperties.java`
- Test: `modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAdminPropertiesTest.java`

**Interfaces:**
- Produces: `new FeishuAdminPermissionGuard(String apiToken)`
- Produces: `boolean FeishuAdminPermissionGuard.isAllowed(String requestToken)`
- Produces: `FeishuAdminProperties#isEnabled()`, `setEnabled(boolean)`, `getApiToken()`, `setApiToken(String)`

- [ ] **Step 1: 写权限 Guard 失败测试**

在 `FeishuAdminPermissionGuardTest` 中增加：

```java
package com.zimo.module.zimo.admin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuAdminPermissionGuardTest {

    @Test
    void shouldAllowWhenApiTokenIsBlankForLocalDebugging() {
        FeishuAdminPermissionGuard guard = new FeishuAdminPermissionGuard(" ");

        assertThat(guard.isAllowed(null)).isTrue();
        assertThat(guard.isAllowed("any-token")).isTrue();
    }

    @Test
    void shouldAllowWhenRequestTokenMatchesConfiguredToken() {
        FeishuAdminPermissionGuard guard = new FeishuAdminPermissionGuard("secret-token");

        assertThat(guard.isAllowed("secret-token")).isTrue();
    }

    @Test
    void shouldRejectWhenRequestTokenDoesNotMatchConfiguredToken() {
        FeishuAdminPermissionGuard guard = new FeishuAdminPermissionGuard("secret-token");

        assertThat(guard.isAllowed(null)).isFalse();
        assertThat(guard.isAllowed("wrong-token")).isFalse();
    }
}
```

- [ ] **Step 2: 运行失败测试**

Run:

```powershell
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAdminPermissionGuardTest test
```

Expected: FAIL，提示 `FeishuAdminPermissionGuard` 不存在。

- [ ] **Step 3: 实现权限 Guard**

创建 `FeishuAdminPermissionGuard.java`：

```java
package com.zimo.module.zimo.admin;

import org.springframework.util.StringUtils;

/**
 * 飞书 Agent 管理调试接口的轻量权限校验器。
 *
 * <p>该 Guard 不依赖系统 RBAC 上下文，避免管理调试接口与业务权限强耦合。
 * 当 apiToken 未配置时默认放行，方便本地开发和 Postman 调试；生产环境应配置
 * feishu.admin.api-token。</p>
 */
public class FeishuAdminPermissionGuard {

    private final String apiToken;

    public FeishuAdminPermissionGuard(String apiToken) {
        this.apiToken = apiToken;
    }

    public boolean isAllowed(String requestToken) {
        if (!StringUtils.hasText(apiToken)) {
            return true;
        }
        return apiToken.equals(requestToken);
    }
}
```

- [ ] **Step 4: 运行 Guard 测试通过**

Run:

```powershell
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAdminPermissionGuardTest test
```

Expected: PASS。

- [ ] **Step 5: 写配置属性失败测试**

创建 `FeishuAdminPropertiesTest.java`：

```java
package com.zimo.module.zimo.autoconfig;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuAdminPropertiesTest {

    @Test
    void shouldUseLocalDebugDefaults() {
        FeishuAdminProperties properties = new FeishuAdminProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getApiToken()).isNull();
    }

    @Test
    void shouldAllowOverrideValues() {
        FeishuAdminProperties properties = new FeishuAdminProperties();
        properties.setEnabled(false);
        properties.setApiToken("secret-token");

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getApiToken()).isEqualTo("secret-token");
    }
}
```

- [ ] **Step 6: 运行配置属性失败测试**

Run:

```powershell
mvn -pl modules/module-feishu/module-feishu-autoconfig -Dtest=FeishuAdminPropertiesTest test
```

Expected: FAIL，提示 `FeishuAdminProperties` 不存在。

- [ ] **Step 7: 实现配置属性**

创建 `FeishuAdminProperties.java`：

```java
package com.zimo.module.zimo.autoconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 飞书 Agent 管理调试接口配置。
 */
@ConfigurationProperties(prefix = "feishu.admin")
public class FeishuAdminProperties {

    /**
     * 是否启用飞书 Agent 管理调试接口。
     */
    private boolean enabled = true;

    /**
     * 管理接口访问令牌。为空时允许本地调试访问。
     */
    private String apiToken;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }
}
```

- [ ] **Step 8: 运行配置属性测试通过**

Run:

```powershell
mvn -pl modules/module-feishu/module-feishu-autoconfig -Dtest=FeishuAdminPropertiesTest test
```

Expected: PASS。

- [ ] **Step 9: 提交**

```powershell
git add modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/admin/FeishuAdminPermissionGuard.java `
  modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/admin/FeishuAdminPermissionGuardTest.java `
  modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAdminProperties.java `
  modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAdminPropertiesTest.java
git commit -m "feat: add feishu admin permission guard"
```

---

### Task 2: DTO 与管理 Service 编排骨架

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/admin/dto/FeishuBitableWriteTestRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/admin/dto/FeishuChannelStatusResponse.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/admin/dto/FeishuCliDebugResponse.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/admin/dto/FeishuMessageLogPageRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/admin/dto/FeishuRobotSwitchRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/admin/dto/FeishuRobotSwitchResponse.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/admin/FeishuAgentAdminService.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/admin/FeishuAgentAdminServiceTest.java`

**Interfaces:**
- Consumes: `FeishuAgentCredentialService#initTenantScan(FeishuTenantScanInitRequest)`
- Consumes: `FeishuConfigService#getActiveConfig()`
- Consumes: `FeishuChannelClientManager#isRunning()`
- Consumes: `FeishuBitableCliService#createRecord(BitableRecordCreateRequest)`
- Produces: `FeishuAgentAdminService#initTenantScan(FeishuTenantScanInitRequest)`
- Produces: `FeishuAgentAdminService#getChannelStatus()`
- Produces: `FeishuAgentAdminService#writeBitableTest(FeishuBitableWriteTestRequest)`

- [ ] **Step 1: 写 Service 状态与 CLI 失败测试**

创建 `FeishuAgentAdminServiceTest.java`，先覆盖两个无需数据库的核心编排行为：

```java
package com.zimo.module.zimo.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import dto.admin.com.zimo.module.feishu.FeishuBitableWriteTestRequest;
import dto.admin.com.zimo.module.feishu.FeishuChannelStatusResponse;
import dto.admin.com.zimo.module.feishu.FeishuCliDebugResponse;
import agent.com.zimo.module.feishu.FeishuAgentCredentialService;
import channel.com.zimo.module.feishu.FeishuChannelClientManager;
import com.zimo.module.zimo.cli.BitableRecordCreateRequest;
import com.zimo.module.zimo.cli.FeishuBitableCliService;
import cli.com.zimo.module.feishu.FeishuCliCommandResult;
import config.com.zimo.module.feishu.FeishuConfigResponse;
import config.com.zimo.module.feishu.FeishuConfigService;
import log.com.zimo.module.feishu.FeishuMessageLogService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeishuAgentAdminServiceTest {

    @Test
    void shouldReturnChannelStatusWithoutSecrets() {
        FeishuConfigService configService = mock(FeishuConfigService.class);
        FeishuChannelClientManager channelManager = mock(FeishuChannelClientManager.class);
        FeishuConfigResponse config = new FeishuConfigResponse();
        config.setId(10L);
        config.setAppId("cli_xxx");
        config.setTenantKey("tenant_a");
        config.setTenantName("测试租户");
        config.setEnabled(true);
        config.setCredentialStatus("VALID");
        when(configService.getActiveConfig()).thenReturn(config);
        when(channelManager.isRunning()).thenReturn(true);

        FeishuAgentAdminService service = newService(configService, channelManager);

        FeishuChannelStatusResponse response = service.getChannelStatus();

        assertThat(response.isConfigured()).isTrue();
        assertThat(response.isEnabled()).isTrue();
        assertThat(response.isRunning()).isTrue();
        assertThat(response.getAppId()).isEqualTo("cli_xxx");
        assertThat(response.getTenantKey()).isEqualTo("tenant_a");
        assertThat(response.getCredentialStatus()).isEqualTo("VALID");
        assertThat(response.getMessage()).isEqualTo("飞书机器人连接运行中");
    }

    @Test
    void shouldWriteBitableDebugRecordWithDefaultFieldsWhenFieldsEmpty() {
        FeishuBitableCliService bitableCliService = mock(FeishuBitableCliService.class);
        when(bitableCliService.createRecord(any(BitableRecordCreateRequest.class)))
                .thenReturn(FeishuCliCommandResult.success("created", new ObjectMapper().createObjectNode(), 25L, 1));
        FeishuAgentAdminService service = newService(bitableCliService);
        FeishuBitableWriteTestRequest request = new FeishuBitableWriteTestRequest();
        request.setAppToken("app_token");
        request.setTableId("tbl_abc");

        FeishuCliDebugResponse response = service.writeBitableTest(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCostMillis()).isEqualTo(25L);
        assertThat(response.getAttempts()).isEqualTo(1);
        assertThat(response.getStdout()).isEqualTo("created");
        verify(bitableCliService).createRecord(any(BitableRecordCreateRequest.class));
    }

    @Test
    void shouldWriteBitableDebugRecordWithProvidedFields() {
        FeishuBitableCliService bitableCliService = mock(FeishuBitableCliService.class);
        when(bitableCliService.createRecord(any(BitableRecordCreateRequest.class)))
                .thenReturn(FeishuCliCommandResult.failure(1, "", "bad", "failed", 30L, 2));
        FeishuAgentAdminService service = newService(bitableCliService);
        FeishuBitableWriteTestRequest request = new FeishuBitableWriteTestRequest();
        request.setAppToken("app_token");
        request.setTableId("tbl_abc");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("名称", "调试记录");
        request.setFields(fields);

        FeishuCliDebugResponse response = service.writeBitableTest(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getExitCode()).isEqualTo(1);
        assertThat(response.getStderr()).isEqualTo("bad");
        assertThat(response.getErrorMessage()).isEqualTo("failed");
    }

    private FeishuAgentAdminService newService(FeishuConfigService configService,
                                               FeishuChannelClientManager channelManager) {
        return new FeishuAgentAdminService(
                mock(FeishuAgentCredentialService.class),
                configService,
                channelManager,
                mock(FeishuBitableCliService.class),
                mock(FeishuMessageLogService.class)
        );
    }

    private FeishuAgentAdminService newService(FeishuBitableCliService bitableCliService) {
        return new FeishuAgentAdminService(
                mock(FeishuAgentCredentialService.class),
                mock(FeishuConfigService.class),
                mock(FeishuChannelClientManager.class),
                bitableCliService,
                mock(FeishuMessageLogService.class)
        );
    }
}
```

- [ ] **Step 2: 运行失败测试**

Run:

```powershell
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentAdminServiceTest test
```

Expected: FAIL，提示 DTO 和 `FeishuAgentAdminService` 不存在。

- [ ] **Step 3: 创建 DTO**

按以下字段创建 DTO，全部使用普通 Java Bean getter/setter：

```java
package com.zimo.module.zimo.admin.dto;

import java.util.Map;

public class FeishuBitableWriteTestRequest {
    private String appToken;
    private String tableId;
    private Map<String, Object> fields;

    public String getAppToken() {
        return appToken;
    }

    public void setAppToken(String appToken) {
        this.appToken = appToken;
    }

    public String getTableId() {
        return tableId;
    }

    public void setTableId(String tableId) {
        this.tableId = tableId;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields;
    }
}
```

```java
package com.zimo.module.zimo.admin.dto;

public class FeishuChannelStatusResponse {
    private boolean configured;
    private boolean enabled;
    private boolean running;
    private String appId;
    private String tenantKey;
    private String tenantName;
    private String credentialStatus;
    private String message;

    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getTenantKey() {
        return tenantKey;
    }

    public void setTenantKey(String tenantKey) {
        this.tenantKey = tenantKey;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getCredentialStatus() {
        return credentialStatus;
    }

    public void setCredentialStatus(String credentialStatus) {
        this.credentialStatus = credentialStatus;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
```

```java
package com.zimo.module.zimo.admin.dto;

public class FeishuCliDebugResponse {
    private boolean success;
    private Integer exitCode;
    private Long costMillis;
    private int attempts;
    private String stdout;
    private String stderr;
    private String errorMessage;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public Long getCostMillis() {
        return costMillis;
    }

    public void setCostMillis(Long costMillis) {
        this.costMillis = costMillis;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
```

```java
package com.zimo.module.zimo.admin.dto;

public class FeishuMessageLogPageRequest {
    private long current = 1L;
    private long size = 10L;
    private String tenantKey;
    private String senderUserId;
    private String stage;
    private Boolean success;
    private String commandText;

    public long getCurrent() {
        return current;
    }

    public void setCurrent(long current) {
        this.current = current;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getTenantKey() {
        return tenantKey;
    }

    public void setTenantKey(String tenantKey) {
        this.tenantKey = tenantKey;
    }

    public String getSenderUserId() {
        return senderUserId;
    }

    public void setSenderUserId(String senderUserId) {
        this.senderUserId = senderUserId;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getCommandText() {
        return commandText;
    }

    public void setCommandText(String commandText) {
        this.commandText = commandText;
    }
}
```

```java
package com.zimo.module.zimo.admin.dto;

public class FeishuRobotSwitchRequest {
    private boolean enabled;
    private Long configId;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getConfigId() {
        return configId;
    }

    public void setConfigId(Long configId) {
        this.configId = configId;
    }
}
```

```java
package com.zimo.module.zimo.admin.dto;

public class FeishuRobotSwitchResponse {
    private boolean enabled;
    private boolean running;
    private Long configId;
    private String message;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public Long getConfigId() {
        return configId;
    }

    public void setConfigId(Long configId) {
        this.configId = configId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
```

- [ ] **Step 4: 实现 Service 基础方法**

创建 `FeishuAgentAdminService.java`，包含以下方法签名和核心逻辑：

```java
package com.zimo.module.zimo.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dto.admin.com.zimo.module.feishu.FeishuBitableWriteTestRequest;
import dto.admin.com.zimo.module.feishu.FeishuChannelStatusResponse;
import dto.admin.com.zimo.module.feishu.FeishuCliDebugResponse;
import dto.admin.com.zimo.module.feishu.FeishuMessageLogPageRequest;
import dto.admin.com.zimo.module.feishu.FeishuRobotSwitchRequest;
import dto.admin.com.zimo.module.feishu.FeishuRobotSwitchResponse;
import agent.com.zimo.module.feishu.FeishuAgentCredentialService;
import com.zimo.module.zimo.agent.FeishuTenantScanInitRequest;
import com.zimo.module.zimo.agent.FeishuTenantScanInitResponse;
import channel.com.zimo.module.feishu.FeishuChannelClientManager;
import com.zimo.module.zimo.cli.BitableRecordCreateRequest;
import com.zimo.module.zimo.cli.FeishuBitableCliService;
import cli.com.zimo.module.feishu.FeishuCliCommandResult;
import config.com.zimo.module.feishu.FeishuConfigResponse;
import config.com.zimo.module.feishu.FeishuConfigService;
import log.com.zimo.module.feishu.FeishuMessageLogEntity;
import log.com.zimo.module.feishu.FeishuMessageLogService;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class FeishuAgentAdminService {

    private static final int OUTPUT_LIMIT = 2000;

    private final FeishuAgentCredentialService credentialService;
    private final FeishuConfigService configService;
    private final FeishuChannelClientManager channelClientManager;
    private final FeishuBitableCliService bitableCliService;
    private final FeishuMessageLogService messageLogService;

    public FeishuAgentAdminService(FeishuAgentCredentialService credentialService,
                                   FeishuConfigService configService,
                                   FeishuChannelClientManager channelClientManager,
                                   FeishuBitableCliService bitableCliService,
                                   FeishuMessageLogService messageLogService) {
        this.credentialService = credentialService;
        this.configService = configService;
        this.channelClientManager = channelClientManager;
        this.bitableCliService = bitableCliService;
        this.messageLogService = messageLogService;
    }

    public FeishuTenantScanInitResponse initTenantScan(FeishuTenantScanInitRequest request) {
        return credentialService.initTenantScan(request);
    }

    public FeishuChannelStatusResponse getChannelStatus() {
        FeishuConfigResponse config = configService.getActiveConfig();
        boolean running = channelClientManager.isRunning();
        FeishuChannelStatusResponse response = new FeishuChannelStatusResponse();
        response.setConfigured(config != null);
        response.setRunning(running);
        if (config == null) {
            response.setEnabled(false);
            response.setMessage("未配置启用中的飞书应用");
            return response;
        }
        response.setEnabled(config.isEnabled());
        response.setAppId(config.getAppId());
        response.setTenantKey(config.getTenantKey());
        response.setTenantName(config.getTenantName());
        response.setCredentialStatus(config.getCredentialStatus());
        response.setMessage(running ? "飞书机器人连接运行中" : "飞书机器人未连接");
        return response;
    }

    public FeishuCliDebugResponse writeBitableTest(FeishuBitableWriteTestRequest request) {
        validateText(request.getAppToken(), "appToken不能为空");
        validateText(request.getTableId(), "tableId不能为空");
        BitableRecordCreateRequest cliRequest = new BitableRecordCreateRequest();
        cliRequest.setAppToken(request.getAppToken());
        cliRequest.setTableId(request.getTableId());
        cliRequest.setFields(resolveFields(request.getFields()));
        return toDebugResponse(bitableCliService.createRecord(cliRequest));
    }

    public Page<FeishuMessageLogEntity> pageMessageLogs(FeishuMessageLogPageRequest request) {
        return messageLogService.page(request);
    }

    public FeishuRobotSwitchResponse switchRobot(FeishuRobotSwitchRequest request) {
        throw new UnsupportedOperationException("Task 3 implements robot switch");
    }

    private Map<String, Object> resolveFields(Map<String, Object> fields) {
        if (fields != null && !fields.isEmpty()) {
            return fields;
        }
        Map<String, Object> defaultFields = new LinkedHashMap<>();
        defaultFields.put("来源", "production-studio");
        defaultFields.put("调试时间", LocalDateTime.now().toString());
        defaultFields.put("类型", "飞书CLI写入调试");
        return defaultFields;
    }

    private FeishuCliDebugResponse toDebugResponse(FeishuCliCommandResult result) {
        FeishuCliDebugResponse response = new FeishuCliDebugResponse();
        response.setSuccess(result.isSuccess());
        response.setExitCode(result.getExitCode());
        response.setCostMillis(result.getCostMillis());
        response.setAttempts(result.getAttempts());
        response.setStdout(truncate(result.getStdout()));
        response.setStderr(truncate(result.getStderr()));
        response.setErrorMessage(truncate(result.getErrorMessage()));
        return response;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= OUTPUT_LIMIT) {
            return value;
        }
        return value.substring(0, OUTPUT_LIMIT) + "...[truncated]";
    }

    private void validateText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }
}
```

- [ ] **Step 5: 运行 Service 测试通过**

Run:

```powershell
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentAdminServiceTest test
```

Expected: PASS。

- [ ] **Step 6: 提交**

```powershell
git add modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/admin `
  modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/admin/FeishuAgentAdminServiceTest.java
git commit -m "feat: add feishu agent admin service"
```

---

### Task 3: 消息日志分页与配置停用能力

**Files:**
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/log/FeishuMessageLogService.java`
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigService.java`
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigServiceImpl.java`
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/admin/FeishuAgentAdminService.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/log/FeishuMessageLogServiceTest.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuConfigServiceImplTest.java`
- Modify Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/admin/FeishuAgentAdminServiceTest.java`

**Interfaces:**
- Produces: `Page<FeishuMessageLogEntity> FeishuMessageLogService.page(FeishuMessageLogPageRequest request)`
- Produces: `FeishuConfigResponse FeishuConfigService.disableActive()`
- Consumes: `FeishuConfigService#enable(Long id)`
- Produces: `FeishuRobotSwitchResponse FeishuAgentAdminService.switchRobot(FeishuRobotSwitchRequest request)`

- [ ] **Step 1: 写消息日志分页失败测试**

在 `FeishuMessageLogServiceTest` 中增加：

```java
package com.zimo.module.zimo.log;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dto.admin.com.zimo.module.feishu.FeishuMessageLogPageRequest;
import com.zimo.module.zimo.mapper.FeishuMessageLogMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeishuMessageLogServiceTest {

    @Test
    void shouldReturnEmptyPageWhenMapperMissing() {
        FeishuMessageLogService service = new FeishuMessageLogService(null, true);
        FeishuMessageLogPageRequest request = new FeishuMessageLogPageRequest();
        request.setCurrent(2L);
        request.setSize(5L);

        Page<FeishuMessageLogEntity> page = service.page(request);

        assertThat(page.getCurrent()).isEqualTo(2L);
        assertThat(page.getSize()).isEqualTo(5L);
        assertThat(page.getRecords()).isEmpty();
    }

    @Test
    void shouldDelegatePageQueryWhenMapperAvailable() {
        FeishuMessageLogMapper mapper = mock(FeishuMessageLogMapper.class);
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FeishuMessageLogService service = new FeishuMessageLogService(mapper, true);
        FeishuMessageLogPageRequest request = new FeishuMessageLogPageRequest();
        request.setTenantKey("tenant_a");
        request.setSenderUserId("ou_xxx");
        request.setStage("DISPATCH");
        request.setSuccess(true);
        request.setCommandText("项目");

        Page<FeishuMessageLogEntity> page = service.page(request);

        assertThat(page.getCurrent()).isEqualTo(1L);
        assertThat(page.getSize()).isEqualTo(10L);
        verify(mapper).selectPage(any(Page.class), any(Wrapper.class));
    }
}
```

- [ ] **Step 2: 运行分页失败测试**

Run:

```powershell
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuMessageLogServiceTest test
```

Expected: FAIL，提示 `page(FeishuMessageLogPageRequest)` 不存在。

- [ ] **Step 3: 实现日志分页**

在 `FeishuMessageLogService` 增加：

```java
public Page<FeishuMessageLogEntity> page(FeishuMessageLogPageRequest request) {
    long current = request == null || request.getCurrent() < 1 ? 1L : request.getCurrent();
    long size = request == null || request.getSize() < 1 ? 10L : Math.min(request.getSize(), 100L);
    Page<FeishuMessageLogEntity> page = new Page<>(current, size);
    if (!enabled || mapper == null) {
        return page;
    }
    LambdaQueryWrapper<FeishuMessageLogEntity> wrapper = Wrappers.lambdaQuery(FeishuMessageLogEntity.class);
    if (StringUtils.hasText(request.getTenantKey())) {
        wrapper.eq(FeishuMessageLogEntity::getTenantKey, request.getTenantKey());
    }
    if (StringUtils.hasText(request.getSenderUserId())) {
        wrapper.eq(FeishuMessageLogEntity::getSenderUserId, request.getSenderUserId());
    }
    if (StringUtils.hasText(request.getStage())) {
        wrapper.eq(FeishuMessageLogEntity::getStage, request.getStage());
    }
    if (request.getSuccess() != null) {
        wrapper.eq(FeishuMessageLogEntity::getSuccess, request.getSuccess());
    }
    if (StringUtils.hasText(request.getCommandText())) {
        wrapper.like(FeishuMessageLogEntity::getCommandText, request.getCommandText());
    }
    wrapper.orderByDesc(FeishuMessageLogEntity::getCreateTime);
    return mapper.selectPage(page, wrapper);
}
```

同时补充 imports：

```java
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dto.admin.com.zimo.module.feishu.FeishuMessageLogPageRequest;
import org.springframework.util.StringUtils;
```

- [ ] **Step 4: 运行分页测试通过**

Run:

```powershell
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuMessageLogServiceTest test
```

Expected: PASS。

- [ ] **Step 5: 写机器人启停失败测试**

在 `FeishuAgentAdminServiceTest` 中追加：

```java
@Test
void shouldEnableRobotAndStartChannel() {
    FeishuConfigService configService = mock(FeishuConfigService.class);
    FeishuChannelClientManager channelManager = mock(FeishuChannelClientManager.class);
    FeishuConfigResponse enabledConfig = new FeishuConfigResponse();
    enabledConfig.setId(10L);
    enabledConfig.setEnabled(true);
    when(configService.enable(10L)).thenReturn(enabledConfig);
    when(channelManager.isRunning()).thenReturn(true);
    FeishuAgentAdminService service = newService(configService, channelManager);
    FeishuRobotSwitchRequest request = new FeishuRobotSwitchRequest();
    request.setEnabled(true);
    request.setConfigId(10L);

    FeishuRobotSwitchResponse response = service.switchRobot(request);

    assertThat(response.isEnabled()).isTrue();
    assertThat(response.isRunning()).isTrue();
    assertThat(response.getConfigId()).isEqualTo(10L);
    assertThat(response.getMessage()).isEqualTo("飞书机器人已启用");
    verify(channelManager).start();
}

@Test
void shouldDisableRobotAndStopChannel() {
    FeishuConfigService configService = mock(FeishuConfigService.class);
    FeishuChannelClientManager channelManager = mock(FeishuChannelClientManager.class);
    FeishuConfigResponse disabledConfig = new FeishuConfigResponse();
    disabledConfig.setId(10L);
    disabledConfig.setEnabled(false);
    when(configService.disableActive()).thenReturn(disabledConfig);
    when(channelManager.isRunning()).thenReturn(false);
    FeishuAgentAdminService service = newService(configService, channelManager);
    FeishuRobotSwitchRequest request = new FeishuRobotSwitchRequest();
    request.setEnabled(false);

    FeishuRobotSwitchResponse response = service.switchRobot(request);

    assertThat(response.isEnabled()).isFalse();
    assertThat(response.isRunning()).isFalse();
    assertThat(response.getConfigId()).isEqualTo(10L);
    assertThat(response.getMessage()).isEqualTo("飞书机器人已停用");
    verify(channelManager).stop();
}
```

- [ ] **Step 6: 运行机器人启停失败测试**

Run:

```powershell
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentAdminServiceTest test
```

Expected: FAIL，提示 `FeishuConfigService#disableActive()` 不存在或 `switchRobot` 抛出 `UnsupportedOperationException`。

- [ ] **Step 7: 扩展配置服务接口**

在 `FeishuConfigService` 增加：

```java
FeishuConfigResponse disableActive();
```

在 `FeishuConfigServiceImpl` 实现：

```java
@Override
public FeishuConfigResponse disableActive() {
    FeishuConfigEntity active = mapper.selectOne(Wrappers.<FeishuConfigEntity>lambdaQuery()
        .eq(FeishuConfigEntity::getEnabled, true)
        .last("limit 1"));
    if (active == null) {
        return null;
    }
    active.setEnabled(false);
    active.setUpdateTime(LocalDateTime.now());
    mapper.updateById(active);
    return toResponse(active);
}
```

- [ ] **Step 8: 实现机器人启停**

替换 `FeishuAgentAdminService#switchRobot`：

```java
public FeishuRobotSwitchResponse switchRobot(FeishuRobotSwitchRequest request) {
    if (request.isEnabled()) {
        FeishuConfigResponse config = request.getConfigId() == null
            ? configService.getActiveConfig()
            : configService.enable(request.getConfigId());
        channelClientManager.start();
        return toSwitchResponse(config, channelClientManager.isRunning(), true, "飞书机器人已启用");
    }
    channelClientManager.stop();
    FeishuConfigResponse config = configService.disableActive();
    return toSwitchResponse(config, channelClientManager.isRunning(), false, "飞书机器人已停用");
}

private FeishuRobotSwitchResponse toSwitchResponse(FeishuConfigResponse config,
                                                   boolean running,
                                                   boolean enabled,
                                                   String message) {
    FeishuRobotSwitchResponse response = new FeishuRobotSwitchResponse();
    response.setEnabled(enabled);
    response.setRunning(running);
    response.setConfigId(config == null ? null : config.getId());
    response.setMessage(message);
    return response;
}
```

- [ ] **Step 9: 运行本任务测试通过**

Run:

```powershell
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuMessageLogServiceTest,FeishuAgentAdminServiceTest test
```

Expected: PASS。

- [ ] **Step 10: 提交**

```powershell
git add modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/log/FeishuMessageLogService.java `
  modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigService.java `
  modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigServiceImpl.java `
  modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/admin/FeishuAgentAdminService.java `
  modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/log/FeishuMessageLogServiceTest.java `
  modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/admin/FeishuAgentAdminServiceTest.java
git commit -m "feat: add feishu admin log page and robot switch"
```

---

### Task 4: 管理 Rest Controller

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/admin/FeishuAgentAdminController.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/admin/FeishuAgentAdminControllerTest.java`

**Interfaces:**
- Consumes: `FeishuAdminPermissionGuard#isAllowed(String requestToken)`
- Consumes: `FeishuAgentAdminService#initTenantScan(...)`
- Consumes: `FeishuAgentAdminService#getChannelStatus()`
- Consumes: `FeishuAgentAdminService#writeBitableTest(...)`
- Consumes: `FeishuAgentAdminService#pageMessageLogs(...)`
- Consumes: `FeishuAgentAdminService#switchRobot(...)`
- Produces: endpoints under `/api/biz/feishu/admin/**`

- [ ] **Step 1: 写 Controller 失败测试**

创建 `FeishuAgentAdminControllerTest.java`：

```java
package com.zimo.module.zimo.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zimo.framework.common.ApiResponse;
import dto.admin.com.zimo.module.feishu.FeishuBitableWriteTestRequest;
import dto.admin.com.zimo.module.feishu.FeishuChannelStatusResponse;
import dto.admin.com.zimo.module.feishu.FeishuCliDebugResponse;
import dto.admin.com.zimo.module.feishu.FeishuMessageLogPageRequest;
import dto.admin.com.zimo.module.feishu.FeishuRobotSwitchRequest;
import dto.admin.com.zimo.module.feishu.FeishuRobotSwitchResponse;
import com.zimo.module.zimo.agent.FeishuTenantScanInitRequest;
import com.zimo.module.zimo.agent.FeishuTenantScanInitResponse;
import log.com.zimo.module.feishu.FeishuMessageLogEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeishuAgentAdminControllerTest {

    @Test
    void shouldRejectWhenAdminTokenInvalid() {
        FeishuAgentAdminController controller = new FeishuAgentAdminController(
                new FeishuAdminPermissionGuard("secret-token"),
                mock(FeishuAgentAdminService.class)
        );

        R<FeishuChannelStatusResponse> response = controller.channelStatus("wrong-token");

        assertThat(response.getCode()).isEqualTo(403);
        assertThat(response.getMsg()).isEqualTo("forbidden");
    }

    @Test
    void shouldReturnChannelStatusWhenAllowed() {
        FeishuAgentAdminService service = mock(FeishuAgentAdminService.class);
        FeishuChannelStatusResponse status = new FeishuChannelStatusResponse();
        status.setRunning(true);
        when(service.getChannelStatus()).thenReturn(status);
        FeishuAgentAdminController controller = new FeishuAgentAdminController(
                new FeishuAdminPermissionGuard(""),
                service
        );

        R<FeishuChannelStatusResponse> response = controller.channelStatus(null);

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData().isRunning()).isTrue();
    }

    @Test
    void shouldDelegateAllAdminOperations() {
        FeishuAgentAdminService service = mock(FeishuAgentAdminService.class);
        when(service.initTenantScan(any(FeishuTenantScanInitRequest.class))).thenReturn(new FeishuTenantScanInitResponse());
        when(service.writeBitableTest(any(FeishuBitableWriteTestRequest.class))).thenReturn(new FeishuCliDebugResponse());
        when(service.pageMessageLogs(any(FeishuMessageLogPageRequest.class))).thenReturn(new Page<FeishuMessageLogEntity>(1, 10));
        when(service.switchRobot(any(FeishuRobotSwitchRequest.class))).thenReturn(new FeishuRobotSwitchResponse());
        FeishuAgentAdminController controller = new FeishuAgentAdminController(
                new FeishuAdminPermissionGuard(""),
                service
        );

        assertThat(controller.initTenantScan(null, new FeishuTenantScanInitRequest()).getCode()).isEqualTo(0);
        assertThat(controller.writeBitableTest(null, new FeishuBitableWriteTestRequest()).getCode()).isEqualTo(0);
        assertThat(controller.messageLogs(null, new FeishuMessageLogPageRequest()).getCode()).isEqualTo(0);
        assertThat(controller.switchRobot(null, new FeishuRobotSwitchRequest()).getCode()).isEqualTo(0);
    }
}
```

- [ ] **Step 2: 运行失败测试**

Run:

```powershell
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentAdminControllerTest test
```

Expected: FAIL，提示 `FeishuAgentAdminController` 不存在。

- [ ] **Step 3: 实现 Controller**

创建 `FeishuAgentAdminController.java`：

```java
package com.zimo.module.zimo.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zimo.framework.common.ApiResponse;
import dto.admin.com.zimo.module.feishu.FeishuBitableWriteTestRequest;
import dto.admin.com.zimo.module.feishu.FeishuChannelStatusResponse;
import dto.admin.com.zimo.module.feishu.FeishuCliDebugResponse;
import dto.admin.com.zimo.module.feishu.FeishuMessageLogPageRequest;
import dto.admin.com.zimo.module.feishu.FeishuRobotSwitchRequest;
import dto.admin.com.zimo.module.feishu.FeishuRobotSwitchResponse;
import com.zimo.module.zimo.agent.FeishuTenantScanInitRequest;
import com.zimo.module.zimo.agent.FeishuTenantScanInitResponse;
import log.com.zimo.module.feishu.FeishuMessageLogEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/biz/feishu/admin")
public class FeishuAgentAdminController {

    private static final String ADMIN_TOKEN_HEADER = "X-Feishu-Admin-Token";

    private final FeishuAdminPermissionGuard permissionGuard;
    private final FeishuAgentAdminService adminService;

    public FeishuAgentAdminController(FeishuAdminPermissionGuard permissionGuard,
                                      FeishuAgentAdminService adminService) {
        this.permissionGuard = permissionGuard;
        this.adminService = adminService;
    }

    @PostMapping("/tenant-scan/init")
    public R<FeishuTenantScanInitResponse> initTenantScan(
            @RequestHeader(value = ADMIN_TOKEN_HEADER, required = false) String adminToken,
            @RequestBody FeishuTenantScanInitRequest request) {
        if (!permissionGuard.isAllowed(adminToken)) {
            return R.fail(403, "forbidden");
        }
        return R.ok(adminService.initTenantScan(request));
    }

    @GetMapping("/channel/status")
    public R<FeishuChannelStatusResponse> channelStatus(
            @RequestHeader(value = ADMIN_TOKEN_HEADER, required = false) String adminToken) {
        if (!permissionGuard.isAllowed(adminToken)) {
            return R.fail(403, "forbidden");
        }
        return R.ok(adminService.getChannelStatus());
    }

    @PostMapping("/cli/bitable/write-test")
    public R<FeishuCliDebugResponse> writeBitableTest(
            @RequestHeader(value = ADMIN_TOKEN_HEADER, required = false) String adminToken,
            @RequestBody FeishuBitableWriteTestRequest request) {
        if (!permissionGuard.isAllowed(adminToken)) {
            return R.fail(403, "forbidden");
        }
        return R.ok(adminService.writeBitableTest(request));
    }

    @GetMapping("/message-logs/page")
    public R<Page<FeishuMessageLogEntity>> messageLogs(
            @RequestHeader(value = ADMIN_TOKEN_HEADER, required = false) String adminToken,
            @ModelAttribute FeishuMessageLogPageRequest request) {
        if (!permissionGuard.isAllowed(adminToken)) {
            return R.fail(403, "forbidden");
        }
        return R.ok(adminService.pageMessageLogs(request));
    }

    @PutMapping("/robot/enabled")
    public R<FeishuRobotSwitchResponse> switchRobot(
            @RequestHeader(value = ADMIN_TOKEN_HEADER, required = false) String adminToken,
            @RequestBody FeishuRobotSwitchRequest request) {
        if (!permissionGuard.isAllowed(adminToken)) {
            return R.fail(403, "forbidden");
        }
        return R.ok(adminService.switchRobot(request));
    }
}
```

- [ ] **Step 4: 运行 Controller 测试通过**

Run:

```powershell
mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentAdminControllerTest test
```

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/admin/FeishuAgentAdminController.java `
  modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/admin/FeishuAgentAdminControllerTest.java
git commit -m "feat: expose feishu agent admin rest api"
```

---

### Task 5: 自动装配与完整验证

**Files:**
- Modify: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAutoConfiguration.java`
- Test: `modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAdminAutoConfigurationTest.java`

**Interfaces:**
- Consumes: `FeishuAdminProperties`
- Consumes: `FeishuAdminPermissionGuard`
- Consumes: `FeishuAgentAdminService`
- Consumes: `FeishuAgentAdminController`
- Produces: `FeishuAgentAdminController` Bean when `feishu.enabled=true` and `feishu.admin.enabled=true`

- [ ] **Step 1: 写自动装配失败测试**

创建 `FeishuAdminAutoConfigurationTest.java`：

```java
package com.zimo.module.zimo.autoconfig;

import admin.com.zimo.module.feishu.FeishuAdminPermissionGuard;
import admin.com.zimo.module.feishu.FeishuAgentAdminController;
import admin.com.zimo.module.feishu.FeishuAgentAdminService;
import agent.com.zimo.module.feishu.FeishuAgentCredentialService;
import channel.com.zimo.module.feishu.FeishuChannelClientManager;
import com.zimo.module.zimo.cli.FeishuBitableCliService;
import config.com.zimo.module.feishu.FeishuConfigService;
import log.com.zimo.module.feishu.FeishuMessageLogService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FeishuAdminAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeishuAutoConfiguration.class))
            .withBean(FeishuAgentCredentialService.class, () -> mock(FeishuAgentCredentialService.class))
            .withBean(FeishuConfigService.class, () -> mock(FeishuConfigService.class))
            .withBean(FeishuChannelClientManager.class, () -> mock(FeishuChannelClientManager.class))
            .withBean(FeishuBitableCliService.class, () -> mock(FeishuBitableCliService.class))
            .withBean(FeishuMessageLogService.class, () -> mock(FeishuMessageLogService.class));

    @Test
    void shouldRegisterAdminBeansByDefaultWhenFeishuEnabled() {
        contextRunner
                .withPropertyValues("feishu.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(FeishuAdminProperties.class)
                        .hasSingleBean(FeishuAdminPermissionGuard.class)
                        .hasSingleBean(FeishuAgentAdminService.class)
                        .hasSingleBean(FeishuAgentAdminController.class));
    }

    @Test
    void shouldNotRegisterAdminControllerWhenAdminDisabled() {
        contextRunner
                .withPropertyValues("feishu.enabled=true", "feishu.admin.enabled=false")
                .run(context -> assertThat(context)
                        .hasSingleBean(FeishuAdminProperties.class)
                        .doesNotHaveBean(FeishuAgentAdminController.class)
                        .doesNotHaveBean(FeishuAgentAdminService.class));
    }
}
```

- [ ] **Step 2: 运行自动装配失败测试**

Run:

```powershell
mvn -pl modules/module-feishu/module-feishu-autoconfig -Dtest=FeishuAdminAutoConfigurationTest test
```

Expected: FAIL，提示管理 Bean 未注册。

- [ ] **Step 3: 修改自动装配**

在 `FeishuAutoConfiguration` 中增加：

```java
@Bean
@ConditionalOnMissingBean
public FeishuAdminPermissionGuard feishuAdminPermissionGuard(FeishuAdminProperties properties) {
    return new FeishuAdminPermissionGuard(properties.getApiToken());
}

@Bean
@ConditionalOnProperty(prefix = "feishu.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnMissingBean
public FeishuAgentAdminService feishuAgentAdminService(FeishuAgentCredentialService credentialService,
                                                       FeishuConfigService configService,
                                                       FeishuChannelClientManager channelClientManager,
                                                       FeishuBitableCliService bitableCliService,
                                                       FeishuMessageLogService messageLogService) {
    return new FeishuAgentAdminService(
        credentialService,
        configService,
        channelClientManager,
        bitableCliService,
        messageLogService
    );
}

@Bean
@ConditionalOnProperty(prefix = "feishu.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnMissingBean
public FeishuAgentAdminController feishuAgentAdminController(FeishuAdminPermissionGuard permissionGuard,
                                                            FeishuAgentAdminService adminService) {
    return new FeishuAgentAdminController(permissionGuard, adminService);
}
```

同时把 `FeishuAdminProperties.class` 加入已有的 `@EnableConfigurationProperties` 列表。

- [ ] **Step 4: 运行自动装配测试通过**

Run:

```powershell
mvn -pl modules/module-feishu/module-feishu-autoconfig -Dtest=FeishuAdminAutoConfigurationTest test
```

Expected: PASS。

- [ ] **Step 5: 运行核心模块完整测试**

Run:

```powershell
mvn -pl modules/module-feishu/module-feishu-core -am test
```

Expected: PASS。若出现飞书 SDK 连接失败的预期兜底日志，只要测试结果为 BUILD SUCCESS 即可。

- [ ] **Step 6: 运行自动装配模块完整测试**

Run:

```powershell
mvn -pl modules/module-feishu/module-feishu-autoconfig -am test
```

Expected: PASS。若出现飞书 SDK 连接失败的预期兜底日志，只要测试结果为 BUILD SUCCESS 即可。

- [ ] **Step 7: 检查待提交文件范围**

Run:

```powershell
git status --short modules/module-feishu docs/superpowers/plans/2026-07-03-feishu-agent-admin-rest-api.md
```

Expected: 只包含本计划涉及的 `module-feishu` 文件和本计划文档；不要提交其他模块改动。

- [ ] **Step 8: 提交**

```powershell
git add modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAutoConfiguration.java `
  modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAdminProperties.java `
  modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAdminAutoConfigurationTest.java
git commit -m "feat: autoconfigure feishu admin rest api"
```

---

## 自检

- Spec coverage: 计划覆盖扫码初始化、连接状态查询、CLI 表格写入调试、消息日志分页、机器人启停、简单权限校验、统一返回体、DTO、自动装配和验证命令。
- Placeholder scan: 文档没有未落地的占位写法，代码片段均给出明确类型、字段和方法签名。
- Type consistency: Controller、Service、Guard、DTO、日志分页和自动装配使用的方法签名在前后任务中保持一致。
