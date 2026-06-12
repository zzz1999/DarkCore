package io.github.zzz1999.entityreskin.web.account;

import io.github.zzz1999.entityreskin.web.email.EmailService;
import io.github.zzz1999.entityreskin.web.security.AttemptLimiter;
import io.github.zzz1999.entityreskin.web.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

/**
 * Registration (with email verification) and login. Passwords and codes are stored hashed.
 * Registration and verification are attempt-limited, and the registration response does not
 * reveal whether an email address is already registered (no account enumeration).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final long CODE_TTL_MINUTES = 10;
    private static final int REGISTER_ATTEMPTS_PER_WINDOW = 3;
    private static final int VERIFY_ATTEMPTS_PER_WINDOW = 5;
    private static final int FORGOT_ATTEMPTS_PER_WINDOW = 3;
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(10);

    private final UserRepository users;
    private final EmailVerificationRepository verifications;
    private final PasswordResetRepository passwordResets;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final AttemptLimiter attemptLimiter;
    private final EmailDomainPolicy emailDomainPolicy;
    private final long sendCooldownSeconds;
    private final long trialBytes;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, EmailVerificationRepository verifications,
                       PasswordResetRepository passwordResets, PasswordEncoder passwordEncoder,
                       EmailService emailService, JwtService jwtService, AttemptLimiter attemptLimiter,
                       EmailDomainPolicy emailDomainPolicy,
                       @Value("${entityreskin.email.send-cooldown-seconds:60}") long sendCooldownSeconds,
                       @Value("${entityreskin.billing.trial-bytes:0}") long trialBytes) {
        this.users = users;
        this.verifications = verifications;
        this.passwordResets = passwordResets;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.jwtService = jwtService;
        this.attemptLimiter = attemptLimiter;
        this.emailDomainPolicy = emailDomainPolicy;
        this.sendCooldownSeconds = sendCooldownSeconds;
        this.trialBytes = trialBytes;
    }

    @Transactional
    public void register(String rawEmail, String password, String inviteCode) {
        String email = emailDomainPolicy.normalize(rawEmail);
        emailDomainPolicy.requireAllowed(email);
        if (!attemptLimiter.tryAcquire("register:" + email, REGISTER_ATTEMPTS_PER_WINDOW, ATTEMPT_WINDOW)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "too many registration attempts");
        }
        if (users.existsByEmailAndEnabledTrue(email)) {
            // Respond identically to a fresh registration so the API does not reveal which
            // email addresses hold accounts.
            log.info("registration attempt for an already-registered email");
            return;
        }
        Optional<User> existing = users.findByEmail(email);
        User user = existing.orElseGet(() -> new User(email, ""));
        // The submitted password is NOT applied to the account here; it is staged on the
        // verification record below and applied only when the emailed code is verified, so an
        // unauthenticated re-registration cannot overwrite a pending account's credential.
        user.setEnabled(false);
        if (existing.isEmpty()) {
            user.setInviteCode(generateUniqueInviteCode());
            applyInviter(user, email, inviteCode);
        }
        users.save(user);

        // Suppress a fresh code (and email) within the send cooldown. The cooldown is derived from
        // the pending verification's own timestamp, so it shares this transaction's fate: if the
        // send below fails and the transaction rolls back, no phantom cooldown is left behind. Any
        // pending code stays valid, and the response is unchanged so the endpoint still does not
        // reveal whether a pending registration exists.
        Optional<EmailVerification> recent = verifications.findTopByEmailOrderByCreatedAtDesc(email);
        if (withinSendCooldown(recent.map(EmailVerification::getCreatedAt).orElse(null))) {
            log.info("verification code send suppressed by cooldown");
            return;
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        verifications.deleteByEmail(email);
        verifications.save(new EmailVerification(email, passwordEncoder.encode(code),
                passwordEncoder.encode(password),
                Instant.now().plus(CODE_TTL_MINUTES, ChronoUnit.MINUTES)));
        emailService.sendVerificationCode(email, code);
    }

    @Transactional
    public void verify(String rawEmail, String code) {
        String email = emailDomainPolicy.normalize(rawEmail);
        if (!attemptLimiter.tryAcquire("verify:" + email, VERIFY_ATTEMPTS_PER_WINDOW, ATTEMPT_WINDOW)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "too many verification attempts");
        }
        EmailVerification verification = verifications.findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "no pending verification"));
        if (Instant.now().isAfter(verification.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "verification code expired");
        }
        if (!passwordEncoder.matches(code, verification.getCodeHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid verification code");
        }
        User user = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "no such account"));
        if (verification.getPendingPasswordHash() != null) {
            // Bind the credential to the code that was emailed and proven, not to whoever last
            // called register for this address.
            user.setPasswordHash(verification.getPendingPasswordHash());
        }
        // Grant the one-time trial traffic allowance on first activation, so a server owner can
        // evaluate the service before purchasing. It is not counted as a recharge (the lifetime
        // recharged total is left untouched). Set entityreskin.billing.trial-bytes to 0 to disable.
        boolean firstActivation = !user.isEnabled();
        if (firstActivation && trialBytes > 0) {
            user.setBalanceBytes(user.getBalanceBytes() + trialBytes);
            log.info("granted {} bytes of trial traffic to a newly verified account", trialBytes);
        }
        user.setEnabled(true);
        users.save(user);
        verifications.deleteByEmail(email);
    }

    @Transactional
    public void forgotPassword(String rawEmail) {
        String email = emailDomainPolicy.normalize(rawEmail);
        if (!attemptLimiter.tryAcquire("forgot:" + email, FORGOT_ATTEMPTS_PER_WINDOW, ATTEMPT_WINDOW)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "too many reset requests");
        }
        Optional<User> user = users.findByEmail(email);
        if (user.isEmpty() || !user.get().isEnabled()) {
            // Do not reveal whether the address has an account.
            return;
        }
        // Suppress a fresh reset code within the send cooldown. As in register(), the cooldown is
        // derived from the pending reset's own timestamp, so a rolled-back send leaves no phantom
        // cooldown, and the response is unchanged so this is not an enumeration signal.
        Optional<PasswordReset> recent = passwordResets.findTopByEmailOrderByCreatedAtDesc(email);
        if (withinSendCooldown(recent.map(PasswordReset::getCreatedAt).orElse(null))) {
            log.info("password reset code send suppressed by cooldown");
            return;
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        passwordResets.deleteByEmail(email);
        passwordResets.save(new PasswordReset(email, passwordEncoder.encode(code),
                Instant.now().plus(CODE_TTL_MINUTES, ChronoUnit.MINUTES)));
        emailService.sendPasswordResetCode(email, code);
    }

    @Transactional
    public void resetPassword(String rawEmail, String code, String newPassword) {
        String email = emailDomainPolicy.normalize(rawEmail);
        if (!attemptLimiter.tryAcquire("reset:" + email, VERIFY_ATTEMPTS_PER_WINDOW, ATTEMPT_WINDOW)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "too many reset attempts");
        }
        PasswordReset reset = passwordResets.findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "no pending reset"));
        if (Instant.now().isAfter(reset.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reset code expired");
        }
        if (!passwordEncoder.matches(code, reset.getCodeHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid reset code");
        }
        User user = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "no such account"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(user);
        passwordResets.deleteByEmail(email);
    }

    public String login(String rawEmail, String password) {
        String email = emailDomainPolicy.normalize(rawEmail);
        User user = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "email not verified");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
        }
        return jwtService.issue(email);
    }

    public long tokenTtlMinutes() {
        return jwtService.ttlMinutes();
    }

    /**
     * Returns whether a code email should be suppressed because one was already sent within the
     * configured cooldown. The decision is based on the timestamp of the still-pending
     * verification/reset record, so it is consistent with the surrounding transaction (a
     * rolled-back send leaves no phantom cooldown) and survives a process restart.
     */
    private boolean withinSendCooldown(Instant lastSentAt) {
        return sendCooldownSeconds > 0
                && lastSentAt != null
                && lastSentAt.isAfter(Instant.now().minusSeconds(sendCooldownSeconds));
    }

    private void applyInviter(User newUser, String email, String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            return;
        }
        users.findByInviteCode(inviteCode.trim().toUpperCase(Locale.ROOT)).ifPresent(inviter -> {
            if (!inviter.getEmail().equals(email)) {
                newUser.setInvitedByEmail(inviter.getEmail());
            }
        });
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = InviteCodes.generate(random);
            if (users.findByInviteCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "could not allocate invite code");
    }
}
