package com.zimo.module.feishu.cli.calendar;

import java.util.List;

public class CalendarAttendeeAddRequest {
    private final String calendarId;
    private final String eventId;
    private final List<String> userIds;

    public CalendarAttendeeAddRequest(String calendarId, String eventId, List<String> userIds) {
        this.calendarId = requireText(calendarId, "calendarId must not be blank");
        this.eventId = requireText(eventId, "eventId must not be blank");
        this.userIds = List.copyOf(userIds == null ? List.of() : userIds);
    }

    public String getCalendarId() {
        return calendarId;
    }

    public String getEventId() {
        return eventId;
    }

    public List<String> getUserIds() {
        return userIds;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
