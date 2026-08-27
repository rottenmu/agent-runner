package com.zimo.intent.service;

import com.zimo.intent.IntentProperties;
import com.zimo.intent.model.IntentRule;
import cn.hutool.core.util.StrUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图实体抽取器：触发词命中统计、槽位实体抽取、Schema 白名单过滤与缺参判定。
 *
 * <p>全部词表与正则均可通过 {@link IntentProperties} 配置外置：
 * 实体词表为空时使用内置默认词表；必填槽位豁免名单（默认 timeRange）缺失不触发追问；
 * 单据号抽取正则可配置，置空则不抽取 orderId 槽位。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-14
 */
public class IntentEntityExtractor {

    private final IntentProperties properties;

    public IntentEntityExtractor(IntentProperties properties) {
        this.properties = properties == null ? new IntentProperties() : properties;
    }

    /** 统计规则触发词命中的词条数；形如 "今天/本周/本月" 的分词列表按一组计一次命中。 */
    public int hitCount(IntentRule rule, String normalized) {
        int hits = 0;
        if (rule.getTriggerKeywords() == null) {
            return 0;
        }
        for (String keyword : rule.getTriggerKeywords()) {
            if (StrUtil.isBlank(keyword)) {
                continue;
            }
            String key = keyword.toLowerCase();
            if (key.contains("/")) {
                for (String part : key.split("/")) {
                    if (StrUtil.isNotBlank(part) && normalized.contains(part)) {
                        hits++;
                        break;
                    }
                }
            } else if (normalized.contains(key)) {
                hits++;
            }
        }
        return hits;
    }

    /** 规则触发词是否至少命中一条。 */
    public boolean hitAny(IntentRule rule, String normalized) {
        return hitCount(rule, normalized) > 0;
    }

