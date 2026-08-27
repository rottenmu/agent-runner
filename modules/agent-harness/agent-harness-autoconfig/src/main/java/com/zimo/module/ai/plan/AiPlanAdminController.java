package com.zimo.module.ai.plan;

import com.zimo.starter.ai.AiAgentService;
import com.zimo.starter.ai.agent.AiAgentRouteRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plan Mode 管理接口（程序化进出，对应官方 enter/exit/isActive）。
 *
 * <p>端点：<ul>
 *   <li>POST /api/ai/plan/enter  进入 Plan Mode（等价 plan_enter，不触发 HITL）</li>
 *   <li>POST /api/ai/plan/exit   退出 Plan Mode（等价 plan_exit）</li>
 *   <li>GET  /api/ai/plan/status 查询是否激活</li>
 * </ul></p>
 */
@RestController
@RequestMapping("/api/ai/plan")
public class AiPlanAdminController {

    private final AiAgentService aiAgentService;

    public AiPlanAdminController(AiAgentService aiAgentService) {
        this.aiAgentService = aiAgentService;
    }

    @PostMapping("/enter")
    public Map<String, Object> enter(@RequestBody(required = false) PlanControlRequest req) {
        PlanControlRequest r = req == null ? new PlanControlRequest(null, null, null, null, null) : req;
        boolean ok = aiAgentService.enterPlanMode(r.sessionKey(), route(r));
        return Map.of("ok", ok, "planMode", true);
    }

    @PostMapping("/exit")
    public Map<String, Object> exit(@RequestBody(required = false) PlanControlRequest req) {
        PlanControlRequest r = req == null ? new PlanControlRequest(null, null, null, null, null) : req;
        boolean ok = aiAgentService.exitPlanMode(r.sessionKey(), route(r));
        return Map.of("ok", ok, "planMode", false);
    }

    @GetMapping("/status")
    public Map<String, Object> status(
            @RequestParam(required = false) String sessionKey,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String conversationId) {
        PlanControlRequest r = new PlanControlRequest(sessionKey, tenantId, channel, userId, conversationId);
        boolean active = aiAgentService.isPlanModeActive(r.sessionKey(), route(r));
        return Map.of("planModeActive", active);
    }

    private AiAgentRouteRequest route(PlanControlRequest r) {
        return new AiAgentRouteRequest(r.tenantId(), r.channel(), r.userId(), r.conversationId(), null, null);
    }

    /** 请求体：会话键 + 路由参数。 */
    public record PlanControlRequest(
            String sessionKey, String tenantId, String channel, String userId, String conversationId) {
    }
}
