package com.zimo.module.feishu.agent;

import com.zimo.module.feishu.cli.FeishuCliCommandRequest;
import com.zimo.module.feishu.cli.FeishuCliCommandResult;
import com.zimo.module.feishu.cli.FeishuCliPolicy;
import com.zimo.module.feishu.cli.FeishuCliTemplate;
import com.zimo.module.feishu.cli.bitable.FeishuBitableCliService;
import com.zimo.module.feishu.cli.document.FeishuDocumentCliService;
import com.zimo.starter.ai.skill.AiSkillResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuAiSkillTest {
    @Test
    void bitableCreateRecordSkillBuildsCliRequest() {
        CapturingTemplate template = new CapturingTemplate();
        FeishuBitableCreateRecordAiSkill skill = new FeishuBitableCreateRecordAiSkill(
                new FeishuBitableCliService(template));

        AiSkillResult result = skill.call(Map.of(
                "appToken", "base_xxx",
                "tableId", "tbl_xxx",
                "fields", Map.of("名称", "样件A")));

        assertThat(result.success()).isTrue();
        assertThat(result.content()).contains("ok");
        assertThat(template.request.getBusinessType()).isEqualTo("bitable");
        assertThat(template.request.getMethod()).isEqualTo("POST");
        assertThat(template.request.getApiPath())
                .isEqualTo("/open-apis/bitable/v1/apps/base_xxx/tables/tbl_xxx/records");
        assertThat(template.request.getData()).containsKey("fields");
    }

    @Test
    void documentCreateSkillBuildsCliRequest() {
        CapturingTemplate template = new CapturingTemplate();
        FeishuDocumentCreateAiSkill skill = new FeishuDocumentCreateAiSkill(
                new FeishuDocumentCliService(template));

        AiSkillResult result = skill.call(Map.of("title", "项目纪要", "folderToken", "fld_xxx"));

        assertThat(result.success()).isTrue();
        assertThat(template.request.getBusinessType()).isEqualTo("document");
        assertThat(template.request.getMethod()).isEqualTo("POST");
        assertThat(template.request.getApiPath()).isEqualTo("/open-apis/docx/v1/documents");
        assertThat(template.request.getData()).containsEntry("title", "项目纪要");
        assertThat(template.request.getData()).containsEntry("folder_token", "fld_xxx");
    }

    @Test
    void documentAppendSkillBuildsCliRequest() {
        CapturingTemplate template = new CapturingTemplate();
        FeishuDocumentAppendAiSkill skill = new FeishuDocumentAppendAiSkill(
                new FeishuDocumentCliService(template));

        AiSkillResult result = skill.call(Map.of(
                "documentId", "doc_xxx",
                "blockId", "blk_xxx",
                "content", "追加内容"));

        assertThat(result.success()).isTrue();
        assertThat(template.request.getBusinessType()).isEqualTo("document");
        assertThat(template.request.getMethod()).isEqualTo("POST");
        assertThat(template.request.getApiPath())
                .isEqualTo("/open-apis/docx/v1/documents/doc_xxx/blocks/blk_xxx/children");
        assertThat(template.request.getData()).containsEntry("content", "追加内容");
    }

    @Test
    void returnsFailureWhenRequiredArgumentIsBlank() {
        CapturingTemplate template = new CapturingTemplate();
        FeishuDocumentCreateAiSkill skill = new FeishuDocumentCreateAiSkill(
                new FeishuDocumentCliService(template));

        AiSkillResult result = skill.call(Map.of("title", " "));

        assertThat(result.success()).isFalse();
        assertThat(result.content()).contains("title");
    }

    private static class CapturingTemplate extends FeishuCliTemplate {
        private FeishuCliCommandRequest request;

        private CapturingTemplate() {
            super(req -> FeishuCliCommandResult.success("{\"ok\":true}", null, 1L, 1),
                    null,
                    FeishuCliPolicy.allowAll(),
                    0);
        }

        @Override
        public FeishuCliCommandResult execute(FeishuCliCommandRequest request) {
            this.request = request;
            return FeishuCliCommandResult.success("{\"ok\":true}", null, 1L, 1);
        }
    }
}
