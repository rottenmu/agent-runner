package com.zimo.module.ai.memory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ChatTurnMemoryRecorderTest {

    @Test
    void recordsOneTurnAsSessionMemoryWrite() throws Exception {
        MemoryMcpClient client = mock(MemoryMcpClient.class);
        ChatMemoryRecorderProperties properties = new ChatMemoryRecorderProperties();
        properties.setQueueCapacity(2);
        properties.setMaxChars(1000);

        CountDownLatch writeCompleted = new CountDownLatch(1);
        AtomicReference<Map<String, Object>> capturedArguments = new AtomicReference<>();
        when(client.callTool(eq("memory_write"), anyMap())).thenAnswer(invocation -> {
            capturedArguments.set(invocation.getArgument(1));
            writeCompleted.countDown();
            return Map.of("sensitiveMasked", false);
        });

        ChatTurnMemoryRecorder recorder = new ChatTurnMemoryRecorder(client, properties);
        try {
            recorder.record("tenant-1", "user-2", "session-3", "  你好  ", "  已为你完成。  ");

            assertTrue(writeCompleted.await(2, TimeUnit.SECONDS), "异步记忆写入应在超时内完成");
            verify(client).callTool(eq("memory_write"), anyMap());

            Map<String, Object> arguments = capturedArguments.get();
            assertNotNull(arguments);
            assertEquals("session", arguments.get("target"));
            assertEquals("session-3", arguments.get("sessionId"));
            assertEquals("tenant-1", arguments.get("tenantId"));
            assertEquals("user-2", arguments.get("userId"));
            assertTrue(((String) arguments.get("key")).startsWith("chat-"));
            assertEquals("[user] 你好\n[assistant] 已为你完成。", arguments.get("content"));
        } finally {
            recorder.shutdown();
        }
    }

    @Test
    void skipsDisabledRecorderAndBlankSession() {
        MemoryMcpClient client = mock(MemoryMcpClient.class);
        ChatMemoryRecorderProperties properties = new ChatMemoryRecorderProperties();
        properties.setEnabled(false);
        ChatTurnMemoryRecorder recorder = new ChatTurnMemoryRecorder(client, properties);
        try {
            recorder.record("tenant-1", "user-2", "session-3", "问题", "回答");

            properties.setEnabled(true);
            recorder.record("tenant-1", "user-2", "  ", "问题", "回答");

            verify(client, never()).callTool(eq("memory_write"), anyMap());
        } finally {
            recorder.shutdown();
        }
    }

    @Test
    void doesNotPropagateMemoryServiceFailureToChatCaller() throws Exception {
        MemoryMcpClient client = mock(MemoryMcpClient.class);
        ChatMemoryRecorderProperties properties = new ChatMemoryRecorderProperties();
        CountDownLatch writeAttempted = new CountDownLatch(1);
        when(client.callTool(eq("memory_write"), anyMap())).thenAnswer(invocation -> {
            writeAttempted.countDown();
            throw new IllegalStateException("memory service unavailable");
        });

        ChatTurnMemoryRecorder recorder = new ChatTurnMemoryRecorder(client, properties);
        try {
            assertDoesNotThrow(() -> recorder.record("tenant-1", "user-2", "session-3", "问题", "回答"));
            assertTrue(writeAttempted.await(2, TimeUnit.SECONDS), "异步写入应已尝试调用记忆服务");
        } finally {
            recorder.shutdown();
        }
    }
}
