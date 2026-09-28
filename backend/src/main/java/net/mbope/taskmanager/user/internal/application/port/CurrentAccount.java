package net.mbope.taskmanager.user.internal.application.port;

import java.util.UUID;

public interface CurrentAccount {
    UUID id();

    void requireAdmin();
}
