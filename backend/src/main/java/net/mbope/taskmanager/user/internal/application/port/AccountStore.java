package net.mbope.taskmanager.user.internal.application.port;

import net.mbope.taskmanager.user.internal.domain.UserAccount;
import net.mbope.taskmanager.user.AccountPage;
import net.mbope.taskmanager.user.EmailAlreadyRegisteredException;

import java.util.Optional;
import java.util.UUID;

public interface AccountStore {
    /**
     * Atomically enforces email uniqueness, including concurrent registrations.
     *
     * @throws EmailAlreadyRegisteredException if the email is already registered
     */
    UserAccount add(UserAccount account);

    Optional<UserAccount> findById(UUID id);

    Optional<UserAccount> findByEmail(String normalizedEmail);

    /**
     * Saves a previously loaded account within the same application transaction.
     */
    void save(UserAccount account);

    /**
     * Orders by creation time descending, then ID ascending.
     */
    AccountPage list(int page, int size);
}