    /** 是否命中导出强信号词（命中任一即优先判定导出意图）。 */
    public boolean hitStrongSignal(String normalized) {
        for (String word : properties.getExportStrongSignal()) {
            if (StrUtil.isNotBlank(word) && normalized.contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /** 从用户输入抽取规则适用槽位的实体值；orderId 使用可配置正则抽取。 */
    public Map<String, String> extractEntities(IntentRule rule, String text) {
        Map<String, String> entities = new LinkedHashMap<>();
        String lower = text.toLowerCase();
        String intentCode = rule.getIntentCode();
        Map<String, List<String>> intentMap = properties.getEntityIntents();

        // 时间范围（适用于配置的所有意图）
        if (appliesTo(intentMap, "timeRange", intentCode)) {
            putAllMatches(entities, "timeRange", words("timeRange"), lower);
        }
        // 单据类型 / 操作类型 / 单据号（ORDER_OPERATE）
        if (appliesTo(intentMap, "orderType", intentCode)) {
            putAllMatches(entities, "orderType", words("orderType"), lower);
        }
        if (appliesTo(intentMap, "operationType", intentCode)) {
            putAllMatches(entities, "operationType", words("operationType"), lower);
        }
        if (appliesTo(intentMap, "orderId", intentCode)) {
            extractOrderId(entities, text);
        }
        // 数据查询对象 / 导出格式
        if (appliesTo(intentMap, "dataType", intentCode)) {
            putAllMatches(entities, "dataType", words("dataType"), lower);
        }
        if (appliesTo(intentMap, "exportFormat", intentCode)) {
            putAllMatches(entities, "exportFormat", words("exportFormat"), lower);
        }
        // FAQ 主题
        if (appliesTo(intentMap, "topic", intentCode)) {
            putAllMatches(entities, "topic", words("topic"), lower);
        }
        return entities;
    }

    /** 计算规则缺失的必填槽位；豁免名单内槽位（默认 timeRange）缺失不触发追问。 */
    public List<String> missingSlots(IntentRule rule, Map<String, String> entities) {
        List<String> missing = new java.util.ArrayList<>();
        if (rule == null || rule.getRequiredSlots() == null) {
            return missing;
        }
        for (Map<String, String> slot : rule.getRequiredSlots()) {
            String name = slot.get("name");
            if (StrUtil.isBlank(name) || properties.getExemptSlots().contains(name)) {
                continue;
            }
            if (!entities.containsKey(name)) {
                missing.add(name);
            }
        }
        return missing;
    }

    /** 实体 Schema 白名单过滤（防 LLM 幻觉生成不存在的参数）。 */
    public Map<String, String> filterBySchema(Map<String, String> entities) {
        if (entities == null || entities.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, String> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entities.entrySet()) {
            if (isKnownSlot(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    /** 槽位是否已注册：配置化槽位意图映射、词表或已配置抽取正则的单据号槽位。 */
    public boolean isKnownSlot(String slot) {
        if (StrUtil.isBlank(slot)) {
            return false;
        }
        if (properties.getEntityIntents().containsKey(slot)) {
            return true;
        }
        if (properties.getEntityWords().containsKey(slot.toLowerCase())) {
            return true;
        }
        return "orderId".equals(slot) && StrUtil.isNotBlank(properties.getOrderIdPattern());
    }

    /* ---------------- 内部实现 ---------------- */

    /** 按可配置正则抽取单据号；正则非法或置空时跳过。 */
    private void extractOrderId(Map<String, String> entities, String text) {
        String pattern = properties.getOrderIdPattern();
        if (StrUtil.isBlank(pattern)) {
            return;
        }
        try {
            Matcher matcher = Pattern.compile(pattern).matcher(text);
            if (matcher.find()) {
                entities.putIfAbsent("orderId", matcher.group());
            }
        } catch (Exception e) {
            // 非法正则不阻断解析流程
        }
    }

    private boolean appliesTo(Map<String, List<String>> intentMap, String slot, String intentCode) {
        List<String> codes = intentMap.get(slot);
        return codes != null && codes.contains(intentCode);
    }

    private void putAllMatches(Map<String, String> entities, String slot, Map<String, String> words, String lower) {
        for (Map.Entry<String, String> entry : words.entrySet()) {
            if (lower.contains(entry.getKey().toLowerCase())) {
                entities.putIfAbsent(slot, entry.getValue());
            }
        }
    }

    /** 槽位词表：配置优先（槽位小写 key，逗号分隔，支持 词=值），缺失时使用内置默认词表 */
    private Map<String, String> words(String slot) {
        String configured = properties.getEntityWords().get(slot.toLowerCase());
        if (StrUtil.isNotBlank(configured)) {
            Map<String, String> words = new LinkedHashMap<>();
            for (String item : configured.split(",")) {
                if (StrUtil.isBlank(item)) {
                    continue;
                }
                String trimmed = item.trim();
                int eq = trimmed.indexOf('=');
                if (eq > 0) {
                    words.putIfAbsent(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
                } else {
                    words.putIfAbsent(trimmed, trimmed);
                }
            }
            if (!words.isEmpty()) {
                return words;
            }
        }
        return defaultWords(slot);
    }

    private Map<String, String> defaultWords(String slot) {
        switch (slot) {
            case "timeRange":
                Map<String, String> time = new LinkedHashMap<>();
                time.put("本周", "本周");
                time.put("本月", "本月");
                time.put("上周", "上周");
                time.put("上月", "上月");
                time.put("今天", "今天");
                time.put("今日", "今天");
                time.put("昨天", "昨天");
                time.put("今年", "今年");
                time.put("近7天", "近7天");
                time.put("近30天", "近30天");
                time.put("最近一周", "近7天");
                time.put("最近一个月", "近30天");
                return time;
            case "dataType":
                Map<String, String> data = new LinkedHashMap<>();
                data.put("订单", "订单");
                data.put("产量", "产量");
                data.put("工单", "工单");
                data.put("库存", "库存");
                data.put("设备", "设备");
                data.put("质量", "质量");
                data.put("报表", "报表");
                data.put("客户", "客户");
                return data;
            case "exportFormat":
                Map<String, String> format = new LinkedHashMap<>();
                format.put("excel", "excel");
                format.put("xlsx", "excel");
                format.put("xls", "excel");
                format.put("csv", "csv");
                format.put("pdf", "pdf");
                return format;
            case "orderType":
                Map<String, String> order = new LinkedHashMap<>();
                order.put("采购单", "采购单");
                order.put("生产单", "生产单");
                order.put("订单", "订单");
                order.put("领料单", "领料单");
                order.put("入库单", "入库单");
                order.put("出库单", "出库单");
                order.put("质检单", "质检单");
                return order;
            case "operationType":
                Map<String, String> op = new LinkedHashMap<>();
                op.put("撤销", "撤销");
                op.put("作废", "撤销");
                op.put("提交", "提交");
                op.put("修改", "修改");
                op.put("编辑", "修改");
                op.put("新增", "新增");
                op.put("创建", "新增");
                op.put("新建", "新增");
                return op;
            case "topic":
                Map<String, String> topic = new LinkedHashMap<>();
                topic.put("请假", "请假");
                topic.put("报销", "报销");
                topic.put("审批", "审批");
                topic.put("入职", "入职");
                topic.put("离职", "离职");
                topic.put("采购", "采购流程");
                topic.put("退货", "退货");
                topic.put("开票", "开票");
                topic.put("盘点", "盘点");
                topic.put("排班", "排班");
                topic.put("加班", "加班");
                topic.put("调休", "调休");
                return topic;
            default:
                return Map.of();
        }
    }
}
