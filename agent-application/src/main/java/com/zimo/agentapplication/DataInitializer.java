package com.zimo.agentapplication;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zimo.module.auth.entity.SysUser;
import com.zimo.module.auth.mapper.SysUserMapper;
import com.zimo.module.sys.entity.SysRole;
import com.zimo.module.sys.entity.SysUserRole;
import com.zimo.module.sys.mapper.SysRoleMapper;
import com.zimo.module.sys.mapper.SysUserRoleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DataInitializer implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public DataInitializer(SysUserMapper userMapper, SysRoleMapper roleMapper, SysUserRoleMapper userRoleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        SysUser admin = initAdminUser();
        SysRole role = initAdminRole();
        bindAdminRole(admin, role);
    }

    private SysUser initAdminUser() {
        SysUser existing = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "admin"));
        if (existing == null) {
            SysUser admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword(encoder.encode("admin"));
            admin.setNickname("系统管理员");
            admin.setDepartment("行政");
            admin.setStatus(1);
            userMapper.insert(admin);
            log.info("Initialized admin user (password: admin)");
            return admin;
        }
        boolean changed = false;
        if (!encoder.matches("admin", existing.getPassword())) {
            existing.setPassword(encoder.encode("admin"));
            changed = true;
            log.info("Reset admin password to: admin");
        }
        if (existing.getStatus() == null || existing.getStatus() != 1) {
            existing.setStatus(1);
            changed = true;
        }
        if (isBlank(existing.getDepartment())) {
            existing.setDepartment("行政");
            changed = true;
        }
        if (changed) {
            userMapper.updateById(existing);
        }
        return existing;
    }

    private SysRole initAdminRole() {
        SysRole existing = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, "admin"));
        if (existing == null) {
            SysRole role = new SysRole();
            role.setRoleKey("admin");
            role.setRoleName("超级管理员");
            role.setStatus(1);
            roleMapper.insert(role);
            log.info("Initialized admin role");
            return role;
        }
        if (existing.getStatus() == null || existing.getStatus() != 1) {
            existing.setStatus(1);
            roleMapper.updateById(existing);
        }
        return existing;
    }

    private void bindAdminRole(SysUser admin, SysRole role) {
        if (admin == null || admin.getId() == null || role == null || role.getId() == null) {
            return;
        }
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, admin.getId())
                .eq(SysUserRole::getRoleId, role.getId()));
        if (count > 0) {
            return;
        }
        SysUserRole relation = new SysUserRole();
        relation.setUserId(admin.getId());
        relation.setRoleId(role.getId());
        userRoleMapper.insert(relation);
        log.info("Bound admin user to admin role");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
