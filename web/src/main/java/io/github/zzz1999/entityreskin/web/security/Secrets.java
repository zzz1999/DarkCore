package io.github.zzz1999.entityreskin.web.security;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.nio.charset.StandardCharsets;

/** Startup validation for configured secrets, so weak keys fail fast instead of silently. */
public final class Secrets {

    private static final int MINIMUM_LENGTH_BYTES = 32;
    private static final String DEVELOPMENT_DEFAULT_PREFIX = "dev-only-";

    private Secrets() {
    }

    /**
     * Validates that the secret is at least 32 bytes, and that the development default has been
     * replaced when the {@code prod} profile is active. Throws {@link IllegalStateException} so
     * the application refuses to start with a weak or default secret.
     */
    public static void requireStrong(String secret, String propertyName, Environment environment) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_LENGTH_BYTES) {
            throw new IllegalStateException(propertyName + " must be at least "
                    + MINIMUM_LENGTH_BYTES + " bytes");
        }
        boolean production = environment != null && environment.acceptsProfiles(Profiles.of("prod"));
        if (production && secret.startsWith(DEVELOPMENT_DEFAULT_PREFIX)) {
            throw new IllegalStateException(propertyName
                    + " still uses the development default; configure a production secret");
        }
    }
}
