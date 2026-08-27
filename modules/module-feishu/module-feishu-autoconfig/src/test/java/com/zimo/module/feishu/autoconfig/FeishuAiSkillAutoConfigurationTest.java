package com.zimo.module.feishu.autoconfig;

import com.zimo.module.feishu.agent.FeishuBitableCreateRecordAiSkill;
import com.zimo.module.feishu.agent.FeishuDocumentAppendAiSkill;
import com.zimo.module.feishu.agent.FeishuDocumentCreateAiSkill;
import com.zimo.starter.ai.autoconfig.AiAgentAutoConfiguration;
import com.zimo.starter.ai.skill.AiSkillDescriptor;
import com.zimo.starter.ai.skill.AiSkillRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuAiSkillAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RestClientAutoConfiguration.class,
                    AiAgentAutoConfiguration.class,
                    FeishuAutoConfiguration.class, JacksonAutoConfiguration.class))
            .withPropertyValues(
                    "ai.agent.enabled=true",
                    "ai.agent.api-key=",
                    "feishu.app-id=test_app",
                    "feishu.app-secret=test_secret",
                    "feishu.verification-token=test_token",
                    "feishu.encrypt-key=test_encrypt_key",
                    "feishu.agent.channel.enabled=false",
                    "feishu.agent.gateway.enabled=false",
                    "feishu.cli.enabled=true");

    @Test
    void registersFeishuCliSkillsIntoAiSkillRegistry() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(FeishuBitableCreateRecordAiSkill.class);
            assertThat(context).hasSingleBean(FeishuDocumentCreateAiSkill.class);
            assertThat(context).hasSingleBean(FeishuDocumentAppendAiSkill.class);

            AiSkillRegistry registry = context.getBean(AiSkillRegistry.class);
            assertThat(registry.list())
                    .extracting(AiSkillDescriptor::name)
                    .contains(
                            "feishu_bitable_create_record",
                            "feishu_document_create",
                            "feishu_document_append");
        });
    }
}
