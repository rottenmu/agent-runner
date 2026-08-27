package com.zimo.module.ai.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.ai.AiPluginRegister;
import com.zimo.module.ai.controller.AiAgentAdminController;
import com.zimo.module.ai.controller.AiModelConfigController;
import com.zimo.module.ai.controller.AiSkillAdminController;
import com.zimo.module.ai.management.AiAgentManagementService;
import com.zimo.module.ai.management.AiPromptTemplateService;
import com.zimo.module.ai.modelconfig.AiModelConfigRepository;
import com.zimo.module.ai.modelconfig.MybatisPlusAiModelConfigRepository;
import com.zimo.module.ai.modelconfig.AiModelConfigService;
import com.zimo.module.ai.modelconfig.mapper.AiModelConfigMapper;
import com.zimo.module.ai.skill.AiPluginStatusSkill;
import com.zimo.module.ai.skillimport.AiSkillZipImportService;
import com.zimo.module.ai.skillimport.AiSkillZipParser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AiModuleAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AiModuleAutoConfiguration.class,
                    AiSkillAdminAutoConfiguration.class,
                    AiModelConfigAutoConfiguration.class));

    @Test
    void registersAiPluginAndModuleSkill() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AiProperties.class);
            assertThat(context).hasSingleBean(AiPluginRegister.class);
            assertThat(context).hasSingleBean(AiPluginStatusSkill.class);
            assertThat(context).doesNotHaveBean(AiSkillAdminController.class);
        });
    }

    @Test
    void createsAllManagementControllersFromModuleServices() {
        // P0-1 后 controller 由 @ComponentScan 注册（AiControllerScanAutoConfiguration），
        // 此处仅做构造器冒烟验证，不再断言配置类 @Bean 方法
        AiAgentManagementService managementService = mock(AiAgentManagementService.class);
        AiSkillZipImportService importService = mock(AiSkillZipImportService.class);

        assertThat(new AiAgentAdminController(managementService))
                .isInstanceOf(AiAgentAdminController.class);
        assertThat(new AiSkillAdminController(managementService, importService))
                .isInstanceOf(AiSkillAdminController.class);
    }

    @Test
    void createsSkillImportBeansAndController() {
        // P1-3 后 aiSkillZipParser 归属 AiSkillCoreAutoConfiguration，此处直接构造冒烟
        ObjectMapper objectMapper = new ObjectMapper();
        AiAgentManagementService managementService = mock(AiAgentManagementService.class);

        AiSkillZipParser parser = new AiSkillZipParser(objectMapper);
        AiSkillZipImportService importService =
                new AiSkillZipImportService(parser, managementService);

        assertThat(parser).isNotNull();
        assertThat(importService).isNotNull();
    }

    @Test
    void createsModelConfigBeansFromMybatisPlusMapper() {
        AiModelConfigAutoConfiguration configuration = new AiModelConfigAutoConfiguration();
        AiModelConfigMapper mapper = mock(AiModelConfigMapper.class);

        AiModelConfigRepository repository = configuration.aiModelConfigRepository(mapper);

        assertThat(repository).isInstanceOf(MybatisPlusAiModelConfigRepository.class);
        assertThat(configuration.aiModelConfigService(repository))
                .isInstanceOf(AiModelConfigService.class);
    }

    @Test
    void skipsModelConfigBeansWhenDataSourceMissing() {
        contextRunner.run(context -> {
                    assertThat(context).doesNotHaveBean(AiModelConfigRepository.class);
                    assertThat(context).doesNotHaveBean(AiModelConfigService.class);
                    assertThat(context).doesNotHaveBean(AiModelConfigController.class);
                });
    }

    @Test
    void skipsManagementBeansWhenStarterRuntimeIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(AiSkillAdminController.class);
            assertThat(context).doesNotHaveBean(AiAgentManagementService.class);
        });
    }
}