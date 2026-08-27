package com.zimo.module.feishu.cli.calendar;

public class CalendarEventCreateRequest {
    private final String calendarId;
    private final String summary;
    private final String startTime;
    private final String endTime;

    public CalendarEventCreateRequest(String calendarId, String summary, String startTime, String endTime) {
        this.calendarId = requireText(calendarId, "calendarId must not be blank");
        this.summary = requireText(summary, "summary must not be blank");
        this.startTime = requireText(startTime, "startTime must not be blank");
        this.endTime = requireText(endTime, "endTime must not be blank");
    }

    public String getCalendarId() {
        return calendarId;
    }

    public String getSummary() {
        return summary;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
