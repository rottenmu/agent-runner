package com.zimo.module.sys.controller;

import com.zimo.framework.common.ApiResponse;
import com.zimo.module.sys.entity.AgentSetting;
import com.zimo.module.sys.service.AgentSettingService;
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
 * 智能体设置管理接口。
 *
 * <p>提供智能体运行配置（长期记忆参数等）的查询、保存，
 * 以及自定义配置项的创建、编辑、删除能力。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@RestController
@RequestMapping("/api/biz/sys/agent-setting")
public class AgentSettingController {

    private final AgentSettingService settingService;

    public AgentSettingController(AgentSettingService settingService) {
        this.settingService = settingService;
    }

    /**
     * 获取全部预设设置项定义（键、默认值、说明）。
     *
     * @return 设置项定义列表
     */
    @GetMapping("/definitions")
    public ApiResponse<List<AgentSettingService.SettingDefinition>> definitions() {
        return ApiResponse.ok(settingService.listDefinitions());
    }

    /**
     * 获取当前生效配置（默认值 + 数据库覆盖值）。
     *
     * @return 配置键值对
     */
    @GetMapping
    public ApiResponse<Map<String, String>> getSettings() {
        return ApiResponse.ok(settingService.getEffectiveSettings());
    }

    /**
     * 保存预设配置（新增或更新）。
     *
     * @param settings 配置键值对
     * @return 操作结果
     */
    @PutMapping
    public ApiResponse<Void> saveSettings(@RequestBody Map<String, String> settings) {
        settingService.saveSettings(settings);
        return ApiResponse.ok();
    }

    /**
     * 获取全部配置项（含自定义项）。
     *
     * @return 配置项列表
     */
    @GetMapping("/all")
    public ApiResponse<List<AgentSetting>> listAll() {
        return ApiResponse.ok(settingService.list());
    }

    /**
     * 创建自定义配置项。
     *
     * @param request 配置键、值、说明
     * @return 新建的配置项
     */
    @PostMapping
    public ApiResponse<AgentSetting> createSetting(@RequestBody SettingRequest request) {
        return ApiResponse.ok(settingService.createSetting(
                request.configKey(), request.configValue(), request.remark()));
    }

    /**
     * 编辑配置项。
     *
     * @param id 配置项 ID
     * @param request 配置值、说明
     * @return 更新后的配置项
     */
    @PutMapping("/{id}")
    public ApiResponse<AgentSetting> updateSetting(
            @PathVariable Long id,
            @RequestBody SettingRequest request) {
        return ApiResponse.ok(settingService.updateSetting(id, request.configValue(), request.remark()));
    }

    /**
     * 删除配置项（仅自定义项可删除）。
     *
     * @param id 配置项 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSetting(@PathVariable Long id) {
        settingService.deleteSetting(id);
        return ApiResponse.ok();
    }

    /**
     * 设置请求体。
     *
     * @param configKey 配置键
     * @param configValue 配置值
     * @param remark 说明
     */
    public record SettingRequest(String configKey, String configValue, String remark) {
    }
}
