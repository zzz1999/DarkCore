package io.github.zzz1999.entityreskin.web.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Restricts which email domains may register an account or be bound as a notification address.
 * Only providers on the configured allow-list are accepted; by default these are the QQ/Tencent
 * and NetEase domains common among mainland-China players.
 *
 * <p>The domain is taken as the segment after the <em>final</em> {@code '@'} and compared
 * case-insensitively against the allow-list by exact equality. Suffix or substring matching is
 * deliberately avoided, so look-alikes such as {@code user@qq.com.evil.com}, {@code user@evilqq.com},
 * and unlisted subdomains such as {@code user@mail.qq.com} are all rejected.</p>
 */
@Component
public class EmailDomainPolicy {

    private final Set<String> allowedDomains;

    public EmailDomainPolicy(
            @Value("${entityreskin.email.allowed-domains:"
                    + "qq.com,vip.qq.com,foxmail.com,163.com,126.com,yeah.net,vip.163.com,vip.126.com,188.com}")
            String allowed) {
        Set<String> domains = new LinkedHashSet<>();
        for (String part : allowed.split(",")) {
            String domain = part.trim().toLowerCase(Locale.ROOT);
            if (!domain.isEmpty()) {
                domains.add(domain);
            }
        }
        this.allowedDomains = Collections.unmodifiableSet(domains);
    }

    /** Returns whether {@code email}'s domain is on the allow-list. */
    public boolean isAllowed(String email) {
        String domain = domainOf(email);
        return domain != null && allowedDomains.contains(domain);
    }

    /** Throws {@code 400 Bad Request} when the address is not on an allowed domain. */
    public void requireAllowed(String email) {
        if (!isAllowed(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only QQ and NetEase email addresses are supported (e.g. qq.com, foxmail.com, 163.com, 126.com)");
        }
    }

    /**
     * Canonical form of an address: trimmed and lower-cased. QQ and NetEase mailboxes are
     * case-insensitive, so this is the single value used for the allow-list check, account
     * uniqueness/lookups, persistence, delivery, and rate-limit/cooldown keys, ensuring one
     * physical mailbox maps to exactly one identity. Returns {@code null} for a {@code null} input.
     */
    public String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** The lower-cased domain after the final {@code '@'}, or {@code null} when malformed. */
    private static String domainOf(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        int at = trimmed.lastIndexOf('@');
        if (at <= 0 || at == trimmed.length() - 1) {
            return null;
        }
        return trimmed.substring(at + 1).toLowerCase(Locale.ROOT);
    }
}
