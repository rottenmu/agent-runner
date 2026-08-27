package com.zimo.module.feishu.log;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ps_feishu_message_log")
public class FeishuMessageLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String chatId;
    private String tenantKey;
    private String senderUserId;
    private String senderOpenId;
    private Long internalUserId;
    private String internalAccount;
    private String commandText;
    private String replyType;
    private String stage;
    private Integer success;
    private Integer errorCode;
    private String errorMessage;
    private Long costMillis;
    private String rawPayload;
    private String replyPayload;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public String getTenantKey() {
        return tenantKey;
    }

    public void setTenantKey(String tenantKey) {
        this.tenantKey = tenantKey;
    }

    public String getSenderUserId() {
        return senderUserId;
    }

    public void setSenderUserId(String senderUserId) {
        this.senderUserId = senderUserId;
    }

    public String getSenderOpenId() {
        return senderOpenId;
    }

    public void setSenderOpenId(String senderOpenId) {
        this.senderOpenId = senderOpenId;
    }

    public Long getInternalUserId() {
        return internalUserId;
    }

    public void setInternalUserId(Long internalUserId) {
        this.internalUserId = internalUserId;
    }

    public String getInternalAccount() {
        return internalAccount;
    }

    public void setInternalAccount(String internalAccount) {
        this.internalAccount = internalAccount;
    }

    public String getCommandText() {
        return commandText;
    }

    public void setCommandText(String commandText) {
        this.commandText = commandText;
    }

    public String getReplyType() {
        return replyType;
    }

    public void setReplyType(String replyType) {
        this.replyType = replyType;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public Integer getSuccess() {
        return success;
    }

    public void setSuccess(Integer success) {
        this.success = success;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(Integer errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getCostMillis() {
        return costMillis;
    }

    public void setCostMillis(Long costMillis) {
        this.costMillis = costMillis;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    public String getReplyPayload() {
        return replyPayload;
    }

    public void setReplyPayload(String replyPayload) {
        this.replyPayload = replyPayload;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
