package net.mbope.taskmanager.task.internal.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonSetter;
import net.mbope.taskmanager.task.FieldChange;
import net.mbope.taskmanager.task.TaskStatus;
import net.mbope.taskmanager.task.TaskUpdate;

/** Setters run only for present JSON fields, including explicit null values. */
final class UpdateTaskRequest {
    private FieldChange<String> title = FieldChange.unchanged();
    private FieldChange<String> description = FieldChange.unchanged();
    private FieldChange<TaskStatus> status = FieldChange.unchanged();
    private FieldChange<String> dueDate = FieldChange.unchanged();

    @Schema(implementation = String.class)
    @JsonSetter("title")
    public void setTitle(String value) {
        title = FieldChange.set(value);
    }

    @Schema(implementation = String.class)
    @JsonSetter("description")
    public void setDescription(String value) {
        description = FieldChange.set(value);
    }

    @Schema(implementation = TaskStatus.class)
    @JsonSetter("status")
    public void setStatus(TaskStatus value) {
        status = FieldChange.set(value);
    }

    @Schema(implementation = String.class)
    @JsonSetter("dueDate")
    public void setDueDate(String value) {
        dueDate = FieldChange.set(value);
    }

    TaskUpdate update() {
        return new TaskUpdate(title, description, status,
                dueDate.present() ? FieldChange.set(CreateTaskRequest.date(dueDate.value())) : FieldChange.unchanged());
    }
}
