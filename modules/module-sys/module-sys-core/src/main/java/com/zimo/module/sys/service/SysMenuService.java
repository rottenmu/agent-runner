package com.zimo.module.sys.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zimo.module.sys.entity.SysMenu;
import java.util.List;

public interface SysMenuService extends IService<SysMenu> {
    List<SysMenu> listMenuTree();
}
