package com.zimo.agentapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zimo.module.auth.entity.SysUser;
import com.zimo.module.auth.mapper.SysUserMapper;
import com.zimo.module.sys.entity.SysRole;
import com.zimo.module.sys.mapper.SysRoleMapper;
import com.zimo.module.sys.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DataInitializerTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void existingAdminWithoutDepartmentGetsDefaultDepartment() {
        SysUserMapper userMapper = org.mockito.Mockito.mock(SysUserMapper.class);
        SysRoleMapper roleMapper = org.mockito.Mockito.mock(SysRoleMapper.class);
        SysUserRoleMapper userRoleMapper = org.mockito.Mockito.mock(SysUserRoleMapper.class);
        SysUser admin = new SysUser();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPassword(encoder.encode("admin"));
        admin.setStatus(1);
        SysRole role = new SysRole();
        role.setId(1L);
        role.setRoleKey("admin");
        role.setStatus(1);
        when(userMapper.selectOne(any())).thenReturn(admin);
        when(roleMapper.selectOne(any())).thenReturn(role);
        when(userRoleMapper.selectCount(any())).thenReturn(1L);

        new DataInitializer(userMapper, roleMapper, userRoleMapper).run(null);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDepartment()).isEqualTo("行政");
    }
}
