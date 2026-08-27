package com.zimo.module.sys.apiregistry;

import com.zimo.framework.common.BizException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 系统管理模块 API 注册信息业务服务。
 *
 * <p>本服务负责 API 注册信息查询、按模块稳定分组和展示状态维护。状态维护只更新
 * {@code api_registry.status} 元数据，不影响真实 Controller 接口调用。当前方法不声明事务，
 * 单条状态更新使用数据访问层的数据库事务边界。</p>
 *
 * @author Codex
 * @since 2026-07-21
 */
public class SysApiRegistryService {

    private final SysApiRegistryRepository repository;

    /**
     * 创建 API 注册信息业务服务。
     *
     * @param repository API 注册表数据访问组件，不允许为 {@code null}
     * @throws NullPointerException 当数据访问组件为 {@code null} 时抛出
     */
    public SysApiRegistryService(SysApiRegistryRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * 查询符合条件的 API 注册信息。
     *
     * <p>查询条件全部可选，结果顺序直接沿用数据访问层定义的模块、排序值、路径和方法顺序。
     * 查询包含启用、停用和草稿状态，除非调用方显式传入状态条件。</p>
     *
     * @param query API 注册信息查询条件，允许为 {@code null}
     * @return API 注册信息列表；无匹配记录时返回空列表
     */
    public List<SysApiRegistryItem> list(SysApiRegistryQuery query) {
        return repository.find(query);
    }

    /**
     * 查询 API 注册信息并按业务模块分组。
     *
     * <p>模块和组内接口均保持数据访问层原始顺序；模块名称、基础路径和说明取该模块首条记录。
     * 本方法只进行展示分组，不修改注册表数据。</p>
     *
     * @param query API 注册信息查询条件，允许为 {@code null}
     * @return 按业务模块分组的 API 列表；无匹配记录时返回空列表
     */
    public List<SysApiRegistryModuleGroup> grouped(SysApiRegistryQuery query) {
        Map<String, MutableGroup> groups = new LinkedHashMap<>();
        for (SysApiRegistryItem item : repository.find(query)) {
            groups.computeIfAbsent(item.moduleCode(), key -> new MutableGroup(item))
                    .items().add(item);
        }
        return groups.values().stream().map(MutableGroup::toGroup).toList();
    }

    /**
     * 修改 API 注册信息的展示状态。
     *
     * <p>仅允许在停用 {@code 0} 和启用 {@code 1} 之间切换；草稿状态不能通过此接口设置。
     * 更新只影响注册表元数据，不会阻断对应的真实 HTTP 接口。</p>
     *
     * @param id API 注册表主键 ID，必须大于 {@code 0}
     * @param status 目标展示状态，只允许 {@code 0} 或 {@code 1}
     * @throws BizException 当 ID 非法、状态非法或注册信息不存在时抛出
     */
    public void updateStatus(Long id, Integer status) {
        validateId(id);
        validateStatus(status);
        if (repository.updateStatus(id, status) == 0) {
            throw new BizException(404, "API 注册信息不存在");
        }
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(400, "API 注册信息 ID 必须大于 0");
        }
    }

    private void validateStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(400, "API 状态只允许为 0 或 1");
        }
    }

    private static final class MutableGroup {

        private final SysApiRegistryItem firstItem;
        private final List<SysApiRegistryItem> items = new ArrayList<>();

        private MutableGroup(SysApiRegistryItem firstItem) {
            this.firstItem = firstItem;
        }

        private List<SysApiRegistryItem> items() {
            return items;
        }

        private SysApiRegistryModuleGroup toGroup() {
            return new SysApiRegistryModuleGroup(
                    firstItem.moduleCode(),
                    firstItem.moduleName(),
                    firstItem.moduleBasePath(),
                    firstItem.moduleDesc(),
                    List.copyOf(items));
        }
    }
}