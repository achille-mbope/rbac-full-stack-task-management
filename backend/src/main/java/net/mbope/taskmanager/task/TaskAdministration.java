package net.mbope.taskmanager.task;

import java.util.UUID;

/** ADMIN-only operations on all tasks. */
public interface TaskAdministration {
    TaskDetails create(UUID assigneeId, TaskDraft draft);
    TaskDetails get(UUID taskId);
    TaskPage list(TaskQuery query, UUID assigneeId);
    TaskDetails update(UUID taskId, TaskUpdate update);
    void delete(UUID taskId);
}
