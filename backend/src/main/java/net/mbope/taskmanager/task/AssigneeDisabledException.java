package net.mbope.taskmanager.task;

public final class AssigneeDisabledException extends RuntimeException {
    public AssigneeDisabledException() {
        super("The selected account is disabled.");
    }
}
