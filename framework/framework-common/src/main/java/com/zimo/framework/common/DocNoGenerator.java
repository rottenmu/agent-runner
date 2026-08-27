package com.zimo.framework.common;

import java.time.LocalDate;
import cn.hutool.core.util.StrUtil;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 单据编号生成工具。
 *
 * <p>对应 ERPNext 的 naming series 语义：按「前缀-日期-随机段」的格式生成
 * 业务单据编号，例如 {@code STE-20260730-483920}。该工具位于 framework-common，
 * 不依赖任何 Spring 组件，可被各业务模块 core 层直接使用。</p>
 *
 * @author WorkBuddy
 * @since 2026-07-30
 */
public final class DocNoGenerator {

    /** 日期段格式：yyyyMMdd */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 随机段下界（含） */
    private static final int RANDOM_MIN = 100000;

    /** 随机段上界（不含） */
    private static final int RANDOM_MAX = 1000000;

    /**
     * 工具类禁止实例化。
     */
    private DocNoGenerator() {
    }

    /**
     * 生成一个新的单据编号。
     *
     * @param prefix 单据前缀，例如 STE / SO / PO / JV
     * @return 形如 {@code 前缀-yyyyMMdd-6位随机数} 的单据编号
     */
    public static String next(String prefix) {
        String datePart = LocalDate.now().format(DATE_FORMAT);
        int randomPart = ThreadLocalRandom.current().nextInt(RANDOM_MIN, RANDOM_MAX);
        return prefix + "-" + datePart + "-" + randomPart;
    }

    /**
     * 若给定编号为空则生成新编号，否则原样返回。
     *
     * @param existing 调用方传入的编号，可能为 null 或空白
     * @param prefix   单据前缀
     * @return 可直接落库的单据编号
     */
    public static String nextIfBlank(String existing, String prefix) {
        if (StrUtil.isBlank(existing)) {
            return next(prefix);
        }
        return existing;
    }
}
