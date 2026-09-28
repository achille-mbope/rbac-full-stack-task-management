package net.mbope.taskmanager.user;

import java.util.Set;
import java.util.UUID;

/**
 * Identity and current roles returned only after successful credential verification.
 */
public record AccountIdentity(UUID id, Set<Role> roles) {
    public AccountIdentity {
        roles = Set.copyOf(roles);
    }
}
