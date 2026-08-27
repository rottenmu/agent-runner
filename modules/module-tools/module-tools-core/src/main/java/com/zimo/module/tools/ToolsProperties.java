package com.zimo.module.tools;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 内置工具库配置。
 *
 * <p>前缀 {@code ai.tools}：邮件 SMTP、文件工作区、代码沙箱。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
@ConfigurationProperties(prefix = "ai.tools")
public class ToolsProperties {

    /** 邮件 SMTP 主机；为空则邮件工具不可用。 */
    private String mailHost = "";

    /** 邮件 SMTP 端口。 */
    private int mailPort = 465;

    /** SMTP 用户名。 */
    private String mailUsername = "";

    /** SMTP 密码（授权码）。 */
    private String mailPassword = "";

    /** 发件人地址。 */
    private String mailFrom = "";

    /** 是否启用 SSL。 */
    private boolean mailSsl = true;

    /** 文件工具工作目录（路径限制在此目录内）。 */
    private String fileWorkspace = "data/tool-files";

    /** 沙箱文件系统可写扩展名白名单（逗号分隔，空=不限制）。 */
    private String sandboxFileAllowedExtensions = "";

    /** 代码沙箱 python 解释器路径。 */
    private String pythonBin = "python3";

    /** 代码沙箱执行超时（秒）。 */
    private int sandboxTimeoutSeconds = 10;

    /** 远程沙箱服务基址；为空则使用本地直通沙箱。 */
    private String remoteSandboxUrl = "";

    /** 远程沙箱 API token（可选，随 Authorization: Bearer 头传递）。 */
    private String remoteSandboxToken = "";

    /** 远程沙箱请求超时（秒）。 */
    private int remoteSandboxTimeoutSeconds = 30;

    public java.util.Set<String> getSandboxFileAllowedExtensions() {
        if (sandboxFileAllowedExtensions == null || sandboxFileAllowedExtensions.isBlank()) {
            return java.util.Set.of();
        }
        return java.util.Arrays.stream(sandboxFileAllowedExtensions.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(ext -> !ext.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public void setSandboxFileAllowedExtensions(String sandboxFileAllowedExtensions) {
        this.sandboxFileAllowedExtensions = sandboxFileAllowedExtensions;
    }

    /** 插件工具默认接口基址（业务接口 baseUrl）。 */
    private String pluginBaseUrl = "http://localhost:9900";

    public String getPluginBaseUrl() {
        return pluginBaseUrl;
    }

    public void setPluginBaseUrl(String pluginBaseUrl) {
        this.pluginBaseUrl = pluginBaseUrl;
    }

    public String getMailHost() {
        return mailHost;
    }

    public void setMailHost(String mailHost) {
        this.mailHost = mailHost;
    }

    public int getMailPort() {
        return mailPort;
    }

    public void setMailPort(int mailPort) {
        this.mailPort = mailPort;
    }

    public String getMailUsername() {
        return mailUsername;
    }

    public void setMailUsername(String mailUsername) {
        this.mailUsername = mailUsername;
    }

    public String getMailPassword() {
        return mailPassword;
    }

    public void setMailPassword(String mailPassword) {
        this.mailPassword = mailPassword;
    }

    public String getMailFrom() {
        return mailFrom;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

    public boolean isMailSsl() {
        return mailSsl;
    }

    public void setMailSsl(boolean mailSsl) {
        this.mailSsl = mailSsl;
    }

    public String getFileWorkspace() {
        return fileWorkspace;
    }

    public void setFileWorkspace(String fileWorkspace) {
        this.fileWorkspace = fileWorkspace;
    }

    public String getPythonBin() {
        return pythonBin;
    }

    public void setPythonBin(String pythonBin) {
        this.pythonBin = pythonBin;
    }

    public int getSandboxTimeoutSeconds() {
        return sandboxTimeoutSeconds;
    }

    public void setSandboxTimeoutSeconds(int sandboxTimeoutSeconds) {
        this.sandboxTimeoutSeconds = sandboxTimeoutSeconds;
    }

    public String getRemoteSandboxUrl() {
        return remoteSandboxUrl;
    }

    public void setRemoteSandboxUrl(String remoteSandboxUrl) {
        this.remoteSandboxUrl = remoteSandboxUrl;
    }

    public String getRemoteSandboxToken() {
        return remoteSandboxToken;
    }

    public void setRemoteSandboxToken(String remoteSandboxToken) {
        this.remoteSandboxToken = remoteSandboxToken;
    }

    public int getRemoteSandboxTimeoutSeconds() {
        return remoteSandboxTimeoutSeconds;
    }

    public void setRemoteSandboxTimeoutSeconds(int remoteSandboxTimeoutSeconds) {
        this.remoteSandboxTimeoutSeconds = remoteSandboxTimeoutSeconds;
    }
}
