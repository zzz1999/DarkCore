package io.github.zzz1999.entityreskin.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentifiersTest {

    @Test
    void acceptsNamespacedIdentifiers() {
        assertTrue(Identifiers.isValid("entityreskin:dragon_red"));
        assertTrue(Identifiers.isValid("my.pack-2:boss/phase_1"));
    }

    @Test
    void rejectsMalformedIdentifiers() {
        assertFalse(Identifiers.isValid(null));
        assertFalse(Identifiers.isValid(""));
        assertFalse(Identifiers.isValid("no_namespace"));
        assertFalse(Identifiers.isValid("Upper:case"));
        assertFalse(Identifiers.isValid("spaces :bad"));
        assertFalse(Identifiers.isValid("a:" + repeat('b', 200)));
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
