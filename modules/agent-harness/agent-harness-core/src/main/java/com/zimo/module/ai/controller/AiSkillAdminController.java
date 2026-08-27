package com.zimo.module.ai.controller;

import com.zimo.framework.common.ApiResponse;
import cn.hutool.core.util.StrUtil;
import com.zimo.framework.common.BizException;
import com.zimo.module.ai.management.AiAgentManagementService;
import com.zimo.module.ai.management.AiManagedSkill;
import com.zimo.module.ai.management.AiManagedSkillRequest;
import com.zimo.module.ai.management.AiSkillApiConfigRequest;
import com.zimo.module.ai.management.AiSkillPromptTemplateRequest;
import com.zimo.module.ai.skillimport.AiSkillImportException;
import com.zimo.module.ai.skillimport.AiSkillZipImportService;
import java.util.List;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 模块技能管理接口。
 *
 * <p>本控制器属于 module-ai 业务插件，对外统一暴露 {@code /api/biz/ai/skills} 平台路径。
 * 控制器只负责路径映射、统一返回体封装和领域参数异常转换，不复制技能创建、编辑、删除、绑定提示词模板等业务逻辑。
 * 当前接口不直接声明权限注解，权限标识由系统 API 注册表和菜单权限配置统一维护。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
@RestController
@RequestMapping("/api/biz/ai/skills")
public class AiSkillAdminController {

    private final AiAgentManagementService managementService;
    private final AiSkillZipImportService importService;

    /**
     * 创建技能管理控制器。
     *
     * @param managementService module-ai 提供的技能管理服务，不能为空
     * @param importService 单技能 ZIP 导入服务，不能为空
     * @throws NullPointerException 当任一依赖为空时抛出
     */
    public AiSkillAdminController(
            AiAgentManagementService managementService,
            AiSkillZipImportService importService) {
        this.managementService = Objects.requireNonNull(managementService, "managementService must not be null");
        this.importService = Objects.requireNonNull(importService, "importService must not be null");
    }

    /**
     * 查询当前系统已注册的全部 AI 技能。
     *
     * <p>请求路径为 {@code GET /api/biz/ai/skills}。返回数据包含内置 Bean 技能和自定义 API 技能，
     * 前端可根据 {@code source} 字段区分只读技能和可编辑技能。</p>
     *
     * @return 统一返回体，数据为技能列表；无技能时返回空列表
     */
    @GetMapping
    public ApiResponse<List<AiManagedSkill>> list() {
        return ApiResponse.ok(managementService.listSkills());
    }

    /**
     * 创建自定义 API 技能。
     *
     * <p>请求路径为 {@code POST /api/biz/ai/skills}。请求体必须包含技能名称、描述和 API 调用配置。
     * 内置技能重名、API 技能重名或 API 配置缺失时，会转换为业务状态码 {@code 400}。</p>
     *
     * @param request 技能创建请求，包含技能基础信息和远程 API 配置
     * @return 统一返回体，数据为创建后的技能信息
     * @throws BizException 当领域参数校验失败时抛出 {@code 400} 业务异常
     */
    @PostMapping
    public ApiResponse<AiManagedSkill> create(@RequestBody AiManagedSkillRequest request) {
        return call(() -> managementService.createApiSkill(request));
    }

    /**
     * 导入单技能 ZIP 包。
     *
     * <p>请求路径为 {@code POST /api/biz/ai/skills/import}，请求类型为 multipart/form-data。
     * {@code file} 字段允许在 Web 绑定阶段缺省，统一由导入服务转换为 {@code 400} 业务错误；
     * 格式错误返回 {@code 400}，资源超限返回 {@code 413}，持久化或注册表故障继续交由全局异常链处理。</p>
     *
     * @param file multipart 中名为 {@code file} 的 ZIP 技能包，缺省时由导入服务校验
     * @return 统一返回体，数据为导入创建后的技能信息
     * @throws BizException 文件或清单错误、资源超限或技能重名时抛出对应业务异常
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AiManagedSkill> importSkill(
            @RequestPart(value = "file", required = false) MultipartFile file) {
        try {
            return ApiResponse.ok(importService.importSkill(file));
        } catch (AiSkillImportException exception) {
            throw new BizException(exception.getCode(), exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    /**
     * 编辑自定义 API 技能。
     *
     * <p>请求路径为 {@code PUT /api/biz/ai/skills/{name}}。技能名称创建后不允许修改，
     * 内置 Bean 技能不允许编辑，相关校验失败会转换为业务状态码 {@code 400}。</p>
     *
     * @param name 路径中的技能名称，不能为空
     * @param request 技能编辑请求，包含描述、只读标记、API 配置和可选提示词模板绑定
     * @return 统一返回体，数据为编辑后的技能信息
     * @throws BizException 当领域参数校验失败时抛出 {@code 400} 业务异常
     */
    @PutMapping("/{name}")
    public ApiResponse<AiManagedSkill> update(
            @PathVariable String name,
            @RequestBody AiManagedSkillRequest request) {
        return call(() -> managementService.updateApiSkill(name, request));
    }

