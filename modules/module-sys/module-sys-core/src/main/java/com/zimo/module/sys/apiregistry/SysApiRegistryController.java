package com.zimo.module.sys.apiregistry;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.zimo.framework.common.ApiResponse;
import com.zimo.module.sys.annotation.HasPerm;
import java.util.List;
import java.util.Objects;

import com.zimo.framework.common.BizException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统管理模块 API 注册信息管理接口。
 *
 * <p>接口统一前缀为 {@code /api/biz/sys/api-registry}。查询接口要求
 * {@code sys:api:list} 权限，状态修改接口要求 {@code sys:api:update} 权限；
 * 所有响应均使用平台统一返回体 {@link ApiResponse}。本控制器由系统模块自动装配层显式注册。</p>
 *
 * @author Codex
 * @since 2026-07-21
 */
@RestController
@RequestMapping("/api/biz/sys/api-registry")
public class SysApiRegistryController {

    private final SysApiRegistryService service;

    /**
     * 创建 API 注册信息管理控制器。
     *
     * @param service API 注册信息业务服务，不允许为 {@code null}
     * @throws NullPointerException 当业务服务为 {@code null} 时抛出
     */
    public SysApiRegistryController(SysApiRegistryService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    /**
     * 查询 API 注册信息列表。
     *
     * <p>请求路径：{@code GET /api/biz/sys/api-registry}。所有条件均可选，未传状态时返回
     * 所有未逻辑删除状态；HTTP 方法由数据访问层按大写匹配。调用方必须拥有
     * {@code sys:api:list} 权限。</p>
     *
     * @param moduleCode 业务模块标识，允许为空
     * @param method HTTP 请求方法，允许为空且大小写不敏感
     * @param status 注册表状态，允许为空；{@code 0} 停用、{@code 1} 启用、{@code 2} 草稿
     * @param keyword 路径、接口名、摘要或 Controller 名称关键字，允许为空
     * @return 统一返回体，其中数据为符合条件的 API 注册信息列表
     */
    @GetMapping
    @SaCheckPermission("sys:api:list")
    @HasPerm("sys:api:list")
    public ApiResponse<List<SysApiRegistryItem>> list(
            @RequestParam(required = false) String moduleCode,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.list(new SysApiRegistryQuery(moduleCode, method, status, keyword)));
    }

    /**
     * 查询按业务模块分组的 API 注册信息。
     *
     * <p>请求路径：{@code GET /api/biz/sys/api-registry/grouped}。筛选规则与列表接口一致，
     * 模块及组内接口保持注册表排序。调用方必须拥有 {@code sys:api:list} 权限。</p>
     *
     * @param moduleCode 业务模块标识，允许为空
     * @param method HTTP 请求方法，允许为空且大小写不敏感
     * @param status 注册表状态，允许为空；{@code 0} 停用、{@code 1} 启用、{@code 2} 草稿
     * @param keyword 路径、接口名、摘要或 Controller 名称关键字，允许为空
     * @return 统一返回体，其中数据为按业务模块分组的 API 注册信息
     */
    @GetMapping("/grouped")
    @SaCheckPermission("sys:api:list")
    @HasPerm("sys:api:list")
    public ApiResponse<List<SysApiRegistryModuleGroup>> grouped(
            @RequestParam(required = false) String moduleCode,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.grouped(new SysApiRegistryQuery(moduleCode, method, status, keyword)));
    }

    /**
     * 修改指定 API 注册信息的展示状态。
     *
     * <p>请求路径：{@code PUT /api/biz/sys/api-registry/{id}/status}。仅允许设置停用
     * {@code 0} 或启用 {@code 1}；该操作不影响真实接口调用。调用方必须拥有
     * {@code sys:api:update} 权限。</p>
     *
     * @param id API 注册表主键 ID，必须大于 {@code 0}
     * @param request 状态修改请求，状态只允许为 {@code 0} 或 {@code 1}
     * @return 不携带业务数据的成功统一返回体
     * @throws BizException 当 ID、状态非法或注册信息不存在时抛出
     */
    @PutMapping("/{id}/status")
    @SaCheckPermission("sys:api:update")
    @HasPerm("sys:api:update")
    public ApiResponse<Void> updateStatus(
            @PathVariable Long id,
            @RequestBody SysApiRegistryStatusUpdateRequest request) {
        service.updateStatus(id, request == null ? null : request.status());
        return ApiResponse.ok();
    }
}