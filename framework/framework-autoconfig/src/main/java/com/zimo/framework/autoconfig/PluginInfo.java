package com.zimo.framework.autoconfig;

import lombok.Data;

@Data
public class PluginInfo {
    private String pluginId;
    private String pluginName;
    private String apiPrefix;
    private String frontendRoute;
    private String frontendModule;
    private String agentName;
    private boolean enabled;
    private int order;
}
