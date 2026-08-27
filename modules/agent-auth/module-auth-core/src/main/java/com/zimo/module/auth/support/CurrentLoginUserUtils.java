package com.zimo.module.auth.support;

import com.zimo.framework.common.BizException;
import com.zimo.module.auth.entity.SysUser;
import com.zimo.module.auth.mapper.SysUserMapper;
import com.zimo.module.auth.security.AuthSessionService;
import java.util.Objects;

/**
 * 当前登录用户工具类，统一从认证会话和用户表中获取当前用户基础资料。
 *
 * <p>该工具类不持有 Spring Bean 状态，调用方需要传入当前模块已有的 AuthSessionService 和 SysUserMapper，便于 Controller、Service 和其他模块复用。</p>
 *
 * @since 2026-07-21
 */
public final class CurrentLoginUserUtils {
    private CurrentLoginUserUtils() {
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @param sessionService 认证会话服务，不允许为空
     * @return 当前登录用户 ID
     */
    public static Long currentUserId(AuthSessionService sessionService) {
        Objects.requireNonNull(sessionService, "sessionService must not be null");
        return sessionService.getLoginIdAsLong();
    }

    /**
     * 获取当前登录用户资料快照。
     *
     * @param sessionService 认证会话服务，不允许为空
     * @param userMapper 用户表数据访问对象，不允许为空
     * @return 当前登录用户资料快照
     * @throws BizException 当会话中的用户 ID 已无法查询到有效用户时抛出
     */
    public static CurrentLoginUser currentUser(AuthSessionService sessionService, SysUserMapper userMapper) {
        Objects.requireNonNull(userMapper, "userMapper must not be null");
        Long userId = currentUserId(sessionService);
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "当前登录用户不存在");
        }
        return from(user);
    }

    /**
     * 将用户实体转换为当前登录用户快照。
     *
     * @param user 用户实体，不允许为空
     * @return 当前登录用户资料快照
     */
    public static CurrentLoginUser from(SysUser user) {
        Objects.requireNonNull(user, "user must not be null");
        return new CurrentLoginUser(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getDepartment(),
                user.getAvatar());
    }
}