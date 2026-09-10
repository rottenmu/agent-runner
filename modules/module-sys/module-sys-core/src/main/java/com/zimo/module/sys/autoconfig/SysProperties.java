package com.zimo.module.sys.autoconfig;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "plugin.sys")
public class SysProperties {
    private boolean enabled = true;
    private String agentName = "sys-agent";
}
