package com.zimo.module.auth.support;

import java.util.List;

public final class DepartmentOptions {
    public static final List<String> ALL = List.of("采购部", "行政", "设计部", "生产部");

    private DepartmentOptions() {
    }

    public static boolean isAllowed(String department) {
        return department != null && ALL.contains(department.trim());
    }
}
