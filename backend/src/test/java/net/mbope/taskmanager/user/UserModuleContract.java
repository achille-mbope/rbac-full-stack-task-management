package net.mbope.taskmanager.user;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
abstract class UserModuleContract {
    private static final String PASSWORD = "a long original password";
    @Autowired
    AccountRegistration registration;
    @Autowired
    UserCredentials credentials;
    @Autowired
    AccountLookup lookup;
    @Autowired
    AccountAdministration administration;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void resetAccounts() {
        SecurityContextHolder.clearContext();
        jdbc.update("delete from user_accounts");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registersAnEnabledUserWithNormalizedEmailAndOnlyAHashInStorage() {
        Account account = registration.register(" Alice@Example.COM ", PASSWORD);
        assertThat(account.id()).isNotNull();
        assertThat(account.email()).isEqualTo("alice@example.com");
        assertThat(account.roles()).containsExactly(Role.USER);
        assertThat(account.enabled()).isTrue();
        assertThat(account.createdAt()).isEqualTo(account.updatedAt());
        String hash = jdbc.queryForObject("select password_hash from user_accounts where id = ?", String.class, account.id());
        assertThat(hash).startsWith("$2a$12$").isNotEqualTo(PASSWORD);
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, hash)).isTrue();
        assertThat(account.toString()).doesNotContain(PASSWORD, hash);
        assertThatThrownBy(() -> account.roles().add(Role.ADMIN)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(lookup.findById(account.id())).contains(new AccountReference(account.id(), true));
        assertThat(lookup.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void rejectsDuplicateNormalizedEmailsAndLeavesTheOriginalAccountIntact() {
        Account original = registration.register("alice@example.com", PASSWORD);
        assertThatThrownBy(() -> registration.register(" ALICE@EXAMPLE.COM ", "another long password"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        assertThat(jdbc.queryForObject("select count(*) from user_accounts", Long.class)).isEqualTo(1);
        assertThat(credentials.verify("alice@example.com", PASSWORD).orElseThrow().id()).isEqualTo(original.id());
    }

    @Test
    void concurrentRegistrationHasOneWinnerAndOneDomainConflict() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            Callable<Object> attempt = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start signal missing");
                }
                try {
                    return registration.register("race@example.com", PASSWORD);
                } catch (EmailAlreadyRegisteredException conflict) {
                    return conflict;
                }
            };
            Future<Object> first = executor.submit(attempt);
            Future<Object> second = executor.submit(attempt);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertThat(results.stream().filter(Account.class::isInstance)).hasSize(1);
            assertThat(results.stream().filter(EmailAlreadyRegisteredException.class::isInstance)).hasSize(1);
            assertThat(jdbc.queryForObject("select count(*) from user_accounts", Long.class)).isEqualTo(1);
        }
    }

    @Test
    void rejectsInvalidRegistrationBeforePersistingAnything() {
        assertThatThrownBy(() -> registration.register("invalid", PASSWORD)).isInstanceOf(UserValidationException.class);
        assertThatThrownBy(() -> registration.register("alice@example.com", "short")).isInstanceOf(UserValidationException.class);
        assertThatThrownBy(() -> registration.register("alice@example.com", "é".repeat(37))).isInstanceOf(UserValidationException.class);
        assertThat(jdbc.queryForObject("select count(*) from user_accounts", Long.class)).isZero();
    }

    @Test
    void verifiesCredentialsWithoutExposingSecretsOrDistinguishingLoginFailures() {
        Account account = registration.register("alice@example.com", PASSWORD);
        assertThat(credentials.verify(" ALICE@EXAMPLE.COM ", PASSWORD))
                .contains(new AccountIdentity(account.id(), Set.of(Role.USER)));
        assertThat(credentials.verify("alice@example.com", "incorrect")).isEmpty();
        assertThat(credentials.verify("missing@example.com", PASSWORD)).isEmpty();
        asAdmin(UUID.randomUUID());
        administration.setEnabled(account.id(), false);
        assertThat(credentials.verify("alice@example.com", PASSWORD)).isEmpty();
        assertThat(lookup.findById(account.id())).contains(new AccountReference(account.id(), false));
    }

    @Test
    void preservesWhitespaceAndUnicodeInCredentials() {
        String password = "  päss phrase 日本語  ";
        registration.register("alice@example.com", password);
        assertThat(credentials.verify("alice@example.com", password)).isPresent();
        assertThat(credentials.verify("alice@example.com", password.strip())).isEmpty();
    }

    @Test
    void changesOnlyTheAuthenticatedAccountsPassword() {
        Account alice = registration.register("alice@example.com", PASSWORD);
        registration.register("bob@example.com", PASSWORD);
        asUser(alice.id());
        credentials.changePassword(PASSWORD, "a different long password");
        assertThat(credentials.verify("alice@example.com", PASSWORD)).isEmpty();
        assertThat(credentials.verify("alice@example.com", "a different long password")).isPresent();
        assertThat(credentials.verify("bob@example.com", PASSWORD)).isPresent();
    }

