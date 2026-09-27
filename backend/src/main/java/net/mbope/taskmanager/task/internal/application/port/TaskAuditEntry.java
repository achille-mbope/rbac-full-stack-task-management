package net.mbope.taskmanager.task.internal.application.port;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Safe task field snapshots only; no principal credentials or tokens. */
public record TaskAuditEntry(UUID actorId, UUID taskId, Instant occurredAt, Action action,
                             Map<String, Change> changes) {
    public TaskAuditEntry {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(taskId);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(action);
        changes = Map.copyOf(changes);
    }

    public enum Action { UPDATE, DELETE }

    public record Change(Object before, Object after) {
    }
}
