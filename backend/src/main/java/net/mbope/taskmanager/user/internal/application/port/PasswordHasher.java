package net.mbope.taskmanager.user.internal.application.port;

public interface PasswordHasher {
    String encode(String password);

    boolean matches(String password, String hash);
}