    @Test
    void wrongCurrentPasswordAndInvalidNewPasswordLeaveCredentialsUnchanged() {
        Account account = registration.register("alice@example.com", PASSWORD);
        asUser(account.id());
        assertThatThrownBy(() -> credentials.changePassword("wrong", "a different long password"))
                .isInstanceOfSatisfying(UserValidationException.class, failure -> {
                    assertThat(failure.field()).isEqualTo("currentPassword");
                    assertThat(failure.code()).isEqualTo("incorrect_password");
                    assertThat(failure.getMessage()).doesNotContain("wrong");
                });
        assertThatThrownBy(() -> credentials.changePassword(PASSWORD, "short")).isInstanceOf(UserValidationException.class);
        assertThat(credentials.verify("alice@example.com", PASSWORD)).isPresent();
    }

    @Test
    void passwordChangesRequireAuthentication() {
        assertThatThrownBy(() -> credentials.changePassword(PASSWORD, "a different long password"))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void adminOperationsRejectOrdinaryUsersEvenForMissingAccounts() {
        asUser(UUID.randomUUID());
        assertThatThrownBy(() -> administration.list(0, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> administration.replaceRoles(UUID.randomUUID(), Set.of(Role.USER, Role.ADMIN)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> administration.setEnabled(UUID.randomUUID(), false)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void administratorCanManageRolesAndStatusAndDemoteOrDisableSelf() {
        Account account = registration.register("admin@example.com", PASSWORD);
        asAdmin(account.id());
        Account promoted = administration.replaceRoles(account.id(), Set.of(Role.USER, Role.ADMIN));
        assertThat(promoted.roles()).containsExactlyInAnyOrder(Role.USER, Role.ADMIN);
        assertThat(promoted.createdAt()).isEqualTo(account.createdAt());
        assertThat(promoted.updatedAt()).isAfterOrEqualTo(account.updatedAt());
        assertThat(credentials.verify(account.email(), PASSWORD).orElseThrow().roles()).contains(Role.ADMIN);
        assertThat(administration.replaceRoles(account.id(), Set.of(Role.USER)).roles()).containsExactly(Role.USER);
        // The existing principal retains its token authorities until token expiry.
        assertThat(administration.setEnabled(account.id(), false).enabled()).isFalse();
        assertThat(administration.setEnabled(account.id(), true).enabled()).isTrue();
    }

    @Test
    void rolesMustAlwaysIncludeUserAndAuthorizedMissingTargetsReturnNotFound() {
        Account account = registration.register("alice@example.com", PASSWORD);
        asAdmin(UUID.randomUUID());
        assertThatThrownBy(() -> administration.replaceRoles(account.id(), Set.of(Role.ADMIN)))
                .isInstanceOf(UserValidationException.class);
        assertThatThrownBy(() -> administration.replaceRoles(account.id(), Set.of()))
                .isInstanceOf(UserValidationException.class);
        assertThatThrownBy(() -> administration.setEnabled(UUID.randomUUID(), true))
                .isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> administration.replaceRoles(UUID.randomUUID(), Set.of(Role.USER)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void listsAccountsWithBoundedPaginationAndDeterministicOrdering() {
        Account first = registration.register("first@example.com", PASSWORD);
        Account second = registration.register("second@example.com", PASSWORD);
        asAdmin(UUID.randomUUID());
        AccountPage page = administration.list(0, 1);
        assertThat(page.items()).extracting(Account::id).containsExactly(second.id());
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(1);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(administration.list(1, 1).items()).extracting(Account::id).containsExactly(first.id());
        assertThat(administration.list(5, 1).items()).isEmpty();
        assertThatThrownBy(() -> administration.list(-1, 20)).isInstanceOf(UserValidationException.class);
        assertThatThrownBy(() -> administration.list(0, 101)).isInstanceOf(UserValidationException.class);
        assertThatThrownBy(() -> administration.list(0, 0)).isInstanceOf(UserValidationException.class);
        jdbc.update("update user_accounts set created_at = ?", Timestamp.from(first.createdAt()));
        List<UUID> expected = jdbc.queryForList("select id from user_accounts order by id asc", UUID.class);
        assertThat(administration.list(0, 20).items()).extracting(Account::id).containsExactlyElementsOf(expected);
    }

    @Test
    void emptyListHasZeroTotals() {
        asAdmin(UUID.randomUUID());
        AccountPage page = administration.list(0, 20);
        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
    }

    private void asUser(UUID id) {
        authenticate(id, "ROLE_USER");
    }

    private void asAdmin(UUID id) {
        authenticate(id, "ROLE_USER", "ROLE_ADMIN");
    }

    private void authenticate(UUID id, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(id.toString(), null,
                        Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
}
