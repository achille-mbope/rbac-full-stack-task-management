package net.mbope.taskmanager.auth.internal.infrastructure;

import org.springframework.security.authorization.AuthorizationDecision;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.CorsFilter;

@org.springframework.boot.context.properties.EnableConfigurationProperties(RateLimitProperties.class)
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class HttpSecurityConfiguration {
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, SecurityProblemHandlers problems, CorsPolicy cors,
            @Value("${app.api-docs.enabled:false}") boolean docsEnabled,
            @Value("${app.security.require-https:false}") boolean requireHttps,
            AuthenticationRateLimiter limiter, com.fasterxml.jackson.databind.ObjectMapper mapper,
            io.micrometer.core.instrument.MeterRegistry metrics) throws Exception {
        if (requireHttps) http.addFilterBefore(new SecureTransportFilter(mapper),
                org.springframework.security.web.header.HeaderWriterFilter.class);
        http.csrf(config -> config.disable())
                .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(config -> config.disable())
                .formLogin(config -> config.disable())
                .httpBasic(config -> config.disable())
                .logout(config -> config.disable())
                .addFilterAt(cors.filter(), CorsFilter.class)
                .addFilterAfter(new AuthenticationRateLimitFilter(limiter, mapper, metrics), CorsFilter.class)
                .authorizeHttpRequests(config -> config
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui.html", "/swagger-ui/**", "/webjars/swagger-ui/**")
                        .access((authentication, context) -> new AuthorizationDecision(docsEnabled))
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/actuator/health",
                                "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .requestMatchers(org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest.toAnyEndpoint()).denyAll()
                        .requestMatchers("/actuator/**").denyAll()
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(config -> config.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .oauth2ResourceServer(config -> config
                        .authenticationEntryPoint(problems).accessDeniedHandler(problems)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter())));
        return http.build();
    }

    private JwtAuthenticationConverter authenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        var authentication = new JwtAuthenticationConverter();
        authentication.setJwtGrantedAuthoritiesConverter(authorities);
        return authentication;
    }
}
