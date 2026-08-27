package com.zimo.module.sys.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.module.sys.entity.SysMenu;
import java.util.List;
import org.junit.jupiter.api.Test;

class SysMenuServiceImplTest {

    @Test
    void listMenuTreeIgnoresInvalidRowsAndSortsBySortOrder() {
        SysMenu nullParent = menu(99L, null, "异常菜单", 1, 1);
        SysMenu secondRoot = menu(2L, 0L, "角色管理", 20, 1);
        SysMenu disabledRoot = menu(3L, 0L, "停用菜单", 5, 0);
        SysMenu firstRoot = menu(1L, 0L, "用户管理", 10, 1);
        SysMenu child = menu(4L, 1L, "用户新增", 5, 1);
        SysMenuServiceImpl service = new StubSysMenuService(
                List.of(nullParent, secondRoot, disabledRoot, firstRoot, child));

        List<SysMenu> tree = service.listMenuTree();

        assertThat(tree).extracting(SysMenu::getMenuName).containsExactly("用户管理", "角色管理");
        assertThat(tree.get(0).getChildren()).extracting(SysMenu::getMenuName).containsExactly("用户新增");
    }

    private static SysMenu menu(Long id, Long parentId, String name, Integer sortOrder, Integer status) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setMenuName(name);
        menu.setSortOrder(sortOrder);
        menu.setStatus(status);
        return menu;
    }

    private static class StubSysMenuService extends SysMenuServiceImpl {
        private final List<SysMenu> menus;

        StubSysMenuService(List<SysMenu> menus) {
            this.menus = menus;
        }

        @Override
        public List<SysMenu> list() {
            return menus;
        }
    }
}
