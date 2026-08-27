package com.zimo.starter.ai.skill;

import java.util.Map;
import cn.hutool.core.collection.CollUtil;

public final class DefaultAiSkills {
    private DefaultAiSkills() {
    }

    public static AiSkill echo() {
        return readOnlySkill("echo", "Echo input arguments for connectivity checks.",
                arguments -> AiSkillResult.ok(String.valueOf(arguments)));
    }

    public static AiSkill summarize() {
        return readOnlySkill("summarize", "Summarize plain text input.",
                arguments -> AiSkillResult.ok(firstText(arguments)));
    }

    public static AiSkill generatePlan() {
        return readOnlySkill("generate_plan", "Generate an implementation plan outline.",
                arguments -> AiSkillResult.ok("Plan request accepted: " + firstText(arguments)));
    }

    public static AiSkill routePluginTask() {
        return readOnlySkill("route_plugin_task", "Route a task to a plugin module.",
                arguments -> AiSkillResult.ok("Plugin task route request: " + firstText(arguments)));
    }

    private static AiSkill readOnlySkill(String name, String description, SkillCall call) {
        return new SimpleAiSkill(name, description, true, call);
    }

    private static String firstText(Map<String, Object> arguments) {
        if (CollUtil.isEmpty(arguments)) {
            return "";
        }
        Object text = arguments.get("text");
        if (text == null) {
            text = arguments.get("prompt");
        }
        if (text == null) {
            text = arguments.values().iterator().next();
        }
        return text == null ? "" : String.valueOf(text);
    }

    private interface SkillCall {
        AiSkillResult call(Map<String, Object> arguments);
    }

    private record SimpleAiSkill(String name, String description, boolean readOnly, SkillCall skillCall)
            implements AiSkill {
        @Override
        public AiSkillResult call(Map<String, Object> arguments) {
            return skillCall.call(arguments);
        }
    }
}
