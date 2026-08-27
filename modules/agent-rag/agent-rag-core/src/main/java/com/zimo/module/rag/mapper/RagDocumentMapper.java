package com.zimo.module.rag.mapper;

import com.zimo.module.rag.entity.RagDocument;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库文档 Mapper。
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
@Mapper
public interface RagDocumentMapper extends BaseMapper<RagDocument> {
}
