package com.zimo.module.sys;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SysPluginRegisterTest {

    @Test
    void exposesReadableChinesePluginName() {
        SysPluginRegister register = new SysPluginRegister();

        assertThat(register.getPluginName()).isEqualTo("系统管理");
        assertThat(register.getOrder()).isEqualTo(6);
    }
}
