package com.zimo.module.feishu.agent;

import com.zimo.module.feishu.agent.dto.FeishuCredentialValidateResponse;
import com.zimo.module.feishu.config.FeishuConfigEntity;
import java.time.LocalDateTime;
import org.springframework.util.StringUtils;

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
