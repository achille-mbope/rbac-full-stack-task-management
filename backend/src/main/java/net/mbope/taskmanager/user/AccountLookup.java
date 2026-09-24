package net.mbope.taskmanager.user;

import java.util.Optional;
import java.util.UUID;

public interface AccountLookup {
    Optional<AccountReference> findById(UUID accountId);
}
