package net.mbope.taskmanager.user.internal.infrastructure.security;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;

class SpringSecurityCurrentAccountTests {
    private final SpringSecurityCurrentAccount current = new SpringSecurityCurrentAccount();

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsAuthenticatedNonAdministrators() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        assertThatThrownBy(current::requireAdmin).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void absentAuthenticationCannotAdministerAccounts() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(current::requireAdmin).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void anonymousPrincipalCannotUseAdminAuthority() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "test", UUID.randomUUID().toString(), List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertThatThrownBy(current::requireAdmin).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void unauthenticatedTokenCannotSupplyAnAccountIdentity() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated(UUID.randomUUID().toString(), null));
        assertThatThrownBy(current::id).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void malformedSubjectCannotSupplyAnAccountIdentity() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("not-a-uuid", null, List.of()));
        assertThatThrownBy(current::id).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
