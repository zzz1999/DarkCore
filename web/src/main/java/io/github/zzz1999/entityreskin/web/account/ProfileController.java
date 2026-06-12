package io.github.zzz1999.entityreskin.web.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Authenticated account profile: the contact email for notifications, the low-balance alert
 * threshold, and the invite code with its referral count.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserRepository users;
    private final EmailDomainPolicy emailDomainPolicy;

    public ProfileController(UserRepository users, EmailDomainPolicy emailDomainPolicy) {
        this.users = users;
        this.emailDomainPolicy = emailDomainPolicy;
    }

    @GetMapping
    public ProfileResponse get(Authentication authentication) {
        return toResponse(require(authentication.getName()));
    }

    @PutMapping
    @Transactional
    public ProfileResponse update(@Valid @RequestBody ProfileUpdate update, Authentication authentication) {
        User user = require(authentication.getName());
        if (update.notificationEmail() != null) {
            String normalized = emailDomainPolicy.normalize(update.notificationEmail());
            if (normalized.isEmpty()) {
                user.setNotificationEmail(null);
            } else {
                emailDomainPolicy.requireAllowed(normalized);
                user.setNotificationEmail(normalized);
            }
        }
        if (update.lowBalanceThresholdBytes() != null) {
            user.setLowBalanceThresholdBytes(Math.max(0, update.lowBalanceThresholdBytes()));
            // A changed threshold re-arms the one-shot low-balance alert.
            user.setLowBalanceNotifiedAt(null);
        }
        users.save(user);
        return toResponse(user);
    }

    @GetMapping("/invite")
    public InviteResponse invite(Authentication authentication) {
        User user = require(authentication.getName());
        return new InviteResponse(user.getInviteCode(), users.countByInvitedByEmail(user.getEmail()));
    }

    private User require(String email) {
        return users.findByEmail(email).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such account"));
    }

    private static ProfileResponse toResponse(User user) {
        return new ProfileResponse(user.getEmail(), user.getNotificationEmail(),
                user.getBalanceBytes(), user.getLowBalanceThresholdBytes(),
                user.getInviteCode(), user.getInvitedByEmail());
    }

    public record ProfileUpdate(@Email String notificationEmail, Long lowBalanceThresholdBytes) {
    }

    public record ProfileResponse(String email, String notificationEmail, long balanceBytes,
                                  long lowBalanceThresholdBytes, String inviteCode, String invitedByEmail) {
    }

    public record InviteResponse(String inviteCode, long referralCount) {
    }
}
