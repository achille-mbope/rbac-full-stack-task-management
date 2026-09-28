package net.mbope.taskmanager.auth.internal.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;

import net.mbope.taskmanager.common.ApiProblems;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Component
class CorsPolicy {
    private final ObjectMapper mapper;
    private final List<String> allowedOrigins;

    CorsPolicy(ObjectMapper mapper, @Value("${app.cors.allowed-origins:}") String origins) {
        this.mapper = mapper;
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::strip).filter(origin -> !origin.isEmpty()).toList();
        if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("CORS origins must be explicit origins, without wildcards.");
        }
    }

    // Constructed only for the security chain, not registered again as a servlet filter bean.
    CorsFilter filter() {
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration());
        var filter = new CorsFilter(source);
        var processor = new DefaultCorsProcessor() {
            @Override
            protected void rejectRequest(ServerHttpResponse response) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
            }
        };
        filter.setCorsProcessor((configuration, request, response) -> {
            boolean accepted = processor.processRequest(configuration, request, response);
            if (!accepted) {
                ApiProblems.write(mapper, request, response, HttpStatus.FORBIDDEN, "forbidden", "Origin is not allowed.");
            }
            return accepted;
        });
        return filter;
    }

    private CorsConfiguration configuration() {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        cors.setExposedHeaders(List.of("Location", "WWW-Authenticate", "Retry-After", "X-Request-ID"));
        cors.setAllowCredentials(false);
        return cors;
    }
}
