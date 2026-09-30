package com.zimo.module.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zimo.framework.ai.AiAgentReply;
import com.zimo.framework.ai.AiAgentService;
import com.zimo.framework.ai.agent.AiAgentRouteRequest;
import com.zimo.framework.common.security.SecurityFacade;
import com.zimo.module.ai.management.AiManagedAgent;
import com.zimo.module.ai.memory.ChatTurnMemoryRecorder;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiChatControllerMemoryTest {

    @Test
    void successfulChatPassesCompletedTurnToMemoryRecorder() throws Exception {
        AiAgentService aiAgentService = mock(AiAgentService.class);
        AiChatPreflight preflight = mock(AiChatPreflight.class);
        ChatTurnMemoryRecorder memoryRecorder = mock(ChatTurnMemoryRecorder.class);
        AiManagedAgent agent = new AiManagedAgent(
                "agent-1", "测试智能体", null, null, null, null, List.of(), "conversation", null,
                true, "user-1", "tenant-1", "测试用户", List.of(), null);
        when(preflight.run(anyMap())).thenReturn(new AiChatPreflight.Result(
                null, agent, null, "tenant-1", "user-1", "session-1", null, null));
        when(aiAgentService.chat(eq("用户问题"), any(AiAgentRouteRequest.class)))
                .thenReturn(new AiAgentReply("agent-1", "助手回复"));

        AiChatController controller = new AiChatController(
                aiAgentService, preflight, Optional.<SecurityFacade>empty(), memoryRecorder);
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(post("/api/biz/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"agent-1\",\"message\":\"用户问题\","
                                + "\"sessionId\":\"session-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply").value("助手回复"));

        verify(memoryRecorder).record("tenant-1", "user-1", "session-1", "用户问题", "助手回复");
        verify(aiAgentService).chat(eq("用户问题"), any(AiAgentRouteRequest.class));
    }
}
