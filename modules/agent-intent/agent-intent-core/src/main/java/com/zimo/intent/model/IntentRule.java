package com.zimo.intent.model;

import java.util.List;
import java.util.Map;

/**
 * 意图识别规则（对应 intent-rules.json 单条规则）。
 *
 * @author WorkBuddy
 * @since 2026-08-14
 */
public class IntentRule {

    private String intentCode;
    private String intentName;
    private String intentDesc;
    private List<String> triggerKeywords;
    private List<Map<String, String>> requiredSlots;
    private List<Map<String, String>> optionalSlots;
    private List<String> supportTool;
    private double confidenceThreshold = 0.8;
    private String routeStrategy;
    private String rejectRule;

    public String getIntentCode() {
        return intentCode;
    }

    public void setIntentCode(String intentCode) {
        this.intentCode = intentCode;
    }

    public String getIntentName() {
        return intentName;
    }

    public void setIntentName(String intentName) {
        this.intentName = intentName;
    }

    public String getIntentDesc() {
        return intentDesc;
    }

    public void setIntentDesc(String intentDesc) {
        this.intentDesc = intentDesc;
    }

    public List<String> getTriggerKeywords() {
        return triggerKeywords;
    }

    public void setTriggerKeywords(List<String> triggerKeywords) {
        this.triggerKeywords = triggerKeywords;
    }

    public List<Map<String, String>> getRequiredSlots() {
        return requiredSlots;
    }

    public void setRequiredSlots(List<Map<String, String>> requiredSlots) {
        this.requiredSlots = requiredSlots;
    }

    public List<Map<String, String>> getOptionalSlots() {
        return optionalSlots;
    }

    public void setOptionalSlots(List<Map<String, String>> optionalSlots) {
        this.optionalSlots = optionalSlots;
    }

    public List<String> getSupportTool() {
        return supportTool;
    }

    public void setSupportTool(List<String> supportTool) {
        this.supportTool = supportTool;
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }

    public String getRouteStrategy() {
        return routeStrategy;
    }

    public void setRouteStrategy(String routeStrategy) {
        this.routeStrategy = routeStrategy;
    }

    public String getRejectRule() {
        return rejectRule;
    }

    public void setRejectRule(String rejectRule) {
        this.rejectRule = rejectRule;
    }
}
