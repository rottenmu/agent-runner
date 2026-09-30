package com.zimo.framework.ai.startup;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型调用失败的错误归因：把上游返回的原始报文翻译成「是哪一环坏了、该去改什么」。
 *
 * <p><b>为什么需要它</b>：上游异常原文形如</p>
 *
 * <pre>
 * HTTP request failed with status 401 | {"code":"InvalidApiKey","message":"Invalid API-key
 * provided.","request_id":"97a7b18e-..."} | Response body: {...同上一段...}
 * </pre>
 *
 * <p>同一份 JSON 被打印了两遍，信息密度极低，且<b>完全没提是哪个配置项的问题</b>。
 * 2026-09-18 的实际排查代价：先怀疑模型名、再怀疑 base-url、再怀疑记忆模块，
 * 最后才发现只是环境变量里的 key 失效。<b>错误文案把人引向了错误的方向。</b></p>
 *
 * <p>本类按上游 {@code code} 字段归因（不靠正则匹配 message 文案，避免上游改文案就失效），
 * 输出一行结论 + 一行处置建议。<b>原始报文仍然保留</b>在末尾 —— 归因是叠加的辅助信息，
 * 不能替代证据本身。</p>
 *
 * @author Codex
 * @since 2026-09-18
 */
public final class ModelCallFailureDiagnosis {

    private ModelCallFailureDiagnosis() {
    }

    /**
     * 按上游错误码给出归因与处置建议。
     *
     * @param rawMessage 上游异常原文（可以含密钥，本方法不做脱敏，脱敏由调用方负责）
     * @return 归因结论；无法识别时返回 {@code null}（调用方保留原文即可）
     */
    public static Diagnosis diagnose(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return null;
        }
        Map<String, Diagnosis> byCode = new LinkedHashMap<>();
        byCode.put("InvalidApiKey", new Diagnosis(
                "模型服务拒绝了当前 API Key（401 InvalidApiKey）—— 这不是模型名或 base-url 的问题。",
                "检查环境变量 AI_API_KEY 是否为有效且未过期的 DashScope key，"
                        + "并确认它对该模型有调用权限；改完需重启后端（key 在启动时读入，不会热生效）。"));
        byCode.put("AccessDenied", new Diagnosis(
                "API Key 通过校验但没有该模型的调用授权（403 AccessDenied）。",
                "在阿里云百炼控制台把目标模型加入这把 key 的授权白名单，"
                        + "或放开调用来源 IP 限制。换 base-url、换模型名都不会有用。"));
        byCode.put("AllocationQuota.FreeTierOnly", new Diagnosis(
                "免费额度下不存在该模型（403 AllocationQuota.FreeTierOnly）。",
                "把智能体的模型名改成实际存在的模型（如 qwen-max / qwen-plus）。"));
        Diagnosis hit = firstMatch(rawMessage, byCode);
        if (hit != null) {
            return hit;
        }
        if (rawMessage.contains("status 404") || rawMessage.contains("\"code\":\"ModelNotFound\"")) {
            return new Diagnosis(
                    "模型或接口地址不存在（404）。",
                    "确认 ai.agent.base-url 是 DashScope 原生地址 https://dashscope.aliyuncs.com"
                            + "（不要带 /compatible-mode/v1），并确认模型名拼写正确。");
        }
        if (rawMessage.contains("status 429")) {
            return new Diagnosis(
                    "请求被限流（429）。",
                    "降低并发或更换配额更高的 key；这不是配置错误。");
        }
        if (rawMessage.contains("status 5")) {
            return new Diagnosis(
                    "模型服务端错误（5xx）。",
                    "属上游故障，稍后重试；若持续出现再查模型服务状态。");
        }
        return null;
    }

    private static Diagnosis firstMatch(String raw, Map<String, Diagnosis> byCode) {
        for (Map.Entry<String, Diagnosis> entry : byCode.entrySet()) {
            if (raw.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 归因结论。
     *
     * @param summary 发生了什么（定位到环节）
     * @param action 该去改什么（可执行）
     */
    public record Diagnosis(String summary, String action) {

        /** 渲染成附加在原始报文前的两行提示。 */
        public String render() {
            return "【归因】" + summary + "\n【处置】" + action;
        }
    }
}
