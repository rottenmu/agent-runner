package com.zimo.framework.common;

public interface PluginRegister {
    String getPluginId();
    String getPluginName();
    String getApiPrefix();
    String getFrontendRoute();
    default String getFrontendModule() { return getPluginId(); }
    String getAgentName();
    default int getOrder() { return 100; }
}
