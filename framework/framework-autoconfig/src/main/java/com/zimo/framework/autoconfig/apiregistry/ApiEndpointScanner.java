package com.zimo.framework.autoconfig.apiregistry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.common.PluginRegister;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spring MVC 业务插件接口扫描器。
 *
 * <p>扫描器只登记路径命中已加载插件 API 前缀的 Controller 映射。插件前缀按长度倒序匹配，
 * 从而在前缀嵌套时优先归属最具体的业务插件。</p>
 *
 * @author Codex
 * @since 2026-07-21
 */
public class ApiEndpointScanner {

    private final List<PluginRegister> plugins;
    private final ObjectMapper objectMapper;
    private final String version;

    /**
     * 创建 API 接口扫描器。
     *
     * @param plugins 当前容器已加载的插件注册信息
     * @param objectMapper 用于生成标准 JSON 字段的序列化器
     * @param version 新扫描接口使用的版本号
     */
    public ApiEndpointScanner(List<PluginRegister> plugins, ObjectMapper objectMapper, String version) {
        this.plugins = plugins.stream()
                .filter(plugin -> StringUtils.hasText(plugin.getApiPrefix()))
                .sorted(Comparator.comparingInt((PluginRegister plugin) -> plugin.getApiPrefix().length()).reversed())
                .toList();
        this.objectMapper = objectMapper;
        this.version = version;
    }

    /**
     * 扫描 Spring MVC 已生效路由并生成注册表元数据。
     *
     * @param mappings 请求映射与处理方法集合，不允许为 {@code null}
     * @return 按模块、路径和请求方法排序的接口元数据；无业务插件路由时返回空列表
     */
    public List<ApiEndpointMetadata> scan(Map<RequestMappingInfo, HandlerMethod> mappings) {
        List<ApiEndpointMetadata> endpoints = new ArrayList<>();
        mappings.forEach((mapping, handler) -> collectMapping(endpoints, mapping, handler));
        endpoints.sort(Comparator.comparing(ApiEndpointMetadata::moduleCode)
                .thenComparing(ApiEndpointMetadata::path)
                .thenComparing(ApiEndpointMetadata::method));
        return List.copyOf(endpoints);
    }

    private void collectMapping(List<ApiEndpointMetadata> endpoints,
                                RequestMappingInfo mapping,
                                HandlerMethod handler) {
        for (String path : extractPaths(mapping)) {
            PluginRegister plugin = findPlugin(path);
            if (plugin == null) {
                continue;
            }
            for (String method : extractMethods(mapping)) {
                endpoints.add(buildMetadata(plugin, method, path, handler));
            }
        }
    }

    private Set<String> extractPaths(RequestMappingInfo mapping) {
        if (mapping.getPathPatternsCondition() != null) {
            return mapping.getPathPatternsCondition().getPatternValues();
        }
        if (mapping.getPatternsCondition() != null) {
            return mapping.getPatternsCondition().getPatterns();
        }
        return Set.of();
    }

    private List<String> extractMethods(RequestMappingInfo mapping) {
        Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
        if (methods.isEmpty()) {
            return List.of("ALL");
        }
        return methods.stream().map(Enum::name).sorted().toList();
    }

    private PluginRegister findPlugin(String path) {
        return plugins.stream()
                .filter(plugin -> ownsPath(plugin.getApiPrefix(), path))
                .findFirst()
                .orElse(null);
    }

    private boolean ownsPath(String prefix, String path) {
        String normalized = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        return path.equals(normalized) || path.startsWith(normalized + "/");
    }

    private ApiEndpointMetadata buildMetadata(PluginRegister plugin,
                                              String method,
                                              String path,
                                              HandlerMethod handler) {
        Class<?> controllerType = handler.getBeanType();
        boolean deprecated = AnnotatedElementUtils.hasAnnotation(handler.getMethod(), Deprecated.class)
                || AnnotatedElementUtils.hasAnnotation(controllerType, Deprecated.class);
        return new ApiEndpointMetadata(
                plugin.getPluginId(), plugin.getPluginName(), plugin.getApiPrefix(), "",
                "", method, path,
                handler.getMethod().getName(), handler.getMethod().getName(), "",
                toJson(List.of(plugin.getPluginId(), controllerType.getSimpleName())),
                buildRequestParams(handler), null, !ignoresPermission(handler), deprecated,
                ApiHashGenerator.generate(method, path, plugin.getPluginId()), version);
    }

    private String buildRequestParams(HandlerMethod handler) {
        List<Map<String, Object>> params = new ArrayList<>();
        for (Parameter parameter : handler.getMethod().getParameters()) {
            params.add(describeParameter(parameter));
        }
        return toJson(params);
    }

    private Map<String, Object> describeParameter(Parameter parameter) {
        ParameterDescriptor descriptor = resolveDescriptor(parameter);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", descriptor.name());
        metadata.put("type", parameter.getParameterizedType().getTypeName());
        metadata.put("location", descriptor.location());
        metadata.put("required", descriptor.required());
        metadata.put("desc", "");
        return metadata;
    }

    private ParameterDescriptor resolveDescriptor(Parameter parameter) {
        RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
        if (requestParam != null) {
            return new ParameterDescriptor(annotationName(requestParam.name(), requestParam.value(), parameter),
                    "query", isRequired(requestParam.required(), requestParam.defaultValue()));
        }
        PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
        if (pathVariable != null) {
            return new ParameterDescriptor(annotationName(pathVariable.name(), pathVariable.value(), parameter),
                    "path", pathVariable.required());
        }
        RequestHeader header = parameter.getAnnotation(RequestHeader.class);
        if (header != null) {
            return new ParameterDescriptor(annotationName(header.name(), header.value(), parameter),
                    "header", isRequired(header.required(), header.defaultValue()));
        }
        if (parameter.isAnnotationPresent(RequestBody.class)) {
            return new ParameterDescriptor(parameter.getName(), "body",
                    parameter.getAnnotation(RequestBody.class).required());
        }
        if (parameter.isAnnotationPresent(RequestPart.class)) {
            RequestPart part = parameter.getAnnotation(RequestPart.class);
            return new ParameterDescriptor(annotationName(part.name(), part.value(), parameter),
                    "part", part.required());
        }
        return new ParameterDescriptor(parameter.getName(), "parameter", parameter.getType().isPrimitive());
    }

    private String annotationName(String name, String value, Parameter parameter) {
        if (StringUtils.hasText(name)) {
            return name;
        }
        return StringUtils.hasText(value) ? value : parameter.getName();
    }

    private boolean isRequired(boolean required, String defaultValue) {
        return required && ValueConstants.DEFAULT_NONE.equals(defaultValue);
    }

    private boolean ignoresPermission(HandlerMethod handler) {
        return hasAnnotationNamed(handler.getMethod().getAnnotations(), "IgnorePermission")
                || hasAnnotationNamed(handler.getBeanType().getAnnotations(), "IgnorePermission");
    }

    private boolean hasAnnotationNamed(Annotation[] annotations, String simpleName) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().getSimpleName().equals(simpleName)) {
                return true;
            }
        }
        return false;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("API 元数据 JSON 序列化失败", exception);
        }
    }

    private record ParameterDescriptor(String name, String location, boolean required) {
    }
}
