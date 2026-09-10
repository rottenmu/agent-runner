package com.zimo.framework.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.framework.ai.channel.AiChannelMessage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiHarnessAgentRouterTest {
    @Test
    void routesExplicitProfileBeforeChannelBinding() {
        AiAgentProfile explicit = profile("explicit");
        AiHarnessAgentRouter router = new AiHarnessAgentRouter(resolver(profile("bound"), profile("channel")), profile("global"));
        assertThat(router.route(request(explicit))).containsSame(explicit);
    }

    @Test
    void routesMessageBindingBeforeChannelDefault() {
        AiAgentProfile bound = profile("bound");
        AiHarnessAgentRouter router = new AiHarnessAgentRouter(resolver(bound, profile("channel")), profile("global"));
        assertThat(router.route(request(null))).containsSame(bound);
    }

    @Test
    void routesChannelDefaultBeforeGlobalDefault() {
        AiAgentProfile channel = profile("channel");
        AiHarnessAgentRouter router = new AiHarnessAgentRouter(resolver(null, channel), profile("global"));
        assertThat(router.route(request(null))).containsSame(channel);
    }

    @Test
    void returnsEmptyWhenNoProfileCanBeResolved() {
        assertThat(new AiHarnessAgentRouter(resolver(null, null), null).route(request(null))).isEmpty();
    }

    @Test
    void skipsInvalidHigherPriorityCandidatesUntilItFindsMatchingTenantProfile() {
        AiAgentProfile explicit = profile("explicit", "tenant-b", true);
        AiAgentProfile disabled = profile("bound", "tenant-a", false);
        AiAgentProfile channel = profile("channel", "tenant-a", true);
        AiHarnessAgentRouter router = new AiHarnessAgentRouter(resolver(disabled, channel), profile("global", "tenant-a", true));
        assertThat(router.route(request(explicit))).containsSame(channel);
    }

    private static AiAgentRouteRequest request(AiAgentProfile explicit) {
        AiChannelMessage message = AiChannelMessage.of("feishu", "tenant-a", "user-a", "conversation-a", "message-a", "hello");
        return new AiAgentRouteRequest("tenant-a", "feishu", "user-a", "conversation-a", explicit, message);
    }

    private static AiAgentProfileResolver resolver(AiAgentProfile messageProfile, AiAgentProfile channelProfile) {
        return new AiAgentProfileResolver() {
            @Override public Optional<AiAgentProfile> resolveDefaultForChannel(String channel) { return Optional.ofNullable(channelProfile); }
            @Override public Optional<AiAgentProfile> resolveForMessage(AiChannelMessage message) { return Optional.ofNullable(messageProfile); }
        };
    }

    private static AiAgentProfile profile(String id) { return profile(id, "tenant-a", true); }
    private static AiAgentProfile profile(String id, String tenantId, boolean enabled) {
        return new AiAgentProfile(id, tenantId, id, "model", "prompt", List.of(), enabled);
    }
}