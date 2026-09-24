package net.mbope.taskmanager.user;

public interface AccountRegistration {
    /**
     * Creates an enabled USER. Role, ID and status cannot be supplied by the caller.
     */
    Account register(String email, String password);
}
