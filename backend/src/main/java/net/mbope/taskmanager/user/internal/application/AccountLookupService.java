package net.mbope.taskmanager.user.internal.application;

import net.mbope.taskmanager.user.internal.application.port.*;
import net.mbope.taskmanager.user.internal.domain.*;

import net.mbope.taskmanager.user.AccountLookup;
import net.mbope.taskmanager.user.AccountReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

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
