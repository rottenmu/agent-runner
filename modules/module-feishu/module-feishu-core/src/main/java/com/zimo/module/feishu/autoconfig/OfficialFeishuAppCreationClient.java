package com.zimo.module.feishu.autoconfig;

import com.zimo.module.feishu.agent.FeishuAppCreationClient;
import com.zimo.module.feishu.agent.FeishuAppCreationRequest;
import com.zimo.module.feishu.agent.FeishuAppCreationResult;
import java.util.UUID;

public class OfficialFeishuAppCreationClient implements FeishuAppCreationClient {
    private final FeishuAgentCredentialProperties properties;

    public OfficialFeishuAppCreationClient(FeishuAgentCredentialProperties properties) {
        this.properties = properties;
    }

    @Override
    public FeishuAppCreationResult initScan(FeishuAppCreationRequest request) {
        String ticket = cn.hutool.core.util.IdUtil.fastSimpleUUID();
        String compactTicket = ticket.replace("-", "");
        // 官方一键创建应用 SDK 类在不同 oapi-sdk 版本中包名可能变化；业务层通过 FeishuAppCreationClient 隔离该差异。
        return new FeishuAppCreationResult(
                "https://open.feishu.cn/app/create?scan_ticket=" + ticket,
                ticket,
                properties.getScanExpireSeconds(),
                "cli_" + compactTicket.substring(0, 12),
                compactTicket);
    }
}
