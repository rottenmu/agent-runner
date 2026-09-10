package com.zimo.framework.ai.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.framework.ai.AiAgentProperties;
import com.zimo.framework.ai.intent.IntentAwareSkillRouter;
import com.zimo.framework.ai.intent.IntentCheckableSkill;
import com.zimo.framework.ai.intent.LlmIntentChecker;
import com.zimo.framework.ai.intent.demo.OrderQuerySkill;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class IntentAutoConfigurationTest {


    /** 测试上下文：注册一个演示技能 + 触发自动装配。 */
    @Configuration(proxyBeanMethods = false)
    static class DemoSkillConfig {
        @Bean
        IntentCheckableSkill orderQuerySkill() {
            return new OrderQuerySkill();
        }
    }

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(DemoSkillConfig.class)
            .withConfiguration(AutoConfigurations.of(
                    RestClientAutoConfiguration.class,
                    JacksonAutoConfiguration.class,
                    AiAgentAutoConfiguration.class,
                    IntentAutoConfiguration.class))
            .withPropertyValues(
                    "ai.agent.enabled=true",
                    "ai.agent.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1",
                    "ai.agent.api-key=test-key",
                    "ai.agent.model-name=qwen-plus");

    @Test
    void assemblesRouterWithCollectedSkills() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(IntentAwareSkillRouter.class);
            IntentAwareSkillRouter router = context.getBean(IntentAwareSkillRouter.class);
            // 自动收集了演示技能
            assertThat(router.skills()).hasSize(1);
            assertThat(router.skills().get(0).name()).isEqualTo("order_query");
            // LLM 校验器复用 Agent 配置
            assertThat(context).hasSingleBean(LlmIntentChecker.class);
        });
    }

    @Test
    void routerRoutesWithCollectedSkills() {
        contextRunner.run(context -> {
            IntentAwareSkillRouter router = context.getBean(IntentAwareSkillRouter.class);
            var decision = router.route("帮我查一下订单", com.zimo.framework.ai.intent.ConversationContext.of("t", "u", "s"));
            assertThat(decision.status()).isEqualTo(com.zimo.framework.ai.intent.IntentRoutingDecision.Status.SINGLE);
        });
    }
}
