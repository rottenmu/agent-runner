package com.zimo.agentapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import cn.dev33.satoken.exception.NotLoginException;
import com.zimo.module.auth.web.AuthExceptionHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class AdminAuthBoundaryTest {

    @Test
    void adminShellDoesNotOwnAuthControllerOrSaTokenExceptionHandling() throws IOException {
        Path root = repositoryRoot();
        Path adminMain = root.resolve("agent-application/src/main/java/com/zimo/agentapplication");
        String globalExceptionHandler = read(adminMain.resolve("GlobalExceptionHandler.java"));

        assertThat(Files.exists(adminMain.resolve("AuthController.java"))).isFalse();
        assertThat(Files.exists(adminMain.resolve("LoginDTO.java"))).isFalse();
        assertThat(Files.exists(adminMain.resolve("RegisterDTO.java"))).isFalse();
        assertThat(globalExceptionHandler).doesNotContain("cn.dev33.satoken");
        assertThat(globalExceptionHandler).doesNotContain("NotLoginException");
    }

    @Test
    void authExceptionHandlerWinsOverAdminFallbackHandler() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new NotLoginController())
                .setControllerAdvice(new AuthExceptionHandler(), new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/not-login"))
                .andExpect(jsonPath("$.code").value(401));
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null
                && !(Files.exists(current.resolve("pom.xml"))
                && Files.isDirectory(current.resolve("modules"))
                && Files.isDirectory(current.resolve("framework")))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root not found");
        }
        return current;
    }

    @RestController
    private static class NotLoginController {
        @GetMapping("/not-login")
        public void notLogin() {
            throw new NotLoginException("not login", null, null);
        }
    }
}
