package net.mbope.taskmanager.auth.internal.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import net.mbope.taskmanager.common.ApiProblems;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** Container TLS state may only be supplied directly or by a configured trusted proxy. */
class SecureTransportFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;
    SecureTransportFilter(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.isSecure()) {
            ApiProblems.write(mapper, request, response, HttpStatus.FORBIDDEN, "https-required", "HTTPS is required.");
            return;
        }
        chain.doFilter(request, response);
    }
}
