package com.zimo.module.sys.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class PermissionAnnotationContractTest {

    @Test
    void hasPermTargetsTypeAndMethodAndKeepsPermissionValueAtRuntime() throws Exception {
        assertRuntimeDocumentedAnnotation(HasPerm.class);
        assertThat(targetsOf(HasPerm.class)).containsExactlyInAnyOrder(ElementType.TYPE, ElementType.METHOD);

        HasPerm annotation = SampleApi.class.getDeclaredMethod("protectedAction").getAnnotation(HasPerm.class);

        assertThat(annotation.value()).isEqualTo("sys:role:list");
    }

    @Test
    void ignorePermissionTargetsTypeAndMethodAtRuntime() {
        assertRuntimeDocumentedAnnotation(IgnorePermission.class);
        assertThat(targetsOf(IgnorePermission.class)).containsExactlyInAnyOrder(ElementType.TYPE, ElementType.METHOD);
    }

    @Test
    void dataScopeTargetsTypeAndMethodAndBindsBusinessTableName() throws Exception {
        assertRuntimeDocumentedAnnotation(DataScope.class);
        assertThat(targetsOf(DataScope.class)).containsExactlyInAnyOrder(ElementType.TYPE, ElementType.METHOD);

        Method method = SampleApi.class.getDeclaredMethod("scopedAction");
        DataScope annotation = method.getAnnotation(DataScope.class);

        assertThat(annotation.value()).isEqualTo("pm_project");
        assertThat(annotation.tableAlias()).isEqualTo("p");
        assertThat(annotation.deptColumn()).isEqualTo("dept_id");
        assertThat(annotation.userColumn()).isEqualTo("create_by");
    }

    @Test
    void fieldDesensitizeTargetsFieldAndSupportsDesensitizeType() throws Exception {
        assertRuntimeDocumentedAnnotation(FieldDesensitize.class);
        assertThat(targetsOf(FieldDesensitize.class)).containsExactly(ElementType.FIELD);

        Field field = SamplePayload.class.getDeclaredField("phone");
        FieldDesensitize annotation = field.getAnnotation(FieldDesensitize.class);

        assertThat(annotation.type()).isEqualTo(FieldDesensitize.Type.PHONE);
        assertThat(annotation.mask()).isEqualTo("");
    }

    private static void assertRuntimeDocumentedAnnotation(Class<?> annotationType) {
        Retention retention = annotationType.getAnnotation(Retention.class);

        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotationType.getAnnotation(Documented.class)).isNotNull();
    }

    private static ElementType[] targetsOf(Class<?> annotationType) {
        return annotationType.getAnnotation(Target.class).value();
    }

    private static class SampleApi {
        @HasPerm("sys:role:list")
        void protectedAction() {
        }

        @IgnorePermission
        void publicAction() {
        }

        @DataScope(value = "pm_project", tableAlias = "p")
        void scopedAction() {
        }
    }

    private static class SamplePayload {
        @FieldDesensitize(type = FieldDesensitize.Type.PHONE)
        private String phone;
    }
}
