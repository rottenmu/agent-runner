package com.zimo.framework.ai.a2a;

import com.zimo.framework.ai.AiAgentProperties;
import com.zimo.framework.ai.AiAgentReply;
import com.zimo.framework.ai.AiAgentService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/a2a")
public class A2aController {
    private final AiAgentProperties properties;
    private final AiAgentService aiAgentService;

    public A2aController(AiAgentProperties properties, AiAgentService aiAgentService) {
        this.properties = properties;
        this.aiAgentService = aiAgentService;
    }

    @GetMapping("/agent-card")
    public A2aAgentCard agentCard() {
        return new A2aAgentCard(properties.getName(), "Production studio AI agent.",
                List.of("mcp-tools", "skill-routing", "plugin-assistant"));
    }

    @PostMapping("/message")
    public AiAgentReply message(@RequestBody A2aMessageRequest request) {
        return aiAgentService.reply(request.getMessage());
    }
}
