package net.mbope.taskmanager.task.internal.application;

import java.time.Clock;
import java.util.UUID;

import net.mbope.taskmanager.task.*;
import net.mbope.taskmanager.task.internal.application.port.CurrentTaskActor;
import net.mbope.taskmanager.task.internal.application.port.TaskStore;
import net.mbope.taskmanager.task.internal.domain.Task;
import org.springframework.transaction.annotation.Transactional;

/** Personal use cases; transaction management is enabled by TaskConfiguration. */
@Transactional(readOnly = true)
public class PersonalTaskService implements PersonalTasks {
    private final TaskStore tasks;
    private final CurrentTaskActor actor;
    private final Clock clock;

    public PersonalTaskService(TaskStore tasks, CurrentTaskActor actor, Clock clock) {
        this.tasks = tasks;
        this.actor = actor;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TaskDetails create(TaskDraft draft) {
        UUID caller = actor.id();
        return TaskValues.view(tasks.add(TaskValues.create(caller, caller, draft, clock.instant())));
    }

    @Override
    public TaskDetails get(UUID taskId) {
        return TaskValues.view(assigned(taskId));
    }

    @Override
    public TaskPage list(TaskQuery query) {
        UUID caller = actor.id();
        return tasks.list(TaskValues.query(query), caller);
    }

    @Override
    @Transactional
    public TaskDetails update(UUID taskId, TaskUpdate update) {
        Task task = assigned(taskId);
        TaskValues.update(task, update, clock.instant());
        tasks.save(task);
        return TaskValues.view(task);
    }

    @Override
    @Transactional
    public void delete(UUID taskId) {
        tasks.delete(assigned(taskId));
    }

    private Task assigned(UUID taskId) {
        UUID caller = actor.id();
        return tasks.findAssignedById(TaskValues.id(taskId, "taskId"), caller)
                .orElseThrow(TaskNotFoundException::new);
    }
}
