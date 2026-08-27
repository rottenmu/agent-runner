package com.zimo.module.auth.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.zimo.framework.common.BizException;
import com.zimo.module.auth.entity.SysUser;
import com.zimo.module.auth.mapper.SysUserMapper;
import com.zimo.module.auth.security.AuthSessionService;
import java.util.List;
import org.junit.jupiter.api.Test;

class CurrentLoginUserUtilsTest {
    private final SysUserMapper userMapper = org.mockito.Mockito.mock(SysUserMapper.class);
    private final FakeAuthSessionService sessionService = new FakeAuthSessionService();

    @Test
    void currentUserReturnsProfileFromSessionLoginId() {
        sessionService.login(7L);
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("admin");
        user.setNickname("管理员");
        user.setDepartment("行政");
        user.setAvatar("avatar.png");
        when(userMapper.selectById(7L)).thenReturn(user);

        CurrentLoginUser currentUser = CurrentLoginUserUtils.currentUser(sessionService, userMapper);

        assertThat(currentUser.userId()).isEqualTo(7L);
        assertThat(currentUser.username()).isEqualTo("admin");
        assertThat(currentUser.nickname()).isEqualTo("管理员");
        assertThat(currentUser.department()).isEqualTo("行政");
        assertThat(currentUser.avatar()).isEqualTo("avatar.png");
        assertThat(currentUser.displayName()).isEqualTo("管理员");
    }

    @Test
    void currentUserUsesUsernameWhenNicknameIsBlank() {
        sessionService.login(8L);
        SysUser user = new SysUser();
        user.setId(8L);
        user.setUsername("operator");
        user.setNickname(" ");
        when(userMapper.selectById(8L)).thenReturn(user);

        CurrentLoginUser currentUser = CurrentLoginUserUtils.currentUser(sessionService, userMapper);

        assertThat(currentUser.displayName()).isEqualTo("operator");
    }

    @Test
    void currentUserRejectsMissingDatabaseUser() {
        sessionService.login(9L);
        when(userMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> CurrentLoginUserUtils.currentUser(sessionService, userMapper))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("当前登录用户不存在");
    }

    private static final class FakeAuthSessionService implements AuthSessionService {
        private Long loginId;

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
            return List.of();
        }
    }
}