package com.zimo.module.feishu.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.feishu.channel.FeishuAgentCommandMessage;
import com.zimo.module.feishu.cli.FeishuCliCommandResult;
import com.zimo.module.feishu.cli.bitable.BitableRecordCreateRequest;
import com.zimo.module.feishu.cli.bitable.FeishuBitableCliService;
import com.zimo.module.feishu.config.FeishuConfigProvider;
import com.zimo.module.feishu.config.FeishuRuntimeConfig;
import com.zimo.module.feishu.message.FeishuMessageResponse;
import com.zimo.module.feishu.reply.FeishuAgentReplyClient;
import com.zimo.module.feishu.reply.FeishuAgentReplyService;
import com.zimo.module.feishu.reply.FeishuCardTemplateFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeishuAgentDispatchServiceTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void dispatchesBusinessResultArchivesAndRepliesCard() {
        FeishuBitableCliService bitableCliService = mock(FeishuBitableCliService.class);
        when(bitableCliService.createRecord(any())).thenReturn(successCliResult());
        RecordingReplyClient replyClient = new RecordingReplyClient();
        FeishuAgentDispatchService service = serviceWith(
                validConfig(),
                new FeishuAgentCommandRouter(List.of(new ProjectProgressHandler())),
                bitableCliService,
                new FeishuAgentRateLimiter(Clock.systemUTC(), true, 60, 10),
                true,
                true,
                "app_token",
                "table_id");

        service.dispatch(message("项目 XJ100 进度"), new FeishuAgentReplyService(replyClient));

        ArgumentCaptor<BitableRecordCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(BitableRecordCreateRequest.class);
        verify(bitableCliService).createRecord(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getAppToken()).isEqualTo("app_token");
        assertThat(requestCaptor.getValue().getTableId()).isEqualTo("table_id");
        assertThat(requestCaptor.getValue().getFields())
                .containsEntry("指令", "项目 XJ100 进度")
                .containsEntry("租户", "tenant_1")
                .containsEntry("发送人", "user_1")
                .containsEntry("结果标题", "项目进度")
                .containsEntry("结果摘要", "查询成功")
                .containsEntry("项目编号", "XJ100");
        assertThat(replyClient.textPayloads).anyMatch(text -> text.equals("正在处理：项目 XJ100 进度"));
        assertThat(replyClient.cardPayload).contains("项目进度");
        assertThat(replyClient.cardPayload).contains("打开表格");
    }

    @Test
    void repliesHelpWhenCommandTextIsBlank() {
        FeishuBitableCliService bitableCliService = mock(FeishuBitableCliService.class);
        RecordingReplyClient replyClient = new RecordingReplyClient();
        FeishuAgentDispatchService service = defaultService(bitableCliService);

        service.dispatch(message("   "), new FeishuAgentReplyService(replyClient));

        verify(bitableCliService, never()).createRecord(any());
        assertThat(replyClient.textPayloads)
                .containsExactly("请输入有效指令，例如：项目 XJ100 进度。");
    }

    @Test
    void repliesCredentialErrorWhenActiveConfigMissing() {
        FeishuBitableCliService bitableCliService = mock(FeishuBitableCliService.class);
        RecordingReplyClient replyClient = new RecordingReplyClient();
        FeishuAgentDispatchService service = serviceWith(
                () -> null,
                new FeishuAgentCommandRouter(List.of(new ProjectProgressHandler())),
                bitableCliService,
                new FeishuAgentRateLimiter(Clock.systemUTC(), true, 60, 10),
                true,
                true,
                "app_token",
                "table_id");

        service.dispatch(message("项目 XJ100"), new FeishuAgentReplyService(replyClient));

        verify(bitableCliService, never()).createRecord(any());
        assertThat(replyClient.textPayloads).containsExactly("飞书应用未完成配置");
    }

    @Test
    void repliesRateLimitMessageWhenLimiterRejects() {
        FeishuBitableCliService bitableCliService = mock(FeishuBitableCliService.class);
        RecordingReplyClient replyClient = new RecordingReplyClient();
        FeishuAgentRateLimiter limiter = new FeishuAgentRateLimiter(
                Clock.fixed(Instant.parse("2026-07-03T00:00:00Z"), ZoneId.of("UTC")),
                true,
                60,
                1);
        assertThat(limiter.tryAcquire("tenant_1", "user_1")).isTrue();
        FeishuAgentDispatchService service = serviceWith(
                validConfig(),
                new FeishuAgentCommandRouter(List.of(new ProjectProgressHandler())),
                bitableCliService,
                limiter,
                true,
                true,
                "app_token",
                "table_id");

        service.dispatch(message("项目 XJ100"), new FeishuAgentReplyService(replyClient));

        verify(bitableCliService, never()).createRecord(any());
        assertThat(replyClient.textPayloads).containsExactly("请求过于频繁，请稍后再试。");
    }

    @Test
    void repliesUnknownRouteWhenNoHandlerMatched() {
        FeishuBitableCliService bitableCliService = mock(FeishuBitableCliService.class);
        RecordingReplyClient replyClient = new RecordingReplyClient();
        FeishuAgentDispatchService service = serviceWith(
                validConfig(),
                new FeishuAgentCommandRouter(List.of()),
                bitableCliService,
                new FeishuAgentRateLimiter(Clock.systemUTC(), true, 60, 10),
                true,
                true,
                "app_token",
                "table_id");

        service.dispatch(message("未知指令"), new FeishuAgentReplyService(replyClient));

        verify(bitableCliService, never()).createRecord(any());
        assertThat(replyClient.textPayloads).containsExactly("暂未匹配到可执行指令，请输入：帮助。");
    }

    @Test
    void repliesBusinessErrorWhenHandlerThrows() {
        FeishuBitableCliService bitableCliService = mock(FeishuBitableCliService.class);
        RecordingReplyClient replyClient = new RecordingReplyClient();
        FeishuAgentDispatchService service = serviceWith(
                validConfig(),
                new FeishuAgentCommandRouter(List.of(new ThrowingHandler())),
                bitableCliService,
                new FeishuAgentRateLimiter(Clock.systemUTC(), true, 60, 10),
                true,
                true,
                "app_token",
                "table_id");

        service.dispatch(message("项目 XJ100"), new FeishuAgentReplyService(replyClient));

        verify(bitableCliService, never()).createRecord(any());
        assertThat(replyClient.textPayloads).contains("正在处理：项目 XJ100");
        assertThat(replyClient.textPayloads).contains("指令执行失败，请稍后再试。");
    }

    @Test
    void sendsFinalCardWhenArchiveFails() {
        FeishuBitableCliService bitableCliService = mock(FeishuBitableCliService.class);
        when(bitableCliService.createRecord(any()))
                .thenReturn(FeishuCliCommandResult.failure(1, "", "error", "archive failed", 12L, 1));
        RecordingReplyClient replyClient = new RecordingReplyClient();
        FeishuAgentDispatchService service = serviceWith(
                validConfig(),
                new FeishuAgentCommandRouter(List.of(new ProjectProgressHandler())),
                bitableCliService,
                new FeishuAgentRateLimiter(Clock.systemUTC(), true, 60, 10),
                false,
                true,
                "app_token",
                "table_id");

        service.dispatch(message("项目 XJ100"), new FeishuAgentReplyService(replyClient));

        verify(bitableCliService).createRecord(any());
        assertThat(replyClient.cardPayload).contains("项目进度");
        assertThat(replyClient.textPayloads).isEmpty();
    }

    @Test
    void continuesBusinessFlowWhenProgressReplyFails() {
        FeishuBitableCliService bitableCliService = mock(FeishuBitableCliService.class);
        when(bitableCliService.createRecord(any())).thenReturn(successCliResult());
        RecordingReplyClient replyClient = new RecordingReplyClient();
        replyClient.failTextReply = true;
        FeishuAgentDispatchService service = serviceWith(
                validConfig(),
                new FeishuAgentCommandRouter(List.of(new ProjectProgressHandler())),
                bitableCliService,
                new FeishuAgentRateLimiter(Clock.systemUTC(), true, 60, 10),
                true,
                true,
                "app_token",
                "table_id");

        service.dispatch(message("项目 XJ100"), new FeishuAgentReplyService(replyClient));

        verify(bitableCliService).createRecord(any());
        assertThat(replyClient.cardPayload).contains("项目进度");
    }

    @Test
    void keepsBaseArchiveFieldsWhenBusinessArchiveFieldsConflict() {
        FeishuBitableCliService bitableCliService = mock(FeishuBitableCliService.class);
        when(bitableCliService.createRecord(any())).thenReturn(successCliResult());
        RecordingReplyClient replyClient = new RecordingReplyClient();
        FeishuAgentBusinessHandler conflictingHandler = new FeishuAgentBusinessHandler() {
            @Override
            public List<FeishuAgentCommandRoute> routes() {
                return List.of(new FeishuAgentCommandRoute("项目", List.of("项目"), List.of(), 10));
            }

            @Override
            public FeishuAgentBusinessResult handle(FeishuAgentBusinessRequest request) {
                return FeishuAgentBusinessResult.success("项目进度", "查询成功")
                        .archiveField("指令", "覆盖指令")
                        .archiveField("租户", "覆盖租户")
                        .archiveField("扩展字段", "扩展值");
            }
        };
        FeishuAgentDispatchService service = serviceWith(
                validConfig(),
                new FeishuAgentCommandRouter(List.of(conflictingHandler)),
                bitableCliService,
                new FeishuAgentRateLimiter(Clock.systemUTC(), true, 60, 10),
                false,
                true,
                "app_token",
                "table_id");

        service.dispatch(message("项目 XJ100"), new FeishuAgentReplyService(replyClient));

        ArgumentCaptor<BitableRecordCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(BitableRecordCreateRequest.class);
        verify(bitableCliService).createRecord(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getFields())
                .containsEntry("指令", "项目 XJ100")
                .containsEntry("租户", "tenant_1")
                .containsEntry("扩展字段", "扩展值");
    }

    @Test
    void skipsArchiveWhenArchiveConfigIsBlank() {
        FeishuBitableCliService bitableCliService = mock(FeishuBitableCliService.class);
        RecordingReplyClient replyClient = new RecordingReplyClient();
        FeishuAgentDispatchService service = serviceWith(
                validConfig(),
                new FeishuAgentCommandRouter(List.of(new ProjectProgressHandler())),
                bitableCliService,
                new FeishuAgentRateLimiter(Clock.systemUTC(), true, 60, 10),
                false,
                true,
                "",
                "table_id");

        service.dispatch(message("项目 XJ100"), new FeishuAgentReplyService(replyClient));

        verify(bitableCliService, never()).createRecord(any());
        assertThat(replyClient.cardPayload).contains("项目进度");
    }

    @Test
    void validatesRequiredArguments() {
        FeishuAgentDispatchService service = defaultService(mock(FeishuBitableCliService.class));
        FeishuAgentReplyService replyService = new FeishuAgentReplyService(new RecordingReplyClient());

        assertThatThrownBy(() -> service.dispatch(null, replyService))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("message must not be null");
        assertThatThrownBy(() -> service.dispatch(message("项目 XJ100"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("replyService must not be null");
    }

    private static FeishuAgentDispatchService defaultService(FeishuBitableCliService bitableCliService) {
        return serviceWith(
                validConfig(),
                new FeishuAgentCommandRouter(List.of(new ProjectProgressHandler())),
                bitableCliService,
                new FeishuAgentRateLimiter(Clock.systemUTC(), true, 60, 10),
                true,
                true,
                "app_token",
                "table_id");
    }

    private static FeishuAgentDispatchService serviceWith(
            FeishuConfigProvider configProvider,
            FeishuAgentCommandRouter router,
            FeishuBitableCliService bitableCliService,
            FeishuAgentRateLimiter rateLimiter,
            boolean progressReplyEnabled,
            boolean archiveEnabled,
            String archiveAppToken,
            String archiveTableId) {
        return new FeishuAgentDispatchService(
                configProvider,
                router,
                bitableCliService,
                rateLimiter,
                new FeishuAgentResultCardFactory(new FeishuCardTemplateFactory()),
                progressReplyEnabled,
                archiveEnabled,
                archiveAppToken,
                archiveTableId);
    }

    private static FeishuConfigProvider validConfig() {
        return () -> new FeishuRuntimeConfig("app_id", "secret", "token", "key");
    }

    private static FeishuCliCommandResult successCliResult() {
        return FeishuCliCommandResult.success(
                "{\"ok\":true}",
                OBJECT_MAPPER.createObjectNode().put("ok", true),
                12L,
                1);
    }

    private static FeishuAgentCommandMessage message(String commandText) {
        return new FeishuAgentCommandMessage("msg_1", "chat_1", "group", "tenant_1",
                "user_1", "open_1", "union_1", commandText, commandText, "text", true);
    }

    private static class ProjectProgressHandler implements FeishuAgentBusinessHandler {
        @Override
        public List<FeishuAgentCommandRoute> routes() {
            return List.of(new FeishuAgentCommandRoute("项目", List.of("项目"), List.of(), 10));
        }

        @Override
        public FeishuAgentBusinessResult handle(FeishuAgentBusinessRequest request) {
            return FeishuAgentBusinessResult.success("项目进度", "查询成功")
                    .field("项目编号", "XJ100")
                    .archiveField("项目编号", "XJ100")
                    .link("打开表格", "https://feishu.cn/base/app123");
        }
    }

    private static class ThrowingHandler implements FeishuAgentBusinessHandler {
        @Override
        public List<FeishuAgentCommandRoute> routes() {
            return List.of(new FeishuAgentCommandRoute("项目", List.of("项目"), List.of(), 10));
        }

        @Override
        public FeishuAgentBusinessResult handle(FeishuAgentBusinessRequest request) {
            throw new FeishuAgentGatewayException("业务接口异常");
        }
    }

    private static class RecordingReplyClient implements FeishuAgentReplyClient {
        private final List<String> textPayloads = new ArrayList<>();
        private String cardPayload;
        private boolean failTextReply;

        @Override
        public FeishuMessageResponse replyText(String messageId, String text) {
            if (failTextReply) {
                throw new IllegalStateException("reply failed");
            }
            textPayloads.add(text);
            return FeishuMessageResponse.success("reply_" + messageId);
        }

        @Override
        public FeishuMessageResponse updateText(String messageId, String text) {
            textPayloads.add(text);
            return FeishuMessageResponse.success(messageId);
        }

        @Override
        public FeishuMessageResponse replyCard(String messageId, String cardJson) {
            cardPayload = cardJson;
            return FeishuMessageResponse.success("card_" + messageId);
        }
    }
}
