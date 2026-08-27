package com.zimo.starter.ai.mcp;

import com.zimo.starter.ai.skill.AiSkillRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/mcp")
public class McpController {
    private final AiSkillRegistry skillRegistry;
    private final Optional<ToolBridge> toolBridge;

    public McpController(AiSkillRegistry skillRegistry, Optional<ToolBridge> toolBridge) {
        this.skillRegistry = skillRegistry;
        this.toolBridge = toolBridge;
    }

    @PostMapping
    public McpJsonRpcResponse handle(@RequestBody McpJsonRpcRequest request) {
        if ("tools/list".equals(request.getMethod())) {
            List<Object> tools = new ArrayList<>(skillRegistry.list());
            toolBridge.ifPresent(bridge -> tools.addAll(bridge.listTools()));
            return McpJsonRpcResponse.ok(request.getId(), Map.of("tools", tools));
        }
        if ("tools/call".equals(request.getMethod())) {
            String name = String.valueOf(request.getParams().get("name"));
            Map<String, Object> arguments = arguments(request.getParams().get("arguments"));
            // 动态工具优先（经治理）；未命中则回退内置技能注册表
            if (toolBridge.isPresent() && toolBridge.get().listTools().stream()
                    .anyMatch(t -> name.equals(String.valueOf(t.get("name"))))) {
                return McpJsonRpcResponse.ok(request.getId(), toolBridge.get().call(name, arguments));
            }
            return McpJsonRpcResponse.ok(request.getId(), skillRegistry.call(name, arguments));
        }
        return McpJsonRpcResponse.error(request.getId(), -32601, "Unknown MCP method: " + request.getMethod());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> arguments(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
