package com.zimo.module.tools.service;

import com.zimo.module.tools.entity.ToolPythonScript;
import com.zimo.module.tools.mapper.ToolPythonScriptMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zimo.module.tools.govern.PythonScriptExecutor;
import com.zimo.module.tools.govern.ToolRegistry;
import com.zimo.framework.ai.sandbox.SandboxBackend;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * 自定义 Python 脚本工具服务：脚本 CRUD + 启用/停用（注册/注销执行器）。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class ToolScriptService {

    private final ToolPythonScriptMapper scriptMapper;
    private final ToolRegistry registry;
    private final String pythonBin;
    private final SandboxBackend sandbox;

    public ToolScriptService(ToolPythonScriptMapper scriptMapper, ToolRegistry registry, String pythonBin) {
        this(scriptMapper, registry, pythonBin, null);
    }

    public ToolScriptService(ToolPythonScriptMapper scriptMapper, ToolRegistry registry, String pythonBin,
                             SandboxBackend sandbox) {
        this.scriptMapper = scriptMapper;
        this.registry = registry;
        this.pythonBin = pythonBin;
        this.sandbox = sandbox;
    }

    /** 脚本列表。 */
    public List<ToolPythonScript> listScripts() {
        return scriptMapper.selectList(Wrappers.<ToolPythonScript>lambdaQuery()
                .orderByDesc(ToolPythonScript::getId));
    }

    /** 启动恢复：注册所有已启用的脚本工具（重启后内存注册表恢复）。 */
    public void restoreEnabled() {
        for (ToolPythonScript entity : scriptMapper.selectList(Wrappers.<ToolPythonScript>lambdaQuery()
                .eq(ToolPythonScript::getEnabled, true))) {
            registry.register(new PythonScriptExecutor(entity.getName(), entity.getDescription(),
                    entity.getScript(), pythonBin, sandbox));
        }
    }

    /** 创建脚本（工具名唯一）。 */
    public ToolPythonScript create(String name, String description, String script) {
        validate(name, script);
        ToolPythonScript entity = new ToolPythonScript();
        entity.setName(name.trim());
        entity.setDescription(description);
        entity.setScript(script);
        entity.setEnabled(false);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        scriptMapper.insert(entity);
        return entity;
    }

    /** 更新脚本（启用状态下自动重注册）。 */
    public ToolPythonScript update(Long id, String name, String description, String script) {
        ToolPythonScript entity = scriptMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("脚本不存在: " + id);
        }
        if (StringUtils.hasText(name)) {
            entity.setName(name.trim());
        }
        if (description != null) {
            entity.setDescription(description);
        }
        if (StringUtils.hasText(script)) {
            entity.setScript(script);
        }
        entity.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(entity);
        if (Boolean.TRUE.equals(entity.getEnabled())) {
            registry.unregister(entity.getName());
            registry.register(new PythonScriptExecutor(entity.getName(), entity.getDescription(),
                    entity.getScript(), pythonBin, sandbox));
        }
        return entity;
    }

    /** 启用/停用脚本。 */
    public boolean setEnabled(Long id, boolean enabled) {
        ToolPythonScript entity = scriptMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("脚本不存在: " + id);
        }
        entity.setEnabled(enabled);
        entity.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(entity);
        if (enabled) {
            registry.register(new PythonScriptExecutor(entity.getName(), entity.getDescription(),
                    entity.getScript(), pythonBin, sandbox));
        } else {
            registry.unregister(entity.getName());
        }
        return true;
    }

    /** 删除脚本。 */
    public boolean delete(Long id) {
        ToolPythonScript entity = scriptMapper.selectById(id);
        if (entity != null && Boolean.TRUE.equals(entity.getEnabled())) {
            registry.unregister(entity.getName());
        }
        return scriptMapper.deleteById(id) > 0;
    }

    private void validate(String name, String script) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("工具名称不能为空");
        }
        if (!StringUtils.hasText(script)) {
            throw new IllegalArgumentException("脚本内容不能为空");
        }
        if (scriptMapper.selectOne(Wrappers.<ToolPythonScript>lambdaQuery()
                .eq(ToolPythonScript::getName, name.trim())) != null) {
            throw new IllegalArgumentException("工具名称已存在: " + name.trim());
        }
    }
}
