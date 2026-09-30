package com.zimo.framework.ai.startup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimo.framework.ai.AiAgentProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 模型凭据自检的行为验证。
 *
 * <p>核心判据是<b>归因是否精确</b>：2026-09-18 的 401 事故里，真实根因是「key 失效」，
 * 而当时的排查路径先怀疑了模型名、base-url 与记忆模块。因此这里的测试不满足于
 * 「报了个错」，而是断言<b>报到了正确的字段上</b>。</p>
 *
 * @author Codex
 * @since 2026-09-18
 */
class AiCredentialHealthCheckTest {

    private static AiAgentProperties props(String apiKey, String baseUrl, String model) {
        AiAgentProperties p = new AiAgentProperties();
        p.setApiKey(apiKey);
        p.setBaseUrl(baseUrl);
        p.setModelName(model);
        p.setModelType("dashscope_chat");
        return p;
    }

    @Test
    @DisplayName("全部配置正确时自检通过，且不产生任何问题项")
    void healthyWhenConfigured() {
        AiAgentProperties p = props("sk-" + "0".repeat(32),
                "https://dashscope.aliyuncs.com", "qwen-max");
        AiCredentialHealthCheck.Result r = new AiCredentialHealthCheck(p).report();
        assertTrue(r.healthy(), "配置齐全且形态正确时应通过，实际问题=" + r.problems());
        assertTrue(r.problems().isEmpty());
    }

    @Test
    @DisplayName("★ key 缺失：问题必须落在 apiKey 字段上，而不是含糊的「调用失败」")
    void reportsMissingApiKeyOnApiKeyField() {
        AiAgentProperties p = props(null, "https://dashscope.aliyuncs.com", "qwen-max");
        AiCredentialHealthCheck.Result r = new AiCredentialHealthCheck(p).report();
        assertFalse(r.healthy());
        assertTrue(r.problems().containsKey("apiKey"),
                "缺失 key 必须归因到 apiKey，实际问题=" + r.problems());
        assertFalse(r.problems().containsKey("baseUrl"),
                "baseUrl 正常时不应被牵连报错（否则归因失真）");
        assertFalse(r.problems().containsKey("modelName"));
    }

    @Test
    @DisplayName("★ 百炼 workspace key（sk-ws-…）实测可用，不得被判为问题")
    void workspaceKeyIsAcceptedNotFlagged() {
        // 2026-09-18 实测：这把形态的 key 对 qwen-max 在原生端点返回 200（真实回复）。
        // 旧实现把它判成「形态可疑 + ERROR 日志」，是假警报，会误导用户换掉好 key。
        AiAgentProperties p = props("sk-ws-H.PHYPMEE.vZ6vQdummyDummyDummyDummyDummyDummyDummyDummy"
                        + "DummyDummyDummyDummyDummyDummyDummyDummyDummyDummyJpgLYG",
                "https://dashscope.aliyuncs.com", "qwen-max");
        AiCredentialHealthCheck.Result r = new AiCredentialHealthCheck(p).report();
        assertTrue(r.healthy(),
                "sk-ws- 形态合法，不应计入 problems，实际问题=" + r.problems());
        assertTrue(AiCredentialHealthCheck.isWorkspaceKey(p.getApiKey()),
                "应被识别为 workspace 级凭据（用于输出限权提示）");
    }

    @Test
    @DisplayName("★ 含空白字符的 key 才判非法：复制带换行是最常见的真实失效原因")
    void flagsKeyWithWhitespace() {
        assertFalse(AiCredentialHealthCheck.looksLikeDashScopeKey("sk-abc def"),
                "中间有空格说明复制带了多余内容，会 401");
        assertFalse(AiCredentialHealthCheck.looksLikeDashScopeKey("sk-abcdef\n"),
                "尾部换行同样非法");
        assertTrue(AiCredentialHealthCheck.looksLikeDashScopeKey("  sk-" + "a".repeat(32) + "  "),
                "首尾空白由 trim 吸收，不应误判");
    }

    @Test
    @DisplayName("★ base-url 带 /compatible-mode 被拦下：这是 2026-08-23 全站 404 的根因")
    void flagsCompatibleModeBaseUrl() {
        AiAgentProperties p = props("sk-" + "0".repeat(32),
                "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-max");
        AiCredentialHealthCheck.Result r = new AiCredentialHealthCheck(p).report();
        assertFalse(r.healthy());
        assertTrue(r.problems().containsKey("baseUrl"),
                "带兼容前缀的 base 必须被拦，实际问题=" + r.problems());
        assertTrue(r.problems().get("baseUrl").contains("404"),
                "提示应说明后果是 404，实际=" + r.problems().get("baseUrl"));
    }

    @Test
    @DisplayName("多个问题同时存在时全部报出（不因先命中一个就短路）")
    void reportsAllProblems() {
        AiAgentProperties p = props(null, "", "");
        AiCredentialHealthCheck.Result r = new AiCredentialHealthCheck(p).report();
        assertFalse(r.healthy());
        assertEquals(3, r.problems().size(), "三个字段都应报出，实际=" + r.problems());
        assertTrue(r.problems().containsKey("apiKey"));
        assertTrue(r.problems().containsKey("baseUrl"));
        assertTrue(r.problems().containsKey("modelName"));
    }

    @Test
    @DisplayName("key 脱敏：不泄露完整值，但保留可核对的头尾")
    void masksApiKey() {
        String key = "sk-abcdefghijklmnopqrstuvwxyz012345";
        String masked = AiCredentialHealthCheck.mask(key);
        assertFalse(masked.contains(key), "脱敏结果不得包含原文");
        assertTrue(masked.startsWith("sk-abcde"), "保留前 8 位供核对，实际=" + masked);
        assertTrue(masked.endsWith(key.substring(key.length() - 4)),
                "保留末 4 位供核对，实际=" + masked);
        assertTrue(masked.contains("****"));
        assertEquals("(未配置)", AiCredentialHealthCheck.mask(null));
        assertEquals("****", AiCredentialHealthCheck.mask("sk-x"), "短值整体打码，不泄露长度特征");
    }

    @Test
    @DisplayName("形态判断：只拒绝明显非法的串，不越权断言「key 坏了」")
    void keyShapeRecognition() {
        assertTrue(AiCredentialHealthCheck.looksLikeDashScopeKey("sk-" + "a1B2".repeat(8)),
                "标准 35 位应通过");
        assertTrue(AiCredentialHealthCheck.looksLikeDashScopeKey("sk-" + "0".repeat(31)),
                "31 位只是非标准，不能据此判死（长度不是可用性判据）");
        assertTrue(AiCredentialHealthCheck.looksLikeDashScopeKey("sk-ws-H.PHYPMEE.abc.def-JpgLYG"),
                "workspace 形态应通过");
        assertTrue(AiCredentialHealthCheck.looksLikeDashScopeKey("sk-" + "z".repeat(32)),
                "长度达标、以 sk- 开头：形态检查已放宽，不再因字符集判非法");
        assertFalse(AiCredentialHealthCheck.looksLikeDashScopeKey("AKIAIOSFODNN7EXAMPLE"),
                "不是 sk- 开头，判非法");
        assertFalse(AiCredentialHealthCheck.looksLikeDashScopeKey("sk-short"),
                "过短明显不是 key，判非法");
        assertFalse(AiCredentialHealthCheck.looksLikeDashScopeKey(null));
    }
}
