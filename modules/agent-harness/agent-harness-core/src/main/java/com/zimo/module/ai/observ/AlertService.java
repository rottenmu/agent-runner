package com.zimo.module.ai.observ;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zimo.framework.common.validation.ValidationUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 监控告警服务：告警规则管理 + 事件触发与查询。
 *
 * <p>告警类型：agent_error(Agent 报错)、timeout(超时)、sensitive_word(敏感词)、
 * token_overload(Token 过载)、api_failure(接口调用失败)。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class AlertService {

    /** 默认敏感词列表（示例）。 */
    private static final List<String> SENSITIVE_WORDS = List.of(
            "赌博", "博彩", "裸聊", "洗钱", "毒品", "枪支");

    private final ObservAlertRuleMapper ruleMapper;
    private final ObservAlertEventMapper eventMapper;

    public AlertService(ObservAlertRuleMapper ruleMapper, ObservAlertEventMapper eventMapper) {
        this.ruleMapper = ruleMapper;
        this.eventMapper = eventMapper;
        initSeedRules();
    }

    private void initSeedRules() {
        seedRule("Agent 报错告警", "agent_error", 0, "critical");
        seedRule("调用超时告警", "timeout", 30_000, "warning");
        seedRule("敏感词触发告警", "sensitive_word", 0, "critical");
        seedRule("Token 过载告警", "token_overload", 20_000, "warning");
    }

    private void seedRule(String name, String type, double threshold, String level) {
        if (ruleMapper.selectOne(Wrappers.<ObservAlertRule>lambdaQuery()
                .eq(ObservAlertRule::getType, type)) != null) {
            return;
        }
        ObservAlertRule rule = new ObservAlertRule();
        rule.setName(name);
        rule.setType(type);
        rule.setThreshold(threshold);
        rule.setLevel(level);
        rule.setEnabled(true);
        rule.setCreatedAt(LocalDateTime.now());
        ruleMapper.insert(rule);
    }

    /** 报告事件（匹配启用规则后入库）。 */
    public void report(String type, String message, String agentId) {
        try {
            List<ObservAlertRule> rules = ruleMapper.selectList(Wrappers.<ObservAlertRule>lambdaQuery()
                    .eq(ObservAlertRule::getType, type)
                    .eq(ObservAlertRule::getEnabled, true));
            for (ObservAlertRule rule : rules) {
                ObservAlertEvent event = new ObservAlertEvent();
                event.setRuleId(rule.getId());
                event.setType(type);
                event.setLevel(rule.getLevel());
                event.setMessage(message);
                event.setAgentId(agentId);
                event.setHandled(false);
                event.setCreatedAt(LocalDateTime.now());
                eventMapper.insert(event);
            }
        } catch (Exception ignored) {
        }
    }

    /** 检测文本中的敏感词。 */
    public String findSensitiveWord(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        for (String word : SENSITIVE_WORDS) {
            if (text.contains(word)) {
                return word;
            }
        }
        return null;
    }

    /* ---------------- 查询 ---------------- */

    /** 告警规则列表。 */
    public List<ObservAlertRule> listRules() {
        return ruleMapper.selectList(Wrappers.<ObservAlertRule>lambdaQuery()
                .orderByAsc(ObservAlertRule::getId));
    }

    /** 更新规则。 */
    public ObservAlertRule updateRule(Long id, Map<String, Object> body) {
        ObservAlertRule rule = ruleMapper.selectById(id);
        ValidationUtil.requireNotNull(rule, "规则不存在: ");
        if (body.get("threshold") instanceof Number n) {
            rule.setThreshold(n.doubleValue());
        }
        if (body.get("enabled") != null) {
            rule.setEnabled(Boolean.parseBoolean(String.valueOf(body.get("enabled"))));
        }
        if (body.get("level") != null) {
            rule.setLevel(String.valueOf(body.get("level")));
        }
        ruleMapper.updateById(rule);
        return rule;
    }

    /** 告警事件列表。 */
    public List<ObservAlertEvent> listEvents(String type, String level, Boolean handled, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return eventMapper.selectList(Wrappers.<ObservAlertEvent>lambdaQuery()
                .eq(StringUtils.hasText(type), ObservAlertEvent::getType, type)
                .eq(StringUtils.hasText(level), ObservAlertEvent::getLevel, level)
                .eq(handled != null, ObservAlertEvent::getHandled, handled)
                .orderByDesc(ObservAlertEvent::getId)
                .last("LIMIT " + safeLimit));
    }

    /** 处理告警事件。 */
    public boolean handleEvent(Long id) {
        ObservAlertEvent event = eventMapper.selectById(id);
        if (event == null) {
            return false;
        }
        event.setHandled(true);
        eventMapper.updateById(event);
        return true;
    }
}
