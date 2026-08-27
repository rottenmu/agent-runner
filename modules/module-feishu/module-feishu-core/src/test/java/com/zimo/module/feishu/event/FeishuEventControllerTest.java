package com.zimo.module.feishu.event;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class FeishuEventControllerTest {

    private final CapturingEventHandler handler = new CapturingEventHandler();
    private final MockMvc mockMvc = standaloneSetup(new FeishuEventController(testProperties(), handler)).build();

    @Test
    void returnsChallengeForUrlVerification() throws Exception {
        mockMvc.perform(post("/api/feishu/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "url_verification",
                                  "token": "test_token",
                                  "challenge": "challenge_code"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge").value("challenge_code"));
    }

    @Test
    void rejectsInvalidVerificationToken() throws Exception {
        mockMvc.perform(post("/api/feishu/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "url_verification",
                                  "token": "wrong_token",
                                  "challenge": "challenge_code"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void dispatchesBotMentionMessageEvent() throws Exception {
        mockMvc.perform(post("/api/feishu/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": "2.0",
                                  "header": {
                                    "event_type": "im.message.receive_v1",
                                    "token": "test_token"
                                  },
                                  "event": {
                                    "sender": {
                                      "sender_id": {
                                        "open_id": "ou_sender",
                                        "user_id": "u_sender"
                                      }
                                    },
                                    "message": {
                                      "message_id": "om_message",
                                      "chat_id": "oc_chat",
                                      "content": "{\\"text\\":\\"@机器人 帮我查库存\\"}",
                                      "mentions": [
                                        {
                                          "key": "@机器人",
                                          "name": "机器人"
                                        }
                                      ]
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(handler.event).isNotNull();
        assertThat(handler.event.getMessageId()).isEqualTo("om_message");
        assertThat(handler.event.getChatId()).isEqualTo("oc_chat");
        assertThat(handler.event.getText()).isEqualTo("@机器人 帮我查库存");
        assertThat(handler.event.getSenderOpenId()).isEqualTo("ou_sender");
        assertThat(handler.event.getSenderUserId()).isEqualTo("u_sender");
    }

    private static FeishuEventProperties testProperties() {
        return new FeishuEventProperties() {
            @Override
            public String getVerificationToken() {
                return "test_token";
            }

            @Override
            public String getEncryptKey() {
                return "test_encrypt_key";
            }
        };
    }

    private static class CapturingEventHandler implements FeishuEventHandler {
        private FeishuBotMentionEvent event;

        @Override
        public void handleBotMention(FeishuBotMentionEvent event) {
            this.event = event;
        }
    }
}
