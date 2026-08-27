package com.zimo.module.feishu.cli;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FeishuCliCallLogServiceTest {

    @Test
    void recordsFinalCliCallResultAndTruncatesLargeOutput() {
        FeishuCliCallLogMapper mapper = mock(FeishuCliCallLogMapper.class);
        FeishuCliCallLogService service = new FeishuCliCallLogService(mapper, true);
        FeishuCliCommandRequest request = FeishuCliCommandRequest
                .api("document", "POST", "/open-apis/docx/v1/documents")
                .withData("title", "测试文档");
        FeishuCliCommandResult result = FeishuCliCommandResult
                .failure(1, "x".repeat(5000), "stderr", "failed", 33L, 2);

        service.record(request, result);

        verify(mapper).insert(any(FeishuCliCallLogEntity.class));
        FeishuCliCallLogEntity entity = service.toEntity(request, result);
        assertThat(entity.getBusinessType()).isEqualTo("document");
        assertThat(entity.getApiPath()).isEqualTo("/open-apis/docx/v1/documents");
        assertThat(entity.getSuccess()).isZero();
        assertThat(entity.getAttempts()).isEqualTo(2);
        assertThat(entity.getStdout()).hasSizeLessThanOrEqualTo(4096);
    }
}
