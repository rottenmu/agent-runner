package com.zimo.module.feishu.autoconfig;

import com.lark.oapi.Client;
import com.lark.oapi.core.enums.BaseUrlEnum;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import com.lark.oapi.service.im.v1.model.CreateMessageRespBody;
import com.zimo.module.feishu.config.FeishuConfigProvider;
import com.zimo.module.feishu.config.FeishuRuntimeConfig;
import com.zimo.module.feishu.message.FeishuMessageClient;
import com.zimo.module.feishu.message.FeishuMessageResponse;
import com.zimo.module.feishu.message.FeishuTextMessageRequest;
import org.springframework.util.StringUtils;

public class OfficialFeishuMessageClient implements FeishuMessageClient {
    private final Client client;
    private final FeishuConfigProvider configProvider;

    public OfficialFeishuMessageClient(Client client) {
        this.client = client;
        this.configProvider = null;
    }

    public OfficialFeishuMessageClient(FeishuConfigProvider configProvider) {
        this.client = null;
        this.configProvider = configProvider;
    }

    @Override
    public FeishuMessageResponse sendText(FeishuTextMessageRequest request) {
        CreateMessageReq sdkRequest = CreateMessageReq.newBuilder()
                .receiveIdType(request.getReceiveIdType())
                .createMessageReqBody(CreateMessageReqBody.newBuilder()
                        .receiveId(request.getReceiveId())
                        .msgType("text")
                        .content(request.getContentJson())
                        .build())
                .build();

        try {
            CreateMessageResp sdkResponse = currentClient().im().message().create(sdkRequest);
            if (sdkResponse.success()) {
                CreateMessageRespBody data = sdkResponse.getData();
                return FeishuMessageResponse.success(data == null ? null : data.getMessageId());
            }
            return FeishuMessageResponse.failure(sdkResponse.getCode(), sdkResponse.getMsg());
        } catch (Exception e) {
            return FeishuMessageResponse.failure(null, e.getMessage());
        }
    }

    private Client currentClient() {
        if (configProvider == null) {
            return client;
        }
        FeishuRuntimeConfig config = configProvider.getActiveConfig();
        if (config == null || !StringUtils.hasText(config.getAppId()) || !StringUtils.hasText(config.getAppSecret())) {
            throw new IllegalStateException("active feishu app config is missing");
        }
        return Client.newBuilder(config.getAppId(), config.getAppSecret())
                .openBaseUrl(BaseUrlEnum.FeiShu)
                .logReqAtDebug(true)
                .build();
    }
}
