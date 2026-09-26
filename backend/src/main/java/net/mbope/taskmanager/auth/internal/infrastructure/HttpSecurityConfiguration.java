package net.mbope.taskmanager.auth.internal.infrastructure;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class HttpSecurityConfiguration {
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, SecurityProblemHandlers problems, CorsPolicy cors) throws Exception {
        http.csrf(config -> config.disable())
                .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(config -> config.disable())
                .formLogin(config -> config.disable())
                .httpBasic(config -> config.disable())
                .logout(config -> config.disable())
                .addFilterBefore(cors.filter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(config -> config
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
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
