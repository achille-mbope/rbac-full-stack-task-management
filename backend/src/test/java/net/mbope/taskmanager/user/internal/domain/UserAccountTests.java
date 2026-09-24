package net.mbope.taskmanager.user.internal.domain;

import net.mbope.taskmanager.user.Role;
import net.mbope.taskmanager.user.UserValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class UserAccountTests {
    @Test
    void newAccountsAreEnabledUsers() {
        var account = UserAccount.register("person@example.com", "hash", Instant.EPOCH);
        assertThat(account.enabled()).isTrue();
        assertThat(account.roles()).containsExactly(Role.USER);
        assertThat(account.createdAt()).isEqualTo(account.updatedAt());
    }

    @Test
    void rolesCannotLoseUserOrContainNull() {
        assertThatThrownBy(() -> new AccountRoles(Set.of(Role.ADMIN))).isInstanceOf(UserValidationException.class);
        assertThatThrownBy(() -> new AccountRoles(null)).isInstanceOf(UserValidationException.class);
        var invalid = new HashSet<Role>();
        invalid.add(Role.USER);
        invalid.add(null);
        assertThatThrownBy(() -> new AccountRoles(invalid)).isInstanceOf(UserValidationException.class);
    }

    @Test
    void roleGrantsCannotBeMutatedExternally() {
        var grants = new HashSet<>(Set.of(Role.USER));
        var roles = new AccountRoles(grants);
        grants.add(Role.ADMIN);
        assertThat(roles.values()).containsExactly(Role.USER);
        assertThatThrownBy(() -> roles.values().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void changesPreserveIdentityAndCreationTime() {
        var id = UUID.randomUUID();
        var account = UserAccount.restore(id, "person@example.com", "old", AccountRoles.user(),
                true, Instant.EPOCH, Instant.EPOCH);
        var changedAt = Instant.EPOCH.plusSeconds(10);
        account.replaceRoles(new AccountRoles(Set.of(Role.USER, Role.ADMIN)), changedAt);
        account.changePassword("new", changedAt);
        account.setEnabled(false, changedAt);
        assertThat(account.id()).isEqualTo(id);
        assertThat(account.email()).isEqualTo("person@example.com");
        assertThat(account.createdAt()).isEqualTo(Instant.EPOCH);
        assertThat(account.updatedAt()).isEqualTo(changedAt);
        assertThat(account.passwordHash()).isEqualTo("new");
        assertThat(account.enabled()).isFalse();
        assertThat(account.roles()).containsExactlyInAnyOrder(Role.USER, Role.ADMIN);
    }
}
