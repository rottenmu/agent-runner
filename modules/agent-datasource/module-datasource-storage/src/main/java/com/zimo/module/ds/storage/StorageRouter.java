package com.zimo.module.ds.storage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 存储路由：按配置的引擎名从已注册的存储后端中选出 Provider 并创建仓储。
 *
 * <p>路由规则：</p>
 * <ul>
 *   <li>配置为空 → 使用默认引擎；</li>
 *   <li>配置的引擎未注册 → 告警并回退默认引擎（此时传给 Provider 的是**实际生效**的引擎名）；</li>
 *   <li>默认引擎也不可用 → 抛出 {@link IllegalStateException}。</li>
 * </ul>
 *
 * <p>本类无状态，仅做「按名路由 + 回退」，不含任何业务语义，可被任意模块复用。</p>
 */
public final class StorageRouter {

    private static final Logger log = LoggerFactory.getLogger(StorageRouter.class);

    private StorageRouter() {
    }

    /**
     * 路由并创建仓储。
     *
     * @param providers       已注册的同类存储后端（同一后端多实例时以先注册者为准）
     * @param configuredEngine 配置的引擎名（可为空）
     * @param defaultEngine   默认引擎名（配置为空或未匹配时回退）
     * @param context         装配上下文
     * @param kind            存储类别名（仅用于日志与异常文案，如 "OLTP" / "OLAP"）
     * @param <T>             仓储类型
     * @return 仓储实例
     */
    public static <T> T route(
            List<StorageProvider<T>> providers,
            String configuredEngine,
            String defaultEngine,
            StorageContext context,
            String kind) {
        Map<String, StorageProvider<T>> registry = toRegistry(providers);
        String engine = configuredEngine == null || configuredEngine.isBlank()
                ? defaultEngine : configuredEngine;
        StorageProvider<T> provider = registry.get(engine);
        if (provider == null) {
            log.warn("未找到 {} 引擎 [{}]，回退默认 [{}]，已注册引擎：{}",
                    kind, engine, defaultEngine, registry.keySet());
            provider = registry.get(defaultEngine);
            engine = defaultEngine;
        }
        if (provider == null) {
            throw new IllegalStateException("无可用 " + kind + " 存储 Provider");
        }
        return provider.create(context.withEngine(engine));
    }

    private static <T> Map<String, StorageProvider<T>> toRegistry(List<StorageProvider<T>> providers) {
        if (providers == null || providers.isEmpty()) {
            return Map.of();
        }
        return providers.stream().collect(Collectors.toMap(
                StorageProvider::engine,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new));
    }
}
