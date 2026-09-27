package net.mbope.taskmanager.task;

/**
 * Every transition is allowed, including reopening DONE and retaining the current status.
 */
public enum TaskStatus {
    TODO, IN_PROGRESS, DONE
}
