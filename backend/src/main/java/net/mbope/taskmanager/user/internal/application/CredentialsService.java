package net.mbope.taskmanager.user.internal.application;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import net.mbope.taskmanager.user.AccountIdentity;
import net.mbope.taskmanager.user.AccountNotFoundException;
import net.mbope.taskmanager.user.UserCredentials;
import net.mbope.taskmanager.user.UserValidationException;
import net.mbope.taskmanager.user.internal.application.port.AccountStore;
import net.mbope.taskmanager.user.internal.application.port.CurrentAccount;
import net.mbope.taskmanager.user.internal.application.port.EmailNormalizer;
import net.mbope.taskmanager.user.internal.application.port.PasswordHasher;
import net.mbope.taskmanager.user.internal.domain.PasswordPolicy;
import net.mbope.taskmanager.user.internal.domain.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class CredentialsService implements UserCredentials {
    private final AccountStore accounts;
    private final EmailNormalizer emails;
    private final PasswordHasher passwords;
    private final CurrentAccount currentAccount;
    private final Clock clock;
    private final String dummyHash;

    CredentialsService(AccountStore accounts, EmailNormalizer emails, PasswordHasher passwords,
                       CurrentAccount currentAccount, Clock clock) {
        this.accounts = accounts;
        this.emails = emails;
        this.passwords = passwords;
        this.currentAccount = currentAccount;
        this.clock = clock;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Override
    public Optional<AccountIdentity> verify(String email, String password) {
        String normalized = emails.normalize(email);
        PasswordPolicy.validateCurrent(password, "password");
        Optional<UserAccount> account = accounts.findByEmail(normalized);
        // Run BCrypt for unknown and disabled accounts too; do not expose their status.
        boolean matches = passwords.matches(password, account.map(UserAccount::passwordHash).orElse(dummyHash));
        return account.filter(found -> matches && found.enabled())
                .map(found -> new AccountIdentity(found.id(), found.roles()));
    }

    @Override
    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        UUID subject = currentAccount.id();
        PasswordPolicy.validateCurrent(currentPassword, "currentPassword");
        PasswordPolicy.validateNew(newPassword, "newPassword");
        UserAccount account = accounts.findById(subject).orElseThrow(AccountNotFoundException::new);
        if (!passwords.matches(currentPassword, account.passwordHash())) {
            throw new UserValidationException("currentPassword", "incorrect_password", "Current password is incorrect.");
        }
        account.changePassword(passwords.encode(newPassword), clock.instant());
        accounts.save(account);
    }
}
