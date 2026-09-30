package com.zimo.framework.ai.startup;

import com.zimo.framework.ai.AiAgentProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * 模型凭据启动自检：把「key 不可用」从聊天时才暴露的 401 提前到启动期告警。
 *
 * <p><b>为什么需要它</b>：本仓库曾长期出现「聊天页发送消息 → 报
 * {@code HTTP 401 InvalidApiKey}」的问题，而根因只是环境变量里那把 key 失效。
 * 401 发生在<b>请求期</b>，日志里只有一行 agent 失败，很容易被误判成
 * 「模型名不对 / base-url 写错 / 记忆模块有 bug」——2026-08-23 的 404 事故
 * 与 2026-09-17 的 403 事故都是同一种误判路径。</p>
 *
 * <p><b>本类只做「验形状 + 分类归因」，不发真实请求</b>。理由是自检必须在启动期
 * 无条件快速完成，而真实调用会引入网络依赖与配额消耗（且免费额度下探测本身
 * 可能返回 403 AllocationQuota，反而制造新的误判）。真正的可用性探测交给
 * 管理接口的「测试连接」，那里是按 id 定位、用户显式触发的。</p>
 *
 * <p><b>刻意不阻止启动</b>：模型不可用时系统仍应能起（管理页、资源页、记忆页
 * 都不依赖模型）。因此这里只打 {@code ERROR}/{@code WARN} 级日志，不抛异常。</p>
 *
 * @author Codex
 * @since 2026-09-18
 */
