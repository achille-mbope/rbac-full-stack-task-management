package net.mbope.taskmanager.user.internal.infrastructure.persistence;

import net.mbope.taskmanager.user.internal.domain.UserAccount;

import net.mbope.taskmanager.user.EmailAlreadyRegisteredException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JpaAccountStoreTests {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final JpaAccountStore adapter = new JpaAccountStore(accounts);

    @ParameterizedTest
    @ValueSource(strings = {"uk_user_accounts_email", "PUBLIC.UK_USER_ACCOUNTS_EMAIL_INDEX_1"})
    void translatesEmailUniquenessFailures(String constraintName) {
        failWith("23505", constraintName);
        assertThatThrownBy(this::register).isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"other_constraint", "uk_user_accounts_email_other"})
    void preservesUnrelatedUniquenessFailures(String constraintName) {
        var failure = failWith("23505", constraintName);
        assertThatThrownBy(this::register).isSameAs(failure);
    }

    @ParameterizedTest
    @ValueSource(strings = {"23502", "23503"})
    void preservesFailuresWithOtherSqlStates(String state) {
        var failure = failWith(state, "uk_user_accounts_email");
        assertThatThrownBy(this::register).isSameAs(failure);
    }

    private DataIntegrityViolationException failWith(String state, String constraintName) {
        var constraint = new ConstraintViolationException("constraint failure",
                new SQLException("constraint failure", state), constraintName);
        var failure = new DataIntegrityViolationException("insert failed", constraint);
        when(accounts.saveAndFlush(any(AccountEntity.class))).thenThrow(failure);
        return failure;
    }

    private void register() {
        adapter.add(UserAccount.register("person@example.com", "encoded-password", Instant.EPOCH));
    }
}
