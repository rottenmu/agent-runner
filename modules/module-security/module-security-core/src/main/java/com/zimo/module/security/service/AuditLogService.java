package com.zimo.module.security.service;

import com.zimo.module.security.entity.SecAuditLog;
import com.zimo.module.security.mapper.SecAuditLogMapper;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 全链路审计日志服务：每次对话、工具调用、数据读取、修改操作永久留痕。
 *
 * <p>采用 <b>哈希链</b> 防篡改：每条日志记录 {@code hash = SHA256(prev_hash + 内容摘要 + 时间)}，
 * 前一跳哈希参与下一跳计算，任何一条被修改都会导致后续所有哈希失配，可在审计校验时定位篡改位置。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final String GENESIS = "genesis";

    private final SecAuditLogMapper auditLogMapper;

    public AuditLogService(SecAuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 记录一条审计日志（自动计算哈希链）。
     *
     * @param actorType 操作者类型（user/agent/system）
     * @param actorId 操作者 ID
     * @param action 动作（chat.send/tool.call/data.read/data.modify/approval.*）
     * @param objectType 对象类型
     * @param objectId 对象 ID
     * @param detail 明细
     * @param result success/failed
     * @param traceId 链路 ID（可空）
     * @param ip 来源 IP（可空）
     */
    public void record(String actorType, String actorId, String action, String objectType,
                       String objectId, String detail, String result, String traceId, String ip) {
        try {
            SecAuditLog last = lastLog();
            String prevHash = last == null ? GENESIS : last.getHash();
            String content = buildContent(actorType, actorId, action, objectType, objectId, detail, result, traceId, ip);
            String hash = sha256(prevHash + "|" + content);

            SecAuditLog entry = new SecAuditLog();
            entry.setTraceId(traceId == null ? "" : traceId);
            entry.setActorType(actorType);
            entry.setActorId(actorId == null ? "" : actorId);
            entry.setAction(action);
            entry.setObjectType(objectType == null ? "" : objectType);
            entry.setObjectId(objectId == null ? "" : objectId);
            entry.setDetail(truncate(detail, 4000));
            entry.setResult(result == null ? "success" : result);
            entry.setIp(ip == null ? "" : ip);
            entry.setPrevHash(prevHash);
            entry.setHash(hash);
            entry.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("审计日志写入失败: {}", e.getMessage());
        }
    }

    /** 查询日志（按时间倒序）。 */
    public List<SecAuditLog> listLogs(String action, String actorId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        return auditLogMapper.selectList(Wrappers.<SecAuditLog>lambdaQuery()
                .like(StrUtil.isNotBlank(action), SecAuditLog::getAction, action)
                .eq(StrUtil.isNotBlank(actorId), SecAuditLog::getActorId, actorId)
                .orderByDesc(SecAuditLog::getId)
                .last("LIMIT " + safeLimit));
    }

    /**
     * 校验整条审计链完整性：返回篡改位置列表。
     *
     * @return 每条 {@code {id, action, ok}}；ok=false 表示该条与链不一致
     */
    public List<Map<String, Object>> verifyChain() {
        List<SecAuditLog> logs = auditLogMapper.selectList(
                Wrappers.<SecAuditLog>lambdaQuery().orderByAsc(SecAuditLog::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        String expectedPrev = GENESIS;
        boolean broken = false;
        for (SecAuditLog entry : logs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", entry.getId());
            item.put("action", entry.getAction());
            boolean hashOk;
            if (broken) {
                // 前置链已断裂，后续无法验证
                hashOk = false;
            } else {
                String content = buildContent(entry.getActorType(), entry.getActorId(), entry.getAction(),
                        entry.getObjectType(), entry.getObjectId(), entry.getDetail(), entry.getResult(),
                        entry.getTraceId(), entry.getIp());
                String expectedHash = sha256(expectedPrev + "|" + content);
                hashOk = expectedHash.equals(entry.getHash());
                if (!hashOk || !expectedPrev.equals(entry.getPrevHash())) {
                    broken = true;
                }
            }
            item.put("ok", hashOk);
            result.add(item);
            expectedPrev = entry.getHash();
        }
        return result;
    }

    private SecAuditLog lastLog() {
        return auditLogMapper.selectOne(Wrappers.<SecAuditLog>lambdaQuery()
                .orderByDesc(SecAuditLog::getId)
                .last("LIMIT 1"));
    }

    private String buildContent(String actorType, String actorId, String action, String objectType,
                                String objectId, String detail, String result, String traceId, String ip) {
        return String.join("|",
                nvl(actorType), nvl(actorId), nvl(action), nvl(objectType), nvl(objectId),
                nvl(detail), nvl(result), nvl(traceId), nvl(ip));
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String sha256(String input) {
        // hutool SecureUtil.sha256：入参为 null 时返回空串，避免哈希链断裂
        return input == null ? "" : SecureUtil.sha256(input);
    }
}
