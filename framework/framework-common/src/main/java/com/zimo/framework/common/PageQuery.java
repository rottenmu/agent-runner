package com.zimo.framework.common;

import lombok.Data;

@Data
public class PageQuery {
    private long current = 1;
    private long size = 10;
}
