package net.mbope.taskmanager.user;

import java.util.Optional;

public interface UserCredentials {
    /**
     * Empty for an unknown account, incorrect password, or disabled account.
     */
    Optional<AccountIdentity> verify(String email, String password);

    /**
     * Changes only the authenticated subject's password; existing JWTs remain valid.
     */
    void changePassword(String currentPassword, String newPassword);
}
