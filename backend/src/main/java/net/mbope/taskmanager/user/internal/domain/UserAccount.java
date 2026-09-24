package net.mbope.taskmanager.user.internal.domain;

import net.mbope.taskmanager.user.Account;
import net.mbope.taskmanager.user.Role;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Account state and transitions, independent of persistence and security frameworks.
 */
public final class UserAccount {
    private final UUID id;
    private final String email;
    private final Instant createdAt;
    private String passwordHash;
    private AccountRoles roles;
    private boolean enabled;
    private Instant updatedAt;

    private UserAccount(UUID id, String email, String passwordHash, AccountRoles roles,
                        boolean enabled, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = Objects.requireNonNull(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.roles = Objects.requireNonNull(roles);
        this.enabled = enabled;
        this.createdAt = Objects.requireNonNull(createdAt).truncatedTo(ChronoUnit.MICROS);
        this.updatedAt = Objects.requireNonNull(updatedAt).truncatedTo(ChronoUnit.MICROS);
    }

    public static UserAccount register(String normalizedEmail, String passwordHash, Instant now) {
        return new UserAccount(null, normalizedEmail, passwordHash, AccountRoles.user(), true, now, now);
    }

    public static UserAccount restore(UUID id, String email, String passwordHash, AccountRoles roles,
                                      boolean enabled, Instant createdAt, Instant updatedAt) {
        return new UserAccount(Objects.requireNonNull(id), email, passwordHash, roles, enabled, createdAt, updatedAt);
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Set<Role> roles() {
        return roles.values();
    }

    public boolean enabled() {
        return enabled;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public void changePassword(String hash, Instant now) {
        passwordHash = Objects.requireNonNull(hash);
        updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public void replaceRoles(AccountRoles roles, Instant now) {
        this.roles = Objects.requireNonNull(roles);
        updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public void setEnabled(boolean enabled, Instant now) {
        this.enabled = enabled;
        updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public Account view() {
        return new Account(id, email, roles(), enabled, createdAt, updatedAt);
    }
}
