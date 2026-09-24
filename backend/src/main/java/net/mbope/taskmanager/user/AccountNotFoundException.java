package net.mbope.taskmanager.user;

public final class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException() {
        super("Account not found.");
    }
}
