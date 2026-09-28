package net.mbope.taskmanager.auth.internal.infrastructure;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Component;

/** Fixed-window, bounded-memory per-process admission control before password hashing. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
class AuthenticationRateLimiter {
    private final RateLimitProperties properties;
    private final LongSupplier ticker;
    private final Map<String, int[]> clients = new HashMap<>();
    private final int[] totals = new int[2];
    private long started;

    @org.springframework.beans.factory.annotation.Autowired
    AuthenticationRateLimiter(RateLimitProperties properties) { this(properties, System::nanoTime); }

    AuthenticationRateLimiter(RateLimitProperties properties, LongSupplier ticker) {
        this.properties = properties;
        this.ticker = ticker;
        this.started = ticker.getAsLong();
    }

    /** Returns zero if admitted, otherwise seconds until the current window ends. */
    synchronized long retryAfter(String client, boolean registration) {
        if (!properties.enabled()) return 0;
        long window = properties.windowSeconds() * 1_000_000_000L;
        long elapsed = ticker.getAsLong() - started;
        if (elapsed >= window) {
            started = ticker.getAsLong();
            elapsed = 0;
            clients.clear();
            totals[0] = totals[1] = 0;
        }
        int index = registration ? 1 : 0;
        int limit = registration ? properties.registerPerClient() : properties.loginPerClient();
        int global = registration ? properties.registerGlobal() : properties.loginGlobal();
        int[] counts = clients.get(client);
        if (totals[index] >= global || (counts != null && counts[index] >= limit)
                || (counts == null && clients.size() >= properties.maxClients()))
            return Math.max(1, (window - elapsed + 999_999_999L) / 1_000_000_000L);
        if (counts == null) {
            counts = new int[2];
            clients.put(client, counts);
        }
        counts[index]++;
        totals[index]++;
        return 0;
    }
}
