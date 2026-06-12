package io.github.zzz1999.entityreskin.web.account;

import java.security.SecureRandom;

/** Generates short, human-friendly invite codes (uppercase, no ambiguous characters). */
public final class InviteCodes {

    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 8;

    private InviteCodes() {
    }

    public static String generate(SecureRandom random) {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
