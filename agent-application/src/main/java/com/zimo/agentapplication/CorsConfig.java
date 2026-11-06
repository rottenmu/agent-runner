package com.zimo.agentapplication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    /** 允许跨域的来源白名单（逗号分隔）。不再使用通配符 "*"。 */
    @Value("${app.cors.allowed-origins:}")
    private String allowedOriginsRaw;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = parseOrigins(allowedOriginsRaw);
        if (origins.isEmpty()) {
            log.warn("app.cors.allowed-origins 未配置，CORS 将不放行任何跨域来源。"
                    + "生产环境请显式配置前端来源，例如 http://localhost:16600");
        } else {
            origins.forEach(config::addAllowedOrigin);
            log.info("CORS 已按白名单放行来源：{}", origins);
        }
        // 凭据只能搭配具体来源，不能配合通配符 "*"（否则浏览器会拒绝携带 Cookie/Authorization）
        config.setAllowCredentials(true);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    private List<String> parseOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
