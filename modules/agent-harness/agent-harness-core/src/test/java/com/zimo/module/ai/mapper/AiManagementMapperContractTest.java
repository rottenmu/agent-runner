package com.zimo.module.ai.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.zimo.module.ai.management.AiManagedAgentEntity;
import com.zimo.module.ai.management.AiManagedSkillConfig;
import com.zimo.module.ai.management.AiPromptTemplate;
import com.zimo.module.ai.modelconfig.AiModelConfigEntity;
import com.zimo.module.ai.modelconfig.mapper.AiModelConfigMapper;
import org.junit.jupiter.api.Test;

class AiManagementMapperContractTest {

    @Test
    void allManagementMappersUseMybatisPlusBaseMapper() {
        assertThat(BaseMapper.class).isAssignableFrom(AiManagedAgentMapper.class);
        assertThat(BaseMapper.class).isAssignableFrom(AiManagedSkillConfigMapper.class);
        assertThat(BaseMapper.class).isAssignableFrom(AiPromptTemplateMapper.class);
        assertThat(BaseMapper.class).isAssignableFrom(AiModelConfigMapper.class);
    }

    @Test
    void entitiesMapToExpectedMysqlTables() throws Exception {
        assertThat(AiManagedAgentEntity.class.getAnnotation(TableName.class).value())
                .isEqualTo("ai_managed_agent");
        assertThat(AiManagedAgentEntity.class.getDeclaredField("tenantId")).isNotNull();
        assertThat(AiManagedSkillConfig.class.getAnnotation(TableName.class).value())
                .isEqualTo("ai_agent_skill_config");
        assertThat(AiPromptTemplate.class.getAnnotation(TableName.class).value())
                .isEqualTo("ai_prompt_template");
        assertThat(AiManagedAgentEntity.class.getDeclaredField("deleted").isAnnotationPresent(TableLogic.class))
                .isTrue();
    }

    @Test
    void modelConfigUsesJsonTypeHandlerAndLogicalDelete() throws NoSuchFieldException {
        TableName tableName = AiModelConfigEntity.class.getAnnotation(TableName.class);
        TableField tags = AiModelConfigEntity.class.getDeclaredField("tags").getAnnotation(TableField.class);

        assertThat(tableName.value()).isEqualTo("ai_model_config");
        assertThat(tableName.autoResultMap()).isTrue();
        assertThat(tags.typeHandler()).isEqualTo(JacksonTypeHandler.class);
        assertThat(AiModelConfigEntity.class.getDeclaredField("deleted").isAnnotationPresent(TableLogic.class))
                .isTrue();
    }
}