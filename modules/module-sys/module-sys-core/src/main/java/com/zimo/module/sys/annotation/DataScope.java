package com.zimo.module.sys.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限拦截注解。
 *
 * <p>作用在服务类、接口类或方法上，用于声明当前业务查询需要追加数据范围过滤。
 * 注解通过业务表名绑定数据权限上下文，具体过滤逻辑由后续拦截器或 SQL 增强器实现。</p>
 *
 * <p>示例：{@code @DataScope(value = "pm_project", tableAlias = "p")}</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface DataScope {

    /**
     * 业务表名。
     *
     * <p>作为 {@link #tableName()} 的快捷写法，建议优先使用该属性。</p>
     *
     * @return 业务表名
     */
    String value();

    /**
     * 业务表名。
     *
     * <p>当调用方更偏好显式命名时可使用该属性。若同时设置 {@link #value()}，
     * 后续解析器应优先使用 {@link #value()}。</p>
     *
     * @return 业务表名
     */
    String tableName() default "";

    /**
     * 业务表别名。
     *
     * <p>用于生成数据权限 SQL 条件时限定字段所属表，例如 {@code p.dept_id}。</p>
     *
     * @return 业务表别名
     */
    String tableAlias() default "";

    /**
     * 部门字段名。
     *
     * <p>默认 {@code dept_id}。</p>
     *
     * @return 部门字段名
     */
    String deptColumn() default "dept_id";

    /**
     * 用户字段名。
     *
     * <p>默认 {@code create_by}。</p>
     *
     * @return 用户字段名
     */
    String userColumn() default "create_by";

    /**
     * 是否允许超级管理员跳过数据权限。
     *
     * @return true 表示超级管理员不追加数据权限过滤
     */
    boolean ignoreAdmin() default true;
}
