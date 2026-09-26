package net.mbope.taskmanager.user.internal.application;

import java.time.Clock;

import net.mbope.taskmanager.user.Account;
import net.mbope.taskmanager.user.AccountRegistration;
import net.mbope.taskmanager.user.internal.application.port.AccountStore;
import net.mbope.taskmanager.user.internal.application.port.EmailNormalizer;
import net.mbope.taskmanager.user.internal.application.port.PasswordHasher;
import net.mbope.taskmanager.user.internal.domain.PasswordPolicy;
import net.mbope.taskmanager.user.internal.domain.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class RegistrationService implements AccountRegistration {
    private final AccountStore accounts;
    private final EmailNormalizer emails;
    private final PasswordHasher passwords;
    private final Clock clock;

    RegistrationService(AccountStore accounts, EmailNormalizer emails, PasswordHasher passwords, Clock clock) {
        this.accounts = accounts;
        this.emails = emails;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Override
    public Account register(String email, String password) {
        String normalized = emails.normalize(email);
        PasswordPolicy.validateNew(password, "password");
        String passwordHash = passwords.encode(password);
        UserAccount account = UserAccount.register(normalized, passwordHash, clock.instant());
        return accounts.add(account).view();
    }
}
