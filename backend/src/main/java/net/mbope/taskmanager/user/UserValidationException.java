package net.mbope.taskmanager.user;

/**
 * Safe validation details; never contains rejected values or credentials.
 */
public final class UserValidationException extends IllegalArgumentException {
    private final String field;
    private final String code;

    public UserValidationException(String field, String code, String message) {
        super(message);
        this.field = field;
        this.code = code;
    }

    public String field() {
        return field;
    }

    public String code() {
        return code;
    }
}
