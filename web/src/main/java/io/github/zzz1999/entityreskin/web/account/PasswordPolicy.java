package io.github.zzz1999.entityreskin.web.account;

/** Password complexity policy, shared by registration and password reset. */
public final class PasswordPolicy {

    /** At least 10 characters, with at least one lowercase letter, one uppercase letter, and one digit. */
    public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{10,100}$";

    public static final String MESSAGE = "Password must be at least 10 characters and contain uppercase letters, lowercase letters, and a digit";

    private PasswordPolicy() {
    }
}
