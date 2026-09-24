package net.mbope.taskmanager.user.internal.application;

import net.mbope.taskmanager.user.internal.application.port.*;
import net.mbope.taskmanager.user.internal.domain.*;

import net.mbope.taskmanager.user.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class AccountAdministrationService implements AccountAdministration {
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
        if (page < 0) throw new UserValidationException("page", "range", "Page must be zero or greater.");
        if (size < 1 || size > 100)
            throw new UserValidationException("size", "range", "Size must be between 1 and 100.");
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
