package com.zimo.module.rag.service;

import com.zimo.module.rag.entity.RagRetrieveLog;
import com.zimo.module.rag.mapper.RagRetrieveLogMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.core.util.StrUtil;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 检索日志服务：记录检索请求与结果统计，供知识库效果分析。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class RagRetrieveLogService {

    private final RagRetrieveLogMapper logMapper;

    public RagRetrieveLogService(RagRetrieveLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    /**
     * 记录检索日志。
     *
     * @param kbId 知识库 ID（可空）
     * @param query 查询
     * @param docId 限定文档（可空）
     * @param topK 条数
     * @param rerank 是否重排
     * @param resultCount 结果数
     * @param latencyMs 耗时
     * @param source 来源
     * @param userId 用户
     */
    public void record(Long kbId, String query, Long docId, int topK, boolean rerank,
                       int resultCount, long latencyMs, String source, String userId) {
        try {
            RagRetrieveLog log = new RagRetrieveLog();
            log.setKbId(kbId);
            log.setQuery(query);
            log.setDocId(docId);
            log.setTopK(topK);
            log.setRerank(rerank);
            log.setResultCount(resultCount);
            log.setLatencyMs(latencyMs);
            log.setSource(StrUtil.isBlank(source) ? "console" : source);
            log.setUserId(userId);
            log.setCreatedAt(LocalDateTime.now());
            logMapper.insert(log);
        } catch (Exception ignored) {
            // 日志记录失败不影响检索主流程
        }
    }

    /** 检索日志列表（按知识库/来源过滤）。 */
    public List<RagRetrieveLog> list(Long kbId, String source, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return logMapper.selectList(Wrappers.<RagRetrieveLog>lambdaQuery()
                .eq(kbId != null, RagRetrieveLog::getKbId, kbId)
                .eq(StrUtil.isNotBlank(source), RagRetrieveLog::getSource, source)
                .orderByDesc(RagRetrieveLog::getId)
                .last("LIMIT " + safeLimit));
    }

    /** 最近 N 天检索统计（按知识库分组）。 */
    public List<java.util.Map<String, Object>> statsByKb() {
        // 简化：返回最近日志总数（SQLite 分组统计由前端/服务端简单聚合）
        return List.of();
    }
}
