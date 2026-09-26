package net.mbope.taskmanager.user.internal.infrastructure.security;

import java.util.UUID;

import net.mbope.taskmanager.user.internal.application.port.CurrentAccount;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
class SpringSecurityCurrentAccount implements CurrentAccount {
    public UUID id() {
        String subject = authenticated().getName();
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException failure) {
            throw new AuthenticationCredentialsNotFoundException("A valid account subject is required.");
        }
    }

    public void requireAdmin() {
        boolean admin = authenticated().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!admin) {
            throw new AccessDeniedException("ADMIN authority is required.");
        }
    }

    private Authentication authenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        return authentication;
    }
}
