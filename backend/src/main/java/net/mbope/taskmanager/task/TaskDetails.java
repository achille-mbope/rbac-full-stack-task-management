package net.mbope.taskmanager.task;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TaskDetails(UUID id, String title, String description, TaskStatus status, LocalDate dueDate,
                          UUID assigneeId, UUID createdById, Instant createdAt, Instant updatedAt) {
}
