package com.zimo.agentapplication;

import com.zimo.framework.common.PluginRegister;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class LoadedModuleLogger implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LoadedModuleLogger.class);

    private final List<PluginRegister> plugins;

    public LoadedModuleLogger(List<PluginRegister> plugins) {
        this.plugins = plugins == null ? List.of() : List.copyOf(plugins);
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info(loadedModuleSummary());
    }

    String loadedModuleSummary() {
        List<PluginRegister> sortedPlugins = plugins.stream()
                .sorted(Comparator.comparingInt(PluginRegister::getOrder)
                        .thenComparing(PluginRegister::getPluginId))
                .collect(Collectors.toList());

        StringBuilder summary = new StringBuilder("Loaded modules: ")
                .append(sortedPlugins.size());
        for (int index = 0; index < sortedPlugins.size(); index++) {
            PluginRegister plugin = sortedPlugins.get(index);
            summary.append(System.lineSeparator())
                    .append("  [").append(index + 1).append("] ")
                    .append("pluginId=").append(value(plugin.getPluginId()))
                    .append(", pluginName=").append(value(plugin.getPluginName()))
                    .append(", frontendModule=").append(value(plugin.getFrontendModule()))
                    .append(", apiPrefix=").append(value(plugin.getApiPrefix()))
                    .append(", frontendRoute=").append(value(plugin.getFrontendRoute()))
                    .append(", agentName=").append(value(plugin.getAgentName()))
                    .append(", order=").append(plugin.getOrder());
        }
        return summary.toString();
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
