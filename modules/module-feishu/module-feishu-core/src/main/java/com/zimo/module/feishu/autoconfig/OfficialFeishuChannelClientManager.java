package com.zimo.module.feishu.autoconfig;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.ws.Client;
import com.zimo.module.feishu.channel.FeishuChannelClientManager;
import com.zimo.module.feishu.channel.FeishuChannelMessageListener;
import com.zimo.module.feishu.config.FeishuConfigProvider;
import com.zimo.module.feishu.config.FeishuRuntimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class OfficialFeishuChannelClientManager implements FeishuChannelClientManager, SmartLifecycle, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(OfficialFeishuChannelClientManager.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FeishuConfigProvider configProvider;
    private final FeishuChannelMessageListener listener;
    private final FeishuAgentChannelProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Client client;

    public OfficialFeishuChannelClientManager(
            FeishuConfigProvider configProvider,
            FeishuChannelMessageListener listener,
            FeishuAgentChannelProperties properties) {
        this.configProvider = configProvider;
        this.listener = listener;
        this.properties = properties;
    }

    @Override
    public synchronized void start() {
        if (running.get()) {
            return;
        }
        FeishuRuntimeConfig config = configProvider == null ? null : configProvider.getActiveConfig();
        if (config == null || !StringUtils.hasText(config.getAppId()) || !StringUtils.hasText(config.getAppSecret())) {
            log.warn("Feishu channel client skipped because active app config is missing");
            return;
        }
        try {
            EventDispatcher dispatcher = EventDispatcher.newBuilder(config.getVerificationToken(), config.getEncryptKey())
                    .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                        @Override
                        public void handle(P2MessageReceiveV1 event) {
                            listener.onMessage(toPayload(event));
                        }
                    })
                    .build();
            client = new Client.Builder(config.getAppId(), config.getAppSecret())
                    .eventHandler(dispatcher)
                    .autoReconnect(properties.isAutoReconnect())
                    .build();
            client.start();
            awaitReady();
            running.set(true);
            log.info("Feishu channel client started");
        } catch (Exception e) {
            running.set(false);
            closeClient();
            log.warn("Feishu channel client start failed", e);
        }
    }

    @Override
    public synchronized void stop() {
        running.set(false);
        closeClient();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return properties.isAutoStart();
    }

    @Override
    public void destroy() {
        stop();
    }

    private void awaitReady() throws Exception {
        long seconds = properties.getAwaitReadyTimeoutSeconds();
        if (seconds > 0 && client != null) {
            client.awaitReady(seconds * 1000);
        }
    }

    private void closeClient() {
        Client current = client;
        client = null;
        if (current != null) {
            current.close();
        }
    }

    private static Map<String, Object> toPayload(P2MessageReceiveV1 event) {
        return OBJECT_MAPPER.convertValue(event, new TypeReference<>() {
        });
    }
}
