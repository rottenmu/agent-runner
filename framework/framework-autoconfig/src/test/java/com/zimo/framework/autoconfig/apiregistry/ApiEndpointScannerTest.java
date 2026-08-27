package com.zimo.framework.autoconfig.apiregistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.common.PluginRegister;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

class ApiEndpointScannerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void scanBuildsMetadataForRoutesOwnedByLoadedPlugin() throws Exception {
        ApiEndpointScanner scanner = new ApiEndpointScanner(
                List.of(new MaterialPluginRegister()), objectMapper, "1.0.0");

        List<ApiEndpointMetadata> endpoints = scanner.scan(createMappings());

        assertThat(endpoints).hasSize(1);
        ApiEndpointMetadata endpoint = endpoints.get(0);
        assertThat(endpoint.moduleCode()).isEqualTo("material");
        assertThat(endpoint.moduleName()).isEqualTo("物料管理");
        assertThat(endpoint.moduleBasePath()).isEqualTo("/api/material");
        assertThat(endpoint.method()).isEqualTo("GET");
        assertThat(endpoint.path()).isEqualTo("/api/material/categories");
        List<String> componentNames = java.util.Arrays.stream(ApiEndpointMetadata.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
        assertThat(componentNames).doesNotContain("controllerName", "controllerPath");
        assertThat(endpoint.apiName()).isEqualTo("listCategories");
        assertThat(endpoint.hash()).isEqualTo(ApiHashGenerator.generate(
                "GET", "/api/material/categories", "material"));
        assertThat(endpoint.version()).isEqualTo("1.0.0");
        assertThat(endpoint.authRequired()).isTrue();
        assertThat(endpoint.deprecated()).isFalse();
        assertThat(objectMapper.readValue(endpoint.tags(), String[].class))
                .contains("material", "SampleController");

        JsonNode params = objectMapper.readTree(endpoint.requestParams());
        assertThat(params).hasSize(1);
        assertThat(params.get(0).get("name").asText()).isEqualTo("keyword");
        assertThat(params.get(0).get("location").asText()).isEqualTo("query");
        assertThat(params.get(0).get("required").asBoolean()).isFalse();
    }

    @Test
    void scanIgnoresRoutesOutsidePluginApiPrefixes() throws Exception {
        ApiEndpointScanner scanner = new ApiEndpointScanner(
                List.of(new MaterialPluginRegister()), objectMapper, "1.0.0");
        Map<RequestMappingInfo, HandlerMethod> mappings = new LinkedHashMap<>();
        mappings.put(RequestMappingInfo.paths("/api/internal/health").methods(GET).build(),
                handlerMethod("health"));

        assertThat(scanner.scan(mappings)).isEmpty();
    }

    @Test
    void scanMarksIgnoredDeprecatedEndpoints() throws Exception {
        ApiEndpointScanner scanner = new ApiEndpointScanner(
                List.of(new MaterialPluginRegister()), objectMapper, "1.0.0");
        Map<RequestMappingInfo, HandlerMethod> mappings = new LinkedHashMap<>();
        mappings.put(RequestMappingInfo.paths("/api/material/deprecated").methods(GET).build(),
                handlerMethod("ignoredDeprecatedEndpoint"));

        List<ApiEndpointMetadata> endpoints = scanner.scan(mappings);

        assertThat(endpoints).hasSize(1);
        ApiEndpointMetadata endpoint = endpoints.get(0);
        assertThat(endpoint.authRequired()).isFalse();
        assertThat(endpoint.deprecated()).isTrue();
    }

    private Map<RequestMappingInfo, HandlerMethod> createMappings() throws Exception {
        Map<RequestMappingInfo, HandlerMethod> mappings = new LinkedHashMap<>();
        mappings.put(RequestMappingInfo.paths("/api/material/categories").methods(GET).build(),
                handlerMethod("listCategories", String.class));
        mappings.put(RequestMappingInfo.paths("/error").methods(GET).build(), handlerMethod("health"));
        return mappings;
    }

    private HandlerMethod handlerMethod(String methodName, Class<?>... parameterTypes) throws Exception {
        SampleController controller = new SampleController();
        Method method = SampleController.class.getDeclaredMethod(methodName, parameterTypes);
        return new HandlerMethod(controller, method);
    }

    private static final class SampleController {

        @ResponseStatus(HttpStatus.OK)
        public void listCategories(@RequestParam(required = false) String keyword) {
        }

        public void health() {
        }

        @IgnorePermission
        @Deprecated
        public void ignoredDeprecatedEndpoint() {
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    private @interface IgnorePermission {
    }

    private static final class MaterialPluginRegister implements PluginRegister {

        @Override
        public String getPluginId() {
            return "material";
        }

        @Override
        public String getPluginName() {
            return "物料管理";
        }

        @Override
        public String getApiPrefix() {
            return "/api/material";
        }

        @Override
        public String getFrontendRoute() {
            return "/biz/material";
        }

        @Override
        public String getAgentName() {
            return "material-agent";
        }
    }
}
