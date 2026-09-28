package net.mbope.taskmanager.task.internal.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import net.mbope.taskmanager.task.TaskDetails;
import net.mbope.taskmanager.task.TaskStatus;

@Schema(name = "Task")
record TaskResponse(UUID id, String title, String description, TaskStatus status, LocalDate dueDate,
                    Instant createdAt, Instant updatedAt, UUID assigneeId, UUID createdById) {
    static TaskResponse from(TaskDetails task) {
        return new TaskResponse(task.id(), task.title(), task.description(), task.status(), task.dueDate(),
                task.createdAt(), task.updatedAt(), task.assigneeId(), task.createdById());
    }
}
