package com.zimo.framework.ai.startup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 模型调用失败归因的行为验证。
 *
 * <p>判据设计刻意包含<b>负向对照</b>：不能「什么都归因成 key 问题」。
 * 2026-09-18 的事故里 401(InvalidApiKey) 与 403(AccessDenied) 的处置完全不同，
 * 若归因把二者混为一谈，就等于把人从一个坑引到另一个坑。</p>
 *
 * @author Codex
 * @since 2026-09-18
 */
class ModelCallFailureDiagnosisTest {

    /** 用户报障时的真实原文（request_id 已换成占位）。 */
    private static final String REAL_401 = "HTTP request failed with status 401 | "
            + "{\"code\":\"InvalidApiKey\",\"message\":\"Invalid API-key provided.\","
            + "\"request_id\":\"97a7b18e-de82-90b8-b1db-f1e1b01ab065\"} | Response body: "
            + "{\"code\":\"InvalidApiKey\",\"message\":\"Invalid API-key provided.\","
            + "\"request_id\":\"97a7b18e-de82-90b8-b1db-f1e1b01ab065\"}";

    @Test
    @DisplayName("★ 用户报障原文：必须归因到 API Key，且明确排除模型名/base-url")
    void diagnosesRealIncident() {
        ModelCallFailureDiagnosis.Diagnosis d = ModelCallFailureDiagnosis.diagnose(REAL_401);
        assertNotNull(d, "这条真实报障必须能被归因");
        assertTrue(d.summary().contains("401"), "结论应点明 401，实际=" + d.summary());
        assertTrue(d.summary().contains("Key"), "结论应指向 Key，实际=" + d.summary());
        assertTrue(d.summary().contains("不是模型名"),
                "必须显式排除模型名方向的误判，实际=" + d.summary());
        assertTrue(d.action().contains("AI_API_KEY"),
                "处置应给出具体配置项名，实际=" + d.action());
        assertTrue(d.action().contains("重启"),
                "必须提示 key 不热生效，否则改完不重启会以为没好，实际=" + d.action());
    }

    @Test
    @DisplayName("★ 401 与 403 的归因必须不同（对照，防止「统一归因成 key 问题」）")
    void distinguishes401From403() {
        ModelCallFailureDiagnosis.Diagnosis d401 =
                ModelCallFailureDiagnosis.diagnose(REAL_401);
        ModelCallFailureDiagnosis.Diagnosis d403 = ModelCallFailureDiagnosis.diagnose(
                "HTTP request failed with status 403 | {\"code\":\"AccessDenied\","
                        + "\"message\":\"Access denied by API-Key restrictions.\"}");
        assertNotNull(d401);
        assertNotNull(d403);
        assertTrue(d401.action().contains("有效") && !d401.action().contains("白名单"),
                "401 的处置是「换/修 key」，不该让人去改白名单，实际=" + d401.action());
        assertTrue(d403.summary().contains("授权"),
                "403 应归因为「没授权」，实际=" + d403.summary());
        assertTrue(d403.action().contains("白名单"),
                "403 的处置才是改白名单，实际=" + d403.action());
        assertTrue(!d401.summary().equals(d403.summary()), "两者结论必须不同");
    }

    @Test
    @DisplayName("★ 404 归因指向 base-url 的 /compatible-mode 陷阱（2026-08-23 事故）")
    void diagnoses404() {
        ModelCallFailureDiagnosis.Diagnosis d = ModelCallFailureDiagnosis.diagnose(
                "HTTP request failed with status 404 ");
        assertNotNull(d);
        assertTrue(d.action().contains("compatible-mode"),
                "404 处置应提示去掉兼容前缀，实际=" + d.action());
    }

    @Test
    @DisplayName("额度不足 403 AllocationQuota：归因到模型名，而非 key")
    void diagnosesQuota() {
        ModelCallFailureDiagnosis.Diagnosis d = ModelCallFailureDiagnosis.diagnose(
                "{\"code\":\"AllocationQuota.FreeTierOnly\"}");
        assertNotNull(d);
        assertTrue(d.action().contains("模型名"),
                "额度类错误应让人去改模型名，实际=" + d.action());
        assertTrue(!d.action().contains("AI_API_KEY"),
                "不该误导去改 key，实际=" + d.action());
    }

    @Test
    @DisplayName("429 与 5xx：归因为限流/上游故障，明确「不是配置错误」")
    void diagnosesNonConfigErrors() {
        ModelCallFailureDiagnosis.Diagnosis d429 =
                ModelCallFailureDiagnosis.diagnose("status 429 too many requests");
        assertNotNull(d429);
        assertTrue(d429.summary().contains("限流"), "实际=" + d429.summary());
        assertTrue(d429.action().contains("不是配置错误"),
                "必须明确排除配置方向，实际=" + d429.action());

        ModelCallFailureDiagnosis.Diagnosis d500 =
                ModelCallFailureDiagnosis.diagnose("status 500 internal error");
        assertNotNull(d500);
        assertTrue(d500.action().contains("重试"), "实际=" + d500.action());
    }

    @Test
    @DisplayName("不认识的错误返回 null（不硬凑归因，避免误导）")
    void returnsNullForUnknown() {
        assertNull(ModelCallFailureDiagnosis.diagnose("something totally unrelated"));
        assertNull(ModelCallFailureDiagnosis.diagnose(""));
        assertNull(ModelCallFailureDiagnosis.diagnose(null));
    }

    @Test
    @DisplayName("渲染结果同时含归因与处置，且保留原文入口")
    void rendersBothLines() {
        ModelCallFailureDiagnosis.Diagnosis d =
                ModelCallFailureDiagnosis.diagnose(REAL_401);
        String rendered = d.render();
        assertTrue(rendered.startsWith("【归因】"), "实际=" + rendered);
        assertTrue(rendered.contains("【处置】"), "实际=" + rendered);
        assertEquals(2, rendered.split("\n").length, "应恰好两行，实际=" + rendered);
    }
}
