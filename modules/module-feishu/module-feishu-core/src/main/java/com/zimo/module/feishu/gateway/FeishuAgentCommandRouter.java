package com.zimo.module.feishu.gateway;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class FeishuAgentCommandRouter {
    private final List<FeishuAgentBusinessHandler> handlers;

    public FeishuAgentCommandRouter(List<FeishuAgentBusinessHandler> handlers) {
        this.handlers = handlers == null ? List.of() : List.copyOf(handlers);
    }

    public Optional<FeishuAgentBusinessHandler> route(String commandText) {
        return handlers.stream()
                .filter(Objects::nonNull)
                .flatMap(handler -> routesOf(handler).stream()
                        .filter(route -> route.matches(commandText))
                        .map(route -> new MatchedRoute(handler, route)))
                .min(Comparator.comparingInt(match -> match.route().getPriority()))
                .map(MatchedRoute::handler);
    }

    private static List<FeishuAgentCommandRoute> routesOf(FeishuAgentBusinessHandler handler) {
        List<FeishuAgentCommandRoute> routes = handler.routes();
        if (routes == null) {
            return List.of();
        }
        return routes.stream().filter(Objects::nonNull).toList();
    }

    private record MatchedRoute(FeishuAgentBusinessHandler handler, FeishuAgentCommandRoute route) {
    }
}
