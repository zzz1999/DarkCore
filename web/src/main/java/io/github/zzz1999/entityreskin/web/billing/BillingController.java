package io.github.zzz1999.entityreskin.web.billing;

import io.github.zzz1999.entityreskin.web.account.User;
import io.github.zzz1999.entityreskin.web.account.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Billing endpoints. {@code POST /recharge} models the payment provider's webhook: it is guarded
 * by a shared secret (NOT a user session) so accounts cannot credit themselves, and a real
 * payment integration would replace the supplied amount with a verified payment. {@code GET /}
 * returns the authenticated account's balance.
 */
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingService billingService;
    private final UserRepository users;
    private final String rechargeSecret;

    public BillingController(BillingService billingService, UserRepository users,
                             @Value("${entityreskin.billing.recharge-secret:}") String rechargeSecret) {
        this.billingService = billingService;
        this.users = users;
        this.rechargeSecret = rechargeSecret;
    }

    @PostMapping("/recharge")
    public Map<String, Object> recharge(
            @RequestHeader(value = "X-Recharge-Secret", required = false) String secret,
            @Valid @RequestBody RechargeRequest request) {
        if (!secretMatches(secret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid recharge secret");
        }
        long balance = billingService.recharge(request.email(), request.creditBytes());
        return Map.of("email", request.email(), "balanceBytes", balance);
    }

    @GetMapping
    public BalanceResponse balance(Authentication authentication) {
        User user = users.findByEmail(authentication.getName()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such account"));
        return new BalanceResponse(user.getBalanceBytes(), user.getLifetimeRechargedBytes(),
                user.getLowBalanceThresholdBytes());
    }

    private boolean secretMatches(String provided) {
        if (rechargeSecret == null || rechargeSecret.isBlank() || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(rechargeSecret.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    public record RechargeRequest(@Email @NotBlank String email, @Positive long creditBytes) {
    }

    public record BalanceResponse(long balanceBytes, long lifetimeRechargedBytes,
                                  long lowBalanceThresholdBytes) {
    }
}
