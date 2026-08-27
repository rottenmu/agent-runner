package com.zimo.starter.ai.channel;

import com.zimo.starter.ai.agent.AiAgentProfile;

public interface AiChannelIntentHandler {
    AiChannelReply handle(AiChannelMessage message, AiAgentProfile defaultAgent);
}