    /**
     * 更新自定义 API 技能的远程调用配置。
     *
     * <p>请求路径为 {@code PUT /api/biz/ai/skills/{name}/api-config}。该接口只更新 API 地址、
     * HTTP 方法、请求头、超时时间和 API 注册表关联信息，不改变技能名称。</p>
     *
     * @param name 路径中的技能名称，不能为空
     * @param request API 调用配置请求，包含接口路径、方法、请求头和超时时间
     * @return 统一返回体，数据为更新后的技能信息
     * @throws BizException 当技能不存在或参数不合法时抛出 {@code 400} 业务异常
     */
    @PutMapping("/{name}/api-config")
    public ApiResponse<AiManagedSkill> updateApiConfig(
            @PathVariable String name,
            @RequestBody AiSkillApiConfigRequest request) {
        return call(() -> managementService.updateApiSkillConfig(name, request));
    }

    /**
     * 绑定或清空技能提示词模板。
     *
     * <p>请求路径为 {@code PUT /api/biz/ai/skills/{name}/prompt-template}。
     * 请求体中的 {@code promptTemplateId} 为空时表示清空绑定；当技能不存在或未注册时抛出 {@code 404} 业务异常。</p>
     *
     * @param name 路径中的技能名称，不能为空
     * @param request 提示词模板绑定请求，允许为空请求体语义上的空绑定
     * @return 统一返回体，数据为绑定后的技能信息
     * @throws BizException 当技能不存在时抛出 {@code 404} 业务异常；参数不合法时抛出 {@code 400} 业务异常
     */
    @PutMapping("/{name}/prompt-template")
    public ApiResponse<AiManagedSkill> bindPromptTemplate(
            @PathVariable String name,
            @RequestBody(required = false) AiSkillPromptTemplateRequest request) {
        return call(() -> {
            Long promptTemplateId = request == null ? null : request.getPromptTemplateId();
            AiManagedSkill skill = managementService.bindSkillPromptTemplate(name, promptTemplateId);
            if (skill == null) {
                throw new BizException(404, "技能不存在或未注册");
            }
            return skill;
        });
    }

    /**
     * 删除自定义 API 技能。
     *
     * <p>请求路径为 {@code DELETE /api/biz/ai/skills/{name} }。删除成功后管理服务会同步解除智能体技能绑定。
     * 当目标 API 技能不存在时抛出 {@code 404} 业务异常；当目标为内置 Bean 技能时抛出 {@code 400} 业务异常。</p>
     *
     * @param name 路径中的技能名称，不能为空
     * @return 统一返回体，删除成功时不携带业务数据
     * @throws BizException 当技能不存在时抛出 {@code 404} 业务异常；删除内置技能时抛出 {@code 400} 业务异常
     */
    @DeleteMapping("/{name}")
    public ApiResponse<Void> delete(@PathVariable String name) {
        try {
            if (!managementService.deleteApiSkill(name)) {
                throw new BizException(404, "API技能不存在");
            }
            return ApiResponse.ok();
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    private <T> ApiResponse<T> call(ServiceCall<T> call) {
        try {
            return ApiResponse.ok(call.execute());
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    private BizException badRequest(IllegalArgumentException exception) {
        String message = exception.getMessage();
        if (StrUtil.isBlank(message)) {
            message = "请求参数不合法";
        }
        return new BizException(400, message);
    }

    @FunctionalInterface
    private interface ServiceCall<T> {

        T execute();
    }
}
