package com.zimo.module.sys.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zimo.module.sys.entity.SysRole;
import com.zimo.module.sys.mapper.SysRoleMapper;
import com.zimo.module.sys.service.SysRoleService;
import org.springframework.stereotype.Service;

@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {
}
