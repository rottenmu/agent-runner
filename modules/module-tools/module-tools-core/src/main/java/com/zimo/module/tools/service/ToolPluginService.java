package com.zimo.module.tools.service;

import com.zimo.module.tools.entity.ToolDefinition;
import com.zimo.module.tools.entity.ToolPlugin;
import com.zimo.module.tools.mapper.ToolDefinitionMapper;
import com.zimo.module.tools.mapper.ToolPluginMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.tools.govern.GenericHttpExecutor;
import com.zimo.module.tools.govern.ToolRegistry;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 插件市场服务：预置行业插件、插件启用/停用（注册/注销工具）、插件工具管理。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class ToolPluginService {

    private final ToolPluginMapper pluginMapper;
    private final ToolDefinitionMapper definitionMapper;
    private final ToolRegistry registry;
    private final ObjectMapper objectMapper;
    private final String pluginBaseUrl;

    public ToolPluginService(ToolPluginMapper pluginMapper,
                             ToolDefinitionMapper definitionMapper,
                             ToolRegistry registry,
                             ObjectMapper objectMapper,
                             String pluginBaseUrl) {
        this.pluginMapper = pluginMapper;
        this.definitionMapper = definitionMapper;
        this.registry = registry;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.pluginBaseUrl = StringUtils.hasText(pluginBaseUrl) ? pluginBaseUrl : "http://localhost:9900";
    }

    /** 初始化：写入预置行业插件（已存在则跳过）。 */
    public void initSeedPlugins() {
        seedPlugin("采购管理", "procurement", "供应商/订单查询与采购审批",
                List.of(
                        tool("procurement_suppliers", "查询供应商列表", "GET", "/api/biz/plugins/procurement/suppliers", false),
                        tool("procurement_order_query", "查询采购订单详情", "GET", "/api/biz/plugins/procurement/orders/{orderId}", false),
                        tool("procurement_approve", "提交采购审批", "POST", "/api/biz/plugins/procurement/approve", false)));
        seedPlugin("财务管理", "finance", "费用报销与发票查询",
                List.of(
                        tool("finance_expense_query", "查询费用报销单", "GET", "/api/biz/plugins/finance/expenses/{expenseId}", false),
                        tool("finance_invoice_query", "查询发票信息", "GET", "/api/biz/plugins/finance/invoices/{invoiceNo}", false)));
        seedPlugin("人事管理", "hr", "员工信息与请假流程",
                List.of(
                        tool("hr_employee_query", "查询员工信息", "GET", "/api/biz/plugins/hr/employees/{empId}", false),
                        tool("hr_leave_apply", "提交请假申请", "POST", "/api/biz/plugins/hr/leave", false)));
        seedPlugin("销售管理", "sales", "客户查询与订单统计",
                List.of(
                        tool("sales_customer_query", "查询客户信息", "GET", "/api/biz/plugins/sales/customers/{customerId}", false),
                        tool("sales_order_stats", "按时间段统计订单", "GET", "/api/biz/plugins/sales/order-stats", false)));
    }

    private void seedPlugin(String name, String code, String description, List<ToolDefinition> tools) {
        ToolPlugin existing = pluginMapper.selectOne(Wrappers.<ToolPlugin>lambdaQuery()
                .eq(ToolPlugin::getCode, code));
        if (existing != null) {
            return;
        }
        ToolPlugin plugin = new ToolPlugin();
        plugin.setName(name);
        plugin.setCode(code);
        plugin.setCategory("industry");
        plugin.setDescription(description);
        plugin.setVersion("1.0.0");
        plugin.setEnabled(false);
        plugin.setConfigJson("{}");
        LocalDateTime now = LocalDateTime.now();
        plugin.setCreatedAt(now);
        plugin.setUpdatedAt(now);
        pluginMapper.insert(plugin);
        for (ToolDefinition definition : tools) {
            definition.setPluginId(plugin.getId());
            definition.setCreatedAt(LocalDateTime.now());
            definitionMapper.insert(definition);
        }
    }

    private ToolDefinition tool(String name, String description, String method, String path, boolean readOnly) {
        ToolDefinition definition = new ToolDefinition();
        definition.setName(name);
        definition.setDescription(description);
        definition.setType("http");
        definition.setMethod(method);
        definition.setTarget(path);
        definition.setParamsSchema("{}");
        definition.setDefaultAllow(false);
        definition.setEnabled(true);
        return definition;
    }

    /* ---------------- 插件管理 ---------------- */

    /** 插件列表（含工具数）。 */
    public List<Map<String, Object>> listPlugins() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolPlugin plugin : pluginMapper.selectList(Wrappers.<ToolPlugin>lambdaQuery()
                .orderByDesc(ToolPlugin::getId))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", plugin.getId());
            item.put("name", plugin.getName());
            item.put("code", plugin.getCode());
            item.put("category", plugin.getCategory());
            item.put("description", plugin.getDescription());
            item.put("version", plugin.getVersion());
            item.put("enabled", plugin.getEnabled());
            item.put("createdAt", plugin.getCreatedAt());
            item.put("toolCount", definitionMapper.selectCount(Wrappers.<ToolDefinition>lambdaQuery()
                    .eq(ToolDefinition::getPluginId, plugin.getId())));
            result.add(item);
        }
        return result;
    }

    /** 插件工具列表。 */
    public List<ToolDefinition> pluginTools(Long pluginId) {
        return definitionMapper.selectList(Wrappers.<ToolDefinition>lambdaQuery()
                .eq(ToolDefinition::getPluginId, pluginId)
                .orderByAsc(ToolDefinition::getId));
    }

    /** 启用插件：注册工具执行器（HTTP 业务接口）。 */
    public boolean enablePlugin(Long pluginId) {
        ToolPlugin plugin = require(pluginId);
        if (Boolean.TRUE.equals(plugin.getEnabled())) {
            return true;
        }
        registerPluginTools(plugin);
        plugin.setEnabled(true);
        plugin.setUpdatedAt(LocalDateTime.now());
        pluginMapper.updateById(plugin);
        return true;
    }

    /** 启动恢复：注册所有已启用插件的工具（重启后内存注册表恢复）。 */
    public void restoreEnabled() {
        for (ToolPlugin plugin : pluginMapper.selectList(Wrappers.<ToolPlugin>lambdaQuery()
                .eq(ToolPlugin::getEnabled, true))) {
            registerPluginTools(plugin);
        }
    }

    private void registerPluginTools(ToolPlugin plugin) {
        for (ToolDefinition definition : pluginTools(plugin.getId())) {
            registry.register(new GenericHttpExecutor(
                    definition.getName(),
                    definition.getDescription(),
                    definition.getMethod(),
                    pluginBaseUrl,
                    definition.getTarget(),
                    Boolean.TRUE.equals(definition.getDefaultAllow()) && "GET".equals(definition.getMethod())));
        }
    }

    /** 停用插件：注销工具执行器。 */
    public boolean disablePlugin(Long pluginId) {
        ToolPlugin plugin = require(pluginId);
        for (ToolDefinition definition : pluginTools(pluginId)) {
            registry.unregister(definition.getName());
        }
        plugin.setEnabled(false);
        plugin.setUpdatedAt(LocalDateTime.now());
        pluginMapper.updateById(plugin);
        return true;
    }

    /** 更新插件 baseUrl 配置。 */
    public ToolPlugin updateConfig(Long pluginId, Map<String, Object> config) {
        ToolPlugin plugin = require(pluginId);
        try {
            plugin.setConfigJson(objectMapper.writeValueAsString(config == null ? Map.of() : config));
        } catch (Exception e) {
            plugin.setConfigJson("{}");
        }
        plugin.setUpdatedAt(LocalDateTime.now());
        pluginMapper.updateById(plugin);
        return plugin;
    }

    private ToolPlugin require(Long id) {
        ToolPlugin plugin = pluginMapper.selectById(id);
        if (plugin == null) {
            throw new IllegalArgumentException("插件不存在: " + id);
        }
        return plugin;
    }
}
