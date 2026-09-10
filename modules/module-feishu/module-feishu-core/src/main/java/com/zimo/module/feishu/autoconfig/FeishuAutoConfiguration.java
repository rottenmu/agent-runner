package com.zimo.module.feishu.autoconfig;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lark.oapi.Client;
import com.lark.oapi.core.enums.BaseUrlEnum;
import com.zimo.module.feishu.FeishuPluginRegister;
import com.zimo.module.feishu.admin.FeishuAdminPermissionGuard;
import com.zimo.module.feishu.admin.FeishuAgentAdminController;
import com.zimo.module.feishu.admin.FeishuAgentAdminService;
import com.zimo.module.feishu.agent.FeishuAiChannelMessageHandler;
import com.zimo.module.feishu.agent.FeishuAgentCredentialController;
import com.zimo.module.feishu.agent.FeishuAgentCredentialInterceptor;
import com.zimo.module.feishu.agent.FeishuAgentCredentialService;
import com.zimo.module.feishu.agent.FeishuAgentCredentialServiceImpl;
import com.zimo.module.feishu.agent.FeishuAgentCredentialValidator;
import com.zimo.module.feishu.agent.FeishuAppCreationClient;
import com.zimo.module.feishu.agent.FeishuBitableCreateRecordAiSkill;
import com.zimo.module.feishu.agent.FeishuDocumentAppendAiSkill;
import com.zimo.module.feishu.agent.FeishuDocumentCreateAiSkill;
import com.zimo.module.feishu.agent.FeishuProjectCardRenderer;
import com.zimo.module.feishu.cli.FeishuCliCallLogMapper;
import com.zimo.module.feishu.cli.FeishuCliCallLogService;
import com.zimo.module.feishu.cli.FeishuCliExecutor;
import com.zimo.module.feishu.cli.FeishuCliPolicy;
import com.zimo.module.feishu.cli.FeishuCliTemplate;
import com.zimo.module.feishu.cli.bitable.FeishuBitableCliService;
import com.zimo.module.feishu.cli.calendar.FeishuCalendarCliService;
import com.zimo.module.feishu.cli.document.FeishuDocumentCliService;
import com.zimo.module.feishu.cli.task.FeishuTaskCliService;
import com.zimo.module.feishu.channel.FeishuAgentMessageHandler;
import com.zimo.module.feishu.channel.FeishuChannelClientManager;
import com.zimo.module.feishu.channel.FeishuChannelMessageListener;
import com.zimo.module.feishu.channel.FeishuChannelMessageParser;
import com.zimo.module.feishu.channel.NoopFeishuAgentMessageHandler;
import com.zimo.module.feishu.config.FeishuAgentSchemaInitializer;
import com.zimo.module.feishu.config.FeishuConfigController;
import com.zimo.module.feishu.config.FeishuConfigProvider;
import com.zimo.module.feishu.config.FeishuConfigService;
import com.zimo.module.feishu.config.FeishuConfigServiceImpl;
import com.zimo.module.feishu.config.FeishuSchemaInitializer;
import com.zimo.module.feishu.mapper.FeishuConfigMapper;
import com.zimo.module.feishu.event.FeishuEventController;
import com.zimo.module.feishu.event.FeishuEventHandler;
import com.zimo.module.feishu.event.FeishuEventProperties;
import com.zimo.module.feishu.event.NoopFeishuEventHandler;
import com.zimo.module.feishu.file.FeishuFileClient;
import com.zimo.module.feishu.gateway.FeishuAgentBusinessHandler;
import com.zimo.module.feishu.gateway.FeishuAgentCommandRouter;
import com.zimo.module.feishu.gateway.FeishuAgentDispatchService;
import com.zimo.module.feishu.gateway.FeishuAgentGatewayMessageHandler;
import com.zimo.module.feishu.gateway.FeishuAgentRateLimiter;
import com.zimo.module.feishu.gateway.FeishuAgentResultCardFactory;
import com.zimo.module.feishu.log.FeishuMessageLogMapper;
import com.zimo.module.feishu.log.FeishuMessageLogService;
import com.zimo.module.feishu.mapping.FeishuUserMappingMapper;
import com.zimo.module.feishu.mapping.FeishuUserMappingService;
import com.zimo.module.feishu.mapping.FeishuUserPermissionBinder;
import com.zimo.module.feishu.message.FeishuMessageClient;
import com.zimo.module.feishu.message.FeishuMessageService;
import com.zimo.module.feishu.reply.FeishuAgentReplyClient;
import com.zimo.module.feishu.reply.FeishuAgentReplyService;
import com.zimo.module.feishu.reply.FeishuCardTemplateFactory;
import com.zimo.framework.ai.autoconfig.AiAgentAutoConfiguration;
import com.zimo.framework.ai.channel.AiChannelHandler;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;

