package com.zimo.module.feishu.cli;

import com.zimo.module.feishu.cli.bitable.BitableRecordCreateRequest;
import com.zimo.module.feishu.cli.bitable.FeishuBitableCliService;
import com.zimo.module.feishu.cli.task.FeishuTaskCliService;
import com.zimo.module.feishu.cli.task.TaskAssigneeRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuCliBusinessServiceTest {

    @Test
    void bitableCreateRecordBuildsStructuredApiRequest() {
        CapturingTemplate template = new CapturingTemplate();
        FeishuBitableCliService service = new FeishuBitableCliService(template);

        service.createRecord(new BitableRecordCreateRequest("app_token", "table_id", Map.of("name", "sample")));

        assertThat(template.request.getBusinessType()).isEqualTo("bitable");
        assertThat(template.request.getMethod()).isEqualTo("POST");
        assertThat(template.request.getApiPath())
                .isEqualTo("/open-apis/bitable/v1/apps/app_token/tables/table_id/records");
        assertThat(template.request.getData()).containsKey("fields");
    }

    @Test
    void taskAssignOwnerBuildsMemberRequest() {
        CapturingTemplate template = new CapturingTemplate();
        FeishuTaskCliService service = new FeishuTaskCliService(template);

        service.assignOwner(new TaskAssigneeRequest("task_guid", List.of("ou_user_1")));

        assertThat(template.request.getMethod()).isEqualTo("POST");
        assertThat(template.request.getApiPath()).isEqualTo("/open-apis/task/v2/tasks/task_guid/members");
        assertThat(template.request.getData()).containsKey("members");
    }

    private static class CapturingTemplate extends FeishuCliTemplate {
        private FeishuCliCommandRequest request;

        private CapturingTemplate() {
            super(req -> FeishuCliCommandResult.success("{\"code\":0}", null, 1L, 1), null, FeishuCliPolicy.allowAll(), 0);
        }

        @Override
        public FeishuCliCommandResult execute(FeishuCliCommandRequest request) {
            this.request = request;
            return FeishuCliCommandResult.success("{\"code\":0}", null, 1L, 1);
        }
    }
}
