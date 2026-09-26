package net.mbope.taskmanager.user.internal.domain;

import java.util.Set;

import net.mbope.taskmanager.user.Role;
import net.mbope.taskmanager.user.UserValidationException;

/**
 * An account always has USER; ADMIN is an optional additional grant.
 */
public record AccountRoles(Set<Role> values) {
    public AccountRoles {
        if (values == null || values.isEmpty() || values.stream().anyMatch(role -> role == null)
                || !values.contains(Role.USER)) {
            throw new UserValidationException("roles", "invalid", "Roles must include USER and may additionally include ADMIN.");
        }
        values = Set.copyOf(values);
    }

    public static AccountRoles user() {
        return new AccountRoles(Set.of(Role.USER));
    }
}
