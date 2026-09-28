package net.mbope.taskmanager.auth.internal.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import net.mbope.taskmanager.common.ApiProblems;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
class SecurityProblemHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {
    private static final String BEARER_PREFIX = "Bearer ";
    private final ObjectMapper mapper;

    SecurityProblemHandlers(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException failure) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge(request));
        ApiProblems.write(mapper, request, response, HttpStatus.UNAUTHORIZED,
                "unauthorized", "Authentication is required.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException failure) throws IOException {
        ApiProblems.write(mapper, request, response, HttpStatus.FORBIDDEN, "forbidden", "Access is denied.");
    }

    private String challenge(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean bearerSupplied = authorization != null
                && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length());
        return bearerSupplied ? "Bearer error=\"invalid_token\"" : "Bearer";
    }
}
