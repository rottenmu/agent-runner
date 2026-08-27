package com.zimo.module.ai;

import com.zimo.framework.common.PluginRegister;

public class AiPluginRegister implements PluginRegister {
    @Override
    public String getPluginId() {
        return "ai";
    }

    @Override
    public String getPluginName() {
        return "AI智能体";
    }

    @Override
    public String getApiPrefix() {
        return "/api/ai";
    }

    @Override
    public String getFrontendRoute() {
        return "/ai";
    }

    @Override
    public String getFrontendModule() {
        return "ai";
    }

    @Override
    public String getAgentName() {
        return "ai-agent";
    }

    @Override
    public int getOrder() {
        return 4;
    }
}
