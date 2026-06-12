package io.github.zzz1999.entityreskin.protocol;

import java.util.regex.Pattern;

/**
 * Validation for appearance identifiers (for example {@code entityreskin:dragon_red}). The same
 * rule is enforced by the backend on entry creation, by the server plugin on commands and API
 * calls, and by the client before resolving resources.
 */
public final class Identifiers {

    public static final int MAX_LENGTH = 128;

    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    private Identifiers() {
    }

    public static boolean isValid(String identifier) {
        return identifier != null
                && identifier.length() <= MAX_LENGTH
                && PATTERN.matcher(identifier).matches();
    }
}
