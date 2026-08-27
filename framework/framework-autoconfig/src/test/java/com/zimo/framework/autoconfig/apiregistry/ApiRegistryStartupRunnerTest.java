package com.zimo.framework.autoconfig.apiregistry;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiRegistryStartupRunnerTest {

    @Test
    void runInitializesScansAndPersistsEndpointsInOrder() throws Exception {
        ApiRegistryProperties properties = new ApiRegistryProperties();
        ApiRegistrySchemaInitializer initializer = mock(ApiRegistrySchemaInitializer.class);
        ApiEndpointScanner scanner = mock(ApiEndpointScanner.class);
        ApiRegistryRepository repository = mock(ApiRegistryRepository.class);
        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        Map<RequestMappingInfo, HandlerMethod> mappings = Map.of();
        List<ApiEndpointMetadata> endpoints = List.of(endpoint());
        when(handlerMapping.getHandlerMethods()).thenReturn(mappings);
        when(scanner.scan(mappings)).thenReturn(endpoints);
        when(repository.saveAll(endpoints)).thenReturn(new ApiRegistrySaveResult(1, 0));
        ApiRegistryStartupRunner runner = new ApiRegistryStartupRunner(
                properties, initializer, scanner, repository, handlerMapping);

        runner.run(new DefaultApplicationArguments());

        InOrder order = inOrder(initializer, scanner, repository);
        order.verify(initializer).initializeIfNecessary();
        order.verify(scanner).scan(mappings);
        order.verify(repository).saveAll(endpoints);
    }

    @Test
    void runDoesNothingWhenRegistryIsDisabled() throws Exception {
        ApiRegistryProperties properties = new ApiRegistryProperties();
        properties.setEnabled(false);
        ApiRegistrySchemaInitializer initializer = mock(ApiRegistrySchemaInitializer.class);
        ApiEndpointScanner scanner = mock(ApiEndpointScanner.class);
        ApiRegistryRepository repository = mock(ApiRegistryRepository.class);
        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        ApiRegistryStartupRunner runner = new ApiRegistryStartupRunner(
                properties, initializer, scanner, repository, handlerMapping);

        runner.run(new DefaultApplicationArguments());

        verify(initializer, never()).initializeIfNecessary();
        verify(handlerMapping, never()).getHandlerMethods();
        verify(repository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void autoConfigurationImportContainsApiRegistryConfiguration() throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(input).isNotNull();
            String imports = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(imports).contains("com.zimo.framework.autoconfig.FrameworkApiRegistryAutoConfiguration");
        }
    }

    private ApiEndpointMetadata endpoint() {
        return new ApiEndpointMetadata(
                "material", "物料管理", "/api/material", "", "", "GET",
                "/api/material/categories", "list", "list", "", "[]", "[]", null,
                true, false, "hash", "1.0.0");
    }
}
