package com.zimo.module.sys.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口功能权限校验注解。
 *
 * <p>作用在 Controller 类或方法上，用于声明访问当前接口所需的权限标识。
 * 后续权限拦截器或 AOP 可在运行期读取该注解，并调用权限服务完成校验。</p>
 *
 * <p>示例：{@code @HasPerm("sys:role:list")}</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface HasPerm {

    /**
     * 访问当前接口所需的权限标识。
     *
     * <p>建议采用 {@code 模块:资源:动作} 格式，例如 {@code sys:user:create}。</p>
     *
     * @return 权限标识
     */
    String value();
}
