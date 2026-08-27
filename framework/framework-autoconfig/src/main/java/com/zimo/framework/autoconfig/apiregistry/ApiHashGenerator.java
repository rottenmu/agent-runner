package com.zimo.framework.autoconfig.apiregistry;

import cn.hutool.crypto.digest.DigestUtil;

/**
 * API 接口签名生成工具。
 *
 * <p>签名输入固定为 HTTP 方法、接口路径和模块标识，供启动扫描时判断接口是否已登记。
 * 工具类仅提供静态方法，不允许实例化。</p>
 *
 * @author Codex
 * @since 2026-07-21
 */
public final class ApiHashGenerator {

    private ApiHashGenerator() {
    }

    /**
     * 生成 API 注册表使用的 32 位小写 MD5。
     *
     * @param method HTTP 方法，不允许为 {@code null}
     * @param path 完整接口路径，不允许为 {@code null}
     * @param moduleCode 模块英文标识，不允许为 {@code null}
     * @return 32 位小写十六进制签名
     * @throws NullPointerException 任一参数为 {@code null} 时抛出
     */
    public static String generate(String method, String path, String moduleCode) {
        String source = method + "|" + path + "|" + moduleCode;
        // hutool DigestUtil.md5Hex：返回 32 位小写十六进制摘要
        return DigestUtil.md5Hex(source);
    }
}
