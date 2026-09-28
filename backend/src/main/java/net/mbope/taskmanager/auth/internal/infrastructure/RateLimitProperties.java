package net.mbope.taskmanager.auth.internal.infrastructure;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.rate-limit")
record RateLimitProperties(boolean enabled, @Min(1) @Max(3600) int windowSeconds,
        @Min(1) int loginPerClient, @Min(1) int registerPerClient,
        @Min(1) int loginGlobal, @Min(1) int registerGlobal, @Min(1) @Max(100000) int maxClients) {
}
