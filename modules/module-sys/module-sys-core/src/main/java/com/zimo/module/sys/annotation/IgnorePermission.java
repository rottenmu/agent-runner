package com.zimo.module.sys.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 忽略权限拦截注解。
 *
 * <p>作用在 Controller 类或方法上，用于声明当前接口跳过所有权限拦截。
 * 常用于登录、健康检查、公开资源等无需登录或无需鉴权的场景。</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface IgnorePermission {
}
