package com.zimo.framework.ai.channel;

import com.zimo.framework.ai.agent.AiAgentProfile;

public interface AiChannelIntentHandler {
    AiChannelReply handle(AiChannelMessage message, AiAgentProfile defaultAgent);
}
