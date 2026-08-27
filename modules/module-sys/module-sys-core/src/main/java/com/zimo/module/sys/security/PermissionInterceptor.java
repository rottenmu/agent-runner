package com.zimo.module.sys.security;

import com.zimo.module.sys.annotation.HasPerm;
import cn.hutool.core.util.StrUtil;
import com.zimo.module.sys.annotation.IgnorePermission;
import com.zimo.module.sys.context.UserPermissionContext;
import com.zimo.module.sys.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * RBAC permission interceptor for controller level function permission checks.
 */
public class PermissionInterceptor implements HandlerInterceptor {

    private static final List<String> DEFAULT_WHITELIST_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/logout",
            "/api/auth/info",
            "/actuator/health",
            "/assets/**",
            "/favicon.ico"
    );

    private final boolean enabled;
    private final List<String> whitelistPaths;
    private final PermissionService permissionService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public PermissionInterceptor() {
        this(true, DEFAULT_WHITELIST_PATHS);
    }

    public PermissionInterceptor(boolean enabled, Collection<String> whitelistPaths) {
        this(enabled, whitelistPaths, new PermissionService());
    }

    public PermissionInterceptor(boolean enabled, Collection<String> whitelistPaths, PermissionService permissionService) {
        this.enabled = enabled;
        this.whitelistPaths = immutableCleanPaths(whitelistPaths);
        this.permissionService = permissionService == null ? new PermissionService() : permissionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled || !(handler instanceof HandlerMethod handlerMethod) || isWhitelisted(request)) {
            return true;
        }
        if (hasIgnorePermission(handlerMethod)) {
            return true;
        }

        HasPerm hasPerm = resolveHasPerm(handlerMethod);
        if (hasPerm == null || !hasText(hasPerm.value())) {
            return true;
        }
        if (permissionService.hasPermission(hasPerm.value().trim(), UserPermissionContext.currentPermissions())) {
            return true;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: missing permission " + hasPerm.value());
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
        String lookupPath = getLookupPath(request);
        for (String pattern : whitelistPaths) {
            if (pathMatcher.match(pattern, lookupPath)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasIgnorePermission(HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        Class<?> beanType = handlerMethod.getBeanType();
        return AnnotatedElementUtils.hasAnnotation(method, IgnorePermission.class)
                || AnnotatedElementUtils.hasAnnotation(beanType, IgnorePermission.class);
    }

    private HasPerm resolveHasPerm(HandlerMethod handlerMethod) {
        HasPerm methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), HasPerm.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        return AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), HasPerm.class);
    }

    private String getLookupPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (hasText(contextPath) && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return hasText(requestUri) ? requestUri : "/";
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
        if (clean.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(clean);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
