package com.zimo.module.ai.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiPromptTemplateServiceTest {
    @Test
    void createSavesManualTemplateWithCompleteCostarFields() {
        InMemoryPromptTemplateRepository repository = new InMemoryPromptTemplateRepository();
        AiPromptTemplateService service = new AiPromptTemplateService(repository, new AiPromptTemplateGenerator());

        AiPromptTemplate saved = service.create(completeRequest());

        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getTemplateName()).isEqualTo("采购审批提示词");
        assertThat(saved.getSourceType()).isEqualTo("manual");
        assertThat(saved.getContextText()).isEqualTo("说明采购审批业务背景和约束");
        assertThat(saved.getObjectiveText()).isEqualTo("判断采购单是否可以自动审批");
        assertThat(saved.getStyleText()).isEqualTo("使用结构化审核语言");
        assertThat(saved.getToneText()).isEqualTo("专业、审慎、明确");
        assertThat(saved.getAudienceText()).isEqualTo("面向采购主管和审批人");
        assertThat(saved.getResponseText()).isEqualTo("输出结论、依据和风险提醒");
        assertThat(repository.savedTemplates()).hasSize(1);
    }

    @Test
    void createDefaultsTemplateTypeToAgentWhenRequestOmitsType() {
        InMemoryPromptTemplateRepository repository = new InMemoryPromptTemplateRepository();
        AiPromptTemplateService service = new AiPromptTemplateService(repository, new AiPromptTemplateGenerator());

        AiPromptTemplate saved = service.create(completeRequest());

        assertThat(saved.getTemplateType()).isEqualTo("agent");
    }

    @Test
    void createSavesSkillTemplateType() {
        InMemoryPromptTemplateRepository repository = new InMemoryPromptTemplateRepository();
        AiPromptTemplateService service = new AiPromptTemplateService(repository, new AiPromptTemplateGenerator());
        AiPromptTemplateRequest request = completeRequest();
        request.setTemplateType("skill");

        AiPromptTemplate saved = service.create(request);

        assertThat(saved.getTemplateType()).isEqualTo("skill");
    }

    @Test
    void listDelegatesOptionalTemplateTypeFilterToRepository() {
        InMemoryPromptTemplateRepository repository = new InMemoryPromptTemplateRepository();
        AiPromptTemplateService service = new AiPromptTemplateService(repository, new AiPromptTemplateGenerator());

        service.list("skill");

        assertThat(repository.lastTemplateType()).isEqualTo("skill");
    }

    @Test
    void createRejectsUnsupportedTemplateType() {
        AiPromptTemplateService service = new AiPromptTemplateService(
                new InMemoryPromptTemplateRepository(),
                new AiPromptTemplateGenerator());
        AiPromptTemplateRequest request = completeRequest();
        request.setTemplateType("workflow");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("templateType");
    }

    @Test
    void createRejectsRequestWhenAnyCostarFieldIsMissing() {
        AiPromptTemplateService service = new AiPromptTemplateService(
                new InMemoryPromptTemplateRepository(),
                new AiPromptTemplateGenerator());
        AiPromptTemplateRequest request = completeRequest();
        request.setToneText(" ");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toneText");
    }

    @Test
    void createRejectsRequestWhenTemplateCodeIsBlank() {
        AiPromptTemplateService service = new AiPromptTemplateService(
                new InMemoryPromptTemplateRepository(),
                new AiPromptTemplateGenerator());
        AiPromptTemplateRequest request = completeRequest();
        request.setTemplateCode(" ");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("templateCode");
    }

    @Test
    void updatePreservesExistingCreationAuditFields() {
        InMemoryPromptTemplateRepository repository = new InMemoryPromptTemplateRepository();
        AiPromptTemplateService service = new AiPromptTemplateService(repository, new AiPromptTemplateGenerator());
        AiPromptTemplate existing = service.create(completeRequest());
        LocalDateTime createdAt = existing.getCreatedAt();
        AiPromptTemplateRequest request = completeRequest();
        request.setTemplateCode("quality-review");
        request.setTemplateName("Quality review prompt");
        request.setCreatedBy("u999");
        request.setCreatedName("New creator");
        request.setUpdatedBy("u002");
        request.setUpdatedName("Editor");

        AiPromptTemplate updated = service.update(existing.getId(), request);

        assertThat(updated.getTemplateCode()).isEqualTo("quality-review");
        assertThat(updated.getTemplateName()).isEqualTo("Quality review prompt");
        assertThat(updated.getCreatedBy()).isEqualTo(existing.getCreatedBy());
        assertThat(updated.getCreatedName()).isEqualTo(existing.getCreatedName());
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedBy()).isEqualTo("u002");
        assertThat(updated.getUpdatedName()).isEqualTo("Editor");
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void generateReturnsGeneratedDraftWithoutSavingRepository() {
        InMemoryPromptTemplateRepository repository = new InMemoryPromptTemplateRepository();
        AiPromptTemplateService service = new AiPromptTemplateService(repository, new AiPromptTemplateGenerator());
        AiPromptTemplateGenerateRequest request = new AiPromptTemplateGenerateRequest();
        request.setBusinessDescription("为生产排程异常生成处理建议");

        AiPromptTemplate draft = service.generate(request);

        assertThat(draft.getId()).isNull();
        assertThat(draft.getSourceType()).isEqualTo("generated");
        assertThat(draft.getBusinessDescription()).isEqualTo("为生产排程异常生成处理建议");
        assertThat(draft.getTemplateName()).contains("生产排程异常");
        assertThat(draft.getContextText()).contains("为生产排程异常生成处理建议");
        assertThat(draft.getObjectiveText()).isNotBlank();
        assertThat(draft.getStyleText()).isNotBlank();
        assertThat(draft.getToneText()).isNotBlank();
        assertThat(draft.getAudienceText()).isNotBlank();
        assertThat(draft.getResponseText()).isNotBlank();
        assertThat(repository.savedTemplates()).isEmpty();
    }

    @Test
    void generateKeepsRequestedTemplateTypeOnDraft() {
        AiPromptTemplateService service = new AiPromptTemplateService(
                new InMemoryPromptTemplateRepository(),
                new AiPromptTemplateGenerator());
        AiPromptTemplateGenerateRequest request = new AiPromptTemplateGenerateRequest();
        request.setBusinessDescription("generate project query prompt for a skill");
        request.setTemplateType("skill");

        AiPromptTemplate draft = service.generate(request);

        assertThat(draft.getTemplateType()).isEqualTo("skill");
    }

    @Test
    void deleteDelegatesSoftDeleteToRepository() {
        InMemoryPromptTemplateRepository repository = new InMemoryPromptTemplateRepository();
        AiPromptTemplateService service = new AiPromptTemplateService(repository, new AiPromptTemplateGenerator());

        boolean deleted = service.delete(42L);

        assertThat(deleted).isTrue();
        assertThat(repository.deletedIds()).containsExactly(42L);
    }

    private AiPromptTemplateRequest completeRequest() {
        AiPromptTemplateRequest request = new AiPromptTemplateRequest();
        request.setTemplateCode("purchase-approval");
        request.setTemplateName("采购审批提示词");
        request.setDescription("用于常规采购单审批");
        request.setContextText("说明采购审批业务背景和约束");
        request.setObjectiveText("判断采购单是否可以自动审批");
        request.setStyleText("使用结构化审核语言");
        request.setToneText("专业、审慎、明确");
        request.setAudienceText("面向采购主管和审批人");
        request.setResponseText("输出结论、依据和风险提醒");
        request.setBusinessDescription("采购单自动审批");
        request.setEnabled(true);
        request.setCreatedBy("u001");
        request.setCreatedName("测试用户");
        request.setUpdatedBy("u001");
        request.setUpdatedName("测试用户");
        return request;
    }

    private static final class InMemoryPromptTemplateRepository implements AiPromptTemplateRepository {
        private final Map<Long, AiPromptTemplate> templates = new LinkedHashMap<>();
        private final List<AiPromptTemplate> savedTemplates = new ArrayList<>();
        private final List<Long> deletedIds = new ArrayList<>();
        private String lastTemplateType;
        private long sequence;

        @Override
        public List<AiPromptTemplate> listActive() {
            return new ArrayList<>(templates.values());
        }

        @Override
        public List<AiPromptTemplate> listActive(String templateType) {
            lastTemplateType = templateType;
            return listActive();
        }

        @Override
        public Optional<AiPromptTemplate> findById(Long id) {
            return Optional.ofNullable(templates.get(id));
        }

        @Override
        public AiPromptTemplate save(AiPromptTemplate template) {
            Long id = template.getId() == null ? ++sequence : template.getId();
            AiPromptTemplate saved = copyWithId(template, id);
            templates.put(id, saved);
            savedTemplates.add(saved);
            return saved;
        }

        @Override
        public boolean softDelete(Long id) {
            deletedIds.add(id);
            return true;
        }

        List<AiPromptTemplate> savedTemplates() {
            return savedTemplates;
        }

        List<Long> deletedIds() {
            return deletedIds;
        }

        String lastTemplateType() {
            return lastTemplateType;
        }

        private AiPromptTemplate copyWithId(AiPromptTemplate source, Long id) {
            AiPromptTemplate target = new AiPromptTemplate();
            target.setId(id);
            target.setTemplateCode(source.getTemplateCode());
            target.setTemplateName(source.getTemplateName());
            target.setDescription(source.getDescription());
            target.setTemplateType(source.getTemplateType());
            target.setContextText(source.getContextText());
            target.setObjectiveText(source.getObjectiveText());
            target.setStyleText(source.getStyleText());
            target.setToneText(source.getToneText());
            target.setAudienceText(source.getAudienceText());
            target.setResponseText(source.getResponseText());
            target.setSourceType(source.getSourceType());
            target.setBusinessDescription(source.getBusinessDescription());
            target.setEnabled(source.isEnabled());
            target.setDeleted(source.isDeleted());
            target.setCreatedBy(source.getCreatedBy());
            target.setCreatedName(source.getCreatedName());
            target.setUpdatedBy(source.getUpdatedBy());
            target.setUpdatedName(source.getUpdatedName());
            target.setCreatedAt(source.getCreatedAt());
            target.setUpdatedAt(source.getUpdatedAt());
            return target;
        }
    }
}
