package net.mbope.taskmanager.user;

import java.util.Set;
import java.util.UUID;

/**
 * Every operation requires ADMIN authority from the current authenticated principal.
 */
public interface AccountAdministration {
    AccountPage list(int page, int size);

    Account replaceRoles(UUID accountId, Set<Role> roles);

    Account setEnabled(UUID accountId, boolean enabled);
}
