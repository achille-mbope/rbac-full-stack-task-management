package net.mbope.taskmanager.auth.internal.infrastructure;

import java.time.Duration;

final class JwtPolicy {
    static final String ISSUER = "task-manager";
    static final String AUDIENCE = "task-manager-api";
    static final Duration LIFETIME = Duration.ofMinutes(15);
    static final int MINIMUM_KEY_BYTES = 32;

    private JwtPolicy() {
    }
}
