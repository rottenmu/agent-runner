package com.zimo.framework.common.validation;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import java.util.Collection;
import java.util.Map;

/**
 * 后端统一校验工具（基于 hutool 实现）。
 *
 * <p>阿里巴巴规范：参数校验统一收敛，禁止散落手写 {@code if (x == null || x.isBlank()) throw ...}。
 * 本工具覆盖五类空值场景：空字符串、空对象、空 List/Collection、空 Map、空数组。
 * 校验失败统一抛出 {@link IllegalArgumentException}，便于全局异常处理器统一兜底。</p>
 *
 * <pre>
 *   String name = ValidationUtil.requireText(body.get("name"), "渠道名称不能为空");
 *   Object user = ValidationUtil.requireNotNull(userService.get(1L), "用户不存在");
 *   List&lt;Long&gt; ids = ValidationUtil.requireNonEmptyList(body.get("ids"), "ids 不能为空");
 *   Map&lt;String,Object&gt; cfg = ValidationUtil.requireNonEmptyMap(config, "配置不能为空");
 *   String[] tags = ValidationUtil.requireNonEmptyArray(tags, "标签不能为空");
 * </pre>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public final class ValidationUtil {

    private ValidationUtil() {
    }

    /* ---------------- 空字符串 ---------------- */

    /**
     * 校验非空文本（null、空串、纯空白均视为非法），返回原值便于链式赋值。
     *
     * @param value 待校验值
     * @param message 校验失败提示
     * @return 原值
     * @throws IllegalArgumentException 为空时抛出
     */
    public static String requireText(String value, String message) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * 校验非空字符串（null/空串非法，纯空白允许），返回原值。
     *
     * @param value 待校验值
     * @param message 校验失败提示
     * @return 原值
     * @throws IllegalArgumentException 为空时抛出
     */
    public static String requireNotEmpty(String value, String message) {
        if (StrUtil.isEmpty(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /* ---------------- 空对象 ---------------- */

    /**
     * 校验对象非 null，返回原值。
     *
     * @param value 待校验对象
     * @param message 校验失败提示
     * @return 原值
     * @throws IllegalArgumentException 为 null 时抛出
     */
    public static <T> T requireNotNull(T value, String message) {
        if (ObjectUtil.isNull(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /* ---------------- 空 List / Collection ---------------- */

    /**
     * 校验集合非空（null 或 size==0 均非法），返回原集合。
     *
     * @param collection 待校验集合
     * @param message 校验失败提示
     * @return 原集合
     * @throws IllegalArgumentException 为空时抛出
     */
    public static <T extends Collection<?>> T requireNonEmptyList(T collection, String message) {
        if (CollUtil.isEmpty(collection)) {
            throw new IllegalArgumentException(message);
        }
        return collection;
    }

    /* ---------------- 空 Map ---------------- */

    /**
     * 校验 Map 非空（null 或 size==0 均非法），返回原 Map。
     *
     * @param map 待校验 Map
     * @param message 校验失败提示
     * @return 原 Map
     * @throws IllegalArgumentException 为空时抛出
     */
    public static <T extends Map<?, ?>> T requireNonEmptyMap(T map, String message) {
        if (MapUtil.isEmpty(map)) {
            throw new IllegalArgumentException(message);
        }
        return map;
    }

    /* ---------------- 空数组 ---------------- */

    /**
     * 校验数组非空（null 或 length==0 均非法），返回原数组。
     *
     * @param array 待校验数组
     * @param message 校验失败提示
     * @return 原数组
     * @throws IllegalArgumentException 为空时抛出
     */
    public static <T> T[] requireNonEmptyArray(T[] array, String message) {
        if (ArrayUtil.isEmpty(array)) {
            throw new IllegalArgumentException(message);
        }
        return array;
    }

    /* ---------------- 通用空值断言 ---------------- */

    /**
     * 通用空值断言：对象、集合、Map、数组均适用，为空则抛出。
     *
     * @param value 待校验对象（对象/集合/Map/数组）
     * @param message 校验失败提示
     * @throws IllegalArgumentException 为空时抛出
     */
    public static void assertNotEmpty(Object value, String message) {
        if (ObjectUtil.isEmpty(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 通用非空断言（null 即抛出）。
     *
     * @param value 待校验对象
     * @param message 校验失败提示
     * @throws IllegalArgumentException 为 null 时抛出
     */
    public static void assertNotNull(Object value, String message) {
        if (ObjectUtil.isNull(value)) {
            throw new IllegalArgumentException(message);
        }
    }
}
