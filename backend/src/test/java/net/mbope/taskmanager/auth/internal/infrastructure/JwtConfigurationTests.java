package net.mbope.taskmanager.auth.internal.infrastructure;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import net.mbope.taskmanager.user.AccountIdentity;
import net.mbope.taskmanager.user.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.assertj.core.api.Assertions.*;

class JwtConfigurationTests {
    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JwtConfiguration config = new JwtConfiguration();
    private final SecretKey key = JwtConfiguration.signingKey(
            Base64.getEncoder().encodeToString("unit-test-key-material-64-bytes-abcdefghijklmnopqrstuvwxyz01234567".getBytes(StandardCharsets.UTF_8)));
    private final JwtEncoder encoder = config.jwtEncoder(key);
    private final JwtDecoder decoder = config.jwtDecoder(key, clock);

    @Test
    void issuedTokensHaveRequiredClaimsAndExpireInFifteenMinutes() {
        var id = UUID.randomUUID();
        var token = new JwtTokenIssuer(encoder, clock).issue(new AccountIdentity(id, Set.of(Role.USER, Role.ADMIN)));
        var jwt = decoder.decode(token.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(id.toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(JwtPolicy.ISSUER);
        assertThat(jwt.getAudience()).containsExactly(JwtPolicy.AUDIENCE);
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN", "USER");
        assertThat(token.expiresIn()).isEqualTo(900);
        assertThat(token.toString()).doesNotContain(token.accessToken());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidClaims")
    void rejectsInvalidClaims(String description, Consumer<JwtClaimsSet.Builder> mutation) {
        var builder = claims();
        mutation.accept(builder);
        assertThatThrownBy(() -> decoder.decode(sign(builder.build(), encoder, MacAlgorithm.HS256)))
                .as(description).isInstanceOf(JwtException.class);
    }

    private static Stream<Arguments> invalidClaims() {
        return Stream.of(
                invalidClaim("wrong issuer", claims -> claims.issuer("other")),
                invalidClaim("wrong audience", claims -> claims.audience(List.of("other"))),
                invalidClaim("invalid subject", claims -> claims.subject("not-a-uuid")),
                invalidClaim("missing USER role", claims -> claims.claim("roles", List.of("ADMIN"))),
                invalidClaim("unknown role", claims -> claims.claim("roles", List.of("USER", "SUPERUSER"))),
                invalidClaim("roles must be an array", claims -> claims.claim("roles", "USER")),
                invalidClaim("duplicate roles", claims -> claims.claim("roles", List.of("USER", "USER"))),
                invalidClaim("future issuance", claims -> claims.issuedAt(NOW.plusSeconds(1))),
                invalidClaim("expired token", claims -> claims.issuedAt(NOW.minusSeconds(901)).expiresAt(NOW.minusSeconds(1))),
                invalidClaim("excessive lifetime", claims -> claims.expiresAt(NOW.plusSeconds(901))),
                invalidClaim("missing expiration", claims -> claims.claims(values -> values.remove("exp"))),
                invalidClaim("missing issuance", claims -> claims.claims(values -> values.remove("iat"))),
                invalidClaim("missing subject", claims -> claims.claims(values -> values.remove("sub"))));
    }

    private static Arguments invalidClaim(String description, Consumer<JwtClaimsSet.Builder> mutation) {
        return Arguments.of(description, mutation);
    }

    @Test
    void rejectsOtherAlgorithmsAndSignatures() {
        assertThatThrownBy(() -> decoder.decode(sign(claims().build(), encoder, MacAlgorithm.HS384)))
                .isInstanceOf(JwtException.class);
        var other = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(new byte[64], "HmacSHA256")));
        assertThatThrownBy(() -> decoder.decode(sign(claims().build(), other, MacAlgorithm.HS256)))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode("not.a.token")).isInstanceOf(JwtException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-base64!", "c2hvcnQ="})
    void rejectsMissingOrWeakSecret(String value) {
        new ApplicationContextRunner().withUserConfiguration(JwtConfiguration.class)
                .withBean(Clock.class, () -> clock)
                .withPropertyValues("JWT_SECRET=" + (value == null ? "" : value))
                .run(context -> assertThat(context).hasFailed());
    }

    private JwtClaimsSet.Builder claims() {
        return JwtClaimsSet.builder().subject(UUID.randomUUID().toString()).issuer(JwtPolicy.ISSUER)
                .audience(List.of(JwtPolicy.AUDIENCE)).issuedAt(NOW).expiresAt(NOW.plusSeconds(900))
                .claim("roles", List.of("USER"));
    }

    private String sign(JwtClaimsSet claims, JwtEncoder encoder, MacAlgorithm algorithm) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(algorithm).build(), claims)).getTokenValue();
    }
}
