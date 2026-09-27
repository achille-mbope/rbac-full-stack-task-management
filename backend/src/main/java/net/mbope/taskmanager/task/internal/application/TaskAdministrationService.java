package net.mbope.taskmanager.task.internal.application;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import net.mbope.taskmanager.task.*;
import net.mbope.taskmanager.task.internal.application.port.CurrentTaskActor;
import net.mbope.taskmanager.task.internal.application.port.TaskAudit;
import net.mbope.taskmanager.task.internal.application.port.TaskAuditEntry;
import net.mbope.taskmanager.task.internal.application.port.TaskStore;
import net.mbope.taskmanager.task.internal.domain.Task;
import net.mbope.taskmanager.user.AccountLookup;
import org.springframework.transaction.annotation.Transactional;

/** Register as a transactional bean when task storage and audit adapters are available. */
@Transactional(readOnly = true)
public class TaskAdministrationService implements TaskAdministration {
    private final TaskStore tasks;
    private final CurrentTaskActor actor;
    private final AccountLookup accounts;
    private final TaskAudit audit;
    private final Clock clock;

    public TaskAdministrationService(TaskStore tasks, CurrentTaskActor actor, AccountLookup accounts,
                                     TaskAudit audit, Clock clock) {
        this.tasks = tasks;
        this.actor = actor;
        this.accounts = accounts;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TaskDetails create(UUID assigneeId, TaskDraft draft) {
        actor.requireAdmin();
        UUID caller = actor.id();
        Task task = TaskValues.create(TaskValues.id(assigneeId, "assigneeId"), caller, draft, clock.instant());
        var recipient = accounts.findById(assigneeId).orElseThrow(AssigneeNotFoundException::new);
        if (!recipient.enabled()) {
            throw new AssigneeDisabledException();
        }
        return TaskValues.view(tasks.add(task));
    }

    @Override
    public TaskDetails get(UUID taskId) {
        actor.requireAdmin();
        return TaskValues.view(find(taskId));
    }

    @Override
    public TaskPage list(TaskQuery query, UUID assigneeId) {
        actor.requireAdmin();
        return tasks.list(TaskValues.query(query), assigneeId);
    }

    @Override
    @Transactional
    public TaskDetails update(UUID taskId, TaskUpdate update) {
        actor.requireAdmin();
        UUID caller = actor.id();
        Task task = find(taskId);
        TaskDetails before = TaskValues.view(task);
        TaskValues.update(task, update, clock.instant());
        TaskDetails after = TaskValues.view(task);
        tasks.save(task);
        audit.append(new TaskAuditEntry(caller, task.id(), task.updatedAt(), TaskAuditEntry.Action.UPDATE,
                TaskValues.changes(before, after)));
        return after;
    }

    @Override
    @Transactional
    public void delete(UUID taskId) {
        actor.requireAdmin();
        UUID caller = actor.id();
        Task task = find(taskId);
        var entry = new TaskAuditEntry(caller, task.id(), clock.instant().truncatedTo(ChronoUnit.MICROS),
                TaskAuditEntry.Action.DELETE, TaskValues.changes(TaskValues.view(task), null));
        tasks.delete(task);
        audit.append(entry);
    }

    private Task find(UUID taskId) {
        return tasks.findById(TaskValues.id(taskId, "taskId")).orElseThrow(TaskNotFoundException::new);
    }
}
