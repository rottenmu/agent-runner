package com.zimo.module.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zimo.framework.common.BizException;
import com.zimo.framework.common.ApiResponse;
import com.zimo.module.auth.dto.LoginDTO;
import com.zimo.module.auth.dto.RegisterDTO;
import com.zimo.module.auth.entity.SysUser;
import com.zimo.module.auth.mapper.SysUserMapper;
import com.zimo.module.auth.security.AuthSessionService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthControllerTest {

    private final SysUserMapper userMapper = org.mockito.Mockito.mock(SysUserMapper.class);
    private final FakeAuthSessionService sessionService = new FakeAuthSessionService();
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final AuthController controller = new AuthController(userMapper, sessionService, encoder);

    @Test
    void registerCreatesUserWithNicknameDepartmentAndEncryptedPassword() {
        when(userMapper.selectOne(any())).thenReturn(null);
        RegisterDTO dto = new RegisterDTO();
        dto.setNickname(" 张三 ");
        dto.setUsername(" zhangsan ");
        dto.setPassword("demo123");
        dto.setDepartment("设计部");

        ApiResponse<Map<String, Object>> response = controller.register(dto);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        SysUser saved = captor.getValue();
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(saved.getNickname()).isEqualTo("张三");
        assertThat(saved.getUsername()).isEqualTo("zhangsan");
        assertThat(saved.getDepartment()).isEqualTo("设计部");
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(encoder.matches("demo123", saved.getPassword())).isTrue();
        assertThat(sessionService.permissionReadCount).isZero();
        assertThat(response.getData()).containsEntry("department", "设计部");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userMapper.selectOne(any())).thenReturn(new SysUser());
        RegisterDTO dto = new RegisterDTO();
        dto.setNickname("张三");
        dto.setUsername("zhangsan");
        dto.setPassword("demo123");
        dto.setDepartment("设计部");

        assertThatThrownBy(() -> controller.register(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("账户已存在");
    }

    @Test
    void registerChecksDuplicateUsernameAfterTrimming() {
        when(userMapper.selectOne(any())).thenReturn(new SysUser());
        RegisterDTO dto = new RegisterDTO();
        dto.setNickname("张三");
        dto.setUsername(" zhangsan ");
        dto.setPassword("demo123");
        dto.setDepartment("设计部");

        assertThatThrownBy(() -> controller.register(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("账户已存在");
    }

    @Test
    void loginReturnsTokenUserAndDepartment() {
        sessionService.permissions = List.of("sys:api:list", "sys:api:update");
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("zhangsan");
        user.setNickname("张三");
        user.setPassword(encoder.encode("demo123"));
        user.setDepartment("设计部");
        user.setStatus(1);
        when(userMapper.selectOne(any())).thenReturn(user);
        LoginDTO dto = new LoginDTO();
        dto.setUsername("zhangsan");
        dto.setPassword("demo123");
        dto.setDepartment("设计部");

        ApiResponse<Map<String, Object>> response = controller.login(dto);

        assertThat(response.getData()).containsEntry("token", "test-token");
        assertThat(response.getData()).containsEntry("userId", 7L);
        assertThat(response.getData()).containsEntry("username", "zhangsan");
        assertThat(response.getData()).containsEntry("nickname", "张三");
        assertThat(response.getData()).containsEntry("department", "设计部");
        assertThat(response.getData()).containsEntry("permissions", sessionService.permissions);
        assertThat(sessionService.loginId).isEqualTo(7L);
    }

    @Test
    void loginRejectsMismatchedDepartment() {
        SysUser user = new SysUser();
        user.setUsername("zhangsan");
        user.setPassword(encoder.encode("demo123"));
        user.setDepartment("设计部");
        user.setStatus(1);
        when(userMapper.selectOne(any())).thenReturn(user);
        LoginDTO dto = new LoginDTO();
        dto.setUsername("zhangsan");
        dto.setPassword("demo123");
        dto.setDepartment("采购部");

        assertThatThrownBy(() -> controller.login(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("账户、密码或部门错误");
    }

    @Test
    void loginRejectsBlankUsernameWithValidationMessage() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(" ");
        dto.setPassword("demo123");
        dto.setDepartment("设计部");

        assertThatThrownBy(() -> controller.login(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名不能为空");
    }

    @Test
    void registerRejectsBlankNicknameWithValidationMessage() {
        RegisterDTO dto = new RegisterDTO();
        dto.setNickname(" ");
        dto.setUsername("zhangsan");
        dto.setPassword("demo123");
        dto.setDepartment("设计部");

        assertThatThrownBy(() -> controller.register(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("姓名不能为空");
    }

    @Test
    void logoutClearsCurrentSession() {
        sessionService.login(9L);

        ApiResponse<Void> response = controller.logout();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(sessionService.loginId).isNull();
    }

    @Test
    void infoReturnsCurrentUserProfile() {
        sessionService.login(7L);
        sessionService.permissions = List.of("sys:api:list");
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("zhangsan");
        user.setNickname("张三");
        user.setDepartment("设计部");
        user.setAvatar("avatar.png");
        when(userMapper.selectById(7L)).thenReturn(user);

        ApiResponse<Map<String, Object>> response = controller.info();

        assertThat(response.getData()).containsEntry("userId", 7L);
        assertThat(response.getData()).containsEntry("username", "zhangsan");
        assertThat(response.getData()).containsEntry("nickname", "张三");
        assertThat(response.getData()).containsEntry("department", "设计部");
        assertThat(response.getData()).containsEntry("avatar", "avatar.png");
        assertThat(response.getData()).containsEntry("permissions", sessionService.permissions);
    }

    private static class FakeAuthSessionService implements AuthSessionService {
        private Long loginId;
        private List<String> permissions = List.of();
        private int permissionReadCount;

        @Override
        public void login(Long userId) {
            this.loginId = userId;
        }

        @Override
        public String getTokenValue() {
            return "test-token";
        }

        @Override
        public void logout() {
            this.loginId = null;
        }

        @Override
        public long getLoginIdAsLong() {
            return loginId == null ? 0L : loginId;
        }

        @Override
        public List<String> getPermissions() {
            permissionReadCount++;
            return List.copyOf(permissions);
        }
    }
}
