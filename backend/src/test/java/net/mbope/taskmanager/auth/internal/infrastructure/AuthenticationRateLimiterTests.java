package net.mbope.taskmanager.auth.internal.infrastructure;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthenticationRateLimiterTests {
    private final AtomicLong time = new AtomicLong();
    private AuthenticationRateLimiter limiter(int perClient, int global, int capacity) {
        return new AuthenticationRateLimiter(new RateLimitProperties(true, 60, perClient, 1, global, 2, capacity), time::get);
    }

    @Test
    void quotasArePerClientAndPerOperationAndResetAtBoundary() {
        var limiter = limiter(2, 10, 10);
        assertThat(limiter.retryAfter("a", false)).isZero();
        assertThat(limiter.retryAfter("a", false)).isZero();
        assertThat(limiter.retryAfter("a", false)).isEqualTo(60);
        assertThat(limiter.retryAfter("a", true)).isZero();
        assertThat(limiter.retryAfter("a", true)).isEqualTo(60);
        assertThat(limiter.retryAfter("b", false)).isZero();
        time.set(59_999_999_999L);
        assertThat(limiter.retryAfter("a", false)).isEqualTo(1);
        time.set(60_000_000_000L);
        assertThat(limiter.retryAfter("a", false)).isZero();
    }

    @Test
    void globalQuotaStopsRotatingClients() {
        var limiter = limiter(2, 2, 10);
        assertThat(limiter.retryAfter("a", false)).isZero();
        assertThat(limiter.retryAfter("b", false)).isZero();
        assertThat(limiter.retryAfter("c", false)).isEqualTo(60);
        assertThat(limiter.retryAfter("c", true)).isZero();
    }

    @Test
    void capacityRejectsNewClientsWithoutEvictingExistingQuotas() {
        var limiter = limiter(2, 10, 1);
        assertThat(limiter.retryAfter("a", false)).isZero();
        assertThat(limiter.retryAfter("b", false)).isEqualTo(60);
        assertThat(limiter.retryAfter("a", false)).isZero();
        assertThat(limiter.retryAfter("a", false)).isEqualTo(60);
        time.set(60_000_000_000L);
        assertThat(limiter.retryAfter("b", false)).isZero();
    }

    @Test
    void concurrentRequestsCannotExceedQuota() throws Exception {
        var limiter = limiter(10, 100, 10);
        try (var executor = Executors.newFixedThreadPool(12)) {
            var tasks = IntStream.range(0, 100).<Callable<Long>>mapToObj(i -> () -> limiter.retryAfter("a", false)).toList();
            long admitted = 0;
            for (var result : executor.invokeAll(tasks)) if (result.get() == 0) admitted++;
            assertThat(admitted).isEqualTo(10);
        }
    }
}
