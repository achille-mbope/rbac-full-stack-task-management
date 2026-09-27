package net.mbope.taskmanager.task.internal.application.port;

import java.util.Optional;
import java.util.UUID;

import net.mbope.taskmanager.task.TaskPage;
import net.mbope.taskmanager.task.TaskQuery;
import net.mbope.taskmanager.task.internal.domain.Task;

public interface TaskStore {
    /** Assigns a UUID and returns the saved task. */
    Task add(Task task);

    Optional<Task> findById(UUID taskId);

    /** Apply both predicates in storage; inaccessible and missing tasks are indistinguishable. */
    Optional<Task> findAssignedById(UUID taskId, UUID assigneeId);

    void save(Task task);
    void delete(Task task);

    /**
     * Null assignee means global administrative scope. Otherwise filter by assignee.
     * Combine scope, status, and case-insensitive literal title substring with AND before
     * counting/pagination. Escape SQL wildcards. Order by createdAt descending, ID ascending.
     * Out-of-range pages are empty; an empty result has zero totals.
     * Mutations must participate in the same transaction as TaskAudit.
     */
    TaskPage list(TaskQuery query, UUID assigneeId);
}
