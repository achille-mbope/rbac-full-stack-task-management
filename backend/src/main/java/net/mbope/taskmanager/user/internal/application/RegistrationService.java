package net.mbope.taskmanager.user.internal.application;

import net.mbope.taskmanager.user.internal.application.port.*;
import net.mbope.taskmanager.user.internal.domain.*;

import net.mbope.taskmanager.user.Account;
import net.mbope.taskmanager.user.AccountRegistration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

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
        return accounts.add(UserAccount.register(normalized, passwords.encode(password), clock.instant())).view();
    }
}
