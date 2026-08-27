package com.zimo.module.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SysUserJsonContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void passwordIsWriteOnlyAndDepartmentIsVisible() throws Exception {
        SysUser user = objectMapper.readValue("""
                {"id":1,"username":"admin","password":"secret","nickname":"管理员","department":"行政"}
                """, SysUser.class);

        assertThat(user.getPassword()).isEqualTo("secret");
        assertThat(user.getDepartment()).isEqualTo("行政");
        assertThat(objectMapper.writeValueAsString(user)).doesNotContain("secret");
        assertThat(objectMapper.writeValueAsString(user)).contains("\"department\":\"行政\"");
    }
}
