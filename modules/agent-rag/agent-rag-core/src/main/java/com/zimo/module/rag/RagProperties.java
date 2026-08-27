package com.zimo.module.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 知识库配置。
 *
 * <p>前缀 {@code ai.rag}：向量化模型、重排配置、切片参数。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
@ConfigurationProperties(prefix = "ai.rag")
public class RagProperties {

    /** 向量化模型名称（OpenAI 兼容 /embeddings）。 */
    private String embeddingModel = "text-embedding-v3";

    /** 向量维度。 */
    private int embeddingDimension = 1024;

    /** 远程重排模型名称；为空时使用本地余弦相似度重排。 */
    private String rerankModel = "";

    /** 远程重排接口地址（OpenAI/Cohere 兼容，如 http://host:port/v1/rerank）。 */
    private String rerankBaseUrl = "";

    /** 默认切片大小（字符）。 */
    private int chunkSize = 800;

    /** 切片重叠（字符）。 */
    private int overlap = 100;

    /** 切片策略：paragraph=按段落，heading=按标题，sentence=按句子，fixed=固定大小。 */
    private String splitBy = "paragraph";

    /** 检索默认返回条数。 */
    private int topK = 8;

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getEmbeddingDimension() {
        return embeddingDimension > 0 ? embeddingDimension : 1024;
    }

    public void setEmbeddingDimension(int embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
    }

    public String getRerankModel() {
        return rerankModel;
    }

    public void setRerankModel(String rerankModel) {
        this.rerankModel = rerankModel;
    }

    public String getRerankBaseUrl() {
        return rerankBaseUrl;
    }

    public void setRerankBaseUrl(String rerankBaseUrl) {
        this.rerankBaseUrl = rerankBaseUrl;
    }

    public int getChunkSize() {
        return chunkSize > 0 ? chunkSize : 800;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getOverlap() {
        return overlap >= 0 ? overlap : 100;
    }

    public void setOverlap(int overlap) {
        this.overlap = overlap;
    }

    public String getSplitBy() {
        return splitBy;
    }

    public void setSplitBy(String splitBy) {
        this.splitBy = splitBy;
    }

    public int getTopK() {
        return topK > 0 ? topK : 8;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }
}
