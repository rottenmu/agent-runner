package com.zimo.module.feishu.admin;

import cn.hutool.core.util.StrUtil;

public class FeishuAdminPermissionGuard {

    private final String apiToken;

    public FeishuAdminPermissionGuard(String apiToken) {
        this.apiToken = apiToken;
    }

    public boolean isAllowed(String requestToken) {
        if (StrUtil.isBlank(apiToken)) {
            return true;
        }
        return apiToken.equals(requestToken);
    }
}
