package com.zimo.agentapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.framework.common.PluginRegister;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoadedModuleLoggerTest {

    @Test
    void formatsLoadedModulesByPluginOrder() {
        LoadedModuleLogger logger = new LoadedModuleLogger(List.of(
                plugin("ai", "AI智能体", "/api/ai", "/ai", "ai", "ai-agent", 4),
                plugin("sys", "系统管理", "/api/biz/sys", "/biz/sys", "sys", "", 6),
                plugin("feishu", "飞书平台", "/api/feishu", "/biz/feishu", "feishu", "feishu-agent", 5)
        ));

        String summary = logger.loadedModuleSummary();

        assertThat(summary).contains("Loaded modules: 3");
        assertThat(summary).contains("[1] pluginId=ai");
        assertThat(summary).contains("[2] pluginId=feishu");
        assertThat(summary).contains("[3] pluginId=sys");
        assertThat(summary).contains("frontendModule=ai");
        assertThat(summary).contains("apiPrefix=/api/ai");
        assertThat(summary).contains("frontendRoute=/ai");
        assertThat(summary.indexOf("pluginId=ai")).isLessThan(summary.indexOf("pluginId=feishu"));
        assertThat(summary.indexOf("pluginId=feishu")).isLessThan(summary.indexOf("pluginId=sys"));
    }

    private static PluginRegister plugin(
            String pluginId,
            String pluginName,
            String apiPrefix,
            String frontendRoute,
            String frontendModule,
            String agentName,
            int order
    ) {
        return new PluginRegister() {
            @Override
            public String getPluginId() {
                return pluginId;
            }

            @Override
            public String getPluginName() {
                return pluginName;
            }

            @Override
            public String getApiPrefix() {
                return apiPrefix;
            }

            @Override
            public String getFrontendRoute() {
                return frontendRoute;
            }

            @Override
            public String getFrontendModule() {
                return frontendModule;
            }

            @Override
            public String getAgentName() {
                return agentName;
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }
}
