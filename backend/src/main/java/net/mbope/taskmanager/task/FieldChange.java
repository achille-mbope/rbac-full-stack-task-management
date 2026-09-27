package net.mbope.taskmanager.task;

/** Distinguishes an omitted update field from an explicitly supplied null. */
public record FieldChange<T>(boolean present, T value) {
    public FieldChange {
        if (!present && value != null) {
            throw new IllegalArgumentException("An omitted field cannot contain a value.");
        }
    }

    public static <T> FieldChange<T> unchanged() {
        return new FieldChange<>(false, null);
    }

    public static <T> FieldChange<T> set(T value) {
        return new FieldChange<>(true, value);
    }

    public T orElse(T current) {
        return present ? value : current;
    }
}
