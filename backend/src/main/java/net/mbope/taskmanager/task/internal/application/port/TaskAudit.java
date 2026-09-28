package net.mbope.taskmanager.task.internal.application.port;

/** Append synchronously in the task mutation transaction; propagate all failures. */
public interface TaskAudit {
    /** Records must survive task deletion and be accessible only to authorized operators. */
    void append(TaskAuditEntry entry);
}
