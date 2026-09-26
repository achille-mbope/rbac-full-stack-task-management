package net.mbope.taskmanager.auth.internal.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class JwtClaimsValidator implements OAuth2TokenValidator<Jwt> {
    private final Clock clock;

    JwtClaimsValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            if (hasValidSubject(jwt) && hasValidAudience(jwt) && hasValidLifetime(jwt) && hasValidRoles(jwt)) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (IllegalArgumentException | ClassCastException invalidClaimType) {
            // Malformed claims are authentication failures, not application errors.
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid access token.", null));
    }

    private boolean hasValidSubject(Jwt jwt) {
        String subject = jwt.getSubject();
        return subject != null && UUID.fromString(subject).toString().equals(subject);
    }

    private boolean hasValidAudience(Jwt jwt) {
        return jwt.getAudience() != null && jwt.getAudience().contains(JwtPolicy.AUDIENCE);
    }

    private boolean hasValidLifetime(Jwt jwt) {
        Instant issuedAt = jwt.getIssuedAt();
        Instant expiresAt = jwt.getExpiresAt();
        Instant now = clock.instant();
        return issuedAt != null && expiresAt != null
                && !issuedAt.isAfter(now) && expiresAt.isAfter(now) && expiresAt.isAfter(issuedAt)
                && Duration.between(issuedAt, expiresAt).compareTo(JwtPolicy.LIFETIME) <= 0;
    }

    private boolean hasValidRoles(Jwt jwt) {
        Object claim = jwt.getClaims().get("roles");
        return claim instanceof List<?> roles && roles.contains("USER") && roles.size() <= 2
                && new HashSet<>(roles).size() == roles.size()
                && roles.stream().allMatch(role -> "USER".equals(role) || "ADMIN".equals(role));
    }
}
