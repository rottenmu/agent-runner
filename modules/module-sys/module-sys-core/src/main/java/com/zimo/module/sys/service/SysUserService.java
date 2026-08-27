package com.zimo.module.sys.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zimo.module.auth.entity.SysUser;

public interface SysUserService extends IService<SysUser> {
    Page<SysUser> pageUsers(long current, long size);
}
