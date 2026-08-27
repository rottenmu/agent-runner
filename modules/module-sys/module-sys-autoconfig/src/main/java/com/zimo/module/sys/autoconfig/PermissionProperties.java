package com.zimo.module.sys.autoconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限体系全局配置。
 *
 * <p>统一使用 {@code manufacture.permission} 作为 yml 配置前缀，
 * 用于控制平台权限能力的总开关、数据权限、字段脱敏、操作日志和白名单路径。</p>
 */
@ConfigurationProperties(prefix = "manufacture.permission")
public class PermissionProperties {

    /**
     * 权限体系总开关。
     *
     * <p>默认开启。关闭后，业务侧可跳过数据权限、字段脱敏和操作日志等权限增强逻辑。</p>
     */
    private boolean enabled = true;

    /**
     * 数据权限配置。
     *
     * <p>用于控制按部门、用户、角色等维度过滤业务数据。</p>
     */
    private DataScope dataScope = new DataScope();

    /**
     * 字段脱敏配置。
     *
     * <p>用于控制手机号、邮箱、身份证号、地址等敏感字段的返回值脱敏。</p>
     */
    private FieldMask fieldMask = new FieldMask();

    /**
     * 操作日志配置。
     *
     * <p>用于控制接口访问、业务操作和异常信息的审计记录。</p>
     */
    private OperationLog operationLog = new OperationLog();

    /**
     * 白名单路径配置。
     *
     * <p>命中白名单的请求路径可被权限拦截器直接放行。</p>
     */
    private Whitelist whitelist = new Whitelist();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public DataScope getDataScope() {
        return dataScope;
    }

    public void setDataScope(DataScope dataScope) {
        this.dataScope = dataScope;
    }

    public FieldMask getFieldMask() {
        return fieldMask;
    }

    public void setFieldMask(FieldMask fieldMask) {
        this.fieldMask = fieldMask;
    }

    public OperationLog getOperationLog() {
        return operationLog;
    }

    public void setOperationLog(OperationLog operationLog) {
        this.operationLog = operationLog;
    }

    public Whitelist getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(Whitelist whitelist) {
        this.whitelist = whitelist;
    }

    /**
     * 数据权限配置项。
     */
    public static class DataScope {

        /**
         * 数据权限开关。
         *
         * <p>默认开启。关闭后不再自动追加数据范围过滤条件。</p>
         */
        private boolean enabled = true;

        /**
         * 默认数据范围。
         *
         * <p>默认 {@code DEPT_AND_CHILDREN}，表示当前部门及下级部门。</p>
         */
        private String defaultScope = "DEPT_AND_CHILDREN";

        /**
         * 部门字段名。
         *
         * <p>默认 {@code dept_id}，业务表用于匹配部门数据范围的字段。</p>
         */
        private String deptColumn = "dept_id";

        /**
         * 用户字段名。
         *
         * <p>默认 {@code create_by}，业务表用于匹配本人数据范围的字段。</p>
         */
        private String userColumn = "create_by";

        /**
         * 是否允许超级管理员跳过数据权限。
         *
         * <p>默认允许。</p>
         */
        private boolean ignoreAdmin = true;

        /**
         * 超级管理员角色标识。
         *
         * <p>默认 {@code admin}。</p>
         */
        private String adminRoleKey = "admin";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDefaultScope() {
            return defaultScope;
        }

        public void setDefaultScope(String defaultScope) {
            this.defaultScope = defaultScope;
        }

        public String getDeptColumn() {
            return deptColumn;
        }

        public void setDeptColumn(String deptColumn) {
            this.deptColumn = deptColumn;
        }

        public String getUserColumn() {
            return userColumn;
        }

        public void setUserColumn(String userColumn) {
            this.userColumn = userColumn;
        }

        public boolean isIgnoreAdmin() {
            return ignoreAdmin;
        }

        public void setIgnoreAdmin(boolean ignoreAdmin) {
            this.ignoreAdmin = ignoreAdmin;
        }

        public String getAdminRoleKey() {
            return adminRoleKey;
        }

        public void setAdminRoleKey(String adminRoleKey) {
            this.adminRoleKey = adminRoleKey;
        }
    }

    /**
     * 字段脱敏配置项。
     */
    public static class FieldMask {

        /**
         * 字段脱敏开关。
         *
         * <p>默认开启。</p>
         */
        private boolean enabled = true;

        /**
         * 默认脱敏占位符。
         *
         * <p>默认 {@code ******}。</p>
         */
        private String defaultMask = "******";

        /**
         * 手机号脱敏正则。
         *
         * <p>默认保留前三位和后四位。</p>
         */
        private String phonePattern = "(\\d{3})\\d{4}(\\d{4})";

        /**
         * 手机号脱敏替换表达式。
         *
         * <p>默认输出 {@code $1****$2}。</p>
         */
        private String phoneReplacement = "$1****$2";

