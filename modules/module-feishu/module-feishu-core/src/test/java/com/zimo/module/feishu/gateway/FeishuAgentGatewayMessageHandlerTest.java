package com.zimo.module.feishu.gateway;

import com.zimo.module.feishu.channel.FeishuAgentCommandMessage;
import com.zimo.module.feishu.reply.FeishuAgentReplyService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FeishuAgentGatewayMessageHandlerTest {

    @Test
    void delegatesToDispatchService() {
        FeishuAgentDispatchService dispatchService = mock(FeishuAgentDispatchService.class);
        FeishuAgentGatewayMessageHandler handler = new FeishuAgentGatewayMessageHandler(dispatchService);
        FeishuAgentCommandMessage message = new FeishuAgentCommandMessage("msg_1", "chat_1", "group", "tenant_1",
                "user_1", "open_1", "union_1", "项目 XJ100", "项目 XJ100", "text", true);
        FeishuAgentReplyService replyService = mock(FeishuAgentReplyService.class);

        handler.handle(message, replyService);

        verify(dispatchService).dispatch(message, replyService);
    }
}
