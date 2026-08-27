package com.zimo.module.feishu.agent;

import com.zimo.module.feishu.cli.bitable.BitableRecordCreateRequest;
import com.zimo.module.feishu.cli.bitable.FeishuBitableCliService;
import com.zimo.starter.ai.skill.AiSkill;
import com.zimo.starter.ai.skill.AiSkillResult;
import java.util.Map;
import java.util.Objects;

public class FeishuBitableCreateRecordAiSkill extends FeishuAiSkillSupport implements AiSkill {
    private final FeishuBitableCliService bitableCliService;

    public FeishuBitableCreateRecordAiSkill(FeishuBitableCliService bitableCliService) {
        this.bitableCliService = Objects.requireNonNull(bitableCliService, "bitableCliService must not be null");
    }

    @Override
    public String name() {
        return "feishu_bitable_create_record";
    }

    @Override
    public String description() {
        return "向飞书多维表格新增一条记录";
    }

    @Override
    public boolean readOnly() {
        return false;
    }

    @Override
    public AiSkillResult call(Map<String, Object> arguments) {
        try {
            return result(bitableCliService.createRecord(new BitableRecordCreateRequest(
                    requireText(arguments, "appToken"),
                    requireText(arguments, "tableId"),
                    map(arguments, "fields"))));
        } catch (Exception e) {
            return fail(e);
        }
    }
}
