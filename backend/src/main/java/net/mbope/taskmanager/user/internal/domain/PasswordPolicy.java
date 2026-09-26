package net.mbope.taskmanager.user.internal.domain;

import java.nio.charset.StandardCharsets;

import net.mbope.taskmanager.user.UserValidationException;

public final class PasswordPolicy {
    private static final int MINIMUM_CODE_POINTS = 15;
    private static final int MAXIMUM_UTF8_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void validateNew(String password, String field) {
        validateCurrent(password, field);
        if (password.codePointCount(0, password.length()) < MINIMUM_CODE_POINTS) {
            throw new UserValidationException(field, "size", "Password must contain at least 15 characters.");
        }
    }

    public static void validateCurrent(String password, String field) {
        if (password == null || password.isEmpty()) {
            throw new UserValidationException(field, "required", "Password is required.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_UTF8_BYTES) {
            throw new UserValidationException(field, "size", "Password must not exceed 72 UTF-8 bytes.");
        }
    }
}
