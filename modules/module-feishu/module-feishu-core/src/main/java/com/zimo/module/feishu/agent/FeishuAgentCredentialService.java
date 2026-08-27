package com.zimo.module.feishu.agent;

import com.zimo.module.feishu.agent.dto.FeishuCredentialRefreshRequest;
import com.zimo.module.feishu.agent.dto.FeishuCredentialValidateResponse;
import com.zimo.module.feishu.agent.dto.FeishuTenantCredentialResponse;
import com.zimo.module.feishu.agent.dto.FeishuTenantScanInitRequest;
import com.zimo.module.feishu.agent.dto.FeishuTenantScanInitResponse;
import java.util.List;

public interface FeishuAgentCredentialService {
    FeishuTenantScanInitResponse initTenantScan(FeishuTenantScanInitRequest request);

    List<FeishuTenantCredentialResponse> listCredentials();

    FeishuTenantCredentialResponse getCredential(Long id);

    void deleteCredential(Long id);

    FeishuCredentialValidateResponse validateCredential(Long id);

    FeishuTenantCredentialResponse refreshSecret(Long id, FeishuCredentialRefreshRequest request);
}
