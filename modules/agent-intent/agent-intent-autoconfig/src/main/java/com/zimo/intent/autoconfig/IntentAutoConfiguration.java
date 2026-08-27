package com.zimo.intent.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.intent.IntentProperties;
import com.zimo.intent.service.IntentRecognitionService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 通用意图识别引擎自动装配。
 *
 * <p>受 {@code plugin.intent.enabled} 开关控制；负责注册规则引擎与解析接口。
 * 任意业务模块依赖本模块后即可注入 {@link IntentRecognitionService} 使用。</p>
 *
 * <p><b>与 starter 侧意图组件的边界</b>：本模块（{@code module-intent}）负责
 * <i>用户意图识别</i>（把自然语言输入归类到业务意图，见
 * {@link com.zimo.intent.IntentRecognitionService}）；starter 侧
 * {@code com.zimo.starter.ai.intent}（{@code IntentAwareSkillRouter}/
 * {@code LlmIntentChecker}，受 {@code ai.intent.enabled} 控制）负责
 * <i>技能执行前的意图准入校验</i>（技能是否匹配用户意图才放行）。两者
 * 职责不同、开关独立、互不依赖，类名相近仅为历史命名，勿合并。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-14
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "plugin.intent", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(IntentProperties.class)
public class IntentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IntentRecognitionService intentRecognitionService(ObjectMapper objectMapper, IntentProperties intentProperties) {
        return new IntentRecognitionService(objectMapper, intentProperties);
    }
}
