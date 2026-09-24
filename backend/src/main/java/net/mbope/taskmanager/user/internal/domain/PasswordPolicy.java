package net.mbope.taskmanager.user.internal.domain;

import net.mbope.taskmanager.user.UserValidationException;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() {
    }

    public static void validateNew(String password, String field) {
        validateCurrent(password, field);
        if (password.codePointCount(0, password.length()) < 15) {
            throw new UserValidationException(field, "size", "Password must contain at least 15 characters.");
        }
    }

    public static void validateCurrent(String password, String field) {
        if (password == null || password.isEmpty()) {
            throw new UserValidationException(field, "required", "Password is required.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new UserValidationException(field, "size", "Password must not exceed 72 UTF-8 bytes.");
        }
    }
}
