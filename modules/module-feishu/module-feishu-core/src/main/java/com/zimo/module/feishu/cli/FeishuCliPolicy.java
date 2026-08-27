package com.zimo.module.feishu.cli;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class FeishuCliPolicy {
    private final boolean allowAll;
    private final Set<String> allowedBusinessTypes;

    private FeishuCliPolicy(boolean allowAll, Set<String> allowedBusinessTypes) {
        this.allowAll = allowAll;
        this.allowedBusinessTypes = Collections.unmodifiableSet(new LinkedHashSet<>(allowedBusinessTypes));
    }

    public static FeishuCliPolicy allowAll() {
        return new FeishuCliPolicy(true, Collections.emptySet());
    }

    public static FeishuCliPolicy allowOnly(Set<String> allowedBusinessTypes) {
        return new FeishuCliPolicy(false, allowedBusinessTypes == null ? Collections.emptySet() : allowedBusinessTypes);
    }

    public boolean allows(String businessType) {
        if (allowAll) {
            return true;
        }
        return businessType != null && allowedBusinessTypes.contains(businessType);
    }

    public Set<String> getAllowedBusinessTypes() {
        return allowedBusinessTypes;
    }
}
