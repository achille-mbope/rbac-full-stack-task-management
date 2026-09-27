package net.mbope.taskmanager.task.internal.presentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import net.mbope.taskmanager.task.TaskDraft;
import net.mbope.taskmanager.task.TaskStatus;
import net.mbope.taskmanager.task.TaskValidationException;

class CreateTaskRequest {
    @JsonProperty
    private String title;
    @JsonProperty
    private String description;
    @JsonProperty
    private TaskStatus status = TaskStatus.TODO;
    @JsonProperty
    private String dueDate;

    TaskDraft draft() {
        return new TaskDraft(title, description, status, date(dueDate));
    }

    static LocalDate date(String value) {
        if (value == null) {
            return null;
        }
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                throw new DateTimeParseException("Invalid calendar date", value, 0);
            }
            return LocalDate.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new TaskValidationException("dueDate", "invalid", "Due date must be a valid YYYY-MM-DD date.");
        }
    }
}
