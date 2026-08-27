package com.zimo.module.ai.skillimport;

/**
 * module-ai 技能 ZIP 导入的可预期业务异常。
 *
 * <p>Parser 和导入服务在文件格式错误或资源超限时抛出该异常，由接口层转换为对应业务状态。
 * 持久化或技能注册表故障不得包装为该异常，应继续进入全局 {@code 500} 异常处理链。
 *
 * @author Codex
 * @since 2026-07-27
 */
public final class AiSkillImportException extends RuntimeException {

    private final int code;

    private AiSkillImportException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 创建文件或清单格式错误异常。
     *
     * @param message 可安全展示给用户且不包含清单正文或密钥的错误消息
     * @return 业务状态为 {@code 400} 的导入异常
     */
    public static AiSkillImportException badRequest(String message) {
        return new AiSkillImportException(400, message);
    }

    /**
     * 创建 ZIP 资源超限异常。
     *
     * @param message 可安全展示给用户的超限说明
     * @return 业务状态为 {@code 413} 的导入异常
     */
    public static AiSkillImportException tooLarge(String message) {
        return new AiSkillImportException(413, message);
    }

    /**
     * 获取导入失败对应的业务状态。
     *
     * @return {@code 400} 表示格式错误，{@code 413} 表示资源超限
     */
    public int getCode() {
        return code;
    }
}
