package io.github.zzz1999.entityreskin.web.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * HMAC-SHA256 signing for download URLs. The canonical string binds the blob hash, the expiry,
 * and the server token, so a signature cannot be replayed for a different blob, a later time,
 * or another server's rate bucket. Signatures are lowercase hex (no characters that interfere
 * with URL or path semantics).
 */
@Service
public class UrlSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public UrlSigner(@Value("${entityreskin.signing.secret}") String secret, Environment environment) {
        Secrets.requireStrong(secret, "entityreskin.signing.secret", environment);
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String sign(String sha256, long expiresAtEpochSeconds, String serverToken) {
        String canonical = sha256 + '\n' + expiresAtEpochSeconds + '\n' + serverToken;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITHM + " unavailable", e);
        }
    }

    /** Constant-time signature comparison; accepts uppercase hex input. */
    public boolean verify(String sha256, long expiresAtEpochSeconds, String serverToken, String signature) {
        if (signature == null) {
            return false;
        }
        byte[] expected = sign(sha256, expiresAtEpochSeconds, serverToken).getBytes(StandardCharsets.UTF_8);
        byte[] provided = signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }
}
