package net.mbope.taskmanager.user;

public final class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException() {
        super("An account with this normalized email already exists.");
    }
}
