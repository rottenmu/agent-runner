package com.zimo.module.ai.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zimo.framework.common.validation.ValidationUtil;
import com.zimo.framework.common.ApiResponse;
import com.zimo.module.ai.management.AiApiDoc;
import com.zimo.module.ai.management.AiApiDocService;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;
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
 * API 接口定义管理接口。
 *
 * <p>提供接口的新增、编辑、删除、启停、查询，以及 YApi 文档格式批量导入能力。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@RestController
@RequestMapping("/api/biz/ai/api-docs")
public class AiApiDocController {

    private final AiApiDocService apiDocService;

    public AiApiDocController(AiApiDocService apiDocService) {
        this.apiDocService = apiDocService;
    }

    /**
     * 查询接口列表（可选关键字搜索）。
     *
     * @param keyword 关键字（匹配名称/路径/分组），可为空
     * @return 接口列表
     */
    @GetMapping
    public ApiResponse<List<AiApiDoc>> list(@RequestParam(required = false) String keyword) {
        List<AiApiDoc> list;
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            list = apiDocService.list(Wrappers.<AiApiDoc>lambdaQuery()
                    .and(w -> w
                            .like(AiApiDoc::getName, like)
                            .or()
                            .like(AiApiDoc::getPath, like)
                            .or()
                            .like(AiApiDoc::getGroupName, like))
                    .orderByDesc(AiApiDoc::getId));
        } else {
            list = apiDocService.list(Wrappers.<AiApiDoc>lambdaQuery()
                    .orderByDesc(AiApiDoc::getId));
        }
        return ApiResponse.ok(list);
    }

    /**
     * 创建接口定义。
     *
     * @param doc 接口定义
     * @return 新建的接口
     */
    @PostMapping
    public ApiResponse<AiApiDoc> create(@RequestBody AiApiDoc doc) {
        validate(doc);
        doc.setId(null);
        if (doc.getSource() == null || doc.getSource().isEmpty()) {
            doc.setSource("manual");
        }
        apiDocService.save(doc);
        return ApiResponse.ok(doc);
    }

    /**
     * 编辑接口定义。
     *
     * @param id  接口 ID
     * @param doc 接口定义
     * @return 更新后的接口
     */
    @PutMapping("/{id}")
    public ApiResponse<AiApiDoc> update(@PathVariable Long id, @RequestBody AiApiDoc doc) {
        AiApiDoc existing = requireDoc(id);
        validate(doc);
        existing.setName(doc.getName());
        existing.setMethod(doc.getMethod());
        existing.setPath(doc.getPath());
        existing.setDescription(doc.getDescription());
        existing.setGroupName(doc.getGroupName());
        existing.setRequestHeaders(doc.getRequestHeaders());
        existing.setRequestParams(doc.getRequestParams());
        existing.setResponseSchema(doc.getResponseSchema());
        existing.setEnabled(doc.getEnabled());
        apiDocService.updateById(existing);
        return ApiResponse.ok(existing);
    }

    /**
     * 启用/禁用接口。
     *
     * @param id     接口 ID
     * @param enabled 是否启用
     * @return 更新后的接口
     */
    @PutMapping("/{id}/enabled")
    public ApiResponse<AiApiDoc> setEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        AiApiDoc existing = requireDoc(id);
        Boolean enabled = body == null ? null : body.get("enabled");
        ValidationUtil.requireNotNull(enabled, "enabled 不能为空");
        existing.setEnabled(enabled);
        apiDocService.updateById(existing);
        return ApiResponse.ok(existing);
    }

    /**
     * 删除接口定义。
     *
     * @param id 接口 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        requireDoc(id);
        apiDocService.removeById(id);
        return ApiResponse.ok();
    }

    /**
     * 批量导入 YApi / OpenAPI 格式接口定义。
     *
     * @param body 请求体，需包含 JSON 文本
     * @return 导入结果（数量 + 接口列表）
     */
    /** cURL bash 导入：解析 curl -X POST https://... -H ... -d ... 为接口定义。 */
    @PostMapping("/import-curl")
    public ApiResponse<Map<String, Object>> importCurl(@RequestBody Map<String, String> body) {
        String curl = body == null ? null : body.get("curl");
        String groupName = body == null ? null : body.get("groupName");
        if (!StringUtils.hasText(curl)) {
            throw new IllegalArgumentException("curl 不能为空");
        }
        List<AiApiDoc> imported = apiDocService.importCurl(curl, groupName);
        return ApiResponse.ok(Map.of(
                "count", imported.size(),
                "items", imported));
    }

    @PostMapping("/import-yapi")
    public ApiResponse<Map<String, Object>> importYApi(@RequestBody Map<String, String> body) {
        String json = body == null ? null : body.get("json");
        if (!StringUtils.hasText(json)) {
            throw new IllegalArgumentException("json 不能为空");
        }
        List<AiApiDoc> imported = apiDocService.importYApi(json);
        return ApiResponse.ok(Map.of(
                "count", imported.size(),
                "items", imported));
    }

    private AiApiDoc requireDoc(Long id) {
        AiApiDoc existing = apiDocService.getById(id);
        ValidationUtil.requireNotNull(existing, "接口不存在: id=");
        return existing;
    }

    private void validate(AiApiDoc doc) {
        ValidationUtil.requireNotNull(doc, "接口定义不能为空");
        if (!StringUtils.hasText(doc.getPath())) {
            throw new IllegalArgumentException("接口路径不能为空");
        }
        if (!StringUtils.hasText(doc.getMethod())) {
            throw new IllegalArgumentException("请求方法不能为空");
        }
        String method = doc.getMethod().toUpperCase();
        switch (method) {
            case "GET":
            case "POST":
            case "PUT":
            case "DELETE":
            case "PATCH":
            case "HEAD":
            case "OPTIONS":
                doc.setMethod(method);
                break;
            default:
                throw new IllegalArgumentException("不支持的请求方法: " + doc.getMethod());
        }
        if (doc.getEnabled() == null) {
            doc.setEnabled(true);
        }
    }
}
