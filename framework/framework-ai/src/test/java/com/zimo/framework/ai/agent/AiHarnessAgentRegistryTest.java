package com.zimo.framework.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zimo.framework.ai.AiAgentProperties;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiHarnessAgentRegistryTest {

    private AiAgentProperties properties;
    private AiHarnessAgentFactory factory;
    private AiHarnessAgentRegistry registry;

    @BeforeEach
    void setUp() {
        properties = new AiAgentProperties();
        properties.setHarnessRegistryCapacity(2);
        factory = mock(AiHarnessAgentFactory.class);
        registry = new AiHarnessAgentRegistry(factory, properties);
    }

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @Test
    void createsOnlyOneInstanceForConcurrentRequestsWithSameKey() throws Exception {
        AiAgentProfile profile = profile("agent-a", "model-a");
        HarnessAgent agent = mock(HarnessAgent.class);
        when(factory.create(eq(profile), any(AiHarnessAgentKey.class))).thenAnswer(invocation -> {
            Thread.sleep(30);
            return agent;
        });

        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<HarnessAgent>> futures = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return registry.getOrCreate(profile);
                }));
            }
            start.countDown();

            for (Future<HarnessAgent> future : futures) {
                assertThat(future.get()).isSameAs(agent);
            }
        } finally {
            executor.shutdownNow();
        }
        verify(factory, times(1)).create(eq(profile), any(AiHarnessAgentKey.class));
    }

    @Test
    void rebuildsWhenProfileFingerprintChanges() {
        AiAgentProfile firstProfile = profile("agent-a", "model-a");
        AiAgentProfile changedProfile = profile("agent-a", "model-b");
        HarnessAgent first = mock(HarnessAgent.class);
        HarnessAgent changed = mock(HarnessAgent.class);
        when(factory.create(any(), any())).thenReturn(first, changed);

        assertThat(registry.getOrCreate(firstProfile)).isSameAs(first);
        assertThat(registry.getOrCreate(changedProfile)).isSameAs(changed);

        verify(factory, times(2)).create(any(), any());
    }

    @Test
    void neverReusesInstancesAcrossTenants() {
        AiAgentProfile tenantA = profile("tenant-a", "agent-a", "model-a");
        AiAgentProfile tenantB = profile("tenant-b", "agent-a", "model-a");
        HarnessAgent first = mock(HarnessAgent.class);
        HarnessAgent second = mock(HarnessAgent.class);
        when(factory.create(any(), any())).thenReturn(first, second);

        assertThat(registry.getOrCreate(tenantA)).isSameAs(first);
        assertThat(registry.getOrCreate(tenantB)).isSameAs(second);

        verify(factory, times(2)).create(any(), any());
    }

    @Test
    void rebuildsWhenWorkspaceVersionChanges() {
        AiAgentProfile profile = profile("agent-a", "model-a");
        HarnessAgent first = mock(HarnessAgent.class);
        HarnessAgent rebuilt = mock(HarnessAgent.class);
        when(factory.create(any(), any())).thenReturn(first, rebuilt);

        assertThat(registry.getOrCreate(profile)).isSameAs(first);
        properties.setHarnessWorkspaceVersion("v2");
        assertThat(registry.getOrCreate(profile)).isSameAs(rebuilt);

        verify(factory, times(2)).create(any(), any());
    }
    @Test
    void rebuildsBlankNamedProfileWhenGlobalAgentNameChanges() {
        AiAgentProfile profile = new AiAgentProfile(
                "agent-a",
                "tenant-a",
                " ",
                "model-a",
                "system",
                List.of(),
                true);
        HarnessAgent first = mock(HarnessAgent.class);
        HarnessAgent rebuilt = mock(HarnessAgent.class);
        when(factory.create(any(), any())).thenReturn(first, rebuilt);

        assertThat(registry.getOrCreate(profile)).isSameAs(first);
        properties.setName("renamed-global-agent");
        assertThat(registry.getOrCreate(profile)).isSameAs(rebuilt);

        verify(factory, times(2)).create(any(), any());
    }

    @Test
    void rebuildsAfterTenantAgentInvalidation() {
        AiAgentProfile profile = profile("agent-a", "model-a");
        HarnessAgent first = mock(HarnessAgent.class);
        HarnessAgent rebuilt = mock(HarnessAgent.class);
        when(factory.create(any(), any())).thenReturn(first, rebuilt);

        assertThat(registry.getOrCreate(profile)).isSameAs(first);
        registry.invalidate("tenant-a", "agent-a");
        assertThat(registry.getOrCreate(profile)).isSameAs(rebuilt);

        verify(first).close();
        verify(factory, times(2)).create(any(), any());
    }

    @Test
    void doesNotCacheFactoryFailures() {
        AiAgentProfile profile = profile("agent-a", "model-a");
        HarnessAgent recovered = mock(HarnessAgent.class);
        when(factory.create(any(), any()))
                .thenThrow(new IllegalStateException("temporary failure"))
                .thenReturn(recovered);

        assertThatThrownBy(() -> registry.getOrCreate(profile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("temporary failure");
        assertThat(registry.getOrCreate(profile)).isSameAs(recovered);

        verify(factory, times(2)).create(any(), any());
    }

    @Test
    void evictsLeastRecentlyUsedInstanceWhenCapacityIsExceeded() {
        AiAgentProfile profileA = profile("agent-a", "model-a");
        AiAgentProfile profileB = profile("agent-b", "model-b");
        AiAgentProfile profileC = profile("agent-c", "model-c");
        AtomicInteger sequence = new AtomicInteger();
        when(factory.create(any(), any()))
                .thenAnswer(invocation -> mock(
                        HarnessAgent.class,
                        "agent-" + sequence.incrementAndGet()));

        HarnessAgent firstB = registry.getOrCreate(profileB);
        registry.getOrCreate(profileA);
        registry.getOrCreate(profileC);
        HarnessAgent rebuiltB = registry.getOrCreate(profileB);

        assertThat(rebuiltB).isNotSameAs(firstB);
        verify(firstB).close();
        verify(factory, times(4)).create(any(), any());
    }

    @Test
    void defersEvictedAgentCloseUntilActiveUseCompletes() {
        AiAgentProperties singleCapacityProperties = new AiAgentProperties();
        singleCapacityProperties.setHarnessRegistryCapacity(1);
        AiHarnessAgentFactory singleCapacityFactory = mock(AiHarnessAgentFactory.class);
        AiHarnessAgentRegistry singleCapacityRegistry =
                new AiHarnessAgentRegistry(singleCapacityFactory, singleCapacityProperties);
        AiAgentProfile profileA = profile("agent-a", "model-a");
        AiAgentProfile profileB = profile("agent-b", "model-b");
        HarnessAgent agentA = mock(HarnessAgent.class);
        HarnessAgent agentB = mock(HarnessAgent.class);
        when(singleCapacityFactory.create(any(), any())).thenReturn(agentA, agentB);

        try {
            singleCapacityRegistry.withAgent(profileA, activeAgent -> {
                assertThat(activeAgent).isSameAs(agentA);
                singleCapacityRegistry.getOrCreate(profileB);
                verify(agentA, never()).close();
                return null;
            });
            verify(agentA).close();
        } finally {
            singleCapacityRegistry.close();
        }
    }
    private AiAgentProfile profile(String agentId, String modelName) {
        return profile("tenant-a", agentId, modelName);
    }

    private AiAgentProfile profile(
            String tenantId,
            String agentId,
            String modelName) {
        return new AiAgentProfile(
                agentId,
                tenantId,
                agentId,
                modelName,
                "system",
                List.of("skill-a"),
                true);
    }
}
