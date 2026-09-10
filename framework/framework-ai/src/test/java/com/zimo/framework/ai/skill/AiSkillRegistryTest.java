package com.zimo.framework.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AiSkillRegistryTest {

    @Test
    void listsRegisteredSkillsAndCallsByName() {
        AiSkillRegistry registry = new AiSkillRegistry(java.util.List.of(new TestSkill()));

        assertThat(registry.list()).extracting(AiSkillDescriptor::name).containsExactly("test_echo");

        AiSkillResult result = registry.call("test_echo", Map.of("text", "hello"));

        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("hello");
    }

    @Test
    void unknownSkillReturnsFailureResult() {
        AiSkillRegistry registry = new AiSkillRegistry(java.util.List.of());

        AiSkillResult result = registry.call("missing", Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.content()).contains("Unknown AI skill: missing");
    }

    private static class TestSkill implements AiSkill {
        @Override
        public String name() {
            return "test_echo";
        }

        @Override
        public String description() {
            return "test skill";
        }

        @Override
        public boolean readOnly() {
            return true;
        }

        @Override
        public AiSkillResult call(Map<String, Object> arguments) {
            return AiSkillResult.ok(String.valueOf(arguments.get("text")));
        }
    }
}
