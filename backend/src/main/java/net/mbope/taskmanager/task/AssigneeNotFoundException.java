package net.mbope.taskmanager.task;

public final class AssigneeNotFoundException extends RuntimeException {
    public AssigneeNotFoundException() {
        super("Account not found.");
    }
}
