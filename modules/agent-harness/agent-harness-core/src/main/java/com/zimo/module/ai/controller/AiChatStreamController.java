package com.zimo.module.ai.controller;

import com.zimo.framework.ai.AiAgentService;
import com.zimo.framework.ai.AiAgentStreamEvent;
import com.zimo.framework.ai.agent.AiAgentRouteRequest;
import com.zimo.framework.common.security.SecurityFacade;
import com.zimo.module.ai.memory.ChatTurnMemoryRecorder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

/**
 * 智能体对话流式接口：以 SSE 把模型回复逐块推送到前端页面。
 *
 * <h2>为什么是 POST + fetch 而不是 GET + EventSource</h2>
 * <p>浏览器的 {@code EventSource} 不能自定义请求头，而本项目的鉴权头是
 * {@code Authorization: <token>}（**不带** {@code Bearer } 前缀）。若为迁就
 * {@code EventSource} 把 token 挪到 query string，token 会进入访问日志、浏览器历史与
 * Referer，属于把凭据降级为「谁看到 URL 谁就能用」。因此这里用普通 POST 承载请求体与鉴权头，
 * 响应体是 SSE 分帧，前端用 fetch + ReadableStream 消费。</p>
 *
 * <h2>事件协议</h2>
 * <table>
 *   <tr><td>{@code meta}</td><td>{@code {agentId, agentName, agentType, sessionId}} —— 已受理</td></tr>
 *   <tr><td>{@code delta}</td><td>{@code {text}} —— 增量文本块，消费方应<b>追加</b></td></tr>
 *   <tr><td>{@code replace}</td><td>{@code {reply, ...}} —— 整段替换（闸门接管 / 脱敏校正）</td></tr>
 *   <tr><td>{@code error}</td><td>{@code {message}} —— 失败，{@code message} 已含归因与处置</td></tr>
 *   <tr><td>{@code done}</td><td>{@code {reply, elapsedMs}} —— 收尾，{@code reply} 为权威全文</td></tr>
 * </table>
 *
 * <p><b>失败为什么走 {@code error} 事件而不是 HTTP 非 2xx</b>：前端拿到 200 之后再遭遇
 * 传输层错误会被当成「网络故障、可重试」，而模型侧失败是业务失败（要按归因处置）。
 * 闸门失败（校验/权限/敏感词）仍按 HTTP 语义抛出，与同步端点
 * {@link AiChatController} 完全一致。</p>
 *
 * <p><b>记忆记录</b>：每轮真正收尾时（{@code done}）与闸门接管时，各把这一轮问答记入
 * agent-memory 的会话记忆（见 {@link ChatTurnMemoryRecorder}）。记录是异步旁路 ——
 * 它失败只留日志，不影响对话本身。</p>
 *
 * @author WorkBuddy
 * @since 2026-09-18
 */
@RestController
@RequestMapping("/api/biz/ai/chat")
public class AiChatStreamController {

    private static final Logger log = LoggerFactory.getLogger(AiChatStreamController.class);

    /** 单条流最长存活时间：与前端 chat 请求 180s 超时相比留足余量（长回复 + 工具调用） */
    private static final long TIMEOUT_MS = 5L * 60 * 1000;

    private static final String EVENT_META = "meta";
    private static final String EVENT_DELTA = "delta";
    private static final String EVENT_REPLACE = "replace";
    private static final String EVENT_ERROR = "error";
    private static final String EVENT_DONE = "done";

    private final AiAgentService aiAgentService;
    private final AiChatPreflight preflight;
    private final Optional<SecurityFacade> security;
    private final ChatTurnMemoryRecorder memoryRecorder;

    public AiChatStreamController(AiAgentService aiAgentService,
                                  AiChatPreflight preflight,
                                  Optional<SecurityFacade> security,
                                  ChatTurnMemoryRecorder memoryRecorder) {
        this.aiAgentService = aiAgentService;
        this.preflight = preflight;
        this.security = security;
        this.memoryRecorder = memoryRecorder;
    }

    /**
     * 一轮对话的记录上下文。
     *
     * <p>流式收尾发生在 {@code onEvent} 里（reactor 回调），拿不到 {@code stream()} 的局部变量，
     * 因此把「谁在哪个会话里说了什么」显式带过去 —— 用参数传递而不是塞成员变量，
     * 否则并发对话会互相串写。</p>
     */
    private record Turn(String tenantId, String userId, String sessionId, String message) {
    }

