package com.zimo.module.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zimo.framework.common.BizException;
import com.zimo.module.ai.management.AiAgentManagementService;
import com.zimo.module.ai.management.AiManagedAgentRequest;
import com.zimo.module.ai.management.AiPromptTemplateRequest;
import com.zimo.module.ai.management.AiPromptTemplateService;
import com.zimo.module.ai.management.AiPromptVersionService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

class AiManagementControllerContractTest {

    @Test
    void exposesOnlyPlatformAgentManagementPrefix() {
        RequestMapping mapping = AiAgentAdminController.class.getAnnotation(RequestMapping.class);

        assertThat(mapping.value()).containsExactly("/api/biz/ai/agents");
    }

    @Test
    void exposesOnlyPlatformPromptTemplatePrefix() {
        RequestMapping mapping = AiPromptTemplateAdminController.class.getAnnotation(RequestMapping.class);

        assertThat(mapping.value()).containsExactly("/api/biz/ai/prompt-templates");
    }

    @Test
    void mapsMissingAgentToBusinessNotFound() {
        AiAgentManagementService service = mock(AiAgentManagementService.class);
        when(service.update("missing", new AiManagedAgentRequest())).thenReturn(null);
        AiAgentAdminController controller = new AiAgentAdminController(service);

        assertThatThrownBy(() -> controller.update("missing", new AiManagedAgentRequest()))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(404);
    }

    @Test
    void mapsMissingPromptTemplateToBusinessNotFound() {
        AiPromptTemplateService service = mock(AiPromptTemplateService.class);
        AiPromptVersionService versionService = mock(AiPromptVersionService.class);
        AiPromptTemplateRequest request = new AiPromptTemplateRequest();
        when(service.update(99L, request)).thenReturn(null);
        AiPromptTemplateAdminController controller =
                new AiPromptTemplateAdminController(service, versionService);

        assertThatThrownBy(() -> controller.update(99L, request))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(404);
    }
}
