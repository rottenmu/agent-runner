package com.zimo.framework.ai.chat;

import com.zimo.module.agentmemory.chat.AiConversationMemory;
import com.zimo.module.agentmemory.chat.AiChatMessage;
import com.zimo.module.agentmemory.chat.AiConversationCompressionCandidate;

import com.zimo.module.agentmemory.chat.AiConversationCompressionCandidate;
import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.framework.common.storage.FileStorageService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * AiConversationMemory 共享模式（RocksDB 后端）跨实例一致性测试。
 */
class AiConversationMemorySharedTest {

    private static final class InMemoryStorage implements FileStorageService {
        private final Map<String, byte[]> data = new LinkedHashMap<>();

        @Override
        public void store(String key, byte[] bytes) {
            data.put(key, bytes);
        }

        @Override
        public byte[] get(String key) {
            return data.get(key);
        }

        @Override
        public boolean delete(String key) {
            return data.remove(key) != null;
        }

        @Override
        public List<String> list(String prefix) {
            return data.keySet().stream().filter(k -> k.startsWith(prefix)).toList();
        }

        @Override
        public boolean exists(String key) {
            return data.containsKey(key);
        }
    }

    @Test
    void sharedMemorySurvivesAcrossInstances() {
        FileStorageService storage = new InMemoryStorage();

        AiConversationMemory instanceA = new AiConversationMemory(512, storage);
        AiConversationMemory instanceB = new AiConversationMemory(512, storage);

        assertThat(instanceA.isShared()).isTrue();
        instanceA.appendTurn("s1", "你好", "你好，有什么可以帮你", 20);
        instanceA.appendTurn("s1", "帮我查库存", "好的，正在查询", 20);

        // 实例 B 能读到实例 A 写入的消息（跨实例共享）
        List<AiChatMessage> snapshot = instanceB.snapshot("s1");
        assertThat(snapshot).hasSize(4);
        assertThat(snapshot.get(0).content()).isEqualTo("你好");
        assertThat(snapshot.get(3).content()).isEqualTo("好的，正在查询");
    }

    @Test
    void compressionAppliesAcrossInstances() {
        FileStorageService storage = new InMemoryStorage();
        AiConversationMemory instanceA = new AiConversationMemory(512, storage);
        AiConversationMemory instanceB = new AiConversationMemory(512, storage);

        for (int i = 1; i <= 6; i++) {
            instanceA.appendTurn("s2", "问题" + i, "回答" + i, 20);
        }

        // 实例 B 生成压缩候选（trigger=6, recent=2 → 压缩 4 条）
        AiConversationCompressionCandidate candidate = instanceB
                .prepareCompression("s2", 6, 2).orElseThrow();

        // 实例 A 应用摘要（revision 一致才能成功）
        boolean applied = instanceA.applyCompression(candidate, "已确认前四轮事实", 20);
        assertThat(applied).isTrue();

        // 实例 B 再读：摘要作为首条 system 消息 + 保留最近 recentMessages=2 条消息
        List<AiChatMessage> snapshot = instanceB.snapshot("s2");
        assertThat(snapshot.get(0).role()).isEqualTo("system");
        assertThat(snapshot.get(0).content()).contains("已确认前四轮事实");
        assertThat(snapshot).hasSize(3); // 摘要 + 保留 2 条消息
    }

    @Test
    void memoryModeDefaultsToInProcess() {
        AiConversationMemory memory = new AiConversationMemory();
        assertThat(memory.isShared()).isFalse();
        memory.appendTurn("s3", "hi", "hello", 20);
        assertThat(memory.snapshot("s3")).hasSize(2);
    }
}
