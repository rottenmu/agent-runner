package com.zimo.module.sys.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.auth.entity.SysUser;
import org.junit.jupiter.api.Test;

class SysUserJsonContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void passwordCanBeReadFromCreateRequestButIsNotWrittenToResponse() throws Exception {
        SysUser user = objectMapper.readValue("""
                {
                  "username": "demo",
                  "password": "demo123"
                }
                """, SysUser.class);

        assertThat(user.getPassword()).isEqualTo("demo123");
        assertThat(objectMapper.writeValueAsString(user)).doesNotContain("demo123");
    }
}
