package net.mbope.taskmanager.user;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable account view. Credentials are deliberately absent.
 */
public record Account(UUID id, String email, Set<Role> roles, boolean enabled,
                      Instant createdAt, Instant updatedAt) {
    public Account {
        roles = Set.copyOf(roles);
    }
}
