package com.zimo.module.rag.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.ai.AiAgentProperties;
import com.zimo.module.rag.pipeline.DsFileParser;
import com.zimo.module.rag.mapper.RagChunkMapper;
import com.zimo.module.rag.service.RagAutoSyncService;
import com.zimo.module.rag.service.RagChatService;
import com.zimo.module.rag.pipeline.RagCleaner;
import com.zimo.module.rag.pipeline.RagChunker;
import com.zimo.module.rag.mapper.RagDocumentMapper;
import com.zimo.module.rag.pipeline.RagEmbedder;
import com.zimo.module.rag.mapper.RagEvaluationMapper;
import com.zimo.module.rag.service.RagEvaluationService;
import com.zimo.module.rag.mapper.RagKbPermissionMapper;
import com.zimo.module.rag.service.RagKbPermissionService;
import com.zimo.module.rag.service.RagKbService;
import com.zimo.module.rag.mapper.RagKnowledgeBaseMapper;
import com.zimo.module.rag.service.RagPipelineService;
import com.zimo.module.rag.RagProperties;
import com.zimo.module.rag.pipeline.RagReranker;
import com.zimo.module.rag.mapper.RagRetrieveLogMapper;
import com.zimo.module.rag.service.RagRetrieveLogService;
import com.zimo.module.rag.service.RagRetrieveService;
import com.zimo.module.rag.mapper.RagVersionMapper;
import com.zimo.module.rag.service.RagVersionService;
import com.zimo.module.rag.skill.RagRetrieveSkill;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * RAG 知识库模块自动装配。
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
@AutoConfiguration
@MapperScan("com.zimo.module.rag.mapper")
@EnableConfigurationProperties(RagProperties.class)
@org.springframework.scheduling.annotation.EnableScheduling
public class RagAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DsFileParser ragFileParser() {
        return new DsFileParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public RagCleaner ragCleaner() {
        return new RagCleaner();
    }

    @Bean
    @ConditionalOnMissingBean
    public RagChunker ragChunker() {
        return new RagChunker();
    }

    @Bean
    @ConditionalOnMissingBean
    public RagEmbedder ragEmbedder(AiAgentProperties aiAgentProperties, RagProperties ragProperties) {
        return new RagEmbedder(aiAgentProperties, ragProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RagReranker ragReranker(RagProperties ragProperties, RagEmbedder ragEmbedder) {
        return new RagReranker(ragProperties, ragEmbedder);
    }

    @Bean
    @ConditionalOnMissingBean
    public RagPipelineService ragPipelineService(
            RagDocumentMapper documentMapper,
            RagChunkMapper chunkMapper,
            DsFileParser fileParser,
            RagCleaner cleaner,
            RagChunker chunker,
            RagEmbedder embedder,
            RagVersionService versionService,
            ObjectMapper objectMapper) {
        return new RagPipelineService(
                documentMapper, chunkMapper, fileParser, cleaner, chunker, embedder, versionService, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RagRetrieveService ragRetrieveService(
            RagChunkMapper chunkMapper,
            RagDocumentMapper documentMapper,
            RagEmbedder embedder,
            RagReranker reranker,
            ObjectMapper objectMapper) {
        return new RagRetrieveService(chunkMapper, documentMapper, embedder, reranker, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RagRetrieveSkill ragRetrieveSkill(RagRetrieveService retrieveService) {
        return new RagRetrieveSkill(retrieveService);
    }

    /* ---------------- 知识库管控扩展 ---------------- */

    @Bean
    @ConditionalOnMissingBean
    public RagKbPermissionService ragKbPermissionService(
            RagKbPermissionMapper permissionMapper,
            RagKnowledgeBaseMapper kbMapper) {
        return new RagKbPermissionService(permissionMapper, kbMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RagKbService ragKbService(
            RagKnowledgeBaseMapper kbMapper,
            RagDocumentMapper documentMapper,
            RagKbPermissionService permissionService,
            RagKbPermissionMapper permissionMapper,
            ObjectMapper objectMapper) {
        return new RagKbService(kbMapper, documentMapper, permissionService, permissionMapper, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RagVersionService ragVersionService(
            RagVersionMapper versionMapper,
            RagChunkMapper chunkMapper,
            RagDocumentMapper documentMapper,
            ObjectMapper objectMapper) {
        return new RagVersionService(versionMapper, chunkMapper, documentMapper, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RagAutoSyncService ragAutoSyncService(
            RagKnowledgeBaseMapper kbMapper,
            RagDocumentMapper documentMapper,
            RagPipelineService pipelineService) {
        return new RagAutoSyncService(kbMapper, documentMapper, pipelineService);
    }

    @Bean
    @ConditionalOnMissingBean
    public RagEvaluationService ragEvaluationService(
            RagEvaluationMapper evaluationMapper,
            RagRetrieveService retrieveService) {
        return new RagEvaluationService(evaluationMapper, retrieveService);
    }

    @Bean
    @ConditionalOnMissingBean
    public RagRetrieveLogService ragRetrieveLogService(RagRetrieveLogMapper logMapper) {
        return new RagRetrieveLogService(logMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RagChatService ragChatService(
            RagRetrieveService retrieveService,
            AiAgentProperties aiAgentProperties) {
        return new RagChatService(retrieveService, aiAgentProperties);
    }
}
