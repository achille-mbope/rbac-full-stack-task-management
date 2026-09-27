package net.mbope.taskmanager.task.internal.application.port;

import java.util.UUID;

/** Implementations must reject unauthenticated callers and use current token authorities. */
public interface CurrentTaskActor {
    UUID id();
    void requireAdmin();
}
