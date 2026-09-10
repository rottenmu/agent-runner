package com.zimo.module.ai.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import org.springframework.core.Ordered;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

/**
 * AI 技能 ZIP 导入 Multipart 异常解析器。
 *
 * <p>Multipart 解析发生在 Controller 参数绑定之前，因此本解析器按请求路径识别技能导入接口。
 * 文件超限返回业务状态 {@code 413}，畸形 Multipart 请求返回业务状态 {@code 400}；
 * 其他请求和异常返回 {@code null}，继续交由原有异常链处理。</p>
 *
 * @author Codex
 * @since 2026-07-27
 */
public final class AiSkillMultipartExceptionResolver implements HandlerExceptionResolver, Ordered {

    private static final String SKILL_IMPORT_PATH = "/api/biz/ai/skills/import";

    private final ObjectMapper objectMapper;

    /**
     * 创建技能 ZIP 导入 Multipart 异常解析器。
     *
     * @param objectMapper Spring Boot JSON 映射器，不能为空
     * @throws NullPointerException 当 JSON 映射器为空时抛出
     */
    public AiSkillMultipartExceptionResolver(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 将技能导入接口的 Multipart 异常转换为统一 {@link ApiResponse} 返回体。
     *
     * @param request 当前 HTTP 请求，不能为空
     * @param response 当前 HTTP 响应，不能为空
     * @param handler 已匹配的处理器；Multipart 预解析失败时允许为空
     * @param exception DispatcherServlet 捕获的异常，不能为空
     * @return 已处理时返回空视图模型，非技能导入或非 Multipart 异常时返回 {@code null}
     */
    @Override
    public ModelAndView resolveException(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception) {
        if (!isSkillImportRequest(request) || !(exception instanceof MultipartException)) {
            return null;
        }
        if (exception instanceof MaxUploadSizeExceededException) {
            return response(413, "技能 ZIP 文件不能超过 2MB");
        }
        return response(400, "上传请求格式不正确，请使用 multipart/form-data 并包含 file 字段");
    }

    /**
     * 返回最高异常解析优先级，确保在通用异常处理器之前识别 Multipart 预解析失败。
     *
     * @return {@link Ordered#HIGHEST_PRECEDENCE}
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean isSkillImportRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return SKILL_IMPORT_PATH.equals(path);
    }

    private ModelAndView response(int code, String message) {
        MappingJackson2JsonView view = new MappingJackson2JsonView(objectMapper);
        view.setExtractValueFromSingleKeyModel(true);
        return new ModelAndView(view, "response", ApiResponse.fail(code, message));
    }
}
