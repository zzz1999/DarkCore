package io.github.zzz1999.entityreskin.web.account;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Domain allow-list matching, including the look-alike and malformed-address cases an attacker
 * would probe to slip a non-QQ/NetEase address past registration.
 */
class EmailDomainPolicyTest {

    private final EmailDomainPolicy policy =
            new EmailDomainPolicy("qq.com, foxmail.com ,163.com,126.com");

    @Test
    void acceptsListedDomainsCaseInsensitively() {
        assertTrue(policy.isAllowed("player@qq.com"));
        assertTrue(policy.isAllowed("Player@QQ.CoM"));
        assertTrue(policy.isAllowed("a.b+tag@163.com"));
        assertTrue(policy.isAllowed("  spaced@foxmail.com  "));
    }

    @Test
    void rejectsUnlistedAndLookalikeDomains() {
        assertFalse(policy.isAllowed("player@gmail.com"));
        assertFalse(policy.isAllowed("player@qq.com.evil.com")); // suffix look-alike
        assertFalse(policy.isAllowed("player@evilqq.com"));       // substring look-alike
        assertFalse(policy.isAllowed("player@mail.qq.com"));      // unlisted subdomain
        assertFalse(policy.isAllowed("player@qq.com."));          // trailing dot
    }

    @Test
    void rejectsMalformedAddresses() {
        assertFalse(policy.isAllowed(null));
        assertFalse(policy.isAllowed(""));
        assertFalse(policy.isAllowed("no-at-sign"));
        assertFalse(policy.isAllowed("@qq.com")); // empty local part
        assertFalse(policy.isAllowed("player@"));  // empty domain
    }

    @Test
    void usesDomainAfterFinalAtSign() {
        // The real domain is the segment after the last '@'.
        assertTrue(policy.isAllowed("weird@local@qq.com"));
        assertFalse(policy.isAllowed("qq.com@gmail.com"));
    }
}
