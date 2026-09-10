package com.zimo.module.tools.tool;

import com.zimo.framework.ai.skill.AiSkill;
import cn.hutool.core.util.StrUtil;
import com.zimo.framework.ai.skill.AiSkillResult;
import com.zimo.module.tools.ToolsProperties;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.StringUtils;

/**
 * 邮件发送工具：通过 SMTP 发送邮件。
 *
 * <p>需配置 {@code ai.tools.mail-*}（host/port/username/password/from）；未配置时提示不可用。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class EmailTool implements AiSkill {

    private final ToolsProperties properties;
    private volatile JavaMailSenderImpl sender;

    public EmailTool(ToolsProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public String name() {
        return "email";
    }

    @Override
    public String description() {
        return "发送邮件：to(收件人，多个用逗号分隔，必填)、subject(主题，必填)、body(正文，必填)、"
                + "cc(抄送，可选)。需要管理员已配置 SMTP（ai.tools.mail-*）。";
    }

    @Override
    public boolean readOnly() {
        return false;
    }

    @Override
    public AiSkillResult call(Map<String, Object> arguments) {
        if (arguments == null) {
            return AiSkillResult.fail("缺少参数");
        }
        String to = str(arguments.get("to"));
        String subject = str(arguments.get("subject"));
        String body = str(arguments.get("body"));
        if (!StringUtils.hasText(to) || !StringUtils.hasText(subject) || !StringUtils.hasText(body)) {
            return AiSkillResult.fail("请提供 to、subject、body");
        }
        try {
            JavaMailSenderImpl mailSender = sender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.getMailFrom());
            helper.setTo(to.split("[,，]"));
            String cc = str(arguments.get("cc"));
            if (StringUtils.hasText(cc)) {
                helper.setCc(cc.split("[,，]"));
            }
            helper.setSubject(subject);
            helper.setText(body, body.contains("\n") || body.contains("<"));
            mailSender.send(message);
            return AiSkillResult.ok("邮件已发送至 " + to + "（主题: " + subject + "）");
        } catch (IllegalStateException e) {
            return AiSkillResult.fail(e.getMessage());
        } catch (Exception e) {
            return AiSkillResult.fail("邮件发送失败：" + safeMessage(e));
        }
    }

    private JavaMailSenderImpl sender() {
        JavaMailSenderImpl current = sender;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (sender == null) {
                if (!StringUtils.hasText(properties.getMailHost())
                        || !StringUtils.hasText(properties.getMailUsername())) {
                    throw new IllegalStateException(
                            "邮件功能未配置：请在 ai.tools.mail-host / mail-username / mail-password 中配置 SMTP");
                }
                JavaMailSenderImpl impl = new JavaMailSenderImpl();
                impl.setHost(properties.getMailHost());
                impl.setPort(properties.getMailPort());
                impl.setUsername(properties.getMailUsername());
                impl.setPassword(properties.getMailPassword());
                Properties props = impl.getJavaMailProperties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.ssl.enable", String.valueOf(properties.isMailSsl()));
                props.put("mail.smtp.timeout", "10000");
                props.put("mail.smtp.connectiontimeout", "10000");
                sender = impl;
            }
            return sender;
        }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String safeMessage(Throwable exception) {
        String message = exception == null ? "" : exception.getMessage();
        return StrUtil.isBlank(message)
                ? (exception == null ? "未知错误" : exception.getClass().getSimpleName())
                : message;
    }
}
