package io.github.zzz1999.entityreskin.web.stats;

import io.github.zzz1999.entityreskin.web.security.Secrets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Reduces a player IP to a short salted hash prefix for low-PII analytics. The raw IP of a
 * third-party player is personal information (PIPL); only this non-reversible prefix is kept, so
 * statistics can distinguish visitors without storing identifiable addresses. The salt MUST be a
 * strong random value in production.
 */
@Component
public class IpHasher {

    private static final int PREFIX_BYTES = 4;

    private final byte[] salt;

    public IpHasher(@Value("${entityreskin.stats.ip-salt:}") String salt, Environment environment) {
        Secrets.requireStrong(salt, "entityreskin.stats.ip-salt", environment);
        this.salt = salt.getBytes(StandardCharsets.UTF_8);
    }

    public String hashPrefix(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] hash = digest.digest(ip.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(PREFIX_BYTES * 2);
            for (int i = 0; i < PREFIX_BYTES; i++) {
                sb.append(Character.forDigit((hash[i] >> 4) & 0xF, 16));
                sb.append(Character.forDigit(hash[i] & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
