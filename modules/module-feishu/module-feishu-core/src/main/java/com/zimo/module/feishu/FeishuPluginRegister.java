package com.zimo.module.feishu;

import com.zimo.framework.common.PluginRegister;

public class FeishuPluginRegister implements PluginRegister {
    @Override
    public String getPluginId() {
        return "feishu";
    }

    @Override
    public String getPluginName() {
        return "飞书平台";
    }

    @Override
    public String getApiPrefix() {
        return "/api/feishu";
    }

    @Override
    public String getFrontendRoute() {
        return "/integration/feishu";
    }

    @Override
    public String getFrontendModule() {
        return "feishu";
    }

    @Override
    public String getAgentName() {
        return "feishu-agent";
    }

    @Override
    public int getOrder() {
        return 5;
    }
}
