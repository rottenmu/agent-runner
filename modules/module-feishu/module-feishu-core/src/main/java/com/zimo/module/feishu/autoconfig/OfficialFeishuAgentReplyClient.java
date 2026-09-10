package com.zimo.module.feishu.autoconfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.core.enums.BaseUrlEnum;
import com.lark.oapi.service.im.v1.model.PatchMessageReq;
import com.lark.oapi.service.im.v1.model.PatchMessageReqBody;
import com.lark.oapi.service.im.v1.model.PatchMessageResp;
import com.lark.oapi.service.im.v1.model.ReplyMessageReq;
import com.lark.oapi.service.im.v1.model.ReplyMessageReqBody;
import com.lark.oapi.service.im.v1.model.ReplyMessageResp;
import com.lark.oapi.service.im.v1.model.ReplyMessageRespBody;
import com.zimo.module.feishu.config.FeishuConfigProvider;
import com.zimo.module.feishu.config.FeishuRuntimeConfig;
import com.zimo.module.feishu.message.FeishuMessageResponse;
import com.zimo.module.feishu.reply.FeishuAgentReplyClient;
import org.springframework.util.StringUtils;

import java.util.Map;

public class OfficialFeishuAgentReplyClient implements FeishuAgentReplyClient {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FeishuConfigProvider configProvider;

    public OfficialFeishuAgentReplyClient(FeishuConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    @Override
    public FeishuMessageResponse replyText(String messageId, String text) {
        return reply(messageId, "text", textContentJson(text));
    }

    @Override
    public FeishuMessageResponse updateText(String messageId, String text) {
        PatchMessageReq request = PatchMessageReq.newBuilder()
                .messageId(messageId)
                .patchMessageReqBody(PatchMessageReqBody.newBuilder()
                        .content(textContentJson(text))
                        .build())
                .build();
        try {
            PatchMessageResp response = currentClient().im().message().patch(request);
            if (response.success()) {
                return FeishuMessageResponse.success(messageId);
            }
            return FeishuMessageResponse.failure(response.getCode(), response.getMsg());
        } catch (Exception e) {
            return FeishuMessageResponse.failure(null, e.getMessage());
        }
    }

    @Override
    public FeishuMessageResponse replyCard(String messageId, String cardJson) {
        return reply(messageId, "interactive", cardJson);
    }

    private FeishuMessageResponse reply(String messageId, String msgType, String content) {
        ReplyMessageReq request = ReplyMessageReq.newBuilder()
                .messageId(messageId)
                .replyMessageReqBody(ReplyMessageReqBody.newBuilder()
                        .msgType(msgType)
                        .content(content)
                        .build())
                .build();
        try {
            ReplyMessageResp response = currentClient().im().message().reply(request);
            if (response.success()) {
                ReplyMessageRespBody data = response.getData();
                return FeishuMessageResponse.success(data == null ? null : data.getMessageId());
            }
            return FeishuMessageResponse.failure(response.getCode(), response.getMsg());
        } catch (Exception e) {
            return FeishuMessageResponse.failure(null, e.getMessage());
        }
    }

    private Client currentClient() {
        FeishuRuntimeConfig config = configProvider == null ? null : configProvider.getActiveConfig();
        if (config == null || !StringUtils.hasText(config.getAppId()) || !StringUtils.hasText(config.getAppSecret())) {
            throw new IllegalStateException("active feishu app config is missing");
        }
        return Client.newBuilder(config.getAppId(), config.getAppSecret())
                .openBaseUrl(BaseUrlEnum.FeiShu)
                .logReqAtDebug(true)
                .build();
    }

    private static String textContentJson(String text) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of("text", text));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("text content cannot be serialized", e);
        }
    }
}
