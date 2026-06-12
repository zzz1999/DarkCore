package io.github.zzz1999.entityreskin.web;

import io.github.zzz1999.entityreskin.web.account.User;
import io.github.zzz1999.entityreskin.web.account.UserRepository;
import io.github.zzz1999.entityreskin.web.billing.BillingService;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Billing and referral behavior: recharge credits the account and pays the inviter a 10% bonus;
 * traffic charges debit atomically and reject when exhausted; the low-balance alert is one-shot
 * and re-arms on recharge; registration records the inviter; the recharge endpoint is
 * secret-guarded; and the profile/invite endpoints work for an authenticated account.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:entityreskin-billing;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "entityreskin.storage.root=build/test-blobs-billing",
        "entityreskin.jwt.secret=test-secret-0123456789abcdef0123456789abcdef0123456789",
        "entityreskin.signing.secret=test-signing-secret-0123456789abcdef0123456789",
        "entityreskin.email.dev-mode=true",
        "entityreskin.billing.recharge-secret=test-recharge-secret",
        "entityreskin.billing.referral-bonus-percent=10"
})
class BillingIntegrationTest {

    private static final Gson GSON = new Gson();
    private static final String PASSWORD = "Password123";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private BillingService billingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private User enabledUser(String email, String inviteCode) {
        User user = new User(email, passwordEncoder.encode(PASSWORD));
        user.setEnabled(true);
        user.setInviteCode(inviteCode);
        return users.save(user);
    }

    private User reload(String email) {
        return users.findByEmail(email).orElseThrow();
    }

    @Test
    void rechargePaysReferralBonusToInviter() {
        enabledUser("inviter-a@example.com", "INVITERA");
        User invitee = enabledUser("invitee-a@example.com", "INVITEEA");
        invitee.setInvitedByEmail("inviter-a@example.com");
        users.save(invitee);

        billingService.recharge("invitee-a@example.com", 1000);

        assertEquals(1000, reload("invitee-a@example.com").getBalanceBytes());
        assertEquals(100, reload("inviter-a@example.com").getBalanceBytes());
    }

    @Test
    void chargeTrafficDebitsAtomicallyAndRejectsWhenInsufficient() {
        enabledUser("owner-b@example.com", "OWNERBBB");
        billingService.recharge("owner-b@example.com", 500);

        assertTrue(billingService.chargeTraffic("owner-b@example.com", 300));
        assertEquals(200, reload("owner-b@example.com").getBalanceBytes());
        assertFalse(billingService.chargeTraffic("owner-b@example.com", 300));
        assertEquals(200, reload("owner-b@example.com").getBalanceBytes());
    }

    @Test
    void lowBalanceAlertIsOneShotAndReArmsOnRecharge() {
        User user = enabledUser("alert-c@example.com", "ALERTCCC");
        user.setLowBalanceThresholdBytes(100);
        user.setBalanceBytes(150);
        users.save(user);

        assertTrue(billingService.chargeTraffic("alert-c@example.com", 100)); // 150 -> 50, below 100
        assertNotNull(reload("alert-c@example.com").getLowBalanceNotifiedAt());

        billingService.recharge("alert-c@example.com", 200); // 50 -> 250, above threshold: re-arm
        assertNull(reload("alert-c@example.com").getLowBalanceNotifiedAt());
    }

    @Test
    void rechargeEndpointRequiresTheSharedSecret() throws Exception {
        enabledUser("secret-d@example.com", "SECRETDD");
        String body = GSON.toJson(Map.of("email", "secret-d@example.com", "creditBytes", 1000));

        mockMvc.perform(post("/api/billing/recharge")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/billing/recharge")
                        .header("X-Recharge-Secret", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/billing/recharge")
                        .header("X-Recharge-Secret", "test-recharge-secret")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceBytes").value(1000));
    }

    @Test
    void registrationRecordsTheInviter() throws Exception {
        enabledUser("inviter-e@example.com", "INVITERE");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(Map.of(
                                "email", "invitee-e@qq.com",
                                "password", PASSWORD,
                                "inviteCode", "INVITERE"))))
                .andExpect(status().isOk());

        User invitee = reload("invitee-e@qq.com");
        assertEquals("inviter-e@example.com", invitee.getInvitedByEmail());
        assertNotNull(invitee.getInviteCode());
    }

    @Test
    void profileAndInviteEndpointsServeTheAuthenticatedAccount() throws Exception {
        enabledUser("profile-f@example.com", "PROFILEF");
        User referred = enabledUser("referred-f@example.com", "REFERREF");
        referred.setInvitedByEmail("profile-f@example.com");
        users.save(referred);

        String jwt = login("profile-f@example.com");

        mockMvc.perform(put("/api/profile")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(Map.of(
                                "notificationEmail", "notify-f@qq.com",
                                "lowBalanceThresholdBytes", 4096))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationEmail").value("notify-f@qq.com"))
                .andExpect(jsonPath("$.lowBalanceThresholdBytes").value(4096));

        mockMvc.perform(get("/api/profile/invite").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inviteCode").value("PROFILEF"))
                .andExpect(jsonPath("$.referralCount").value(1));
    }

    @Test
    void profileRejectsDisallowedNotificationDomain() throws Exception {
        enabledUser("profile-g@qq.com", "PROFILEG");
        String jwt = login("profile-g@qq.com");

        mockMvc.perform(put("/api/profile")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(Map.of("notificationEmail", "notify@gmail.com"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void profileClearsNotificationEmailWhenBlank() throws Exception {
        User user = enabledUser("profile-h@qq.com", "PROFILEH");
        user.setNotificationEmail("old@qq.com");
        users.save(user);
        String jwt = login("profile-h@qq.com");

        mockMvc.perform(put("/api/profile")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(Map.of("notificationEmail", ""))))
                .andExpect(status().isOk());

        assertNull(reload("profile-h@qq.com").getNotificationEmail());
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GSON.toJson(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonParser.parseString(body).getAsJsonObject().get("token").getAsString();
    }
}
