package net.mbope.taskmanager.task;

import java.util.UUID;

/** Operations on tasks assigned to the current principal, including ADMIN callers. */
public interface PersonalTasks {
    TaskDetails create(TaskDraft draft);
    TaskDetails get(UUID taskId);
    TaskPage list(TaskQuery query);
    TaskDetails update(UUID taskId, TaskUpdate update);
    void delete(UUID taskId);
}
