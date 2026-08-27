package com.zimo.module.ai.management;

import java.util.List;
import java.util.Optional;

/**
 * AI 管理模块的提示词模板仓储接口，对应数据库表 ai_prompt_template。
 *
 * <p>该接口只定义单表边界内的数据访问能力；复杂业务校验由 Service 层完成。</p>
 *
 * @author xingju
 * @since 2026-07-08
 */
public interface AiPromptTemplateRepository {
    /**
     * 查询所有未软删除的提示词模板。
     *
     * @return 活跃模板列表；没有数据时返回空列表
     */
    List<AiPromptTemplate> listActive();

    /**
     * 按模板类型查询未软删除的提示词模板。
     *
     * @param templateType 模板类型；为空时返回全部类型
     * @return 活跃模板列表
     */
    List<AiPromptTemplate> listActive(String templateType);

    /**
     * 按主键查询未软删除的提示词模板。
     *
     * @param id 主键 ID，不允许为空
     * @return 命中的模板；不存在或已删除时返回 Optional.empty()
     */
    Optional<AiPromptTemplate> findById(Long id);

    /**
     * 保存提示词模板，id 为空时新增，id 非空时更新。
     *
     * @param template 已通过 Service 校验的模板实体，不允许为空
     * @return 仓储保存后的模板实体，新增时应包含主键 ID
     */
    AiPromptTemplate save(AiPromptTemplate template);

    /**
     * 按主键执行软删除。
     *
     * @param id 主键 ID，不允许为空
     * @return true 表示删除成功，false 表示模板不存在或已被删除
     */
    boolean softDelete(Long id);
}
