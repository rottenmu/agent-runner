package com.zimo.module.feishu.gateway;

import java.util.List;

public interface FeishuAgentBusinessHandler {
    List<FeishuAgentCommandRoute> routes();

    FeishuAgentBusinessResult handle(FeishuAgentBusinessRequest request);
}
