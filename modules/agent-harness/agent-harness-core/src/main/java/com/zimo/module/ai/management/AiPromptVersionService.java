package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;

/**
 * 提示词模板版本 / 快照服务。
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
public interface AiPromptVersionService extends IService<AiPromptVersion> {

    /**
     * 查询指定模板的全部版本与快照（按版本号倒序）。
     *
     * @param promptId 模板主键
     * @return 版本列表
     */
    List<AiPromptVersion> listByPrompt(Long promptId);

    /**
     * 保存模板内容时自动生成一个版本。
     *
     * @param template 模板实体
     * @param createdBy 创建人 ID
     * @param createdName 创建人名称
     * @return 生成的版本
     */
    AiPromptVersion capture(AiPromptTemplate template, String createdBy, String createdName);

    /**
     * 为模板手动创建快照。
     *
     * @param template 模板实体
     * @param snapshotName 快照名称
     * @param createdBy 创建人 ID
     * @param createdName 创建人名称
     * @return 生成的快照
     */
    AiPromptVersion createSnapshot(AiPromptTemplate template, String snapshotName,
            String createdBy, String createdName);
}
