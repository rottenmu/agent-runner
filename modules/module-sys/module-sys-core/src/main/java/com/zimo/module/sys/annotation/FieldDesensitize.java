package com.zimo.module.sys.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段脱敏标记注解。
 *
 * <p>作用在 DTO、VO 或实体字段上，用于标记该字段在序列化或响应输出前需要脱敏。
 * 具体脱敏执行逻辑由后续序列化器、切面或响应增强器实现。</p>
 *
 * <p>示例：{@code @FieldDesensitize(type = FieldDesensitize.Type.PHONE)}</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FieldDesensitize {

    /**
     * 脱敏类型。
     *
     * @return 脱敏类型
     */
    Type type() default Type.DEFAULT;

    /**
     * 自定义脱敏占位符。
     *
     * <p>为空时由脱敏处理器根据 {@link #type()} 选择默认规则。</p>
     *
     * @return 自定义脱敏占位符
     */
    String mask() default "";

    /**
     * 自定义前缀保留长度。
     *
     * <p>默认 -1，表示交由脱敏处理器按类型决定。</p>
     *
     * @return 前缀保留长度
     */
    int prefixKeep() default -1;

    /**
     * 自定义后缀保留长度。
     *
     * <p>默认 -1，表示交由脱敏处理器按类型决定。</p>
     *
     * @return 后缀保留长度
     */
    int suffixKeep() default -1;

    /**
     * 字段脱敏类型。
     */
    enum Type {
        /**
         * 默认脱敏规则。
         */
        DEFAULT,

        /**
         * 姓名脱敏。
         */
        NAME,

        /**
         * 手机号脱敏。
         */
        PHONE,

        /**
         * 邮箱脱敏。
         */
        EMAIL,

        /**
         * 身份证号脱敏。
         */
        ID_CARD,

        /**
         * 银行卡号脱敏。
         */
        BANK_CARD,

        /**
         * 地址脱敏。
         */
        ADDRESS,

        /**
         * 密码或密钥类字段脱敏。
         */
        PASSWORD,

        /**
         * 自定义脱敏规则。
         */
        CUSTOM
    }
}
