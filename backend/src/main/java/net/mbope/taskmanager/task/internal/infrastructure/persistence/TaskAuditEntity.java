package net.mbope.taskmanager.task.internal.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import net.mbope.taskmanager.task.internal.application.port.TaskAuditEntry;

/** Scalar IDs intentionally retain attribution after task deletion. */
@Entity
@Table(name = "task_audit")
class TaskAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;
    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TaskAuditEntry.Action action;
    @Column(name = "changes_json", nullable = false, updatable = false, columnDefinition = "text")
    private String changesJson;

    protected TaskAuditEntity() {
    }

    TaskAuditEntity(TaskAuditEntry entry, String changesJson) {
        actorId = entry.actorId();
        taskId = entry.taskId();
        occurredAt = entry.occurredAt();
        action = entry.action();
        this.changesJson = changesJson;
    }
}
