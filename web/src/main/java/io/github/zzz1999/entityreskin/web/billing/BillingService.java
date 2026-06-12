package io.github.zzz1999.entityreskin.web.billing;

import io.github.zzz1999.entityreskin.web.account.User;
import io.github.zzz1999.entityreskin.web.account.UserRepository;
import io.github.zzz1999.entityreskin.web.email.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;

/**
 * Account credit, measured in bytes of downloadable traffic. Every balance change is an atomic
 * database update (no read-modify-write), so concurrent recharges and downloads cannot lose
 * updates. Recharge pays a configurable referral bonus to the inviter; crossing the low-balance
 * threshold triggers a one-shot email alert, atomically claimed and re-armed by the next
 * qualifying recharge.
 */
@Service
public class BillingService {

    private final UserRepository users;
    private final EmailService emailService;
    private final Clock clock;
    private final long referralBonusPercent;

    public BillingService(UserRepository users, EmailService emailService, Clock clock,
                          @Value("${entityreskin.billing.referral-bonus-percent:10}") long referralBonusPercent) {
        this.users = users;
        this.emailService = emailService;
        this.clock = clock;
        this.referralBonusPercent = referralBonusPercent;
    }

    /** Credits an account (called by the payment webhook) and pays any referral bonus. */
    @Transactional
    public long recharge(String email, long creditBytes) {
        if (creditBytes <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "creditBytes must be positive");
        }
        User user = users.findByEmail(email).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such account"));
        users.credit(email, creditBytes);
        users.rearmLowBalanceAlert(email);

        String inviter = user.getInvitedByEmail();
        if (inviter != null) {
            long bonus = creditBytes * referralBonusPercent / 100;
            if (bonus > 0) {
                users.credit(inviter, bonus);
                users.rearmLowBalanceAlert(inviter);
            }
        }
        return users.findByEmail(email).map(User::getBalanceBytes).orElse(0L);
    }

    /** Atomically charges traffic to the server owner; false when the balance is insufficient. */
    @Transactional
    public boolean chargeTraffic(String ownerEmail, long bytes) {
        if (bytes <= 0) {
            return true;
        }
        if (users.debit(ownerEmail, bytes) == 0) {
            return false;
        }
        alertIfLow(ownerEmail);
        return true;
    }

    private void alertIfLow(String ownerEmail) {
        // Atomically claim the one-shot alert; only the caller that wins the claim sends the email.
        if (users.markLowBalanceNotified(ownerEmail, clock.instant()) == 0) {
            return;
        }
        users.findByEmail(ownerEmail).ifPresent(user -> {
            String to = user.getNotificationEmail() != null && !user.getNotificationEmail().isBlank()
                    ? user.getNotificationEmail() : user.getEmail();
            emailService.sendLowBalanceAlert(to, user.getBalanceBytes(), user.getLowBalanceThresholdBytes());
        });
    }
}
