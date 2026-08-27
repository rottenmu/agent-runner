package com.zimo.framework.autoconfig;

import com.zimo.framework.common.PluginRegister;
import com.zimo.framework.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class PluginRegistry {

    private final List<PluginRegister> plugins;

    public PluginRegistry(List<PluginRegister> plugins) {
        this.plugins = plugins;
    }

    @GetMapping("/plugins")
    public ApiResponse<List<PluginInfo>> listPlugins() {
        List<PluginInfo> list = plugins.stream()
                .sorted(Comparator.comparingInt(PluginRegister::getOrder))
                .map(p -> {
                    PluginInfo info = new PluginInfo();
                    info.setPluginId(p.getPluginId());
                    info.setPluginName(p.getPluginName());
                    info.setApiPrefix(p.getApiPrefix());
                    info.setFrontendRoute(p.getFrontendRoute());
                    info.setFrontendModule(p.getFrontendModule());
                    info.setAgentName(p.getAgentName());
                    info.setEnabled(true);
                    info.setOrder(p.getOrder());
                    return info;
                })
                .collect(Collectors.toList());
        return ApiResponse.ok(list);
    }
}
