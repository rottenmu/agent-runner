package com.zimo.framework.ai.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.framework.ai.agent.AiAgentMiddleware;
import com.zimo.framework.ai.agent.AiMiddlewareResult;
import com.zimo.framework.ai.agent.AiRequestContext;
import com.zimo.framework.ai.agent.MiddlewareChain;
import com.zimo.framework.ai.plugin.PluginEventBus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 插件可逆注册回滚单测（dsh A1）：中间件注册/卸载移除、事件订阅撤销。
 */
class DynamicPluginManagerReversibleTest {

    @TempDir
    Path tempDir;

    private record TestPlugin(
            String id,
            List<Object> registeredMiddlewares,
            PluginEventBus bus,
            AtomicInteger eventCount) implements AiPlugin {

        @Override
        public String version() {
            return "1.0.0";
        }

        @Override
        public void onLoad(PluginContext context) {
            // 注册一个 around 中间件：包裹消息，委托下游
            AiAgentMiddleware middleware = (ctx, message, next) -> {
                AiMiddlewareResult result = next.proceed(ctx, "[wrapped]" + message);
                return result;
            };
            registeredMiddlewares.add(middleware);
            context.registerMiddleware(middleware);
            // 订阅 turn 事件
            context.eventBus().on(id, PluginEventBus.AGENT_TURN_BEGIN,
                    (type, payload) -> {
                        eventCount.incrementAndGet();
                        return true;
                    });
        }

        @Override
        public void onUnload() {
            // no-op
        }
    }

    @Test
    void loadedPluginRegistersMiddlewareAndSubscription() throws Exception {
        // 构造一个真实插件 jar：用临时目录直接实例化路径太重，改为直接测管理器注册接口。
        DynamicPluginManager manager = new DynamicPluginManager(tempDir.toString());
        TestPlugin plugin = new TestPlugin("demo", new java.util.ArrayList<>(),
                manager.eventBus(), new AtomicInteger());
        // 模拟 loadJar 后的 onLoad 链路（DefaultPluginContext 是包私有，通过 manager 内部验证）
        DefaultPluginContext ctx = new DefaultPluginContext(plugin.id(), manager,
                tempDir.resolve("demo"), manager.eventBus());
        plugin.onLoad(ctx);

        assertThat(manager.dynamicMiddlewares()).hasSize(1);
        assertThat(manager.eventBus().totalSubscriptions()).isEqualTo(1);

        // 卸载回滚：按 ctx 注册台账移除中间件 + 撤销事件订阅
        manager.removeRegistrationsForTest(ctx.registrationsForTest());
        manager.eventBus().removeAll(plugin.id());
        assertThat(manager.dynamicMiddlewares()).isEmpty();
        assertThat(manager.eventBus().totalSubscriptions()).isZero();
    }

    @Test
    void unloadRevokesMiddlewareAndSubscription() throws Exception {
        Path pluginDir = Files.createDirectories(tempDir.resolve("plugins"));
        DynamicPluginManager manager = new DynamicPluginManager(pluginDir.toString());
        TestPlugin plugin = new TestPlugin("demo", new java.util.ArrayList<>(),
                manager.eventBus(), new AtomicInteger());

        // 模拟 loadJar：直接构造 DefaultPluginContext 并 onLoad
        PluginContext ctx = new DefaultPluginContext(plugin.id(), manager,
                pluginDir.resolve("demo"), manager.eventBus());
        plugin.onLoad(ctx);

        assertThat(manager.dynamicMiddlewares()).hasSize(1);
        assertThat(manager.eventBus().totalSubscriptions()).isEqualTo(1);

        // 触发卸载回滚：移除中间件注册 + 撤销事件订阅
        DefaultPluginContext impl = (DefaultPluginContext) ctx;
        manager.removeRegistrationsForTest(impl.registrationsForTest());
        manager.eventBus().removeAll(plugin.id());

        assertThat(manager.dynamicMiddlewares()).isEmpty();
        assertThat(manager.eventBus().totalSubscriptions()).isZero();
    }

    @Test
    void middlewareChainAppliesPluginContribution() throws Exception {
        Path pluginDir = Files.createDirectories(tempDir.resolve("plugins2"));
        DynamicPluginManager manager = new DynamicPluginManager(pluginDir.toString());
        TestPlugin plugin = new TestPlugin("demo2", new java.util.ArrayList<>(),
                manager.eventBus(), new AtomicInteger());
        PluginContext ctx = new DefaultPluginContext(plugin.id(), manager,
                pluginDir.resolve("demo2"), manager.eventBus());
        plugin.onLoad(ctx);

        List<AiAgentMiddleware> middlewares = manager.dynamicMiddlewares();
        assertThat(middlewares).hasSize(1);

        MiddlewareChain terminal = (c, m) -> AiMiddlewareResult.continueWith(m + "|done");
        MiddlewareChain chain = AiAgentMiddleware.buildChain(middlewares, terminal);
        AiMiddlewareResult result = chain.proceed(null, "hello");

        assertThat(result.message()).isEqualTo("[wrapped]hello|done");
    }
}