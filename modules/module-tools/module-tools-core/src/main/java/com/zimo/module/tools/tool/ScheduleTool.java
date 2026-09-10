package com.zimo.module.tools.tool;

import com.zimo.framework.ai.skill.AiSkill;
import cn.hutool.core.util.StrUtil;
import com.zimo.framework.ai.skill.AiSkillResult;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestTemplate;

/**
 * 定时任务工具：创建一次性 / 周期性任务，到期执行 HTTP 回调或记录提醒。
 *
 * <p>参数：{@code action}(create/list/cancel)、{@code at}(ISO 时间或相对时间如 in 5m/in 2h，必填)、
 * {@code repeat}(秒间隔，可选)、{@code url}(回调地址，可选)、{@code message}(提醒消息，可选)。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class ScheduleTool implements AiSkill {

    private static final Pattern RELATIVE = Pattern.compile("in\\s+(\\d+)\\s*(s|m|h|d)");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]")
            .withZone(ZoneId.systemDefault());

    private final TaskScheduler taskScheduler;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> tasks = new ConcurrentHashMap<>();
    private long nextId = 1;

    public ScheduleTool(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    @Override
    public String name() {
        return "schedule";
    }

    @Override
    public String description() {
        return "定时任务：action(create/list/cancel，必填)、at(执行时间：ISO 时间如 2026-08-10 15:00 或相对时间 "
                + "如 in 30m/in 2h，必填)、repeat(秒，可选周期执行)、url(到期回调地址，可选)、message(提醒内容，可选)。"
                + "create 返回任务 ID；list 查看任务；cancel 取消任务。";
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
        String action = str(arguments.get("action"));
        if (!StringUtils(action)) {
            return AiSkillResult.fail("请提供 action（create/list/cancel）");
        }
        return switch (action) {
            case "create" -> create(arguments);
            case "list" -> list();
            case "cancel" -> cancel(arguments);
            default -> AiSkillResult.fail("不支持的 action: " + action);
        };
    }

    private AiSkillResult create(Map<String, Object> arguments) {
        String at = str(arguments.get("at"));
        if (!StringUtils(at)) {
            return AiSkillResult.fail("请提供 at（执行时间）");
        }
        Instant trigger;
        try {
            trigger = parseTime(at);
        } catch (Exception e) {
            return AiSkillResult.fail("时间格式无法解析: " + at + "（支持 ISO 时间或 in 5m/in 2h）");
        }
        if (trigger.isBefore(Instant.now())) {
            return AiSkillResult.fail("执行时间已过: " + at);
        }
        long repeatSeconds = arguments.get("repeat") instanceof Number n ? n.longValue() : 0;
        String url = str(arguments.get("url"));
        String message = str(arguments.get("message"));

        String taskId = "task-" + (nextId++);
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", taskId);
        task.put("at", LocalDateTime.ofInstant(trigger, ZoneId.systemDefault()).format(ISO));
        task.put("repeat", repeatSeconds);
        task.put("url", url);
        task.put("message", message);
        task.put("state", "scheduled");
        tasks.put(taskId, task);

        Runnable action = () -> {
            String result;
            try {
                if (StringUtils(url)) {
                    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                    result = "回调 HTTP " + response.getStatusCode().value();
                } else {
                    result = "定时提醒: " + (StringUtils(message) ? message : "（无内容）");
                }
                task.put("lastResult", result);
                task.put("state", "executed");
                task.put("lastRunAt", LocalDateTime.now().format(ISO));
            } catch (Exception e) {
                task.put("state", "failed");
                task.put("lastResult", "执行失败: " + safeMessage(e));
            }
            if (repeatSeconds <= 0) {
                futures.remove(taskId);
            }
        };
        ScheduledFuture<?> future;
        if (repeatSeconds > 0) {
            future = taskScheduler.scheduleAtFixedRate(action, trigger, Duration.ofSeconds(repeatSeconds));
        } else {
            future = taskScheduler.schedule(action, trigger);
        }
        futures.put(taskId, future);
        return AiSkillResult.ok("已创建定时任务 " + taskId + "，执行时间 " + task.get("at"));
    }

    private AiSkillResult list() {
        if (tasks.isEmpty()) {
            return AiSkillResult.ok("（暂无定时任务）");
        }
        StringBuilder builder = new StringBuilder("定时任务列表：\n");
        tasks.forEach((id, task) -> builder.append(id)
                .append(" | at=").append(task.get("at"))
                .append(" | state=").append(task.get("state"))
                .append(" | last=").append(task.get("lastResult") == null ? "-" : task.get("lastResult"))
                .append("\n"));
        return AiSkillResult.ok(builder.toString());
    }

    private AiSkillResult cancel(Map<String, Object> arguments) {
        String id = str(arguments.get("id"));
        if (!StringUtils(id)) {
            return AiSkillResult.fail("请提供 id");
        }
        ScheduledFuture<?> future = futures.remove(id);
        if (future == null && !tasks.containsKey(id)) {
            return AiSkillResult.fail("任务不存在: " + id);
        }
        if (future != null) {
            future.cancel(false);
        }
        Map<String, Object> task = tasks.get(id);
        if (task != null) {
            task.put("state", "cancelled");
        }
        return AiSkillResult.ok("已取消任务 " + id);
    }

    private Instant parseTime(String value) {
        String trimmed = value.trim();
        Matcher matcher = RELATIVE.matcher(trimmed);
        if (matcher.matches()) {
            long amount = Long.parseLong(matcher.group(1));
            return Instant.now().plusSeconds(switch (matcher.group(2)) {
                case "m" -> amount * 60;
                case "h" -> amount * 3600;
                case "d" -> amount * 86400;
                default -> amount;
            });
        }
        return LocalDateTime.parse(trimmed.replace("T", " "), ISO)
                .atZone(ZoneId.systemDefault()).toInstant();
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean StringUtils(String value) {
        return StrUtil.isNotBlank(value);
    }

    private String safeMessage(Throwable exception) {
        String message = exception == null ? "" : exception.getMessage();
        return StrUtil.isBlank(message)
                ? (exception == null ? "未知错误" : exception.getClass().getSimpleName())
                : message;
    }
}
