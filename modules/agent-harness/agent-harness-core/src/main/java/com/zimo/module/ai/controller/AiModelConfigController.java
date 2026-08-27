package com.zimo.module.ai.controller;

import com.zimo.framework.common.ApiResponse;
import com.zimo.module.ai.modelconfig.AiModelConfigImportRequest;
import com.zimo.module.ai.modelconfig.AiModelConfigImportResponse;
import com.zimo.module.ai.modelconfig.AiModelConfigQuery;
import com.zimo.module.ai.modelconfig.AiModelConfigRequest;
import com.zimo.module.ai.modelconfig.AiModelConfigResponse;
import com.zimo.module.ai.modelconfig.AiModelConfigService;
import com.zimo.module.ai.modelconfig.AiModelConfigTestResponse;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 模型配置管理控制器。
 *
 * <p>本控制器属于 module-ai 业务插件，统一对外暴露
 * {@code /api/biz/ai/model-configs} 真实后端路径。接口只负责 HTTP 路径适配和
 * {@link ApiResponse} 统一返回体封装，模型配置的参数校验、密钥保留、响应脱敏和测试状态维护由
 * {@link AiModelConfigService} 承担。当前接口默认纳入平台鉴权链路，具体权限标识由系统
 * API 注册表和菜单权限统一配置。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
@RestController
@RequestMapping("/api/biz/ai/model-configs")
public class AiModelConfigController {

    private final AiModelConfigService service;

    /**
     * 创建模型配置管理控制器。
     *
     * @param service 模型配置业务服务，不能为空
     * @throws NullPointerException 当模型配置业务服务为空时抛出
     */
    public AiModelConfigController(AiModelConfigService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    /**
     * 查询模型配置列表。
     *
     * <p>请求路径为 {@code GET /api/biz/ai/model-configs}。支持按运行环境、供应商、
     * 启停状态和关键字过滤；其中 {@code status} 使用字符串契约，仅允许前端传递
     * {@code enabled} 或 {@code disabled}。</p>
     *
     * @param env 运行环境过滤条件，允许为空
     * @param provider 模型供应商过滤条件，允许为空
     * @param status 启停状态过滤条件，允许 enabled/disabled 或为空
     * @param keyword 配置名称或模型 ID 关键字，允许为空
     * @return 统一返回体，数据为已脱敏的模型配置列表
     */
    @GetMapping
    public ApiResponse<List<AiModelConfigResponse>> list(
            @RequestParam(required = false) String env,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.list(query(env, provider, status, keyword)));
    }

    /**
     * 查询单个模型配置详情。
     *
     * <p>请求路径为 {@code GET /api/biz/ai/model-configs/{id}}。响应永远只包含
     * {@code apiKeyMasked}，不返回明文 {@code apiKey}。</p>
     *
     * @param id 模型配置主键，必须大于 0
     * @return 统一返回体，数据为已脱敏的模型配置详情
     */
    @GetMapping("/{id}")
    public ApiResponse<AiModelConfigResponse> get(@PathVariable long id) {
        return ApiResponse.ok(service.get(id));
    }

    /**
     * 新增模型配置。
     *
     * <p>请求路径为 {@code POST /api/biz/ai/model-configs}。新增时配置名称、供应商、
     * 服务地址、API Key、模型 ID 和运行环境必填。</p>
     *
     * @param request 模型配置新增请求体
     * @return 统一返回体，数据为新增后的已脱敏模型配置
     */
    @PostMapping
    public ApiResponse<AiModelConfigResponse> create(@RequestBody AiModelConfigRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    /**
     * 编辑模型配置。
     *
     * <p>请求路径为 {@code PUT /api/biz/ai/model-configs/{id}}。编辑时
     * {@code apiKey} 为空或传入 {@code ***} 表示保留原密钥。</p>
     *
     * @param id 模型配置主键，必须大于 0
     * @param request 模型配置编辑请求体
     * @return 统一返回体，数据为编辑后的已脱敏模型配置
     */
    @PutMapping("/{id}")
    public ApiResponse<AiModelConfigResponse> update(
            @PathVariable long id,
            @RequestBody AiModelConfigRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    /**
     * 删除模型配置。
     *
     * <p>请求路径为 {@code DELETE /api/biz/ai/model-configs/{id}}。删除由业务服务执行
     * 逻辑删除，成功后返回空数据的统一成功响应。</p>
     *
     * @param id 模型配置主键，必须大于 0
     * @return 统一返回体，删除成功时 data 为空
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    /**
     * 复制模型配置。
     *
     * <p>请求路径为 {@code POST /api/biz/ai/model-configs/{id}/copy}。复制后的配置会
     * 继承模型调用参数，但连接测试状态由业务服务重置为未测试。</p>
     *
     * @param id 源模型配置主键，必须存在
     * @return 统一返回体，数据为复制出的新模型配置
     */
    @PostMapping("/{id}/copy")
    public ApiResponse<AiModelConfigResponse> copy(@PathVariable long id) {
        return ApiResponse.ok(service.copy(id));
    }

    /**
     * 测试模型连接。
     *
     * <p>请求路径为 {@code POST /api/biz/ai/model-configs/{id}/test}。当前服务层使用
     * 模拟连接校验并持久化最近一次测试结果。</p>
     *
     * @param id 模型配置主键，必须存在
     * @return 统一返回体，数据为连接测试状态、耗时和消息
     */
    @PostMapping("/{id}/test")
    public ApiResponse<AiModelConfigTestResponse> testConnection(@PathVariable long id) {
        return ApiResponse.ok(service.testConnection(id));
    }

    /**
     * 批量导入模型配置。
     *
     * <p>请求路径为 {@code POST /api/biz/ai/model-configs/import}。导入结果使用
     * {@code successCount} 和 {@code skippedCount} 表示成功数量与跳过数量。</p>
     *
     * @param request 模型配置批量导入请求体，允许由业务服务处理空列表
     * @return 统一返回体，数据为导入统计结果
     */
    @PostMapping("/import")
    public ApiResponse<AiModelConfigImportResponse> importConfigs(@RequestBody AiModelConfigImportRequest request) {
        return ApiResponse.ok(service.importConfigs(request));
    }

    /**
     * 导出模型配置。
     *
     * <p>请求路径为 {@code GET /api/biz/ai/model-configs/export}。导出接口沿用列表查询
     * 条件，响应同样只返回脱敏后的 {@code apiKeyMasked}。</p>
     *
     * @param env 运行环境过滤条件，允许为空
     * @param provider 模型供应商过滤条件，允许为空
     * @param status 启停状态过滤条件，允许 enabled/disabled 或为空
     * @param keyword 配置名称或模型 ID 关键字，允许为空
     * @return 统一返回体，数据为可导出的已脱敏模型配置列表
     */
    @GetMapping("/export")
    public ApiResponse<List<AiModelConfigResponse>> export(
            @RequestParam(required = false) String env,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.export(query(env, provider, status, keyword)));
    }

    private AiModelConfigQuery query(String env, String provider, String status, String keyword) {
        return new AiModelConfigQuery(env, provider, status, keyword);
    }
}
