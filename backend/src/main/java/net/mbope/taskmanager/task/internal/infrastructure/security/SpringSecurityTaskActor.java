package net.mbope.taskmanager.task.internal.infrastructure.security;

import java.util.UUID;
import net.mbope.taskmanager.task.internal.application.port.CurrentTaskActor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
class SpringSecurityTaskActor implements CurrentTaskActor {
    public UUID id() {
        String subject = authenticated().getName();
        try {
            UUID id = UUID.fromString(subject);
            if (!id.toString().equals(subject)) {
                throw new IllegalArgumentException();
            }
            return id;
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw new AuthenticationCredentialsNotFoundException("A valid account subject is required.");
        }
    }

    public void requireAdmin() {
        if (authenticated().getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new AccessDeniedException("ADMIN authority is required.");
        }
        id();
    }

    private Authentication authenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        return auth;
    }
}
