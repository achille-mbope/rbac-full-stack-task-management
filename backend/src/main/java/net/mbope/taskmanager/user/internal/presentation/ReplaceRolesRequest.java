package net.mbope.taskmanager.user.internal.presentation;

import jakarta.validation.constraints.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.mbope.taskmanager.user.Role;
import net.mbope.taskmanager.user.UserValidationException;

record ReplaceRolesRequest(@NotNull List<@NotNull Role> roles) {
    Set<Role> uniqueRoles() {
        var unique = new HashSet<>(roles);
        if (unique.size() != roles.size()) {
            throw new UserValidationException("roles", "invalid", "Roles must be unique.");
        }
        return unique;
    }
}
