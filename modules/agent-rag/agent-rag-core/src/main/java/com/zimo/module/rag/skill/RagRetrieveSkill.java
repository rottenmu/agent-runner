package com.zimo.module.rag.skill;

import com.zimo.starter.ai.skill.AiSkill;
import cn.hutool.core.util.StrUtil;
import com.zimo.starter.ai.skill.AiSkillResult;
import com.zimo.module.rag.service.RagRetrieveService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

/**
 * 知识库检索技能：智能体通过该技能检索 RAG 知识库（支持 Rerank 重排）。
 *
 * <p>参数：{@code query} 查询内容（必填）、{@code topK} 返回条数、{@code rerank} 是否重排、
 * {@code docId} 限定文档。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
public class RagRetrieveSkill implements AiSkill {

    private final RagRetrieveService retrieveService;

    public RagRetrieveSkill(RagRetrieveService retrieveService) {
        this.retrieveService = Objects.requireNonNull(retrieveService, "retrieveService must not be null");
    }

    @Override
    public String name() {
        return "rag_retrieve";
    }

    @Override
    public String description() {
        return "检索 RAG 知识库并返回相关文档片段（支持向量召回 + Rerank 重排）。"
                + "参数：query(查询内容，必填)、topK(返回条数，默认8)、rerank(是否重排，默认true)、"
                + "docId(限定文档ID，可空)。回答需要依据企业知识库/制度文档/业务资料前应先调用本工具。";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public AiSkillResult call(Map<String, Object> arguments) {
        if (arguments == null || !StringUtils.hasText(str(arguments.get("query")))) {
            return AiSkillResult.fail("请提供查询内容（query）");
        }
        String query = str(arguments.get("query"));
        int topK = arguments.get("topK") instanceof Number n ? n.intValue() : 8;
        boolean rerank = arguments.get("rerank") == null
                ? true
                : Boolean.parseBoolean(String.valueOf(arguments.get("rerank")));
        Long docId = null;
        Object docIdValue = arguments.get("docId");
        if (docIdValue != null && StringUtils.hasText(String.valueOf(docIdValue))) {
            docId = Long.valueOf(String.valueOf(docIdValue));
        }
        try {
            List<Map<String, Object>> results = retrieveService.retrieve(query, null, docId, topK, rerank);
            if (results.isEmpty()) {
                return AiSkillResult.ok("知识库中未检索到相关内容");
            }
            StringBuilder builder = new StringBuilder();
            builder.append("知识库检索结果（").append(results.size()).append(" 条，")
                    .append(rerank ? "已 Rerank 重排" : "向量召回").append("）：\n");
            for (int i = 0; i < results.size(); i++) {
                Map<String, Object> result = results.get(i);
                builder.append("\n[").append(i + 1).append("] ").append(result.get("docName"))
                        .append(" #").append(result.get("seq"))
                        .append(" (score=").append(result.get("score")).append(")\n")
                        .append(result.get("content")).append("\n");
            }
            return AiSkillResult.ok(builder.toString());
        } catch (Exception e) {
            return AiSkillResult.fail("知识库检索失败：" + safeMessage(e));
        }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String safeMessage(Throwable exception) {
        String message = exception == null ? "" : exception.getMessage();
        return StrUtil.isBlank(message)
                ? (exception == null ? "未知错误" : exception.getClass().getSimpleName())
                : message;
    }
}
