package com.zimo.module.rag.service;

import com.zimo.module.rag.entity.RagDocument;
import com.zimo.module.rag.entity.RagKnowledgeBase;
import com.zimo.module.rag.mapper.RagDocumentMapper;
import com.zimo.module.rag.mapper.RagKnowledgeBaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.StringUtils;

/**
 * 知识库定时自动更新服务。
 *
 * <p>每 10 分钟扫描开启自动更新的知识库，按 cron 计划触发同步；
 * 同步时检测文档源文件修改时间（mtime &gt; 文档更新时间）并重新执行处理管道。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class RagAutoSyncService {

    private static final Logger log = LoggerFactory.getLogger(RagAutoSyncService.class);

    private final RagKnowledgeBaseMapper kbMapper;
    private final RagDocumentMapper documentMapper;
    private final RagPipelineService pipelineService;

    public RagAutoSyncService(
            RagKnowledgeBaseMapper kbMapper,
            RagDocumentMapper documentMapper,
            RagPipelineService pipelineService) {
        this.kbMapper = kbMapper;
        this.documentMapper = documentMapper;
        this.pipelineService = pipelineService;
    }

    /** 定时扫描：每 10 分钟检查一次。 */
    @Scheduled(fixedDelay = 600_000, initialDelay = 30_000)
    public void scanAutoSync() {
        List<RagKnowledgeBase> kbs = kbMapper.selectList(Wrappers.<RagKnowledgeBase>lambdaQuery()
                .eq(RagKnowledgeBase::getEnabled, true)
                .eq(RagKnowledgeBase::getAutoSyncEnabled, true));
        for (RagKnowledgeBase kb : kbs) {
            try {
                if (due(kb)) {
                    log.info("知识库自动同步触发: kb={}", kb.getName());
                    syncKb(kb.getId());
                }
            } catch (Exception e) {
                log.warn("知识库自动同步失败: kb={}", kb.getName(), e);
            }
        }
    }

    /** 手动同步指定知识库（校验调用方在 Controller 层完成）。 */
    public Map<String, Object> syncKb(Long kbId) {
        RagKnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            throw new IllegalArgumentException("知识库不存在: " + kbId);
        }
        List<RagDocument> documents = documentMapper.selectList(Wrappers.<RagDocument>lambdaQuery()
                .eq(RagDocument::getKbId, kbId));
        int processed = 0;
        int unchanged = 0;
        for (RagDocument document : documents) {
            if (document.getSourcePath() == null || document.getSourcePath().isBlank()) {
                continue;
            }
            File file = new File(document.getSourcePath());
            if (!file.isFile()) {
                continue;
            }
            long updatedMs = document.getUpdatedAt() == null
                    ? 0 : document.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            if (file.lastModified() > updatedMs) {
                pipelineService.processDocument(document.getId(), Map.of("createdBy", "auto-sync"));
                processed++;
            } else {
                unchanged++;
            }
        }
        kb.setLastSyncAt(LocalDateTime.now());
        kb.setUpdatedAt(LocalDateTime.now());
        kbMapper.updateById(kb);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kbId", kbId);
        result.put("processed", processed);
        result.put("unchanged", unchanged);
        result.put("syncedAt", kb.getLastSyncAt());
        return result;
    }

    /** 判断知识库是否到达下次计划同步时间。 */
    private boolean due(RagKnowledgeBase kb) {
        String cron = StringUtils.hasText(kb.getAutoSyncCron())
                ? kb.getAutoSyncCron().trim() : "0 0 */6 * * ?";
        CronExpression expression;
        try {
            expression = CronExpression.parse(cron);
        } catch (Exception e) {
            return false;
        }
        LocalDateTime base = kb.getLastSyncAt() != null ? kb.getLastSyncAt() : kb.getCreatedAt();
        if (base == null) {
            return true;
        }
        LocalDateTime next = expression.next(base);
        return next != null && !next.isAfter(LocalDateTime.now());
    }
}
