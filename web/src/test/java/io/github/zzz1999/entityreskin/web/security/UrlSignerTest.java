package io.github.zzz1999.entityreskin.web.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlSignerTest {

    private static final String SECRET = "unit-test-signing-secret-0123456789abcdef";
    private static final String SHA256 =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String TOKEN = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899";

    private final UrlSigner signer = new UrlSigner(SECRET, new MockEnvironment());

    @Test
    void signatureIsDeterministicAndVerifies() {
        String first = signer.sign(SHA256, 1_000_000L, TOKEN);
        String second = signer.sign(SHA256, 1_000_000L, TOKEN);
        assertEquals(first, second);
        assertTrue(signer.verify(SHA256, 1_000_000L, TOKEN, first));
    }

    @Test
    void uppercaseSignatureIsAccepted() {
        String signature = signer.sign(SHA256, 1_000_000L, TOKEN);
        assertTrue(signer.verify(SHA256, 1_000_000L, TOKEN, signature.toUpperCase(Locale.ROOT)));
    }

    @Test
    void tamperingAnyBoundFieldInvalidatesSignature() {
        String signature = signer.sign(SHA256, 1_000_000L, TOKEN);
        String otherSha = "f" + SHA256.substring(1);
        String otherToken = "f" + TOKEN.substring(1);
        assertFalse(signer.verify(otherSha, 1_000_000L, TOKEN, signature));
        assertFalse(signer.verify(SHA256, 1_000_001L, TOKEN, signature));
        assertFalse(signer.verify(SHA256, 1_000_000L, otherToken, signature));
        assertFalse(signer.verify(SHA256, 1_000_000L, TOKEN, signature.substring(0, 63) + "0"));
        assertFalse(signer.verify(SHA256, 1_000_000L, TOKEN, null));
    }

    @Test
    void shortSecretIsRejectedAtConstruction() {
        assertThrows(IllegalStateException.class, () -> new UrlSigner("short", new MockEnvironment()));
    }

    @Test
    void developmentDefaultSecretIsRejectedUnderProductionProfile() {
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("prod");
        String developmentDefault = "dev-only-insecure-signing-secret-0123456789";
        assertThrows(IllegalStateException.class, () -> new UrlSigner(developmentDefault, production));
    }
}
