package net.mbope.taskmanager.task;

public final class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException() {
        super("Task not found.");
    }
}
