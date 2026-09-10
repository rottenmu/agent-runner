package com.zimo.module.ai.skill;

import com.zimo.framework.ai.skill.AiSkill;
import com.zimo.framework.ai.skill.AiSkillResult;
import java.util.Map;

public class AiPluginStatusSkill implements AiSkill {
    @Override
    public String name() {
        return "ai_plugin_status";
    }

    @Override
    public String description() {
        return "Report AI plugin module status.";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public AiSkillResult call(Map<String, Object> arguments) {
        return AiSkillResult.ok("module-ai ready");
    }
}
