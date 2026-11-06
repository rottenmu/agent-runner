package com.zimo.module.ai.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 主管节点的回执归一与汇总。
 *
 * <p>所属模块：agent-harness 运行时内核。把各 worker 的执行结果统一成"回执"结构，
 * 再汇总成主管节点的输出，并给出本次编排是否发生冲突。</p>
 *
 * <h2>为什么冲突要由回执状态推导，而不是"任一 worker 抛异常就整图失败"</h2>
 * <p>现有 {@code MultiAgentCollaborationService} 的语义是：子任务存在失败/被驳回时，
 * 父任务进入 {@code conflict} <b>等待人工处理</b>，其余子任务结果<b>照常保留</b>。
 * 若这里改成抛异常，主管节点会连带丢掉其他 worker 已经产出的结果 —— 那等于把
 * "部分成功"降级成"全盘失败"，与既有语义相反，也让复核人拿不到可对比的材料。</p>
 *
 * <h2>回执状态词表</h2>
 * <ul>
 *   <li>{@code success}：worker 正常完成；</li>
 *   <li>{@code conflict}：worker 自报结果冲突（多方结论不一致，需裁决）；</li>
 *   <li>{@code denied}：被闸门拒绝（权限 / 敏感词 / 意图拒绝），属"正确的拒绝"；</li>
 *   <li>{@code failed}：执行失败或异常。</li>
 * </ul>
 * <p>除 {@code success} 外都计入冲突集合，与工单式协作"存在失败子任务即 conflict"一致。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public class SupervisorAggregator {

    /** 回执/汇总的成功状态。 */
    public static final String STATUS_SUCCESS = "success";

    /** 回执/汇总的冲突状态。 */
    public static final String STATUS_CONFLICT = "conflict";

    /** 回执的拒绝状态（闸门拒绝）。 */
    public static final String STATUS_DENIED = "denied";

    /** 回执的失败状态。 */
    public static final String STATUS_FAILED = "failed";

    /**
     * 归一单个 worker 的执行结果。
     *
     * @param nodeId worker 节点 ID
     * @param nodeType worker 节点类型
     * @param output worker 输出，可为 null
     * @param error 异常信息；非空时直接判 failed（异常优先于输出）
     * @return 回执：nodeId/nodeType/status/output(/error)
     */
    public Map<String, Object> receipt(String nodeId, String nodeType, Object output, String error) {
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("nodeId", nodeId);
        receipt.put("nodeType", nodeType == null ? "" : nodeType);
        receipt.put("status", statusOf(output, error));
        receipt.put("output", output == null ? Map.of() : output);
        if (StringUtils.hasText(error)) {
            receipt.put("error", error);
        }
        return receipt;
    }

    /**
     * 汇总回执，生成主管节点输出。
     *
     * @param receipts 各 worker 回执（保持调用顺序，便于前端按序展示）
     * @return 主管节点输出：status（success|conflict）/ total / succeeded / receipts / conflicts
     */
    public Map<String, Object> summarize(List<Map<String, Object>> receipts) {
        List<Map<String, Object>> conflicts = new ArrayList<>();
        int succeeded = 0;
        for (Map<String, Object> receipt : receipts) {
            if (STATUS_SUCCESS.equals(receipt.get("status"))) {
                succeeded++;
            } else {
                conflicts.add(conflictBrief(receipt));
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", conflicts.isEmpty() ? STATUS_SUCCESS : STATUS_CONFLICT);
        summary.put("total", receipts.size());
        summary.put("succeeded", succeeded);
        summary.put("receipts", receipts);
        summary.put("conflicts", conflicts);
        return summary;
    }

    /**
     * 判断 worker 输出里是否已自报冲突。
     *
     * <p>供节点执行器使用：{@code task} 节点的 {@code taskType=conflict} 会产出
     * {@code status=conflict} 的输出，从而在不依赖外部服务的情况下复现冲突语义。</p>
     *
     * @param output 节点输出
     * @return 自报冲突返回 true
     */
    public boolean selfReportedConflict(Object output) {
        return output instanceof Map<?, ?> map && STATUS_CONFLICT.equals(map.get("status"));
    }

    /** 从回执中提取冲突摘要（去掉完整 output，避免汇总爆炸）。 */
    private Map<String, Object> conflictBrief(Map<String, Object> receipt) {
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("nodeId", receipt.get("nodeId"));
        brief.put("status", receipt.get("status"));
        brief.put("error", receipt.get("error") == null ? "" : receipt.get("error"));
        return brief;
    }

    /**
     * 由输出推导回执状态：异常优先，其次输出里的 status，最后按成功处理。
     *
     * <p>{@code guarded} 归入 failed：工具无权限时图应把它当作"这一步没做成"，
     * 而不是"成功返回了一个空结果"。</p>
     */
    private String statusOf(Object output, String error) {
        if (StringUtils.hasText(error)) {
            return STATUS_FAILED;
        }
        if (output instanceof Map<?, ?> map) {
            Object raw = map.get("status");
            if (raw != null) {
                String status = String.valueOf(raw);
                return switch (status) {
                    case STATUS_CONFLICT -> STATUS_CONFLICT;
                    case STATUS_DENIED -> STATUS_DENIED;
                    case STATUS_FAILED, "guarded", "error" -> STATUS_FAILED;
                    default -> STATUS_SUCCESS;
                };
            }
        }
        return STATUS_SUCCESS;
    }
}
