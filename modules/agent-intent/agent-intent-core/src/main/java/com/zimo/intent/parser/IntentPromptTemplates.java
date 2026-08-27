package com.zimo.intent.parser;

import cn.hutool.core.io.IoUtil;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

/**
 * 意图识别三模式提示词模板加载工具（仅供静态方法调用，禁止实例化）。
 *
 * <p>模板位于 classpath:ai-intent/ 下：prompt-parse.txt（模式二实时解析）、
 * prompt-generate.txt（模式一规则生成）、prompt-review.txt（模式三校验优化）。
 * 模板内 {rules} 占位符渲染为当前规则库 JSON；模板文件缺失时返回空串，
 * 由调用方决定是否回退内置简化提示词。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-14
 */
public final class IntentPromptTemplates {

    private static final Logger log = LoggerFactory.getLogger(IntentPromptTemplates.class);

    /** {rules} 占位符，渲染时替换为规则库 JSON */
    public static final String RULES_PLACEHOLDER = "{rules}";

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private IntentPromptTemplates() {
    }

    /** 模式二：实时用户意图解析系统提示词模板（含 {rules} 占位符），加载失败返回空串。 */
    public static String parseTemplate() {
        return load("ai-intent/prompt-parse.txt");
    }

    /** 模式一：业务场景 → 意图规则库生成系统提示词模板，加载失败返回空串。 */
    public static String generateTemplate() {
        return load("ai-intent/prompt-generate.txt");
    }

    /** 模式三：规则校验优化系统提示词模板，加载失败返回空串。 */
    public static String reviewTemplate() {
        return load("ai-intent/prompt-review.txt");
    }

    /**
     * 渲染模板：将 {rules} 占位符替换为规则库 JSON。
     *
     * @param template  模板文本，允许为空
     * @param rulesJson 规则库 JSON，允许为空（按空串替换）
     * @return 渲染后的提示词
     */
    public static String render(String template, String rulesJson) {
        if (template == null || template.isBlank()) {
            return "";
        }
        return template.replace(RULES_PLACEHOLDER, rulesJson == null ? "" : rulesJson);
    }

    private static String load(String location) {
        return CACHE.computeIfAbsent(location, path -> {
            try (InputStream in = new ClassPathResource(path).getInputStream()) {
                return IoUtil.read(in, StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("意图提示词模板加载失败: {}", path, e);
                return "";
            }
        });
    }
}
