package com.zimo.module.ai.modelconfig;

import java.util.List;
import java.util.Optional;

/**
 * AI 模型配置仓储接口，对应数据库表 {@code ai_model_config}。
 *
 * <p>接口只定义服务层需要的持久化能力；JDBC、MyBatis 或其他实现由自动装配层提供。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
public interface AiModelConfigRepository {

    /**
     * 按查询条件检索模型配置。
     *
     * @param query 查询条件，允许为空；为空时由实现返回未删除配置
     * @return 模型配置列表，没有数据时返回空列表
     */
    List<AiModelConfigEntity> find(AiModelConfigQuery query);

    /**
     * 按主键查询模型配置。
     *
     * @param id 配置主键，必须大于 0
     * @return 命中的模型配置；不存在或已删除时返回 Optional.empty()
     */
    Optional<AiModelConfigEntity> findById(long id);

    /**
     * 新增模型配置。
     *
     * @param entity 已完成业务校验的模型配置实体，不能为 null
     * @return 仓储保存后的模型配置实体
     */
    AiModelConfigEntity insert(AiModelConfigEntity entity);

    /**
     * 更新模型配置。
     *
     * @param entity 已完成业务校验的模型配置实体，不能为 null
     * @return 仓储保存后的模型配置实体
     */
    AiModelConfigEntity update(AiModelConfigEntity entity);

    /**
     * 按主键执行逻辑删除。
     *
     * @param id 配置主键，必须大于 0
     * @return true 表示删除成功，false 表示数据不存在或已删除
     */
    boolean logicalDelete(long id);

    /**
     * 持久化最近一次模型连接测试结果。
     *
     * @param id 配置主键，必须大于 0
     * @param response 连接测试结果，不能为 null
     */
    void updateTestResult(long id, AiModelConfigTestResponse response);
}