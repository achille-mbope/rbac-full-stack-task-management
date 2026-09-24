package net.mbope.taskmanager.user.internal.application;

import net.mbope.taskmanager.user.internal.application.port.AccountStore;
import net.mbope.taskmanager.user.internal.application.port.CurrentAccount;
import net.mbope.taskmanager.user.UserValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountAuthorizationTests {
    private final AccountStore accounts = mock(AccountStore.class);
    private final CurrentAccount current = mock(CurrentAccount.class);
    private final AccountAdministrationService service =
            new AccountAdministrationService(accounts, current, java.time.Clock.systemUTC());

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
        assertThatThrownBy(() -> service.replaceRoles(java.util.UUID.randomUUID(),
                java.util.Set.of(net.mbope.taskmanager.user.Role.ADMIN)))
                .isInstanceOf(UserValidationException.class);
        verify(current).requireAdmin();
        verifyNoInteractions(accounts);
    }
}
