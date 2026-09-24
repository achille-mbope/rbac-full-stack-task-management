package net.mbope.taskmanager.user.internal.infrastructure.persistence;

import net.mbope.taskmanager.user.internal.application.port.AccountStore;
import net.mbope.taskmanager.user.internal.domain.UserAccount;
import net.mbope.taskmanager.user.AccountPage;
import net.mbope.taskmanager.user.AccountNotFoundException;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import net.mbope.taskmanager.user.EmailAlreadyRegisteredException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Locale;

@Repository
class JpaAccountStore implements AccountStore {
    private final AccountRepository accounts;

    JpaAccountStore(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserAccount add(UserAccount registration) {
        AccountEntity account = new AccountEntity(registration);
        try {
            // Flush within the application's transaction so concurrent duplicates are translated here.
            return accounts.saveAndFlush(account).toDomain();
        } catch (DataIntegrityViolationException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint && isEmailConstraint(constraint)) {
                    throw new EmailAlreadyRegisteredException();
                }
            }
            throw failure;
        }
    }

    @Override
    public Optional<UserAccount> findById(UUID id) {
        return accounts.findById(id).map(AccountEntity::toDomain);
    }

    @Override
    public Optional<UserAccount> findByEmail(String normalizedEmail) {
        return accounts.findByEmail(normalizedEmail).map(AccountEntity::toDomain);
    }

    @Override
    public void save(UserAccount account) {
        // Reuse the entity loaded in this transaction. DynamicUpdate writes only changed columns.
        accounts.findById(account.id()).orElseThrow(AccountNotFoundException::new).apply(account);
    }

    @Override
    public AccountPage list(int page, int size) {
        var result = accounts.findAll(PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
        return new AccountPage(result.map(entity -> entity.toDomain().view()).getContent(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    private boolean isEmailConstraint(ConstraintViolationException failure) {
        if (!"23505".equals(failure.getSQLState()) || failure.getConstraintName() == null) return false;
        String name = failure.getConstraintName().toLowerCase(Locale.ROOT);
        name = name.substring(name.lastIndexOf('.') + 1);
        // PostgreSQL reports the constraint; H2 reports its generated backing index.
        return name.equals("uk_user_accounts_email") || name.startsWith("uk_user_accounts_email_index_");
    }
}
