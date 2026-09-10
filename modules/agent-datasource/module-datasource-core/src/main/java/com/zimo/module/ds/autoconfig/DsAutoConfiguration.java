package com.zimo.module.ds.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.ds.connector.DsConnectorFactory;
import com.zimo.module.ds.mapper.DsDataSourceMapper;
import com.zimo.module.ds.service.DsDataSourceService;
import com.zimo.module.ds.skill.DsQuerySkill;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 多数据源模块自动装配。
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
@AutoConfiguration
@MapperScan("com.zimo.module.ds.mapper")
public class DsAutoConfiguration {

    /** 注册数据源连接器注册表。 */
    @Bean
    @ConditionalOnMissingBean
    public DsConnectorFactory dsConnectorFactory() {
        return new DsConnectorFactory();
    }

    /** 注册数据源管理服务。 */
    @Bean
    @ConditionalOnMissingBean
    public DsDataSourceService dsDataSourceService(
            DsDataSourceMapper mapper,
            DsConnectorFactory connectorFactory,
            ObjectMapper objectMapper) {
        return new DsDataSourceService(mapper, connectorFactory, objectMapper);
    }

    /** 注册数据源查询技能（自动加入智能体技能注册表，供 agent 绑定调用）。 */
    @Bean
    @ConditionalOnMissingBean
    public DsQuerySkill dsQuerySkill(DsDataSourceService service) {
        return new DsQuerySkill(service);
    }
}
