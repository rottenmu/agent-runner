package com.zimo.module.feishu.cli.bitable;

import com.zimo.module.feishu.cli.FeishuCliCommandRequest;
import com.zimo.module.feishu.cli.FeishuCliCommandResult;
import com.zimo.module.feishu.cli.FeishuCliTemplate;
import java.util.Map;
import java.util.Objects;

public class FeishuBitableCliService {
    private final FeishuCliTemplate template;

    public FeishuBitableCliService(FeishuCliTemplate template) {
        this.template = Objects.requireNonNull(template, "template must not be null");
    }

    public FeishuCliCommandResult createRecord(BitableRecordCreateRequest request) {
        return template.execute(FeishuCliCommandRequest
                .api("bitable", "POST", basePath(request.getAppToken(), request.getTableId()) + "/records")
                .withData("fields", request.getFields()));
    }

    public FeishuCliCommandResult queryRecords(BitableRecordQueryRequest request) {
        FeishuCliCommandRequest command = FeishuCliCommandRequest
                .api("bitable", "GET", basePath(request.getAppToken(), request.getTableId()) + "/records");
        if (request.getPageSize() != null) {
            command = command.withParam("page_size", request.getPageSize());
        }
        if (request.getPageToken() != null && !request.getPageToken().trim().isEmpty()) {
            command = command.withParam("page_token", request.getPageToken());
        }
        return template.execute(command);
    }

    public FeishuCliCommandResult updateCell(BitableCellUpdateRequest request) {
        return template.execute(FeishuCliCommandRequest
                .api("bitable", "PUT", basePath(request.getAppToken(), request.getTableId())
                        + "/records/" + request.getRecordId())
                .withData("fields", Map.of(request.getFieldName(), request.getValue())));
    }

    private static String basePath(String appToken, String tableId) {
        return "/open-apis/bitable/v1/apps/" + appToken + "/tables/" + tableId;
    }
}
