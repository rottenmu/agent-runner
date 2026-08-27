package com.zimo.module.ds.controller;

import com.zimo.module.ds.connector.DsConnector;
import com.zimo.module.ds.entity.DsDataSource;
import com.zimo.module.ds.service.DsDataSourceService;
import com.zimo.framework.common.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 多数据源管理接口。
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
@RestController
@RequestMapping("/api/biz/ds/datasources")
public class DsDataSourceController {

    private final DsDataSourceService service;

    public DsDataSourceController(DsDataSourceService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<DsDataSource>> list() {
        return ApiResponse.ok(service.list());
    }

    /** 支持的数据源类型目录。 */
    @GetMapping("/types")
    public ApiResponse<List<Map<String, String>>> types() {
        return ApiResponse.ok(service.types());
    }

    @PostMapping
    public ApiResponse<DsDataSource> create(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(service.create(
                body.get("name"), body.get("type"), body.get("description"), body.get("configJson")));
    }

    @PutMapping("/{id}")
    public ApiResponse<DsDataSource> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(service.update(
                id, body.get("name"), body.get("type"), body.get("description"), body.get("configJson")));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    /** 测试连接。 */
    @PostMapping("/{id}/test")
    public ApiResponse<String> test(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.test(id, body));
    }

    /** 预览数据。 */
    @PostMapping("/{id}/preview")
    public ApiResponse<DsConnector.DsDataPreview> preview(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.preview(id, body));
    }
}
