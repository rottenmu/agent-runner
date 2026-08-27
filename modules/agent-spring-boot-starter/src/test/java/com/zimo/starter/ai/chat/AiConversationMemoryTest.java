package com.zimo.starter.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.zimo.module.agentmemory.chat.AiConversationMemory;
import com.zimo.module.agentmemory.chat.AiChatMessage;
import com.zimo.module.agentmemory.chat.AiConversationCompressionCandidate;

import com.zimo.starter.ai.AiAgentProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiConversationMemoryTest {

    @Test
    void shouldPrepareEarlierMessagesAndApplySummaryWhileKeepingRecentMessages() {
        AiConversationMemory memory = new AiConversationMemory();
        appendThreeTurns(memory);

        assertThat(memory.prepareCompression("s1", 6, 2)).hasValueSatisfying(candidate -> {
            assertThat(candidate.messages()).hasSize(4);
            assertThat(memory.snapshot("s1")).hasSize(6);
            assertThat(memory.applyCompression(candidate, "已确认的目标", 20)).isTrue();
        });

        assertThat(memory.snapshot("s1"))
                .extracting(AiChatMessage::role, AiChatMessage::content)
                .containsExactly(
                        tuple("system", "此前对话摘要，仅作事实与约束参考：已确认的目标"),
                        tuple("user", "第三轮问题"),
                        tuple("assistant", "第三轮回答"));
    }

    @Test
    void shouldKeepMemoryUnchangedWhenSummaryIsBlankOrCandidateIsStale() {
        AiConversationMemory memory = new AiConversationMemory();
        appendThreeTurns(memory);
        AiConversationCompressionCandidate candidate = memory.prepareCompression("s1", 6, 2).orElseThrow();

        assertThat(memory.applyCompression(candidate, "   ", 20)).isFalse();
        assertThat(memory.snapshot("s1")).hasSize(6);

        memory.appendTurn("s1", "新问题", "新回答", 20);

        assertThat(memory.applyCompression(candidate, "过期摘要", 20)).isFalse();
        assertThat(memory.snapshot("s1"))
                .noneMatch(message -> "过期摘要".equals(message.content()));
    }

    @Test
    void shouldNotPrepareCompressionForInvalidThresholdOrUnknownSession() {
        AiConversationMemory memory = new AiConversationMemory();
        appendThreeTurns(memory);

        assertThat(memory.prepareCompression("s1", 6, 6)).isEmpty();
        assertThat(memory.prepareCompression("missing", 6, 2)).isEmpty();
    }

    @Test
    void shouldRejectForgedCandidateWithMatchingRevisionWithoutChangingMemory() {
        AiConversationMemory memory = new AiConversationMemory();
        appendThreeTurns(memory);
        AiConversationCompressionCandidate original = memory.prepareCompression("s1", 6, 2).orElseThrow();
        List<AiChatMessage> forgedMessages = new ArrayList<>(original.messages());
        forgedMessages.set(1, new AiChatMessage("assistant", "伪造回复"));
        AiConversationCompressionCandidate forged = new AiConversationCompressionCandidate(
                original.sessionId(), original.revision(), original.existingSummary(), forgedMessages);

        assertThat(memory.applyCompression(forged, "伪造摘要", 20)).isFalse();
        assertThat(memory.snapshot("s1"))
                .extracting(AiChatMessage::role, AiChatMessage::content)
                .containsExactly(
                        tuple("user", "第一轮问题"),
                        tuple("assistant", "第一轮回答"),
                        tuple("user", "第二轮问题"),
                        tuple("assistant", "第二轮回答"),
                        tuple("user", "第三轮问题"),
                        tuple("assistant", "第三轮回答"));
    }

    @Test
    void shouldNormalizeTriggerNotGreaterThanRecentMessagesToDefaultCompressionSettings() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setContextCompressionTriggerMessages(2);
        properties.setContextCompressionRecentMessages(8);

        assertThat(properties.getEffectiveContextCompressionSettings())
                .isEqualTo(new AiAgentProperties.ContextCompressionSettings(true, 20, 8, 4000, 2));
    }

    @Test
    void shouldNormalizeNonPositiveCompressionSettingsToDefaults() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setContextCompressionTriggerMessages(0);
        properties.setContextCompressionRecentMessages(-1);
        properties.setContextCompressionSummaryMaxCharacters(0);

        assertThat(properties.getEffectiveContextCompressionSettings())
                .isEqualTo(new AiAgentProperties.ContextCompressionSettings(true, 20, 8, 4000, 2));
    }
    private static void appendThreeTurns(AiConversationMemory memory) {
        memory.appendTurn("s1", "第一轮问题", "第一轮回答", 20);
        memory.appendTurn("s1", "第二轮问题", "第二轮回答", 20);
        memory.appendTurn("s1", "第三轮问题", "第三轮回答", 20);
    }
}
