package com.zimo.module.sys.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zimo.module.sys.annotation.FieldDesensitize;
import com.zimo.module.sys.enums.DataLevelEnum;
import com.zimo.module.sys.util.DesensitizeUtil;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Post-query field masking service based on {@link FieldDesensitize}.
 */
public class FieldMaskService {

    private static final String PRICE_MASK = "****";
    private static final ThreadLocal<AccessProfile> ACCESS_PROFILE = new ThreadLocal<>();
    private static final ConcurrentMap<Class<?>, List<MaskField>> FIELD_CACHE = new ConcurrentHashMap<>();

    private final boolean enabled;

    public FieldMaskService() {
        this(true);
    }

    public FieldMaskService(boolean enabled) {
        this.enabled = enabled;
    }

    public static void setAccessProfile(AccessProfile accessProfile) {
        if (accessProfile == null) {
            ACCESS_PROFILE.remove();
            return;
        }
        ACCESS_PROFILE.set(accessProfile);
    }

    public static void clearAccessProfile() {
        ACCESS_PROFILE.remove();
    }

    public static AccessProfile currentAccessProfile() {
        AccessProfile accessProfile = ACCESS_PROFILE.get();
        return accessProfile == null ? AccessProfile.builder().build() : accessProfile;
    }

    public <T> T mask(T result) {
        if (!enabled || result == null) {
            return result;
        }
        maskValue(result, currentAccessProfile(), Collections.newSetFromMap(new IdentityHashMap<>()));
        return result;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void maskValue(Object value, AccessProfile accessProfile, Set<Object> visited) {
        if (value == null || isSimpleValue(value.getClass()) || visited.contains(value)) {
            return;
        }
        visited.add(value);

        if (value instanceof IPage<?> page) {
            maskValue(page.getRecords(), accessProfile, visited);
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                maskValue(item, accessProfile, visited);
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object item : map.values()) {
                maskValue(item, accessProfile, visited);
            }
            return;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                maskValue(Array.get(value, i), accessProfile, visited);
            }
            return;
        }

        for (MaskField maskField : maskFields(value.getClass())) {
            maskField.mask(value, accessProfile);
        }
    }

    private List<MaskField> maskFields(Class<?> type) {
        return FIELD_CACHE.computeIfAbsent(type, this::scanMaskFields);
    }

    private List<MaskField> scanMaskFields(Class<?> type) {
        List<MaskField> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                FieldDesensitize annotation = field.getAnnotation(FieldDesensitize.class);
                if (annotation == null || Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                fields.add(new MaskField(field, annotation));
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static boolean isSimpleValue(Class<?> type) {
        return type.isPrimitive()
                || CharSequence.class.isAssignableFrom(type)
                || Number.class.isAssignableFrom(type)
                || Boolean.class == type
                || Character.class == type
                || Enum.class.isAssignableFrom(type)
                || type.getName().startsWith("java.time.");
    }

    private static final class MaskField {

        private final Field field;
        private final FieldDesensitize annotation;
        private final DataLevelEnum requiredLevel;
        private final boolean privacyField;
        private final boolean priceField;
        private final boolean processField;

        private MaskField(Field field, FieldDesensitize annotation) {
            this.field = field;
            this.annotation = annotation;
            this.priceField = isPriceField(field.getName());
            this.processField = isProcessField(field.getName());
            this.privacyField = isPrivacyType(annotation.type());
            this.requiredLevel = priceField || processField ? DataLevelEnum.CONFIDENTIAL : DataLevelEnum.INTERNAL;
        }

        private void mask(Object target, AccessProfile accessProfile) {
            try {
                Object rawValue = field.get(target);
                if (rawValue == null) {
                    return;
                }
                String fieldName = field.getName();
                if (!privacyField && accessProfile.canRead(fieldName, requiredLevel)) {
                    return;
                }
                String maskedValue = maskValue(String.valueOf(rawValue));
                writeMaskedValue(target, maskedValue);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("Failed to mask field: " + field.getName(), ex);
            }
        }

        private String maskValue(String value) {
            if (priceField) {
                return PRICE_MASK;
            }
            if (processField) {
                return DesensitizeUtil.mask(value, 1, 1, "*");
            }
            if (annotation.prefixKeep() >= 0 || annotation.suffixKeep() >= 0) {
                return DesensitizeUtil.mask(
                        value,
                        annotation.prefixKeep(),
                        annotation.suffixKeep(),
                        annotation.mask()
                );
            }
            return DesensitizeUtil.desensitize(value, annotation.type());
        }

        private void writeMaskedValue(Object target, String maskedValue) throws IllegalAccessException {
            Class<?> fieldType = field.getType();
            if (String.class == fieldType || CharSequence.class.isAssignableFrom(fieldType) || Object.class == fieldType) {
                field.set(target, maskedValue);
            }
        }
    }

    private static boolean isPrivacyType(FieldDesensitize.Type type) {
        return type == FieldDesensitize.Type.PHONE
                || type == FieldDesensitize.Type.EMAIL
                || type == FieldDesensitize.Type.ID_CARD
                || type == FieldDesensitize.Type.BANK_CARD
                || type == FieldDesensitize.Type.NAME
                || type == FieldDesensitize.Type.ADDRESS
                || type == FieldDesensitize.Type.PASSWORD;
    }

    private static boolean isPriceField(String fieldName) {
        String name = normalize(fieldName);
        return name.contains("price")
                || name.contains("cost")
                || name.contains("amount")
                || name.contains("quote")
                || name.contains("budget");
    }

    private static boolean isProcessField(String fieldName) {
        String name = normalize(fieldName);
        return name.contains("process")
                || name.contains("craft")
                || name.contains("route")
                || name.contains("formula")
                || name.contains("technology");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static int rank(DataLevelEnum dataLevel) {
        if (dataLevel == null) {
            return 0;
        }
        return switch (dataLevel) {
            case PUBLIC -> 0;
            case INTERNAL -> 1;
            case CONFIDENTIAL -> 2;
        };
    }

    public static final class AccessProfile {

        private final DataLevelEnum dataLevel;
        private final Set<String> allowedFields;

        private AccessProfile(Builder builder) {
            this.dataLevel = builder.dataLevel == null ? DataLevelEnum.PUBLIC : builder.dataLevel;
            this.allowedFields = Collections.unmodifiableSet(new HashSet<>(builder.allowedFields));
        }

        public static Builder builder() {
            return new Builder();
        }

        public DataLevelEnum getDataLevel() {
            return dataLevel;
        }

        public Set<String> getAllowedFields() {
            return allowedFields;
        }

        private boolean canRead(String fieldName, DataLevelEnum requiredLevel) {
            return allowedFields.contains(fieldName) || rank(dataLevel) >= rank(requiredLevel);
        }

        public static final class Builder {

            private DataLevelEnum dataLevel = DataLevelEnum.PUBLIC;
            private Collection<String> allowedFields = Collections.emptyList();

            public Builder dataLevel(DataLevelEnum dataLevel) {
                this.dataLevel = dataLevel;
                return this;
            }

            public Builder allowedFields(Collection<String> allowedFields) {
                this.allowedFields = allowedFields == null ? Collections.emptyList() : allowedFields;
                return this;
            }

            public AccessProfile build() {
                return new AccessProfile(this);
            }
        }
    }
}
