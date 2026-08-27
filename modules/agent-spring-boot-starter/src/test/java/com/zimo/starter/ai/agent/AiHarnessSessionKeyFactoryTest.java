package com.zimo.starter.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AiHarnessSessionKeyFactoryTest {

    private final AiHarnessSessionKeyFactory factory = new AiHarnessSessionKeyFactory();
    private final AiAgentProfile profile = new AiAgentProfile("agent-a", "agent-a", "model", "prompt", List.of());

    @Test
    void createsKeyInTheRequiredIsolationOrder() {
        String key = factory.create(request("tenant-a", "feishu", "conversation-a", "user-a"), profile);

        assertThat(key).isEqualTo("8:tenant-a7:agent-a6:feishu14:conversation-a6:user-a");
    }

    @Test
    void changesWhenAnyIsolationDimensionChanges() {
        String baseline = factory.create(request("tenant-a", "feishu", "conversation-a", "user-a"), profile);

        assertThat(List.of(
                factory.create(request("tenant-b", "feishu", "conversation-a", "user-a"), profile),
                factory.create(request("tenant-a", "web", "conversation-a", "user-a"), profile),
                factory.create(request("tenant-a", "feishu", "conversation-b", "user-a"), profile),
                factory.create(request("tenant-a", "feishu", "conversation-a", "user-b"), profile),
                factory.create(request("tenant-a", "feishu", "conversation-a", "user-a"), profile("agent-b"))))
                .doesNotContain(baseline);
    }

    @Test
    void usesEmptyFramesForBlankDimensions() {
        String key = factory.create(request(" ", null, "", "  "), profile(" "));

        assertThat(key).isEqualTo("0:0:0:0:0:");
    }

    @Test
    void doesNotCollideWhenDelimiterAppearsInsideAdjacentDimensions() {
        String first = factory.create(
                request("tenant:a", "feishu", "conversation", "user"),
                profile("agent"));
        String second = factory.create(
                request("tenant", "feishu", "conversation", "user"),
                profile("a:agent"));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void distinguishesBlankDimensionFromLiteralUnderscore() {
        String blank = factory.create(request(" ", "web", "conversation", "user"), profile);
        String underscore = factory.create(request("_", "web", "conversation", "user"), profile);

        assertThat(blank).isNotEqualTo(underscore);
    }

    private static AiAgentRouteRequest request(
            String tenantId, String channel, String conversationId, String userId) {
        return new AiAgentRouteRequest(tenantId, channel, userId, conversationId, null, null);
    }

    private static AiAgentProfile profile(String id) {
        return new AiAgentProfile(id, id, "model", "prompt", List.of());
    }
}
