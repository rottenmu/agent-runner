package com.zimo.module.sys.util;

import com.zimo.module.sys.annotation.FieldDesensitize;

/**
 * Common desensitization helpers for permission controlled response fields.
 */
public final class DesensitizeUtil {

    private static final String DEFAULT_MASK = "*";
    private static final String FIXED_SECRET_MASK = "******";

    private DesensitizeUtil() {
    }

    public static String desensitize(String value, FieldDesensitize.Type type) {
        if (value == null) {
            return null;
        }
        FieldDesensitize.Type actualType = type == null ? FieldDesensitize.Type.DEFAULT : type;
        return switch (actualType) {
            case PHONE -> maskPhone(value);
            case EMAIL -> maskEmail(value);
            case ID_CARD -> mask(value, 6, 4, DEFAULT_MASK);
            case BANK_CARD -> maskBankCard(value);
            case NAME -> maskName(value);
            case ADDRESS -> mask(value, 6, 0, DEFAULT_MASK);
            case PASSWORD -> value.isEmpty() ? "" : FIXED_SECRET_MASK;
            case CUSTOM, DEFAULT -> maskPrivacy(value);
        };
    }

    public static String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        if (phone.isEmpty()) {
            return "";
        }
        if (phone.length() < 7) {
            return repeat(DEFAULT_MASK, phone.length());
        }
        return mask(phone, 3, 4, DEFAULT_MASK);
    }

    public static String maskPrice(Object price) {
        if (price == null) {
            return null;
        }
        String value = String.valueOf(price);
        if (value.isEmpty()) {
            return "";
        }
        return "***";
    }

    public static String maskPrivacy(String value) {
        if (value == null) {
            return null;
        }
        if (value.isEmpty()) {
            return "";
        }
        if (value.length() <= 2) {
            return repeat(DEFAULT_MASK, value.length());
        }
        return value.charAt(0) + "****" + value.charAt(value.length() - 1);
    }

    public static String mask(String value, int prefixKeep, int suffixKeep, String mask) {
        if (value == null) {
            return null;
        }
        if (value.isEmpty()) {
            return "";
        }
        String actualMask = mask == null || mask.isEmpty() ? DEFAULT_MASK : mask;
        int actualPrefixKeep = Math.max(prefixKeep, 0);
        int actualSuffixKeep = Math.max(suffixKeep, 0);
        if (actualPrefixKeep + actualSuffixKeep >= value.length()) {
            return repeat(actualMask, value.length());
        }

        int maskLength = value.length() - actualPrefixKeep - actualSuffixKeep;
        String prefix = value.substring(0, actualPrefixKeep);
        String suffix = actualSuffixKeep == 0 ? "" : value.substring(value.length() - actualSuffixKeep);
        return prefix + repeat(actualMask, maskLength) + suffix;
    }

    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return maskPrivacy(email);
        }
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (localPart.length() == 1) {
            return localPart + "***" + domain;
        }
        return localPart.charAt(0) + "***" + domain;
    }

    public static String maskBankCard(String bankCard) {
        if (bankCard == null) {
            return null;
        }
        if (bankCard.isEmpty()) {
            return "";
        }
        String digits = bankCard.replace(" ", "");
        if (digits.length() < 8) {
            return repeat(DEFAULT_MASK, digits.length());
        }
        return digits.substring(0, 4) + " **** **** " + digits.substring(digits.length() - 4);
    }

    public static String maskName(String name) {
        if (name == null) {
            return null;
        }
        if (name.isEmpty()) {
            return "";
        }
        if (name.length() == 1) {
            return DEFAULT_MASK;
        }
        return name.charAt(0) + repeat(DEFAULT_MASK, name.length() - 1);
    }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * Math.max(count, 0));
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