        /**
         * 邮箱脱敏正则。
         *
         * <p>默认保留邮箱首字符和域名。</p>
         */
        private String emailPattern = "(.).+(@.+)";

        /**
         * 邮箱脱敏替换表达式。
         *
         * <p>默认输出 {@code $1***$2}。</p>
         */
        private String emailReplacement = "$1***$2";

        /**
         * 默认需要脱敏的字段名。
         *
         * <p>默认包含 password、phone、email、idCard、address。</p>
         */
        private List<String> fields = new ArrayList<>(List.of("password", "phone", "email", "idCard", "address"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDefaultMask() {
            return defaultMask;
        }

        public void setDefaultMask(String defaultMask) {
            this.defaultMask = defaultMask;
        }

        public String getPhonePattern() {
            return phonePattern;
        }

        public void setPhonePattern(String phonePattern) {
            this.phonePattern = phonePattern;
        }

        public String getPhoneReplacement() {
            return phoneReplacement;
        }

        public void setPhoneReplacement(String phoneReplacement) {
            this.phoneReplacement = phoneReplacement;
        }

        public String getEmailPattern() {
            return emailPattern;
        }

        public void setEmailPattern(String emailPattern) {
            this.emailPattern = emailPattern;
        }

        public String getEmailReplacement() {
            return emailReplacement;
        }

        public void setEmailReplacement(String emailReplacement) {
            this.emailReplacement = emailReplacement;
        }

        public List<String> getFields() {
            return fields;
        }

        public void setFields(List<String> fields) {
            this.fields = fields;
        }
    }

    /**
     * 操作日志配置项。
     */
    public static class OperationLog {

        /**
         * 操作日志开关。
         *
         * <p>默认开启。</p>
         */
        private boolean enabled = true;

        /**
         * 是否记录请求参数。
         *
         * <p>默认开启，用于审计普通查询参数和路径参数。</p>
         */
        private boolean recordRequestParams = true;

        /**
         * 是否记录请求体。
         *
         * <p>默认关闭，避免敏感数据或大报文写入日志。</p>
         */
        private boolean recordRequestBody = false;

        /**
         * 是否记录响应体。
         *
         * <p>默认关闭，避免响应数据过大或包含敏感信息。</p>
         */
        private boolean recordResponseBody = false;

        /**
         * 是否记录异常堆栈。
         *
         * <p>默认开启，便于排查失败操作。</p>
         */
        private boolean recordExceptionStack = true;

        /**
         * 操作日志最大内容长度。
         *
         * <p>默认 4096 字符，超过后业务侧可截断写入。</p>
         */
        private int maxContentLength = 4096;

        /**
         * 忽略记录操作日志的路径。
         *
         * <p>默认忽略健康检查和静态资源。</p>
         */
        private List<String> excludePaths = new ArrayList<>(List.of("/actuator/**", "/assets/**", "/favicon.ico"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRecordRequestParams() {
            return recordRequestParams;
        }

        public void setRecordRequestParams(boolean recordRequestParams) {
            this.recordRequestParams = recordRequestParams;
        }

        public boolean isRecordRequestBody() {
            return recordRequestBody;
        }

        public void setRecordRequestBody(boolean recordRequestBody) {
            this.recordRequestBody = recordRequestBody;
        }

        public boolean isRecordResponseBody() {
            return recordResponseBody;
        }

        public void setRecordResponseBody(boolean recordResponseBody) {
            this.recordResponseBody = recordResponseBody;
        }

        public boolean isRecordExceptionStack() {
            return recordExceptionStack;
        }

        public void setRecordExceptionStack(boolean recordExceptionStack) {
            this.recordExceptionStack = recordExceptionStack;
        }

        public int getMaxContentLength() {
            return maxContentLength;
        }

        public void setMaxContentLength(int maxContentLength) {
            this.maxContentLength = maxContentLength;
        }

        public List<String> getExcludePaths() {
            return excludePaths;
        }

        public void setExcludePaths(List<String> excludePaths) {
            this.excludePaths = excludePaths;
        }
    }

    /**
     * 白名单路径配置项。
     */
    public static class Whitelist {

        /**
         * 白名单开关。
         *
         * <p>默认开启。</p>
         */
        private boolean enabled = true;

        /**
         * 白名单路径列表。
         *
         * <p>默认放行登录、登出、当前用户信息、健康检查和静态资源路径。</p>
         */
        private List<String> paths = new ArrayList<>(List.of(
                "/api/auth/login",
                "/api/auth/register",
                "/api/auth/logout",
                "/api/auth/info",
                "/actuator/health",
                "/assets/**",
                "/favicon.ico"
        ));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getPaths() {
            return paths;
        }

        public void setPaths(List<String> paths) {
            this.paths = paths;
        }
    }
}