    /**
     * 与指定智能体对话（SSE 流式返回）。
     *
     * @param body 与同步端点同构：{@code agentId} / {@code message} 必填
     * @return SSE 事件流；末事件必为 {@code done} 或 {@code error}
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody Map<String, String> body) {
        // 闸门刻意放在建立 SSE 通道之前：校验失败时让异常按既有 HTTP 语义抛出
        AiChatPreflight.Result pre = preflight.run(body);
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        AtomicBoolean closed = new AtomicBoolean(false);

        if (pre.shortCircuited()) {
            // 闸门接管（缺参追问 / 二次确认 / 技能澄清冲突）：本就是一段完整短回复
            Map<String, Object> reply = new LinkedHashMap<>(pre.shortCircuit());
            String shortReply = textOf(reply.get("reply"));
            send(emitter, closed, EVENT_REPLACE, reply);
            send(emitter, closed, EVENT_DONE, payload("reply", shortReply, "elapsedMs", 0L));
            // 闸门接管同样是这个会话里的一轮问答，一并记录 —— 否则「追问→回答→继续」
            // 这段上下文在记忆里会断掉，回看时前后不接
            memoryRecorder.record(pre.tenantId(), pre.userId(), pre.sessionId(),
                    body.get("message"), shortReply);
            finish(emitter, closed);
            return emitter;
        }

        String message = body.get("message");
        String userId = pre.userId();
        String agentId = pre.agent().id();
        Turn turn = new Turn(pre.tenantId(), userId, pre.sessionId(), message);
        AiAgentRouteRequest route = new AiAgentRouteRequest(
                pre.tenantId(), "console", userId, pre.sessionId(), pre.profile(), null);

        send(emitter, closed, EVENT_META, payload(
                "agentId", agentId,
                "agentName", pre.agent().name(),
                "agentType", pre.agent().agentType(),
                "sessionId", pre.sessionId()));
        audit(userId, agentId, message);

        Disposable subscription = aiAgentService.stream(message, route)
                .subscribe(
                        event -> onEvent(emitter, closed, event, turn),
                        error -> {
                            // AiAgentService 已把模型侧失败转成 error 事件；走到这里的是
                            // 真正的意外（例如上游 Flux 自身异常），同样要送达而非静默断流
                            log.warn("[AI 流式] 意外终止: {}", error.toString());
                            send(emitter, closed, EVENT_ERROR, payload(
                                    "message", "AI 智能体调用失败：" + error.getMessage()));
                            finish(emitter, closed);
                        },
                        () -> finish(emitter, closed));

        // 客户端断开（Stop / 关页面）必须取消上游订阅，否则模型会继续生成到结束 ——
        // 既浪费额度，也会让「停止」按钮在观感上失灵
        emitter.onCompletion(() -> dispose(subscription, closed));
        emitter.onError(error -> dispose(subscription, closed));
        emitter.onTimeout(() -> {
            dispose(subscription, closed);
            finish(emitter, closed);
        });
        return emitter;
    }

    /** 单条服务端事件 → SSE 帧。 */
    private void onEvent(SseEmitter emitter, AtomicBoolean closed, AiAgentStreamEvent event, Turn turn) {
        if (event == null || event.kind() == null) {
            return;
        }
        switch (event.kind()) {
            case DELTA -> {
                String text = sanitize(event.text());
                if (!text.isEmpty()) {
                    send(emitter, closed, EVENT_DELTA, payload("text", text));
                }
            }
            case DONE -> {
                // 收尾以**整段净化**为准：逐块净化无法覆盖「敏感词跨块拼接」的情况，
                // 这里把权威全文再净化一次，若与已推送内容不同则以 replace 校正
                String full = sanitize(event.text());
                if (!full.equals(event.text())) {
                    send(emitter, closed, EVENT_REPLACE, payload("reply", full));
                }
                send(emitter, closed, EVENT_DONE,
                        payload("reply", full, "elapsedMs", event.elapsedMs()));
                // 只在**真正收尾**时记录：delta 阶段记会记到半截回复，而 error 分支
                // 压根不该进记忆（失败回复不是对话内容）
                memoryRecorder.record(turn.tenantId(), turn.userId(), turn.sessionId(),
                        turn.message(), full);
            }
            case ERROR -> send(emitter, closed, EVENT_ERROR, payload("message", event.text()));
            default -> {
                // 无其它语义：协议若新增类型，旧客户端忽略未知事件即可
            }
        }
    }

    private String sanitize(String text) {
        if (security.isEmpty() || text == null) {
            return text == null ? "" : text;
        }
        return security.get().sanitizeOutput(text);
    }

    private void audit(String userId, String agentId, String message) {
        security.ifPresent(s -> s.audit("user", userId, "chat.send", "agent", agentId,
                "{\"msg\":\"" + safeJson(message) + "\",\"channel\":\"sse\"}", true));
    }

    /** 发送一帧；通道已关闭时静默丢弃（客户端可能已断开，此处报错没有意义）。 */
    private void send(SseEmitter emitter, AtomicBoolean closed, String name, Map<String, Object> data) {
        if (closed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (Exception exception) {
            // 客户端断开最常见的表现就是这里抛 IOException：标记关闭并停发，
            // 不往上抛 —— 否则一次正常关页会在服务端留下异常噪音
            closed.set(true);
        }
    }

    private void finish(SseEmitter emitter, AtomicBoolean closed) {
        if (closed.getAndSet(true)) {
            return;
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 已完成 / 已断开：无需处理
        }
    }

    private void dispose(Disposable subscription, AtomicBoolean closed) {
        closed.set(true);
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }

    /** 构造 JSON 载荷；值为 {@code null} 时归一化为空串，避免序列化出 null 让前端多一层判空。 */
    private Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]),
                    keyValues[i + 1] == null ? "" : keyValues[i + 1]);
        }
        return map;
    }

    private String textOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String safeJson(String s) {
        return s == null ? "" : s.replace("\"", "'").replace("\n", " ");
    }
}
