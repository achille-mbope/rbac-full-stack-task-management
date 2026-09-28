package net.mbope.taskmanager.user.internal.application;

import java.util.Optional;
import java.util.UUID;

import net.mbope.taskmanager.user.AccountLookup;
import net.mbope.taskmanager.user.AccountReference;
import net.mbope.taskmanager.user.internal.application.port.AccountStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class AccountLookupService implements AccountLookup {
    private final AccountStore accounts;

    AccountLookupService(AccountStore accounts) {
        this.accounts = accounts;
    }

    @Override
    public Optional<AccountReference> findById(UUID accountId) {
        return accounts.findById(accountId).map(account -> new AccountReference(account.id(), account.enabled()));
    }
}
