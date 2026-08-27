package com.zimo.framework.autoconfig.apiregistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.common.PluginRegister;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

class ApiEndpointScannerDefaultValueTest {

    @Test
    void defaultValuesMakeQueryAndHeaderParametersOptional() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ApiEndpointScanner scanner = new ApiEndpointScanner(
                List.of(new TestPlugin()), objectMapper, "1.0.0");
        Method method = DefaultValueController.class.getDeclaredMethod(
                "list", int.class, String.class);
        Map<RequestMappingInfo, HandlerMethod> mappings = Map.of(
                RequestMappingInfo.paths("/api/test/items").methods(GET).build(),
                new HandlerMethod(new DefaultValueController(), method));

        JsonNode params = objectMapper.readTree(scanner.scan(mappings).get(0).requestParams());

        assertThat(params).allSatisfy(param ->
                assertThat(param.get("required").asBoolean()).isFalse());
    }

    private static final class DefaultValueController {

        public void list(@RequestParam(defaultValue = "20") int size,
                         @RequestHeader(defaultValue = "zh-CN") String locale) {
        }
    }

    private static final class TestPlugin implements PluginRegister {

        @Override
        public String getPluginId() {
            return "test";
        }

        @Override
        public String getPluginName() {
            return "测试模块";
        }

        @Override
        public String getApiPrefix() {
            return "/api/test";
        }

        @Override
        public String getFrontendRoute() {
            return "/biz/test";
        }

        @Override
        public String getAgentName() {
            return "test-agent";
        }
    }
}
