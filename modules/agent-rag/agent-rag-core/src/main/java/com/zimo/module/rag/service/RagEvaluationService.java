package com.zimo.module.rag.service;

import com.zimo.module.rag.entity.RagEvaluation;
import com.zimo.module.rag.mapper.RagEvaluationMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 知识库测评服务：测试用例管理 + 检索质量评估（命中率/平均分）。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class RagEvaluationService {

    private final RagEvaluationMapper evaluationMapper;
    private final RagRetrieveService retrieveService;

    public RagEvaluationService(RagEvaluationMapper evaluationMapper, RagRetrieveService retrieveService) {
        this.evaluationMapper = evaluationMapper;
        this.retrieveService = retrieveService;
    }

    /** 测评用例列表。 */
    public List<RagEvaluation> list(Long kbId) {
        return evaluationMapper.selectList(Wrappers.<RagEvaluation>lambdaQuery()
                .eq(kbId != null, RagEvaluation::getKbId, kbId)
                .orderByDesc(RagEvaluation::getId));
    }

    /** 新增测评用例。 */
    public RagEvaluation create(Long kbId, String query, String expectedDoc) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("测评查询不能为空");
        }
        RagEvaluation evaluation = new RagEvaluation();
        evaluation.setKbId(kbId);
        evaluation.setQuery(query.trim());
        evaluation.setExpectedDoc(expectedDoc);
        evaluation.setCreatedAt(LocalDateTime.now());
        evaluationMapper.insert(evaluation);
        return evaluation;
    }

    /** 更新测评用例。 */
    public RagEvaluation update(Long id, String query, String expectedDoc) {
        RagEvaluation evaluation = evaluationMapper.selectById(id);
        if (evaluation == null) {
            throw new IllegalArgumentException("测评用例不存在: " + id);
        }
        if (StringUtils.hasText(query)) {
            evaluation.setQuery(query.trim());
        }
        if (expectedDoc != null) {
            evaluation.setExpectedDoc(expectedDoc);
        }
        evaluationMapper.updateById(evaluation);
        return evaluation;
    }

    /** 删除测评用例。 */
    public boolean delete(Long id) {
        return evaluationMapper.deleteById(id) > 0;
    }

    /**
     * 运行测评：对每条用例执行检索，判断是否命中期望文档。
     *
     * @param kbId 知识库 ID
     * @param topK 每条用例检索条数
     * @return 汇总统计
     */
    public Map<String, Object> run(Long kbId, int topK) {
        List<RagEvaluation> cases = evaluationMapper.selectList(Wrappers.<RagEvaluation>lambdaQuery()
                .eq(RagEvaluation::getKbId, kbId));
        int hits = 0;
        double scoreSum = 0;
        LocalDateTime now = LocalDateTime.now();
        for (RagEvaluation evaluation : cases) {
            List<Map<String, Object>> results = retrieveService.retrieve(
                    evaluation.getQuery(), kbId, null, topK, true);
            boolean hit = false;
            double topScore = 0;
            for (Map<String, Object> result : results) {
                double score = result.get("score") instanceof Number n ? n.doubleValue() : 0;
                if (topScore == 0 || score > topScore) {
                    topScore = score;
                }
                String docName = String.valueOf(result.getOrDefault("docName", ""));
                String content = String.valueOf(result.getOrDefault("content", ""));
                if (matches(evaluation.getExpectedDoc(), docName, content)) {
                    hit = true;
                    break;
                }
            }
            evaluation.setHit(hit);
            evaluation.setHitScore(topScore);
            evaluation.setLastRunAt(now);
            evaluationMapper.updateById(evaluation);
            if (hit) {
                hits++;
            }
            scoreSum += topScore;
        }
        int total = cases.size();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("kbId", kbId);
        summary.put("total", total);
        summary.put("hits", hits);
        summary.put("hitRate", total == 0 ? 0 : Math.round(hits * 10000.0 / total) / 100.0);
        summary.put("avgScore", total == 0 ? 0 : Math.round(scoreSum / total * 10000) / 10000.0);
        summary.put("ranAt", now);
        return summary;
    }

    /** 命中判断：期望文档名出现在结果文档名或内容中。 */
    private boolean matches(String expectedDoc, String docName, String content) {
        if (!StringUtils.hasText(expectedDoc)) {
            return true; // 未设期望则视为命中（只记录分数）
        }
        String expected = expectedDoc.trim();
        if (docName != null && docName.contains(expected)) {
            return true;
        }
        return content != null && content.contains(expected);
    }
}
