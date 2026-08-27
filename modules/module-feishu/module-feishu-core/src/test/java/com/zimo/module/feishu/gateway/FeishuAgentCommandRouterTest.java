package com.zimo.module.feishu.gateway;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuAgentCommandRouterTest {

    @Test
    void routesByPrefixThenPriority() {
        FeishuAgentBusinessHandler highPriority = new FakeHandler(
                "project-query", 10, List.of("项目"), List.of("项目进度"));
        FeishuAgentBusinessHandler lowPriority = new FakeHandler(
                "project-fallback", 50, List.of("项目"), List.of());

        FeishuAgentCommandRouter router = new FeishuAgentCommandRouter(List.of(lowPriority, highPriority));

        assertThat(router.route("项目 XJ100 进度")).containsSame(highPriority);
    }

    @Test
    void routesByKeywordWhenPrefixDoesNotMatch() {
        FeishuAgentBusinessHandler handler = new FakeHandler(
                "delivery-risk", 20, List.of("风险"), List.of("交期风险"));

        FeishuAgentCommandRouter router = new FeishuAgentCommandRouter(List.of(handler));

        assertThat(router.route("帮我看看交期风险")).containsSame(handler);
    }

    @Test
    void returnsEmptyWhenNoRouteMatched() {
        FeishuAgentCommandRouter router = new FeishuAgentCommandRouter(List.of());

        assertThat(router.route("未知指令")).isEmpty();
    }

    @Test
    void trimsCommandTextBeforeMatching() {
        FeishuAgentBusinessHandler handler = new FakeHandler(
                "project-query", 10, List.of("项目"), List.of());

        FeishuAgentCommandRouter router = new FeishuAgentCommandRouter(List.of(handler));

        assertThat(router.route("  项目 XJ100 进度  ")).containsSame(handler);
    }

    @Test
    void ignoresBlankPrefixesAndKeywords() {
        FeishuAgentBusinessHandler handler = new FakeHandler(
                "blank-route", 10, List.of(" ", ""), List.of("", "   "));

        FeishuAgentCommandRouter router = new FeishuAgentCommandRouter(List.of(handler));

        assertThat(router.route("任意指令")).isEmpty();
    }

    private static class FakeHandler implements FeishuAgentBusinessHandler {
        private final FeishuAgentCommandRoute route;

        private FakeHandler(String name, int priority, List<String> prefixes, List<String> keywords) {
            this.route = new FeishuAgentCommandRoute(name, prefixes, keywords, priority);
        }

        @Override
        public List<FeishuAgentCommandRoute> routes() {
            return List.of(route);
        }

        @Override
        public FeishuAgentBusinessResult handle(FeishuAgentBusinessRequest request) {
            return FeishuAgentBusinessResult.success(route.getName(), "ok");
        }
    }
}
