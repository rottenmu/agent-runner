package com.zimo.module.ai.controller;

import com.zimo.framework.common.ApiResponse;
import com.zimo.module.ai.management.AiAgentCapabilityService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能体能力配置接口（目标拆解 / 意图识别 / 多轮澄清 / 参数抽取 / 问答记忆）+ 角色模板库。
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@RestController
@RequestMapping("/api/biz/ai")
public class AiAgentCapabilityController {

    private final AiAgentCapabilityService capabilityService;

    public AiAgentCapabilityController(AiAgentCapabilityService capabilityService) {
        this.capabilityService = capabilityService;
    }

    /**
     * 获取智能体能力配置（无记录时返回默认配置）。
     *
     * @param agentId 智能体 ID
     * @return 能力配置
     */
    @GetMapping("/agents/{id}/capability")
    public ApiResponse<Map<String, Object>> getCapability(@PathVariable("id") String agentId) {
        return ApiResponse.ok(capabilityService.getCapability(agentId));
    }

    /**
     * 保存智能体能力配置。
     *
     * @param agentId 智能体 ID
     * @param config  能力配置 Map
     * @return 保存后的配置
     */
    @PutMapping("/agents/{id}/capability")
    public ApiResponse<Map<String, Object>> saveCapability(
            @PathVariable("id") String agentId,
            @RequestBody Map<String, Object> config) {
        capabilityService.saveCapability(agentId, config);
        return ApiResponse.ok(capabilityService.getCapability(agentId));
    }

    /**
     * 获取预置角色模板库（用于快速填充角色设定）。
     *
     * @return 角色模板列表
     */
    @GetMapping("/role-templates")
    public ApiResponse<List<Map<String, String>>> roleTemplates() {
        return ApiResponse.ok(RoleTemplates.ALL);
    }

    /** 预置角色模板常量。 */
    public static final class RoleTemplates {

        static final List<Map<String, String>> ALL = List.of(
                Map.of("key", "assistant", "name", "通用助手",
                        "description", "通用型 AI 助手，礼貌专业地回答各类问题",
                        "persona", "你是一位专业、耐心的 AI 助手。回答问题时先理解用户意图，给出清晰、准确、结构化的答案；不确定时坦诚说明，并主动询问补充信息。"),
                Map.of("key", "customer-service", "name", "客服专员",
                        "description", "面向客户的售前售后咨询处理",
                        "persona", "你是一位热情的客服专员。始终礼貌友好，快速理解客户问题，提供准确的产品信息和解决方案；遇到无法处理的问题时安抚客户并转交人工。"),
                Map.of("key", "data-analyst", "name", "数据分析师",
                        "description", "数据分析与报表解读专家",
                        "persona", "你是一位严谨的数据分析师。基于给定数据给出客观结论，使用表格或图表辅助说明；明确数据来源和统计口径，避免主观臆断，对异常数据提出合理假设。"),
                Map.of("key", "pm", "name", "项目经理",
                        "description", "项目规划、进度跟踪与风险提醒",
                        "persona", "你是一位经验丰富的项目经理。拆解项目目标为可执行任务，明确负责人与里程碑；定期跟踪进度、识别风险并给出应对方案，沟通简洁、聚焦行动项。"),
                Map.of("key", "sales", "name", "销售顾问",
                        "description", "产品推荐与销售话术支持",
                        "persona", "你是一位洞察力强的销售顾问。通过提问了解客户需求，推荐最合适的产品方案；突出产品价值而非单纯推销，适时提供优惠信息促进成交。"),
                Map.of("key", "tech-support", "name", "技术支持",
                        "description", "技术问题排查与解决方案",
                        "persona", "你是一位资深技术支持工程师。按步骤引导用户排查问题，先收集现象与日志，再给出解决方案；描述技术细节时兼顾通俗易懂，最后总结预防措施。"),
                Map.of("key", "hr", "name", "HR 助理",
                        "description", "招聘流程、制度解答与员工服务",
                        "persona", "你是一位专业的 HR 助理。解答招聘、考勤、福利等制度问题，流程说明清晰准确；涉及隐私信息时严格遵守保密原则，态度亲和、措辞得体。"),
                Map.of("key", "writer", "name", "内容创作",
                        "description", "文案、文章与创意内容撰写",
                        "persona", "你是一位创意丰富的文案写手。根据主题和目标受众创作吸引人的内容，文风可调、结构清晰，注意标题吸引力与段落节奏，输出前检查错别字与逻辑。"));
    }
}
