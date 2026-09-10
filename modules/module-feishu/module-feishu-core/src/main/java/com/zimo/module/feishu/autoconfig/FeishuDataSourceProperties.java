package com.zimo.module.feishu.autoconfig;

record FeishuDataSourceProperties(
        String url,
        String username,
        String password,
        String driverClassName) {

    void validateSupported() {
        String normalizedUrl = url == null ? "" : url.trim().toLowerCase();
        if (!normalizedUrl.startsWith("jdbc:mysql://") && !normalizedUrl.startsWith("jdbc:sqlite:")) {
            throw new IllegalStateException(
                    "Feishu datasource only MySQL or SQLite is supported: plugin.feishu.datasource.url");
        }
    }
}
