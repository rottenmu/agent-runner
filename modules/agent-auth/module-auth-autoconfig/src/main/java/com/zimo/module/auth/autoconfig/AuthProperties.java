package com.zimo.module.auth.autoconfig;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "plugin.auth")
public class AuthProperties {

    private boolean enabled = true;
    private Interceptor interceptor = new Interceptor();

    @Data
    public static class Interceptor {

        private boolean enabled = true;
        private List<String> includePaths = new ArrayList<>(List.of("/api/**"));
        private List<String> excludePaths = new ArrayList<>(List.of(
                "/api/auth/login",
                "/api/auth/register",
                "/api/plugins",
                // /api/agent-memory/mcp 为记忆 MCP 端点，由端点内自行校验 Bearer/裸 token
                "/api/agent-memory/mcp",
                "/api/channel/inbound",
                "/api/channel/sdk.js",
                "/api/mobile/wms/stock/inbound",
                "/api/mobile/wms/stock/outbound",
                // /api/v1/** 为 module-agent-deploy 管理域，由模块自身 X-Admin-Token 鉴权
                "/api/v1/**"));
    }
}
