package net.mbope.taskmanager.auth.internal.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import net.mbope.taskmanager.common.ApiProblems;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

class AuthenticationRateLimitFilter extends OncePerRequestFilter {
    private final AuthenticationRateLimiter limiter;
    private final ObjectMapper mapper;
    private final MeterRegistry metrics;

    AuthenticationRateLimitFilter(AuthenticationRateLimiter limiter, ObjectMapper mapper, MeterRegistry metrics) {
        this.limiter = limiter;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        if (!"POST".equals(request.getMethod()) || !(path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register"))) {
            chain.doFilter(request, response);
            return;
        }
        boolean registration = path.endsWith("/register");
        // Only the container may resolve forwarding headers, using its trusted-proxy configuration.
        long retry = limiter.retryAfter(request.getRemoteAddr(), registration);
        if (retry > 0) {
            metrics.counter("auth.rate.limit.rejections", "operation", registration ? "register" : "login").increment();
            response.setHeader("Retry-After", Long.toString(retry));
            response.setHeader("Cache-Control", "no-store");
            ApiProblems.write(mapper, request, response, HttpStatus.TOO_MANY_REQUESTS,
                    "rate-limited", "Too many authentication requests. Try again later.");
            return;
        }
        chain.doFilter(request, response);
    }
}
