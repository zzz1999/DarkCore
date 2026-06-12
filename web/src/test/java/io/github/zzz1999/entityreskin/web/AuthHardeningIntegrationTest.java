package io.github.zzz1999.entityreskin.web;

import io.github.zzz1999.entityreskin.protocol.manifest.ResourceKind;
import io.github.zzz1999.entityreskin.web.account.EmailVerification;
import io.github.zzz1999.entityreskin.web.account.EmailVerificationRepository;
import io.github.zzz1999.entityreskin.web.account.User;
import io.github.zzz1999.entityreskin.web.account.UserRepository;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hardening behavior: no account enumeration through registration, verification brute-force
 * throttling, and per-account storage quota enforcement (quota is set to 16 bytes here).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:entityreskin-hardening;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "entityreskin.storage.root=build/test-blobs-hardening",
        "entityreskin.jwt.secret=test-secret-0123456789abcdef0123456789abcdef0123456789",
        "entityreskin.signing.secret=test-signing-secret-0123456789abcdef0123456789",
        "entityreskin.email.dev-mode=true",
        "entityreskin.upload.user-quota-bytes=16"
})
class AuthHardeningIntegrationTest {

    private static final Gson GSON = new Gson();

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository users;
    @Autowired
    private EmailVerificationRepository verifications;
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
    void registrationDoesNotRevealExistingAccounts() throws Exception {
        User existing = new User("registered@qq.com", passwordEncoder.encode("Password123"));
        existing.setEnabled(true);
        users.save(existing);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(
                                Map.of("email", "registered@qq.com", "password", "Password456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("verification_sent"));
    }

    @Test
    void verificationAttemptsAreThrottled() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(
                                Map.of("email", "throttled@qq.com", "password", "Password123"))))
                .andExpect(status().isOk());

        String wrongCode = GSON.toJson(Map.of("email", "throttled@qq.com", "code", "999999"));
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/auth/verify")
                            .contentType(MediaType.APPLICATION_JSON).content(wrongCode))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON).content(wrongCode))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void uploadsBeyondTheAccountQuotaAreRejected() throws Exception {
        User uploader = new User("uploader@example.com", passwordEncoder.encode("Password123"));
        uploader.setEnabled(true);
        users.save(uploader);
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(
                                Map.of("email", "uploader@example.com", "password", "Password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String jwt = JsonParser.parseString(body).getAsJsonObject().get("token").getAsString();

        mockMvc.perform(multipart("/api/assets")
                        .file(new MockMultipartFile("file", "a.bin", null,
                                "10 bytes..".getBytes(StandardCharsets.UTF_8)))
                        .param("kind", ResourceKind.GEOMETRY)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/assets")
                        .file(new MockMultipartFile("file", "b.bin", null,
                                "ten more.!".getBytes(StandardCharsets.UTF_8)))
                        .param("kind", ResourceKind.GEOMETRY)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrationRejectsDisallowedEmailDomains() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(
                                Map.of("email", "player@gmail.com", "password", "Password123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrationAcceptsAllowedEmailDomains() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(
                                Map.of("email", "player@163.com", "password", "Password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("verification_sent"));
    }

    @Test
    void verificationCodeSendIsSuppressedWithinCooldown() throws Exception {
        String email = "cooldown@qq.com";
        String body = GSON.toJson(Map.of("email", email, "password", "Password123"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        EmailVerification first = verifications.findTopByEmailOrderByCreatedAtDesc(email).orElseThrow();

        // A second request inside the cooldown still succeeds, but no new code may be minted:
        // the pending verification record is left untouched.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        EmailVerification second = verifications.findTopByEmailOrderByCreatedAtDesc(email).orElseThrow();

        assertEquals(first.getId(), second.getId());
        assertEquals(first.getCodeHash(), second.getCodeHash());
    }

    @Test
    void registrationStagesPasswordUntilVerification() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(Map.of("email", "stage@qq.com", "password", "Password123"))))
                .andExpect(status().isOk());

        User user = users.findByEmail("stage@qq.com").orElseThrow();
        assertFalse(user.isEnabled());
        // The submitted password is not yet applied to the account...
        assertFalse(passwordEncoder.matches("Password123", user.getPasswordHash()));
        // ...it is staged on the pending verification until the emailed code is verified.
        EmailVerification pending =
                verifications.findTopByEmailOrderByCreatedAtDesc("stage@qq.com").orElseThrow();
        assertTrue(passwordEncoder.matches("Password123", pending.getPendingPasswordHash()));
    }

    @Test
    void verificationAppliesStagedPasswordAndEnablesLogin() throws Exception {
        // A pending registration: a disabled account with a placeholder password and a verification
        // record carrying the staged password and code.
        users.save(new User("apply@qq.com", ""));
        verifications.save(new EmailVerification("apply@qq.com", passwordEncoder.encode("654321"),
                passwordEncoder.encode("Newpassword1"), Instant.now().plusSeconds(600)));

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(Map.of("email", "apply@qq.com", "code", "654321"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(Map.of("email", "apply@qq.com", "password", "Newpassword1"))))
                .andExpect(status().isOk());
    }
}
