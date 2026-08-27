package com.zimo.module.security.service;

import com.zimo.module.security.entity.SecDept;
import com.zimo.module.security.entity.SecUserDept;
import com.zimo.module.security.mapper.SecDeptMapper;
import com.zimo.module.security.mapper.SecUserDeptMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 组织架构同步服务：部门树维护 + 人员部门归属 + 外部组织同步。
 *
 * <p>支持手动同步（JSON 数组或外部接口返回的部门列表），启动时自动同步内置部门种子，
 * 并可将已存在的用户（按 department 字段）挂到对应部门。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class OrgSyncService {

    private static final Logger log = LoggerFactory.getLogger(OrgSyncService.class);

    private final SecDeptMapper deptMapper;
    private final SecUserDeptMapper userDeptMapper;

    public OrgSyncService(SecDeptMapper deptMapper, SecUserDeptMapper userDeptMapper) {
        this.deptMapper = deptMapper;
        this.userDeptMapper = userDeptMapper;
    }

    /** 部门树（含人员数）。 */
    public List<Map<String, Object>> tree() {
        List<SecDept> all = deptMapper.selectList(Wrappers.<SecDept>lambdaQuery()
                .orderByAsc(SecDept::getSort).orderByAsc(SecDept::getId));
        Map<Long, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (SecDept dept : all) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", dept.getId());
            node.put("name", dept.getName());
            node.put("parentId", dept.getParentId());
            node.put("path", dept.getPath());
            node.put("level", dept.getLevel());
            node.put("memberCount", memberCount(dept.getId()));
            node.put("children", new ArrayList<>());
            nodes.put(dept.getId(), node);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> node : nodes.values()) {
            Long parentId = (Long) node.get("parentId");
            if (parentId == null || parentId == 0 || !nodes.containsKey(parentId)) {
                roots.add(node);
            } else {
                ((List<Map<String, Object>>) nodes.get(parentId).get("children")).add(node);
            }
        }
        return roots;
    }

    /** 新增/更新部门。 */
    public SecDept upsert(String name, Long parentId, Integer sort) {
        SecDept existing = deptMapper.selectOne(Wrappers.<SecDept>lambdaQuery()
                .eq(SecDept::getName, name));
        if (existing != null) {
            return existing;
        }
        SecDept dept = new SecDept();
        dept.setName(name);
        dept.setParentId(parentId == null ? 0L : parentId);
        dept.setLevel(parentId == null || parentId == 0 ? 1 : levelOf(parentId) + 1);
        dept.setPath(parentId == null || parentId == 0 ? "/" + name : pathOf(parentId) + "/" + name);
        dept.setSort(sort == null ? 0 : sort);
        dept.setCreatedAt(LocalDateTime.now());
        dept.setUpdatedAt(LocalDateTime.now());
        deptMapper.insert(dept);
        return dept;
    }

    /** 挂载用户到部门。 */
    public SecUserDept bindUser(Long userId, Long deptId, String position, boolean primary) {
        SecUserDept existing = userDeptMapper.selectOne(Wrappers.<SecUserDept>lambdaQuery()
                .eq(SecUserDept::getUserId, userId)
                .eq(SecUserDept::getDeptId, deptId));
        if (existing != null) {
            return existing;
        }
        SecUserDept link = new SecUserDept();
        link.setUserId(userId);
        link.setDeptId(deptId);
        link.setPosition(position == null ? "" : position);
        link.setIsPrimary(primary ? 1 : 0);
        link.setCreatedAt(LocalDateTime.now());
        userDeptMapper.insert(link);
        return link;
    }

    /** 删除部门（级联删除人员归属）。 */
    public void deleteDept(Long id) {
        userDeptMapper.delete(Wrappers.<SecUserDept>lambdaQuery().eq(SecUserDept::getDeptId, id));
        deptMapper.deleteById(id);
    }

    /** 人员部门归属列表。 */
    public List<Map<String, Object>> members() {
        List<SecUserDept> links = userDeptMapper.selectList(Wrappers.<SecUserDept>lambdaQuery()
                .orderByDesc(SecUserDept::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SecUserDept link : links) {
            SecDept dept = deptMapper.selectById(link.getDeptId());
            Map<String, Object> item = new HashMap<>();
            item.put("id", link.getId());
            item.put("userId", link.getUserId());
            item.put("deptId", link.getDeptId());
            item.put("deptName", dept == null ? "" : dept.getName());
            item.put("position", link.getPosition());
            item.put("isPrimary", link.getIsPrimary());
            result.add(item);
        }
        return result;
    }

    /**
     * 手动同步组织架构（幂等 upsert）。
     *
     * @param departments 部门列表 [{name, parentId?, sort?}]
     * @return 同步数量
     */
    public int sync(List<Map<String, Object>> departments) {
        int count = 0;
        if (departments != null) {
            for (Map<String, Object> dept : departments) {
                String name = String.valueOf(dept.getOrDefault("name", ""));
                if (name.isBlank()) {
                    continue;
                }
                Object parentObj = dept.get("parentId");
                Long parentId = parentObj == null ? null : Long.valueOf(String.valueOf(parentObj));
                Object sortObj = dept.get("sort");
                upsert(name, parentId, sortObj == null ? null : Integer.valueOf(String.valueOf(sortObj)));
                count++;
            }
        }
        log.info("组织架构同步完成: {} 个部门", count);
        return count;
    }

    /** 初始化种子部门（无部门时创建）。 */
    public void initSeed() {
        if (deptMapper.selectCount(null) > 0) {
            return;
        }
        upsert("公司总部", null, 1);
        upsert("行政部", 1L, 1);
        upsert("采购部", 1L, 2);
        upsert("财务部", 1L, 3);
        upsert("人事部", 1L, 4);
        upsert("销售部", 1L, 5);
        log.info("种子部门初始化完成");
    }

    private int memberCount(Long deptId) {
        return Math.toIntExact(userDeptMapper.selectCount(
                Wrappers.<SecUserDept>lambdaQuery().eq(SecUserDept::getDeptId, deptId)));
    }

    private int levelOf(Long parentId) {
        SecDept parent = deptMapper.selectById(parentId);
        return parent == null ? 1 : parent.getLevel();
    }

    private String pathOf(Long parentId) {
        SecDept parent = deptMapper.selectById(parentId);
        return parent == null ? "" : parent.getPath();
    }
}
