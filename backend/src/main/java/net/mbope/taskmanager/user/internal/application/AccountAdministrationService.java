package net.mbope.taskmanager.user.internal.application;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

import net.mbope.taskmanager.user.Account;
import net.mbope.taskmanager.user.AccountAdministration;
import net.mbope.taskmanager.user.AccountNotFoundException;
import net.mbope.taskmanager.user.AccountPage;
import net.mbope.taskmanager.user.Role;
import net.mbope.taskmanager.user.UserValidationException;
import net.mbope.taskmanager.user.internal.application.port.AccountStore;
import net.mbope.taskmanager.user.internal.application.port.CurrentAccount;
import net.mbope.taskmanager.user.internal.domain.AccountRoles;
import net.mbope.taskmanager.user.internal.domain.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class AccountAdministrationService implements AccountAdministration {
    private static final int MAXIMUM_PAGE_SIZE = 100;

    private final AccountStore accounts;
    private final CurrentAccount currentAccount;
    private final Clock clock;

    AccountAdministrationService(AccountStore accounts, CurrentAccount currentAccount, Clock clock) {
        this.accounts = accounts;
        this.currentAccount = currentAccount;
        this.clock = clock;
    }

    @Override
    public AccountPage list(int page, int size) {
        currentAccount.requireAdmin();
        if (page < 0) {
            throw new UserValidationException("page", "range", "Page must be zero or greater.");
        }
        if (size < 1 || size > MAXIMUM_PAGE_SIZE) {
            throw new UserValidationException("size", "range", "Size must be between 1 and 100.");
        }
        return accounts.list(page, size);
    }

    @Override
    @Transactional
    public Account replaceRoles(UUID accountId, Set<Role> roles) {
        currentAccount.requireAdmin();
        AccountRoles replacement = new AccountRoles(roles);
        UserAccount account = accounts.findById(accountId).orElseThrow(AccountNotFoundException::new);
        account.replaceRoles(replacement, clock.instant());
        accounts.save(account);
        return account.view();
    }

    @Override
    @Transactional
    public Account setEnabled(UUID accountId, boolean enabled) {
        currentAccount.requireAdmin();
        UserAccount account = accounts.findById(accountId).orElseThrow(AccountNotFoundException::new);
        account.setEnabled(enabled, clock.instant());
        accounts.save(account);
        return account.view();
    }
}
