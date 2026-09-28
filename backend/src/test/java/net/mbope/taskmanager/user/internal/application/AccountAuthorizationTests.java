package net.mbope.taskmanager.user.internal.application;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

import net.mbope.taskmanager.user.Role;
import net.mbope.taskmanager.user.UserValidationException;
import net.mbope.taskmanager.user.internal.application.port.AccountStore;
import net.mbope.taskmanager.user.internal.application.port.CurrentAccount;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountAuthorizationTests {
    private final AccountStore accounts = mock(AccountStore.class);
    private final CurrentAccount current = mock(CurrentAccount.class);
    private final AccountAdministrationService service =
            new AccountAdministrationService(accounts, current, Clock.systemUTC());

    @Test
    void checksAuthorizationBeforeValidationAndRepositoryAccess() {
        var denied = new SecurityException("denied");
        doThrow(denied).when(current).requireAdmin();
        assertThatThrownBy(() -> service.list(-1, 0)).isSameAs(denied);
        assertThatThrownBy(() -> service.replaceRoles(null, null)).isSameAs(denied);
        assertThatThrownBy(() -> service.setEnabled(null, false)).isSameAs(denied);
        verifyNoInteractions(accounts);
    }

    @Test
    void validatesRolesBeforeLookingUpAccount() {
        assertThatThrownBy(() -> service.replaceRoles(UUID.randomUUID(),
                Set.of(Role.ADMIN)))
                .isInstanceOf(UserValidationException.class);
        verify(current).requireAdmin();
        verifyNoInteractions(accounts);
    }
}
