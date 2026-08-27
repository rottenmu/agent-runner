package com.zimo.starter.ai.agent;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认工具执行钩子：日志记录工具注册与调用（审计）。
 */
public class LoggingToolExecutionListener implements ToolExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingToolExecutionListener.class);

    @Override
    public boolean onPreExecute(String toolName, Object params) {
        log.info("工具执行前: {} params={}", toolName, params);
        return true;
    }

    @Override
    public void onPostExecute(String toolName, Object params, Object result) {
        log.info("工具执行成功: {} result={}", toolName, result);
    }

    @Override
    public void onError(String toolName, Object params, Throwable error) {
        log.warn("工具执行失败: {} error={}", toolName, error.getMessage());
    }

    @Override
    public void onToolRegistered(String toolName) {
        log.info("工具已注册: {}", toolName);
    }
}
