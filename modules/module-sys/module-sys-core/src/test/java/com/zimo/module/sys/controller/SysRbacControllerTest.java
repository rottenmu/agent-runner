package com.zimo.module.sys.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.auth.service.SysRbacService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SysRbacControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void userRoleEndpointsExposeAssignedRoleIds() throws Exception {
        RecordingRbacService service = new RecordingRbacService();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SysRbacController(service)).build();

        mockMvc.perform(get("/api/biz/sys/user/7/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value(1))
                .andExpect(jsonPath("$.data[1]").value(2));

        mockMvc.perform(put("/api/biz/sys/user/7/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SysRbacController.AssignIdsRequest(List.of(3L, 4L)))))
                .andExpect(status().isOk());
    }

    @Test
    void roleMenuEndpointsExposeAssignedMenuIds() throws Exception {
        RecordingRbacService service = new RecordingRbacService();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SysRbacController(service)).build();

        mockMvc.perform(get("/api/biz/sys/role/9/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value(10))
                .andExpect(jsonPath("$.data[1]").value(20));

        mockMvc.perform(put("/api/biz/sys/role/9/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SysRbacController.AssignIdsRequest(List.of(30L)))))
                .andExpect(status().isOk());
    }

    private static class RecordingRbacService implements SysRbacService {
        @Override
        public List<Long> getUserRoleIds(Long userId) {
            return List.of(1L, 2L);
        }

        @Override
        public void assignUserRoles(Long userId, List<Long> roleIds) {
        }

        @Override
        public List<Long> getRoleMenuIds(Long roleId) {
            return List.of(10L, 20L);
        }

        @Override
        public void assignRoleMenus(Long roleId, List<Long> menuIds) {
        }

        @Override
        public List<String> getRoleKeysByUserId(Long userId) {
            return List.of();
        }

        @Override
        public List<String> getPermissionsByUserId(Long userId) {
            return List.of();
        }
    }
}
