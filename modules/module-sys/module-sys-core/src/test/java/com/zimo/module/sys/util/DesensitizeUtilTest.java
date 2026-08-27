package com.zimo.module.sys.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.module.sys.annotation.FieldDesensitize;
import org.junit.jupiter.api.Test;

class DesensitizeUtilTest {

    @Test
    void masksPhoneByKeepingFirstThreeAndLastFourDigits() {
        assertThat(DesensitizeUtil.maskPhone("13812348000")).isEqualTo("138****8000");
        assertThat(DesensitizeUtil.maskPhone("123")).isEqualTo("***");
        assertThat(DesensitizeUtil.maskPhone(null)).isNull();
    }

    @Test
    void masksPriceAndGenericPrivacyValues() {
        assertThat(DesensitizeUtil.maskPrice("1299.80")).isEqualTo("***");
        assertThat(DesensitizeUtil.maskPrice("")).isEmpty();
        assertThat(DesensitizeUtil.maskPrivacy("secret-token")).isEqualTo("s****n");
        assertThat(DesensitizeUtil.maskPrivacy("A")).isEqualTo("*");
    }

    @Test
    void desensitizesCommonFieldTypes() {
        assertThat(DesensitizeUtil.desensitize("dev@example.com", FieldDesensitize.Type.EMAIL))
                .isEqualTo("d***@example.com");
        assertThat(DesensitizeUtil.desensitize("6222020202021234", FieldDesensitize.Type.BANK_CARD))
                .isEqualTo("6222 **** **** 1234");
        assertThat(DesensitizeUtil.desensitize("110101199001011234", FieldDesensitize.Type.ID_CARD))
                .isEqualTo("110101********1234");
        assertThat(DesensitizeUtil.desensitize("my-secret", FieldDesensitize.Type.PASSWORD))
                .isEqualTo("******");
    }

    @Test
    void supportsCustomKeepLengthMasking() {
        assertThat(DesensitizeUtil.mask("abcdefgh", 2, 2, "#")).isEqualTo("ab####gh");
        assertThat(DesensitizeUtil.mask("abc", 2, 2, "*")).isEqualTo("***");
    }
}