import java.time.Clock;
import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

@AutoConfiguration(
        after = AiAgentAutoConfiguration.class,
        afterName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@ConditionalOnProperty(prefix = "feishu", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({
        FeishuProperties.class,
        FeishuAgentCredentialProperties.class,
        FeishuAgentChannelProperties.class,
        FeishuCliProperties.class,
        FeishuAgentGatewayProperties.class,
        FeishuAdminProperties.class
})
public class FeishuAutoConfiguration {
    private static final String DATASOURCE_PREFIX = "plugin.feishu.datasource.";

    @Bean(name = "feishuDataSourceProperties")
    @ConditionalOnMissingBean(name = "feishuDataSourceProperties")
    public FeishuDataSourceProperties feishuDataSourceProperties(Environment environment) {
        FeishuModuleApplicationYaml yaml = FeishuModuleApplicationYaml.load(environment);
        FeishuDataSourceProperties properties = new FeishuDataSourceProperties(
                required(yaml.get(DATASOURCE_PREFIX + "url"), DATASOURCE_PREFIX + "url"),
                yaml.get(DATASOURCE_PREFIX + "username"),
                yaml.get(DATASOURCE_PREFIX + "password"),
                yaml.get(DATASOURCE_PREFIX + "driver-class-name"));
        properties.validateSupported();
        return properties;
    }

    @Bean(name = "feishuDataSourceHolder")
    @ConditionalOnMissingBean(name = "feishuDataSourceHolder")
    public FeishuDataSourceHolder feishuDataSourceHolder(
            @Qualifier("feishuDataSourceProperties") FeishuDataSourceProperties properties) {
        properties.validateSupported();
        DataSource dataSource = DataSourceBuilder.create()
                .url(properties.url())
                .username(properties.username())
                .password(properties.password())
                .driverClassName(properties.driverClassName())
                .build();
        return new FeishuDataSourceHolder(dataSource);
    }

    @Bean(name = "feishuDataSource", autowireCandidate = false)
    @ConditionalOnMissingBean(name = "feishuDataSource")
    public DataSource feishuDataSource(
            @Qualifier("feishuDataSourceHolder") FeishuDataSourceHolder holder) {
        return holder.dataSource();
    }

    @Bean(name = "feishuTransactionManager", autowireCandidate = false)
    @ConditionalOnMissingBean(name = "feishuTransactionManager")
    public PlatformTransactionManager feishuTransactionManager(
            @Qualifier("feishuDataSourceHolder") FeishuDataSourceHolder holder) {
        return new DataSourceTransactionManager(holder.dataSource());
    }

    @Bean(name = "feishuSqlSessionFactory", autowireCandidate = false)
    @ConditionalOnMissingBean(name = "feishuSqlSessionFactory")
    public SqlSessionFactory feishuSqlSessionFactory(
            @Qualifier("feishuDataSourceHolder") FeishuDataSourceHolder holder) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(holder.dataSource());
        factoryBean.setConfiguration(configuration);
        return Objects.requireNonNull(factoryBean.getObject(), "feishuSqlSessionFactory must not be null");
    }

    @Bean
    @ConditionalOnMissingBean
    public Client feishuClient(FeishuProperties properties) {
        requireText(properties.getAppId(), "feishu.app-id must not be blank");
        requireText(properties.getAppSecret(), "feishu.app-secret must not be blank");
        return Client.newBuilder(properties.getAppId(), properties.getAppSecret())
                .openBaseUrl(BaseUrlEnum.FeiShu)
                .logReqAtDebug(true)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public RuntimeFeishuConfigProvider runtimeFeishuConfigProvider(
            ObjectProvider<FeishuConfigService> configServiceProvider,
            FeishuProperties properties) {
        return new RuntimeFeishuConfigProvider(configServiceProvider, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeishuPluginRegister feishuPluginRegister() {
        return new FeishuPluginRegister();
    }

    @Bean
    @ConditionalOnMissingBean
    public FeishuMessageClient feishuMessageClient(FeishuConfigProvider feishuConfigProvider) {
        return new OfficialFeishuMessageClient(feishuConfigProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeishuMessageService feishuMessageService(FeishuMessageClient feishuMessageClient) {
        return new FeishuMessageService(feishuMessageClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeishuEventHandler feishuEventHandler() {
        return new NoopFeishuEventHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(name = "feishuSqlSessionFactory")
    public FeishuConfigService feishuConfigService(FeishuConfigMapper feishuConfigMapper) {
        return new FeishuConfigServiceImpl(feishuConfigMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(FeishuConfigService.class)
    public FeishuConfigController feishuConfigController(FeishuConfigService feishuConfigService) {
        return new FeishuConfigController(feishuConfigService);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeishuEventController feishuEventController(
            FeishuEventProperties feishuEventProperties,
            FeishuEventHandler feishuEventHandler) {
        return new FeishuEventController(feishuEventProperties, feishuEventHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.agent.credential", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuAppCreationClient feishuAppCreationClient(FeishuAgentCredentialProperties properties) {
        return new OfficialFeishuAppCreationClient(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.agent.credential", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuAgentCredentialValidator feishuAgentCredentialValidator() {
        return new FeishuAgentCredentialValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(FeishuConfigService.class)
    @ConditionalOnProperty(prefix = "feishu.agent.credential", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuAgentCredentialService feishuAgentCredentialService(
            FeishuAppCreationClient appCreationClient,
            FeishuConfigService configService,
            FeishuAgentCredentialValidator validator) {
        return new FeishuAgentCredentialServiceImpl(appCreationClient, configService, validator);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(FeishuAgentCredentialService.class)
    @ConditionalOnProperty(prefix = "feishu.agent.credential", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuAgentCredentialController feishuAgentCredentialController(
            FeishuAgentCredentialService service) {
        return new FeishuAgentCredentialController(service);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(FeishuConfigService.class)
    @ConditionalOnProperty(prefix = "feishu.agent.credential", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuAgentCredentialInterceptor feishuAgentCredentialInterceptor(
            FeishuConfigService configService,
            FeishuAgentCredentialProperties properties) {
        return new FeishuAgentCredentialInterceptor(configService, properties.isValidateBeforeAgentCall());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(FeishuAgentCredentialInterceptor.class)
    @ConditionalOnProperty(prefix = "feishu.agent.credential", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuAgentCredentialWebConfig feishuAgentCredentialWebConfig(
            FeishuAgentCredentialInterceptor interceptor) {
        return new FeishuAgentCredentialWebConfig(interceptor);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.agent.channel", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuChannelMessageParser feishuChannelMessageParser() {
        return new FeishuChannelMessageParser();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.agent.channel", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuAgentReplyClient feishuAgentReplyClient(FeishuConfigProvider feishuConfigProvider) {
        return new OfficialFeishuAgentReplyClient(feishuConfigProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.agent.channel", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuAgentReplyService feishuAgentReplyService(
            FeishuAgentReplyClient feishuAgentReplyClient,
            FeishuMessageLogService feishuMessageLogService
    ) {
        return new FeishuAgentReplyService(feishuAgentReplyClient, feishuMessageLogService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.agent.channel", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuFileClient feishuFileClient(FeishuConfigProvider feishuConfigProvider) {
        return new OfficialFeishuFileClient(feishuConfigProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.agent.channel", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuUserMappingService feishuUserMappingService(ObjectProvider<FeishuUserMappingMapper> mapperProvider) {
        return new FeishuUserMappingService(mapperProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.agent.channel", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuUserPermissionBinder feishuUserPermissionBinder() {
        return new FeishuUserPermissionBinder();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.agent.channel", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuMessageLogService feishuMessageLogService(
            ObjectProvider<FeishuMessageLogMapper> mapperProvider,
            FeishuAgentChannelProperties properties) {
        return new FeishuMessageLogService(mapperProvider.getIfAvailable(), properties.getLog().isEnabled());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.agent", name = {"channel.enabled", "gateway.enabled"},
            havingValue = "true", matchIfMissing = true)
    public FeishuAgentCommandRouter feishuAgentCommandRouter(
            ObjectProvider<FeishuAgentBusinessHandler> handlerProvider) {
        return new FeishuAgentCommandRouter(handlerProvider.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.agent", name = {"channel.enabled", "gateway.enabled"},
            havingValue = "true", matchIfMissing = true)
    public FeishuAgentRateLimiter feishuAgentRateLimiter(FeishuAgentGatewayProperties properties) {
        return new FeishuAgentRateLimiter(
                Clock.systemUTC(),
                properties.isRateLimitEnabled(),
                properties.getRateLimitWindowSeconds(),
                properties.getRateLimitMaxRequests());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.agent", name = {"channel.enabled", "gateway.enabled"},
            havingValue = "true", matchIfMissing = true)
    public FeishuAgentResultCardFactory feishuAgentResultCardFactory() {
        return new FeishuAgentResultCardFactory(new FeishuCardTemplateFactory());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression("${feishu.agent.channel.enabled:true} && ${feishu.agent.gateway.enabled:true} && ${feishu.cli.enabled:true}")
    public FeishuAgentDispatchService feishuAgentDispatchService(
            FeishuConfigProvider configProvider,
            FeishuAgentCommandRouter router,
            FeishuBitableCliService bitableCliService,
            FeishuAgentRateLimiter rateLimiter,
            FeishuAgentResultCardFactory cardFactory,
            FeishuAgentGatewayProperties properties) {
        return new FeishuAgentDispatchService(
                configProvider,
                router,
                bitableCliService,
                rateLimiter,
                cardFactory,
                properties.isProgressReplyEnabled(),
                properties.isArchiveEnabled(),
                properties.getArchiveAppToken(),
                properties.getArchiveTableId());
    }

    @Bean
    @ConditionalOnMissingBean(FeishuAgentMessageHandler.class)
    @ConditionalOnBean(AiChannelHandler.class)
    @ConditionalOnProperty(prefix = "feishu.agent.ai-channel", name = "enabled", havingValue = "true")
    public FeishuAgentMessageHandler feishuAiChannelMessageHandler(
            AiChannelHandler aiChannelHandler,
            FeishuProjectCardRenderer projectCardRenderer,
            FeishuFileClient feishuFileClient,
            FeishuConfigProvider feishuConfigProvider) {
        return new FeishuAiChannelMessageHandler(
                aiChannelHandler,
                projectCardRenderer,
                feishuFileClient,
                feishuConfigProvider);
    }

    @Bean
    @ConditionalOnMissingBean(FeishuAgentMessageHandler.class)
    @ConditionalOnBean(FeishuAgentDispatchService.class)
    @ConditionalOnProperty(prefix = "feishu.agent", name = {"channel.enabled", "gateway.enabled"},
            havingValue = "true", matchIfMissing = true)
    public FeishuAgentMessageHandler feishuAgentGatewayMessageHandler(FeishuAgentDispatchService dispatchService) {
        return new FeishuAgentGatewayMessageHandler(dispatchService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.agent.channel", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuAgentMessageHandler feishuAgentMessageHandler() {
        return new NoopFeishuAgentMessageHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.agent.channel", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuChannelMessageListener feishuChannelMessageListener(
            FeishuChannelMessageParser parser,
            FeishuUserMappingService mappingService,
            FeishuUserPermissionBinder permissionBinder,
            FeishuAgentMessageHandler messageHandler,
            FeishuAgentReplyService replyService,
            FeishuMessageLogService logService) {
        return new FeishuChannelMessageListener(
                parser,
                mappingService,
                permissionBinder,
                messageHandler,
                replyService,
                logService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.agent.channel", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuChannelClientManager feishuChannelClientManager(
            FeishuConfigProvider feishuConfigProvider,
            FeishuChannelMessageListener listener,
            FeishuAgentChannelProperties properties) {
        return new OfficialFeishuChannelClientManager(feishuConfigProvider, listener, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.cli", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuCliExecutor feishuCliExecutor(FeishuCliProperties properties) {
        return new ProcessFeishuCliExecutor(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.cli", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuCliPolicy feishuCliPolicy(FeishuCliProperties properties) {
        return FeishuCliPolicy.allowOnly(properties.getAllowedBusinessTypes());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.cli", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuCliCallLogService feishuCliCallLogService(
            ObjectProvider<FeishuCliCallLogMapper> mapperProvider,
            FeishuCliProperties properties) {
        return new FeishuCliCallLogService(mapperProvider.getIfAvailable(), properties.isLogEnabled());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.cli", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuCliTemplate feishuCliTemplate(
            FeishuCliExecutor executor,
            FeishuCliCallLogService logService,
            FeishuCliPolicy policy,
            FeishuCliProperties properties) {
        return new FeishuCliTemplate(executor, logService, policy, properties.getRetryTimes());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.cli", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuBitableCliService feishuBitableCliService(FeishuCliTemplate template) {
        return new FeishuBitableCliService(template);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.agent.ai-channel", name = "enabled", havingValue = "true")
    public FeishuProjectCardRenderer feishuProjectCardRenderer() {
        return new FeishuProjectCardRenderer();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.cli", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuDocumentCliService feishuDocumentCliService(FeishuCliTemplate template) {
        return new FeishuDocumentCliService(template);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.cli", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuCalendarCliService feishuCalendarCliService(FeishuCliTemplate template) {
        return new FeishuCalendarCliService(template);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feishu.cli", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuTaskCliService feishuTaskCliService(FeishuCliTemplate template) {
        return new FeishuTaskCliService(template);
    }

    @Bean
    @ConditionalOnMissingBean(name = "feishuBitableCreateRecordAiSkill")
    @ConditionalOnBean(FeishuBitableCliService.class)
    @ConditionalOnProperty(prefix = "feishu.cli", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuBitableCreateRecordAiSkill feishuBitableCreateRecordAiSkill(
            FeishuBitableCliService bitableCliService) {
        return new FeishuBitableCreateRecordAiSkill(bitableCliService);
    }

    @Bean
    @ConditionalOnMissingBean(name = "feishuDocumentCreateAiSkill")
    @ConditionalOnBean(FeishuDocumentCliService.class)
    @ConditionalOnProperty(prefix = "feishu.cli", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuDocumentCreateAiSkill feishuDocumentCreateAiSkill(
            FeishuDocumentCliService documentCliService) {
        return new FeishuDocumentCreateAiSkill(documentCliService);
    }

    @Bean
    @ConditionalOnMissingBean(name = "feishuDocumentAppendAiSkill")
    @ConditionalOnBean(FeishuDocumentCliService.class)
    @ConditionalOnProperty(prefix = "feishu.cli", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuDocumentAppendAiSkill feishuDocumentAppendAiSkill(
            FeishuDocumentCliService documentCliService) {
        return new FeishuDocumentAppendAiSkill(documentCliService);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeishuAdminPermissionGuard feishuAdminPermissionGuard(FeishuAdminProperties properties) {
        return new FeishuAdminPermissionGuard(properties.getApiToken());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({
            FeishuAgentCredentialService.class,
            FeishuConfigService.class,
            FeishuChannelClientManager.class,
            FeishuBitableCliService.class,
            FeishuMessageLogService.class
    })
    @ConditionalOnProperty(prefix = "feishu.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuAgentAdminService feishuAgentAdminService(
            FeishuAgentCredentialService credentialService,
            FeishuConfigService configService,
            FeishuChannelClientManager channelClientManager,
            FeishuBitableCliService bitableCliService,
            FeishuMessageLogService messageLogService) {
        return new FeishuAgentAdminService(
                credentialService,
                configService,
                channelClientManager,
                bitableCliService,
                messageLogService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(FeishuAgentAdminService.class)
    @ConditionalOnProperty(prefix = "feishu.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeishuAgentAdminController feishuAgentAdminController(
            FeishuAdminPermissionGuard permissionGuard,
            FeishuAgentAdminService adminService) {
        return new FeishuAgentAdminController(permissionGuard, adminService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(name = "feishuDataSourceHolder")
    public FeishuSchemaInitializer feishuSchemaInitializer(
            @Qualifier("feishuDataSourceHolder") FeishuDataSourceHolder holder) {
        return new FeishuSchemaInitializer(holder.dataSource());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(name = "feishuDataSourceHolder")
    public FeishuAgentSchemaInitializer feishuAgentSchemaInitializer(
            @Qualifier("feishuDataSourceHolder") FeishuDataSourceHolder holder) {
        return new FeishuAgentSchemaInitializer(holder.dataSource());
    }

    private static void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
    }

    private static String required(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName + " must be configured in "
                    + FeishuModuleApplicationYaml.LOCATION);
        }
        return value;
    }

    @Configuration(proxyBeanMethods = false)
    @MapperScan(
            basePackages = {
                    "com.zimo.module.feishu.mapper",
                    "com.zimo.module.feishu.mapping",
                    "com.zimo.module.feishu.log",
                    "com.zimo.module.feishu.cli"
            },
            markerInterface = BaseMapper.class,
            sqlSessionFactoryRef = "feishuSqlSessionFactory",
            nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
    static class FeishuMapperConfiguration {
    }
}
