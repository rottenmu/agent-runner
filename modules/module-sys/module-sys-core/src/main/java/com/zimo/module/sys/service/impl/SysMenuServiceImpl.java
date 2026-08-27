package com.zimo.module.sys.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zimo.module.sys.entity.SysMenu;
import com.zimo.module.sys.mapper.SysMenuMapper;
import com.zimo.module.sys.service.SysMenuService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {
    @Override
    public List<SysMenu> listMenuTree() {
        List<SysMenu> all = list();
        return buildTree(all, 0L, new HashSet<>());
    }

    private List<SysMenu> buildTree(List<SysMenu> all, Long parentId, Set<Long> visited) {
        return all.stream()
                .filter(this::isEnabledMenu)
                .filter(m -> Objects.equals(m.getParentId(), parentId))
                .sorted(Comparator
                        .comparing(SysMenu::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SysMenu::getId, Comparator.nullsLast(Long::compareTo)))
                .filter(m -> m.getId() != null && !visited.contains(m.getId()))
                .peek(m -> {
                    Set<Long> branchVisited = new HashSet<>(visited);
                    branchVisited.add(m.getId());
                    m.setChildren(buildTree(all, m.getId(), branchVisited));
                })
                .collect(Collectors.toList());
    }

    private boolean isEnabledMenu(SysMenu menu) {
        return menu != null && Objects.equals(menu.getStatus(), 1);
    }
}
