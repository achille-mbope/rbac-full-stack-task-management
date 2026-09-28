package net.mbope.taskmanager.task;

/**
 * Safe validation details without rejected task content.
 */
public final class TaskValidationException extends IllegalArgumentException {
    private final String field;
    private final String code;

    public TaskValidationException(String field, String code, String message) {
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
