package net.mbope.taskmanager.task.internal.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import net.mbope.taskmanager.task.TaskDetails;
import net.mbope.taskmanager.task.TaskStatus;
import net.mbope.taskmanager.task.internal.domain.Task;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "tasks")
@DynamicUpdate
class TaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "assignee_id", nullable = false, updatable = false)
    private UUID assigneeId;
    @Column(name = "created_by_id", nullable = false, updatable = false)
    private UUID createdById;
    @Column(nullable = false, columnDefinition = "text")
    private String title;
    @Column(columnDefinition = "text")
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TaskEntity() {
    }

    TaskEntity(Task task) {
        assigneeId = task.assigneeId();
        createdById = task.createdById();
        createdAt = task.createdAt();
        apply(task);
    }

    void apply(Task task) {
        title = task.title();
        description = task.description();
        status = task.status();
        dueDate = task.dueDate();
        updatedAt = task.updatedAt();
    }

    Task toDomain() {
        return Task.restore(id, assigneeId, createdById, title, description, status, dueDate, createdAt, updatedAt);
    }

    TaskDetails view() {
        return new TaskDetails(id, title, description, status, dueDate, assigneeId, createdById, createdAt, updatedAt);
    }
}
