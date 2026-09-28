package net.mbope.taskmanager.task.internal.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import net.mbope.taskmanager.task.TaskStatus;
import net.mbope.taskmanager.task.TaskValidationException;

/**
 * Framework-independent task state. Assignment and creator attribution never change.
 * Account lookup and authorization belong to application services.
 */
public final class Task {
    private final UUID id;
    private final UUID assigneeId;
    private final UUID createdById;
    private final Instant createdAt;
    private String title;
    private String description;
    private TaskStatus status;
    private LocalDate dueDate;
    private Instant updatedAt;

    private Task(UUID id, UUID assigneeId, UUID createdById, String title, String description,
                 TaskStatus status, LocalDate dueDate, Instant createdAt, Instant updatedAt) {
        validate(title, description, status);
        this.id = id;
        this.assigneeId = Objects.requireNonNull(assigneeId);
        this.createdById = Objects.requireNonNull(createdById);
        this.title = title;
        this.description = description;
        this.status = status;
        this.dueDate = dueDate;
        this.createdAt = timestamp(createdAt);
        this.updatedAt = timestamp(updatedAt);
    }

    /**
     * Creates an unsaved task with TODO status and no description or due date.
     */
    public static Task create(UUID assigneeId, UUID createdById, String title, Instant now) {
        return create(assigneeId, createdById, title, null, TaskStatus.TODO, null, now);
    }

    /**
     * The persistence adapter assigns the ID, following the account domain convention.
     */
    public static Task create(UUID assigneeId, UUID createdById, String title, String description,
                              TaskStatus status, LocalDate dueDate, Instant now) {
        return new Task(null, assigneeId, createdById, title, description, status, dueDate, now, now);
    }

    public static Task restore(UUID id, UUID assigneeId, UUID createdById, String title, String description,
                               TaskStatus status, LocalDate dueDate, Instant createdAt, Instant updatedAt) {
        return new Task(Objects.requireNonNull(id), assigneeId, createdById, title, description,
                status, dueDate, createdAt, updatedAt);
    }

    /**
     * Replaces editable state atomically. Callers resolve omitted PATCH fields before calling.
     * Null clears description or due date; title and status remain required.
     */
    public void update(String title, String description, TaskStatus status, LocalDate dueDate, Instant now) {
        validate(title, description, status);
        Instant changedAt = timestamp(now);
        this.title = title;
        this.description = description;
        this.status = status;
        this.dueDate = dueDate;
        this.updatedAt = changedAt;
    }

    private static void validate(String title, String description, TaskStatus status) {
        if (title == null || title.isBlank()) {
            throw new TaskValidationException("title", "required", "Title must contain a non-whitespace character.");
        }
        if (title.codePointCount(0, title.length()) > 200) {
            throw new TaskValidationException("title", "too_long", "Title must contain at most 200 characters.");
        }
        if (description != null && description.codePointCount(0, description.length()) > 10_000) {
            throw new TaskValidationException("description", "too_long",
                    "Description must contain at most 10000 characters.");
        }
        if (status == null) {
            throw new TaskValidationException("status", "required", "Status is required.");
        }
    }

    private static Instant timestamp(Instant value) {
        return Objects.requireNonNull(value).truncatedTo(ChronoUnit.MICROS);
    }

    public UUID id() {
        return id;
    }

    public UUID assigneeId() {
        return assigneeId;
    }

    public UUID createdById() {
        return createdById;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public TaskStatus status() {
        return status;
    }

    public LocalDate dueDate() {
        return dueDate;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
