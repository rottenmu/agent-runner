package com.zimo.module.feishu.cli;

import com.zimo.module.feishu.cli.bitable.BitableRecordCreateRequest;
import com.zimo.module.feishu.cli.bitable.FeishuBitableCliService;
import com.zimo.module.feishu.cli.calendar.CalendarEventCreateRequest;
import com.zimo.module.feishu.cli.calendar.FeishuCalendarCliService;
import com.zimo.module.feishu.cli.document.DocumentCreateRequest;
import com.zimo.module.feishu.cli.document.FeishuDocumentCliService;
import com.zimo.module.feishu.cli.task.FeishuTaskCliService;
import com.zimo.module.feishu.cli.task.TaskCreateRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuCliUsageDemoTest {

    @Test
    void demonstratesBitableDocumentCalendarAndTaskCalls() {
        CapturingTemplate template = new CapturingTemplate();

        new FeishuBitableCliService(template).createRecord(
                new BitableRecordCreateRequest("app", "table", Map.of("名称", "样件A")));
        new FeishuDocumentCliService(template).createDocument(
                new DocumentCreateRequest("项目纪要", "folder_token"));
        new FeishuCalendarCliService(template).createEvent(
                new CalendarEventCreateRequest(
                        "primary",
                        "评审会",
                        "2026-07-03T09:00:00+08:00",
                        "2026-07-03T10:00:00+08:00"));
        new FeishuTaskCliService(template).createTask(
                new TaskCreateRequest("跟进物料齐套", "请确认齐套状态"));

        assertThat(template.requests).hasSize(4);
        assertThat(template.requests)
                .extracting(FeishuCliCommandRequest::getBusinessType)
                .containsExactly("bitable", "document", "calendar", "task");
    }

    private static class CapturingTemplate extends FeishuCliTemplate {
        private final List<FeishuCliCommandRequest> requests = new ArrayList<>();

        private CapturingTemplate() {
            super(req -> FeishuCliCommandResult.success("{\"code\":0}", null, 1L, 1), null, FeishuCliPolicy.allowAll(), 0);
        }

        @Override
        public FeishuCliCommandResult execute(FeishuCliCommandRequest request) {
            requests.add(request);
            return FeishuCliCommandResult.success("{\"code\":0}", null, 1L, 1);
        }
    }
}
