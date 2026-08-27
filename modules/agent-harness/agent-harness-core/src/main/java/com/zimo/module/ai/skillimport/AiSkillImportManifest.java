package com.zimo.module.ai.skillimport;

import java.util.Map;
import cn.hutool.core.util.StrUtil;

/**
 * module-ai 技能 ZIP 导入清单 DTO。
 *
 * <p>该类型只包含可跨环境导入的技能字段，不接受智能体、提示词模板或 API 注册表绑定信息。
 *
 * @param schemaVersion 清单结构版本，当前只支持 {@code 1}
 * @param name 技能名称，去除首尾空白后必须非空
 * @param description 技能描述，去除首尾空白后必须非空
 * @param readOnly 是否为只读技能，为空时默认 {@code true}
 * @param apiConfig API 调用配置，必须提供
 * @author Codex
 * @since 2026-07-27
 */
public record AiSkillImportManifest(
        int schemaVersion,
        String name,
        String description,
        Boolean readOnly,
        ApiConfig apiConfig) {

    /**
     * 获取应用默认值后的只读标记。
     *
     * @return 未声明时返回 {@code true}，否则返回清单声明值
     */
    public boolean readOnlyOrDefault() {
        return readOnly == null || readOnly;
    }

    /**
     * module-ai 技能 ZIP 导入专用 API 配置 DTO。
     *
     * <p>配置不包含环境相关的 API 注册表标识，所有请求头只在内存中解析，不输出到日志。
     *
     * @param enabled 是否启用 API 调用，为空时默认 {@code true}
     * @param baseUrl API 基础地址，去除首尾空白后必须非空
     * @param path API 请求路径，去除首尾空白后必须非空
     * @param method HTTP 方法，为空或空白时默认 {@code POST}
     * @param headers 请求头映射，为空时默认空映射
     * @param timeoutMillis 请求超时时间，单位为毫秒，为空时默认 {@code 3000}
     * @author Codex
     * @since 2026-07-27
     */
    public record ApiConfig(
            Boolean enabled,
            String baseUrl,
            String path,
            String method,
            Map<String, String> headers,
            Integer timeoutMillis) {

        /**
         * 获取应用默认值后的启用标记。
         *
         * @return 未声明时返回 {@code true}，否则返回清单声明值
         */
        public boolean enabledOrDefault() {
            return enabled == null || enabled;
        }

        /**
         * 获取应用默认值并去除首尾空白后的 HTTP 方法。
         *
         * @return 未声明或空白时返回 {@code POST}，否则返回清单声明值
         */
        public String methodOrDefault() {
            return StrUtil.isBlank(method) ? "POST" : method.trim();
        }

        /**
         * 获取不可变请求头映射。
         *
         * @return 未声明时返回空映射，否则返回清单请求头的不可变副本
         * @throws NullPointerException 请求头包含空键或空值时抛出
         */
        public Map<String, String> headersOrDefault() {
            return headers == null ? Map.of() : Map.copyOf(headers);
        }

        /**
         * 获取应用默认值后的请求超时时间。
         *
         * @return 未声明时返回 {@code 3000}，否则返回清单声明的毫秒数
         */
        public int timeoutMillisOrDefault() {
            return timeoutMillis == null ? 3000 : timeoutMillis;
        }
    }
}
