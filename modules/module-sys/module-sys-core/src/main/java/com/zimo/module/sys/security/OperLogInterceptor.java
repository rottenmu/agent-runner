package com.zimo.module.sys.security;

import com.zimo.module.sys.context.UserPermissionContext;
import cn.hutool.core.util.StrUtil;
import com.zimo.module.sys.entity.SysOperLog;
import com.zimo.module.sys.enums.PermOperTypeEnum;
import com.zimo.module.sys.service.OperLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Global operation audit interceptor without business code instrumentation.
 */
public class OperLogInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTRIBUTE = OperLogInterceptor.class.getName() + ".startTime";
    private static final String SKIP_ATTRIBUTE = OperLogInterceptor.class.getName() + ".skip";
    private static final int MAX_PARAM_LENGTH = 4096;
    private static final List<String> DEFAULT_WHITELIST_PATHS = List.of(
            "/actuator/**",
            "/assets/**",
            "/favicon.ico"
    );

    private final OperLogService operLogService;
    private final boolean enabled;
    private final List<String> whitelistPaths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public OperLogInterceptor(OperLogService operLogService) {
        this(operLogService, true, DEFAULT_WHITELIST_PATHS);
    }

    public OperLogInterceptor(OperLogService operLogService, boolean enabled, Collection<String> whitelistPaths) {
        this.operLogService = operLogService == null ? new OperLogService() : operLogService;
        this.enabled = enabled;
        this.whitelistPaths = immutableCleanPaths(whitelistPaths);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled || isWhitelisted(request)) {
            request.setAttribute(SKIP_ATTRIBUTE, Boolean.TRUE);
            return true;
        }
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        if (Boolean.TRUE.equals(request.getAttribute(SKIP_ATTRIBUTE))) {
            return;
        }

        SysOperLog operLog = new SysOperLog();
        fillOperator(operLog);
        operLog.setOperType(resolveOperType(request, handler).getCode());
        operLog.setOperName(resolveOperName(request, handler));
        operLog.setRequestMethod(request.getMethod());
        operLog.setRequestUri(request.getRequestURI());
        operLog.setOperIp(resolveClientIp(request));
        operLog.setRequestParams(resolveRequestParams(request));
        operLog.setHttpStatus(response == null ? null : response.getStatus());
        operLog.setCostMillis(resolveCostMillis(request));
        operLog.setResultStatus(ex == null ? "SUCCESS" : "FAIL");
        operLog.setErrorMessage(ex == null ? null : truncate(ex.getMessage(), 1024));
        operLogService.save(operLog);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getWhitelistPaths() {
        return whitelistPaths;
    }

    public boolean isWhitelisted(HttpServletRequest request) {
        if (request == null || whitelistPaths.isEmpty()) {
            return false;
        }
        String uri = request.getRequestURI();
        for (String pattern : whitelistPaths) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        return false;
    }

    private void fillOperator(SysOperLog operLog) {
        UserPermissionContext.current().ifPresent(context -> {
            operLog.setOperUserId(context.getUserId());
            operLog.setOperAccount(context.getAccount());
            operLog.setOrganizationId(context.getOrganizationId());
            operLog.setOrganizationName(context.getOrganizationName());
        });
    }

    private PermOperTypeEnum resolveOperType(HttpServletRequest request, Object handler) {
        String method = request == null ? "" : request.getMethod();
        String uri = request == null ? "" : request.getRequestURI();
        String handlerName = handler instanceof HandlerMethod handlerMethod ? handlerMethod.getMethod().getName() : "";
        String source = (uri + " " + handlerName).toLowerCase();

        if (source.contains("export")) {
            return PermOperTypeEnum.EXPORT;
        }
        if (source.contains("import")) {
            return PermOperTypeEnum.IMPORT;
        }
        if (source.contains("audit") || source.contains("approve")) {
            return PermOperTypeEnum.AUDIT;
        }
        if (source.contains("enable")) {
            return PermOperTypeEnum.ENABLE;
        }
        if (source.contains("disable")) {
            return PermOperTypeEnum.DISABLE;
        }
        if (source.contains("assign")) {
            return PermOperTypeEnum.ASSIGN;
        }
        if ("POST".equalsIgnoreCase(method)) {
            return PermOperTypeEnum.ADD;
        }
        if ("PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
            return PermOperTypeEnum.EDIT;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            return PermOperTypeEnum.DELETE;
        }
        return PermOperTypeEnum.QUERY;
    }

    private String resolveOperName(HttpServletRequest request, Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            return handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName();
        }
        return request == null ? "" : request.getRequestURI();
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveRequestParams(HttpServletRequest request) {
        if (request == null || request.getParameterMap().isEmpty()) {
            return "";
        }
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            String[] values = entry.getValue();
            if (values == null || values.length == 0) {
                entries.add(entry.getKey() + "=");
                continue;
            }
            for (String value : values) {
                entries.add(entry.getKey() + "=" + value);
            }
        }
        return truncate(String.join("&", entries), MAX_PARAM_LENGTH);
    }

    private Long resolveCostMillis(HttpServletRequest request) {
        Object startTime = request.getAttribute(START_TIME_ATTRIBUTE);
        if (!(startTime instanceof Long start)) {
            return 0L;
        }
        return Math.max(System.currentTimeMillis() - start, 0L);
    }

    private static List<String> immutableCleanPaths(Collection<String> paths) {
        if (cn.hutool.core.collection.CollUtil.isEmpty(paths)) {
            return Collections.emptyList();
        }
        List<String> clean = new ArrayList<>();
        for (String path : paths) {
            if (hasText(path)) {
                clean.add(path.trim());
            }
        }
        return clean.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(clean);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
