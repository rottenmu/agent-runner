package com.zimo.framework.autoconfig;

import com.zimo.framework.common.PluginRegister;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class FrameworkWebAutoConfiguration {

    @Bean
    public PluginRegistry pluginRegistry(List<PluginRegister> plugins) {
        return new PluginRegistry(plugins);
    }
}
