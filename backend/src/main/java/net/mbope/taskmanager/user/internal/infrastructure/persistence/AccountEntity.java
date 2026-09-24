package net.mbope.taskmanager.user.internal.infrastructure.persistence;

import net.mbope.taskmanager.user.internal.domain.*;

import jakarta.persistence.*;
import net.mbope.taskmanager.user.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_accounts", uniqueConstraints = @UniqueConstraint(name = "uk_user_accounts_email", columnNames = "email"))
@DynamicUpdate
class AccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    // With exactly two roles, this representation guarantees that every account has USER.
    @Column(name = "is_admin", nullable = false)
    private boolean administrator;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountEntity() {
    }

    AccountEntity(UserAccount account) {
        email = account.email();
        createdAt = account.createdAt();
        apply(account);
    }

    UserAccount toDomain() {
        return UserAccount.restore(id, email, passwordHash,
                new AccountRoles(administrator ? Set.of(Role.USER, Role.ADMIN) : Set.of(Role.USER)),
                enabled, createdAt, updatedAt);
    }

    void apply(UserAccount account) {
        passwordHash = account.passwordHash();
        administrator = account.roles().contains(Role.ADMIN);
        enabled = account.enabled();
        updatedAt = account.updatedAt();
    }
}
