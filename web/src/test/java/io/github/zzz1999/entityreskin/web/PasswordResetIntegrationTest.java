package io.github.zzz1999.entityreskin.web;

import io.github.zzz1999.entityreskin.web.account.PasswordReset;
import io.github.zzz1999.entityreskin.web.account.PasswordResetRepository;
import io.github.zzz1999.entityreskin.web.account.User;
import io.github.zzz1999.entityreskin.web.account.UserRepository;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Email-based account recovery and password-complexity enforcement: a valid reset code changes
 * the password, the forgot endpoint never reveals whether an account exists, and registration
 * rejects passwords that do not meet the policy. (Captcha is disabled here, as in production
 * dev mode, so these flows run without a captcha provider.)
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:entityreskin-reset;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "entityreskin.storage.root=build/test-blobs-reset",
        "entityreskin.jwt.secret=test-secret-0123456789abcdef0123456789abcdef0123456789",
        "entityreskin.signing.secret=test-signing-secret-0123456789abcdef0123456789",
        "entityreskin.email.dev-mode=true"
})
class PasswordResetIntegrationTest {

    private static final Gson GSON = new Gson();

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordResetRepository resets;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void resetWithAValidCodeChangesThePassword() throws Exception {
        User user = new User("reset-a@example.com", passwordEncoder.encode("oldpassword1"));
        user.setEnabled(true);
        users.save(user);
        resets.save(new PasswordReset("reset-a@example.com", passwordEncoder.encode("123456"),
                Instant.now().plus(10, ChronoUnit.MINUTES)));

        mockMvc.perform(post("/api/auth/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(Map.of(
                                "email", "reset-a@example.com",
                                "code", "123456",
                                "newPassword", "Newpassword1"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(Map.of("email", "reset-a@example.com", "password", "Newpassword1"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(Map.of("email", "reset-a@example.com", "password", "oldpassword1"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotReturnsGenericResponseForUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/auth/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(Map.of("email", "nobody-b@example.com"))))
                .andExpect(status().isOk());
    }

    @Test
    void registrationRejectsWeakPasswords() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(Map.of("email", "weak-c@example.com", "password", "short"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetCodeSendIsSuppressedWithinCooldown() throws Exception {
        User user = new User("cooldown-d@qq.com", passwordEncoder.encode("Password123"));
        user.setEnabled(true);
        users.save(user);
        String body = GSON.toJson(Map.of("email", "cooldown-d@qq.com"));

        mockMvc.perform(post("/api/auth/forgot")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        PasswordReset first = resets.findTopByEmailOrderByCreatedAtDesc("cooldown-d@qq.com").orElseThrow();

        // Inside the cooldown the response is unchanged but no new reset code may be minted.
        mockMvc.perform(post("/api/auth/forgot")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        PasswordReset second = resets.findTopByEmailOrderByCreatedAtDesc("cooldown-d@qq.com").orElseThrow();

        assertEquals(first.getCodeHash(), second.getCodeHash());
    }
}