public class AiCredentialHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(AiCredentialHealthCheck.class);

    private final AiAgentProperties properties;

    public AiCredentialHealthCheck(AiAgentProperties properties) {
        this.properties = properties;
    }

    /** 应用就绪后做一次凭据自检，输出可操作的归因结论。 */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        report();
    }

    /**
     * 执行自检并输出结论。返回归因结果，便于测试断言与外层复用。
     *
     * @return 自检结论
     */
    public Result report() {
        String key = properties.getApiKey();
        String baseUrl = properties.getBaseUrl();
        String modelName = properties.getModelName();
        String modelType = properties.getModelType();
        Map<String, String> problems = new LinkedHashMap<>();
        java.util.List<String> notes = new java.util.ArrayList<>();

        if (isBlank(key)) {
            problems.put("apiKey", "未注入。检查环境变量 AI_API_KEY 是否存在，"
                    + "以及 application.yml 的 ai.agent.api-key=${AI_API_KEY} 是否被覆盖");
        } else if (!looksLikeDashScopeKey(key)) {
            // ⚠️ 只有「确定非法」才计入 problems。非标准但可能合法的形态
            //    （百炼 workspace key）单独走 notes，避免假警报 —— 见 lookLike 的注释。
            problems.put("apiKey", "形态非法（长度 " + key.length() + "，前缀 "
                    + prefix(key) + "）。key 应以 sk- 开头，"
                    + "且不含空白字符；请核对是否复制了多余内容");
        } else if (isWorkspaceKey(key)) {
            // sk-ws- 是百炼 workspace 级凭据，**可以完全合法**。
            // 2026-09-18 实测：sk-ws-… 对 qwen-max 在原生端点返回 200。
            // 因此这里只提示、不算问题，否则会把「key 好用但被判非法」的假警报
            // 推给用户，诱使他们去换一把其实没问题的 key（本轮真实踩过的坑）。
            notes.add("apiKey：百炼 workspace 级凭据（sk-ws-…）。此类 key 常按"
                    + "「模型白名单 + 来源 IP」限权，可能只对部分模型可用；"
                    + "若聊天报 403 AccessDenied / 404 Model not exist，优先核对"
                    + "modelName 是否在该 key 的授权清单内");
        }
        if (isBlank(baseUrl)) {
            problems.put("baseUrl", "为空");
        } else if (baseUrl.contains("/compatible-mode")) {
            // 这是 2026-08-23 那次「全站 404 且响应体为空」的根因，必须显式拦下来。
            problems.put("baseUrl", "含 /compatible-mode 前缀。DashScopeChatModel 走原生协议，"
                    + "会把该前缀拼进 /api/v1/services/aigc/text-generation/generation 前，"
                    + "得到不存在的路径 → HTTP 404 且响应体为空。应改为 https://dashscope.aliyuncs.com");
        }
        if (isBlank(modelName)) {
            problems.put("modelName", "为空");
        }
        if (isBlank(modelType)) {
            problems.put("modelType", "为空");
        }

        Result result = new Result(
                mask(key), baseUrl, modelName, modelType, problems.isEmpty(), problems);
        if (problems.isEmpty()) {
            log.info("[AI 凭据自检] 通过：apiKey={} baseUrl={} model={} type={}",
                    result.maskedApiKey(), baseUrl, modelName, modelType);
            for (String note : notes) {
                log.info("[AI 凭据自检] 提示：{}", note);
            }
        } else {
            // 用 ERROR 而不是 WARN：这个状态下「聊天页发消息必失败」，
            // 属于功能性损坏而非降级，必须让人在启动日志里一眼看到。
            log.error("[AI 凭据自检] 未通过 —— 聊天页发送消息将失败（通常是 HTTP 401 InvalidApiKey）。"
                            + "当前配置：apiKey={}（长度 {}）baseUrl={} model={} type={}；发现 {} 处问题：\n{}",
                    result.maskedApiKey(), key == null ? 0 : key.length(), baseUrl, modelName,
                    modelType, problems.size(), format(problems));
        }
        return result;
    }

    private static String format(Map<String, String> problems) {
        StringBuilder sb = new StringBuilder();
        problems.forEach((field, reason) ->
                sb.append("                      - ").append(field).append("：")
                        .append(reason).append(System.lineSeparator()));
        return sb.toString().stripTrailing();
    }

    /** 脱敏：只保留前缀与末 4 位，中间固定长度点号（不泄露长度以外的信息）。 */
    static String mask(String key) {
        if (isBlank(key)) {
            return "(未配置)";
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 8) + "****" + key.substring(key.length() - 4);
    }

    private static String prefix(String key) {
        return key.length() <= 6 ? "****" : key.substring(0, 6) + "…";
    }

    /**
     * 形态判断：key 是否「像一把可用的凭据」。
     *
     * <p><b>2026-09-18 修订</b>：原实现要求严格匹配 {@code ^sk-} + 32 位十六进制（总长 35），
     * 结果把<b>合法且实测可用</b>的百炼 workspace key（{@code sk-ws-…}，长度 116）
     * 判成「形态可疑」并打 ERROR 日志。该假警报会诱导用户去换一把其实没问题的 key
     * ——本轮真实踩过，故放宽。</p>
     *
     * <p>现在的判据是「<b>不能是明显非法的东西</b>」，而不是「必须长成某一种样子」：
     * 只要以 {@code sk-} 开头、长度合理、不含空白，就认为形态可用。
     * 具体变体（标准 35 位 / workspace 116 位）由 {@link #isWorkspaceKey} 单独提示。
     * 真正的可用性只能靠发请求验证，形态检查不该越权断言「key 坏了」。</p>
     */
    static boolean looksLikeDashScopeKey(String key) {
        if (key == null) {
            return false;
        }
        String trimmed = key.trim();
        if (!trimmed.startsWith("sk-") || trimmed.length() < 10) {
            return false;
        }
        // 含空白说明复制时带了多余内容（换行/空格），这类才是真的会 401。
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isWhitespace(trimmed.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** 百炼 workspace 级凭据（{@code sk-ws-…}）：合法但常按模型白名单 / 来源 IP 限权。 */
    static boolean isWorkspaceKey(String key) {
        return key != null && key.trim().startsWith("sk-ws-");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 自检结论。
     *
     * @param maskedApiKey 脱敏后的 key
     * @param baseUrl 模型服务地址
     * @param modelName 模型名
     * @param modelType 接口类型
     * @param healthy 是否通过
     * @param problems 字段 → 问题描述（有序）
     */
    public record Result(
            String maskedApiKey,
            String baseUrl,
            String modelName,
            String modelType,
            boolean healthy,
            Map<String, String> problems) {
    }
}
