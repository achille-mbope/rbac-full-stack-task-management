package net.mbope.taskmanager.task;

import java.time.LocalDate;

/** Creation values; attribution is always supplied by the application service. */
public record TaskDraft(String title, String description, TaskStatus status, LocalDate dueDate) {
    public TaskDraft(String title) {
        this(title, null, TaskStatus.TODO, null);
    }
}
