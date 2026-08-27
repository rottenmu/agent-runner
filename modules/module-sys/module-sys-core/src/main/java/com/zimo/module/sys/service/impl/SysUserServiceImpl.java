package com.zimo.module.sys.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zimo.module.auth.entity.SysUser;
import com.zimo.module.auth.mapper.SysUserMapper;
import com.zimo.module.sys.security.SysUserPasswordPolicy;
import com.zimo.module.sys.service.SysUserService;
import org.springframework.stereotype.Service;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {
    @Override
    public Page<SysUser> pageUsers(long current, long size) {
        return page(new Page<>(current, size));
    }

    @Override
    public boolean save(SysUser user) {
        SysUserPasswordPolicy.prepareForCreate(user);
        return super.save(user);
    }

    @Override
    public boolean updateById(SysUser user) {
        SysUserPasswordPolicy.prepareForUpdate(user);
        return super.updateById(user);
    }
}
