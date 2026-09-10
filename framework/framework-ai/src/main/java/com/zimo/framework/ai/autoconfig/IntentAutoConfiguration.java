package com.zimo.framework.ai.autoconfig;

import com.zimo.framework.ai.AiAgentProperties;
import com.zimo.framework.ai.intent.IntentAwareSkillRouter;
import com.zimo.framework.ai.intent.IntentCheckableSkill;
import com.zimo.framework.ai.intent.LlmIntentChecker;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 技能意图准入框架自动装配。
 *
 * <p>启用后自动收集全部 {@link IntentCheckableSkill} Bean，注册
 * {@link IntentAwareSkillRouter}（Agent 入口做技能意图路由）；同时注册
 * {@link LlmIntentChecker}（复用 ai.agent.base-url / api-key / model-name 配置）。</p>
 *
 * <p><b>与 module-intent 的边界</b>：本配置（{@code ai.intent.enabled}）负责
 * <i>技能执行前的意图准入</i>（{@code IntentAwareSkillRouter} 判定技能是否匹配
 * 用户意图）；{@code module-intent} 模块（{@code plugin.intent.enabled}）负责
 * <i>用户意图识别</i>（{@code IntentRecognitionService}）。两者职责不同、开关
 * 独立、互不依赖，类名相近仅为历史命名，勿合并。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-15
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ai.intent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IntentAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(IntentAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public LlmIntentChecker llmIntentChecker(AiAgentProperties aiAgentProperties) {
        String baseUrl = aiAgentProperties.getBaseUrl();
        LlmIntentChecker.LlmConfig config = new LlmIntentChecker.LlmConfig(
                baseUrl == null ? "" : baseUrl,
                aiAgentProperties.getApiKey(),
                aiAgentProperties.getModelName(),
                30000);
        log.info("意图校验 LLM 解析器已装配（base-url={}）", baseUrl);
        return new LlmIntentChecker(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public IntentAwareSkillRouter intentAwareSkillRouter(List<IntentCheckableSkill> skills) {
        log.info("意图准入路由已装配，登记技能 {} 个", skills == null ? 0 : skills.size());
        return new IntentAwareSkillRouter(skills);
    }
}
