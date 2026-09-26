package net.mbope.taskmanager.auth.internal.application;

/**
 * A credential rejection that does not reveal account existence or status.
 */
public final class LoginFailedException extends RuntimeException {
    public LoginFailedException() {
        super("Invalid email or password.");
    }
}
