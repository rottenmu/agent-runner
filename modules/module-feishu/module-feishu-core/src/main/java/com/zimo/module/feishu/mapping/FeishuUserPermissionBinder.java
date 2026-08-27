package com.zimo.module.feishu.mapping;

import com.zimo.module.sys.context.PermissionCache;
import com.zimo.module.sys.context.UserPermissionContext;

import java.util.Collections;

public class FeishuUserPermissionBinder {

    public AutoCloseable bind(FeishuInternalUserSnapshot snapshot) {
        if (snapshot == null || snapshot.getUserId() == null) {
            PermissionCache.unbindCurrent();
            return PermissionCache::unbindCurrent;
        }
        UserPermissionContext context = UserPermissionContext.of(
                snapshot.getUserId(),
                snapshot.getAccount(),
                snapshot.getOrganizationId(),
                snapshot.getOrganizationName(),
                snapshot.getPermissions(),
                snapshot.getDataScope(),
                Collections.emptySet());
        PermissionCache.bindCurrent(context);
        return PermissionCache::unbindCurrent;
    }
}
