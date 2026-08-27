package com.zimo.module.feishu.cli.calendar;

import com.zimo.module.feishu.cli.FeishuCliCommandRequest;
import com.zimo.module.feishu.cli.FeishuCliCommandResult;
import com.zimo.module.feishu.cli.FeishuCliTemplate;

import java.util.Objects;

public class FeishuCalendarCliService {
    private final FeishuCliTemplate template;

    public FeishuCalendarCliService(FeishuCliTemplate template) {
        this.template = Objects.requireNonNull(template, "template must not be null");
    }

    public FeishuCliCommandResult createEvent(CalendarEventCreateRequest request) {
        return template.execute(FeishuCliCommandRequest
                .api("calendar", "POST", "/open-apis/calendar/v4/calendars/"
                        + request.getCalendarId() + "/events")
                .withData("summary", request.getSummary())
                .withData("start_time", request.getStartTime())
                .withData("end_time", request.getEndTime()));
    }

    public FeishuCliCommandResult addAttendees(CalendarAttendeeAddRequest request) {
        return template.execute(FeishuCliCommandRequest
                .api("calendar", "POST", "/open-apis/calendar/v4/calendars/"
                        + request.getCalendarId() + "/events/" + request.getEventId() + "/attendees")
                .withData("attendees", request.getUserIds()));
    }
}
