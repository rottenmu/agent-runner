package com.zimo.module.ai.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * AI 技能 ZIP 导入的 Servlet Multipart 自动配置。
 *
 * <p>该配置为技能导入提供低优先级的 Multipart 默认值，并注册仅处理技能导入路径的异常解析器。
 * 应用显式声明的 {@code spring.servlet.multipart.*} 配置优先级更高，可继续满足其他业务模块的上传需求。</p>
 *
 * @author Codex
 * @since 2026-07-27
 */
@AutoConfiguration(
        after = JacksonAutoConfiguration.class,
        before = MultipartAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({MultipartFile.class, HandlerExceptionResolver.class})
@ConditionalOnProperty(prefix = "plugin.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
@PropertySource("classpath:META-INF/module-ai/skill-import-multipart-defaults.properties")
public class AiSkillMultipartWebAutoConfiguration {

    /**
     * 注册技能 ZIP 导入 Multipart 异常解析器。
     *
     * @param objectMapper Spring Boot JSON 映射器，不能为空
     * @return 仅匹配技能导入路径的高优先级异常解析器
     */
    @Bean
    @ConditionalOnMissingBean(name = "aiSkillMultipartExceptionResolver")
    public HandlerExceptionResolver aiSkillMultipartExceptionResolver(ObjectMapper objectMapper) {
        return new AiSkillMultipartExceptionResolver(objectMapper);
    }
}
