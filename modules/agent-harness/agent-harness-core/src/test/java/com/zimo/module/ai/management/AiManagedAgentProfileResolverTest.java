package com.zimo.module.ai.management;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.framework.ai.channel.AiChannelAgentBinding;
import com.zimo.framework.ai.channel.AiChannelMessage;
import com.zimo.framework.ai.skill.AiSkillRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiManagedAgentProfileResolverTest {

    @Test
    void resolvesBoundEnabledAgentForFeishuMessage() {
        AiManagedAgentProfileResolver resolver = resolverWithAgents(
                agent("bound-agent", "owner-user", true, List.of()),
                agent("feishu-default", "tenant", true, List.of("feishu")));
        AiChannelMessage message = message("feishu", bindingAttributes("bound-agent"));

        assertThat(resolver.resolveForMessage(message))
                .hasValueSatisfying(profile -> {
                    assertThat(profile.id()).isEqualTo("bound-agent");
                    assertThat(profile.tenantId()).isEqualTo("owner-user");
                    assertThat(profile.enabled()).isTrue();
                });
    }

    @Test
    void fallsBackToFeishuDefaultWhenBoundAgentIsUnavailable() {
        AiManagedAgentProfileResolver resolver = resolverWithAgents(
                agent("disabled-agent", "tenant", false, List.of()),
                agent("other-tenant-default", "other-tenant", true, List.of("feishu")),
                agent("feishu-default", "tenant", true, List.of("feishu")));

        assertThat(resolver.resolveForMessage(message("feishu", bindingAttributes("missing-agent"))))
                .hasValueSatisfying(profile -> assertThat(profile.id()).isEqualTo("feishu-default"));
        assertThat(resolver.resolveForMessage(message("feishu", bindingAttributes("disabled-agent"))))
                .hasValueSatisfying(profile -> assertThat(profile.id()).isEqualTo("feishu-default"));
        assertThat(resolver.resolveForMessage(message("feishu", Map.of())))
                .hasValueSatisfying(profile -> assertThat(profile.id()).isEqualTo("feishu-default"));
    }

    @Test
    void ignoresUnverifiedRawAgentIdForFeishuMessage() {
        AiManagedAgentProfileResolver resolver = resolverWithAgents(
                agent("bound-agent", "owner-user", true, List.of()),
                agent("feishu-default", "tenant", true, List.of("feishu")));

        assertThat(resolver.resolveForMessage(
                message("feishu", Map.of("agentId", "bound-agent"))))
                .hasValueSatisfying(profile ->
                        assertThat(profile.id()).isEqualTo("feishu-default"));
    }

    @Test
    void ignoresBoundAgentIdForNonFeishuMessage() {
        AiManagedAgentProfileResolver resolver = resolverWithAgents(
                agent("bound-agent", "other-tenant", true, List.of()),
                agent("other-web-default", "other-tenant", true, List.of("web")),
                agent("web-default", "tenant", true, List.of("web")));
        AiChannelMessage message = message("web", Map.of("agentId", "bound-agent"));

        assertThat(resolver.resolveForMessage(message))
                .hasValueSatisfying(profile -> assertThat(profile.id()).isEqualTo("web-default"));
    }

    private static Map<String, Object> bindingAttributes(String agentId) {
        return Map.of(
                AiChannelAgentBinding.ATTRIBUTE_NAME,
                new AiChannelAgentBinding("tenant", agentId));
    }

    private static AiManagedAgentProfileResolver resolverWithAgents(AiManagedAgent... agents) {
        AiAgentManagementService service = new AiAgentManagementService(
                new AiSkillRegistry(List.of()),
                List.of(() -> List.of(agents)),
                null,
                null);
        return new AiManagedAgentProfileResolver(service);
    }

    private static AiManagedAgent agent(String id, boolean enabled, List<String> defaultChannels) {
        return agent(id, "admin", enabled, defaultChannels);
    }

    private static AiManagedAgent agent(
            String id,
            String tenantId,
            boolean enabled,
            List<String> defaultChannels) {
        return new AiManagedAgent(
                id,
                id,
                id + "描述",
                "你是" + id,
                "qwen-plus",
                null,
                List.of("view_all_projects"),
                "conversation",
                null,
                enabled,
                "admin",
                tenantId,
                "管理员",
                defaultChannels,
                null);
    }

    private static AiChannelMessage message(String channel, Map<String, Object> attributes) {
        return AiChannelMessage.of(
                channel,
                "tenant",
                "user",
                "conversation",
                "message",
                "你好",
                attributes);
    }
}
