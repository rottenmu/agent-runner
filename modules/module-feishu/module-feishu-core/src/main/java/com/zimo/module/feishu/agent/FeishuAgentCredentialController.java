package com.zimo.module.feishu.agent;

import com.zimo.framework.common.ApiResponse;
import com.zimo.module.feishu.agent.dto.FeishuCredentialRefreshRequest;
import com.zimo.module.feishu.agent.dto.FeishuCredentialValidateResponse;
import com.zimo.module.feishu.agent.dto.FeishuTenantCredentialResponse;
import com.zimo.module.feishu.agent.dto.FeishuTenantScanInitRequest;
import com.zimo.module.feishu.agent.dto.FeishuTenantScanInitResponse;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/biz/feishu/agent")
public class FeishuAgentCredentialController {
    private final FeishuAgentCredentialService credentialService;

    public FeishuAgentCredentialController(FeishuAgentCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping("/tenant-scan/init")
    public ApiResponse<FeishuTenantScanInitResponse> initTenantScan(@RequestBody FeishuTenantScanInitRequest request) {
        return ApiResponse.ok(credentialService.initTenantScan(request));
    }

    @GetMapping("/credentials")
    public ApiResponse<List<FeishuTenantCredentialResponse>> listCredentials() {
        return ApiResponse.ok(credentialService.listCredentials());
    }

    @GetMapping("/credentials/{id}")
    public ApiResponse<FeishuTenantCredentialResponse> getCredential(@PathVariable Long id) {
        return ApiResponse.ok(credentialService.getCredential(id));
    }

    @DeleteMapping("/credentials/{id}")
    public ApiResponse<Void> deleteCredential(@PathVariable Long id) {
        credentialService.deleteCredential(id);
        return ApiResponse.ok();
    }

    @PostMapping("/credentials/{id}/validate")
    public ApiResponse<FeishuCredentialValidateResponse> validateCredential(@PathVariable Long id) {
        return ApiResponse.ok(credentialService.validateCredential(id));
    }

    @PostMapping("/credentials/{id}/refresh-secret")
    public ApiResponse<FeishuTenantCredentialResponse> refreshSecret(
            @PathVariable Long id,
            @RequestBody FeishuCredentialRefreshRequest request) {
        return ApiResponse.ok(credentialService.refreshSecret(id, request));
    }
}
