package com.zimo.module.sys.apiregistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestController;

class SysApiRegistryControllerTest {

    @Test
    void exposesListEndpointAndPassesAllFilters() throws Exception {
        SysApiRegistryRepository repository = mock(SysApiRegistryRepository.class);
        when(repository.find(any())).thenReturn(List.of(item(7L, "sys")));
        MockMvc mockMvc = mockMvc(repository);

        mockMvc.perform(get("/api/biz/sys/api-registry")
                        .param("moduleCode", "sys")
                        .param("method", "get")
                        .param("status", "0")
                        .param("keyword", "用户"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].moduleCode").value("sys"));

        ArgumentCaptor<SysApiRegistryQuery> queryCaptor = ArgumentCaptor.forClass(SysApiRegistryQuery.class);
        verify(repository).find(queryCaptor.capture());
        assertThat(queryCaptor.getValue()).isEqualTo(new SysApiRegistryQuery("sys", "get", 0, "用户"));
    }

    @Test
    void exposesGroupedEndpoint() throws Exception {
        SysApiRegistryRepository repository = mock(SysApiRegistryRepository.class);
        when(repository.find(any())).thenReturn(List.of(item(7L, "sys")));

        mockMvc(repository).perform(get("/api/biz/sys/api-registry/grouped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].moduleCode").value("sys"))
                .andExpect(jsonPath("$.data[0].apis[0].id").value(7));
    }

    @Test
    void exposesStatusUpdateEndpoint() throws Exception {
        SysApiRegistryRepository repository = mock(SysApiRegistryRepository.class);
        when(repository.updateStatus(7L, 0)).thenReturn(1);

        mockMvc(repository).perform(put("/api/biz/sys/api-registry/7/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(repository).updateStatus(7L, 0);
    }

    @Test
    void declaresRestControllerContract() {
        assertThat(SysApiRegistryController.class.isAnnotationPresent(RestController.class)).isTrue();
    }

    private static MockMvc mockMvc(SysApiRegistryRepository repository) {
        SysApiRegistryController controller = new SysApiRegistryController(
                new SysApiRegistryService(repository));
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static SysApiRegistryItem item(long id, String moduleCode) {
        return new SysApiRegistryItem(id, moduleCode, "系统管理", "/api/biz/sys", "",
                "",
                "GET", "/api/biz/sys/user/list", "list", "查询用户", "",
                List.of(), List.of(), null, true, false, "1.0.0", 1, 0);
    }
}