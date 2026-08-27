package com.zimo.module.ai.autoconfig;

import com.zimo.module.ai.modelconfig.AiModelConfigRepository;
import com.zimo.module.ai.modelconfig.AiModelConfigService;
import com.zimo.module.ai.modelconfig.MybatisPlusAiModelConfigRepository;
import com.zimo.module.ai.modelconfig.mapper.AiModelConfigMapper;
import javax.sql.DataSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * AI 模型配置 MyBatis-Plus 自动装配。
 *
 * <p>配置类只注册 Mapper、Repository、Service 和管理接口，不创建或修改数据库表。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
@AutoConfiguration(after = AiModuleAutoConfiguration.class)
@ConditionalOnProperty(prefix = "plugin.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(DataSource.class)
@MapperScan(basePackageClasses = AiModelConfigMapper.class)
public class AiModelConfigAutoConfiguration {

    /** 注册模型配置 MyBatis-Plus 仓储。 */
    @Bean
    @ConditionalOnMissingBean(AiModelConfigRepository.class)
    public AiModelConfigRepository aiModelConfigRepository(AiModelConfigMapper mapper) {
        return new MybatisPlusAiModelConfigRepository(mapper);
    }

    /** 注册模型配置业务服务。 */
    @Bean
    @ConditionalOnBean(AiModelConfigRepository.class)
    @ConditionalOnMissingBean
    public AiModelConfigService aiModelConfigService(AiModelConfigRepository repository) {
        return new AiModelConfigService(repository);
    }

}