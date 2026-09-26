package net.mbope.taskmanager.auth.internal.infrastructure;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import net.mbope.taskmanager.auth.internal.application.TokenResponse;
import net.mbope.taskmanager.auth.internal.application.port.TokenIssuer;
import net.mbope.taskmanager.user.AccountIdentity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
class JwtTokenIssuer implements TokenIssuer {
    private final JwtEncoder encoder;
    private final Clock clock;

    JwtTokenIssuer(JwtEncoder encoder, Clock clock) {
        this.encoder = encoder;
        this.clock = clock;
    }

    public TokenResponse issue(AccountIdentity identity) {
        var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        var claims = JwtClaimsSet.builder().subject(identity.id().toString())
                .issuer(JwtPolicy.ISSUER).audience(List.of(JwtPolicy.AUDIENCE))
                .issuedAt(now).expiresAt(now.plus(JwtPolicy.LIFETIME))
                .claim("roles", identity.roles().stream().map(Enum::name).sorted().toList()).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenResponse(token, "Bearer", JwtPolicy.LIFETIME.toSeconds());
    }
}
