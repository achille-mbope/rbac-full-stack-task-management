package net.mbope.taskmanager.common.internal.infrastructure;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** Refuse to boot a production process with development or disclosure-prone settings. */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
class ProductionConfiguration {
    private final Environment environment;

    ProductionConfiguration(Environment environment) { this.environment = environment; }

    @Bean
    static BeanFactoryPostProcessor productionSafetyChecks(Environment environment) {
        // Check before datasource, Flyway and Hibernate beans can touch the database.
        return beanFactory -> new ProductionConfiguration(environment).verify();
    }

    private void verify() {
        require(!Arrays.asList(environment.getActiveProfiles()).contains("h2"), "h2 profile is forbidden");
        require(value("spring.datasource.url").startsWith("jdbc:postgresql:"), "PostgreSQL datasource is required");
        require(!value("spring.datasource.username").isBlank() && !value("spring.datasource.password").isBlank(), "database credentials are required");
        require(value("spring.jpa.hibernate.ddl-auto").equals("validate"), "Hibernate must use validate");
        require(flag("spring.flyway.enabled"), "Flyway must be enabled");
        for (String key : Set.of("app.api-docs.enabled", "springdoc.api-docs.enabled", "springdoc.swagger-ui.enabled",
                "spring.h2.console.enabled", "spring.jpa.show-sql")) require(!flag(key), key + " must be disabled");
        require(flag("app.security.require-https"), "HTTPS enforcement is required");
        require(flag("app.rate-limit.enabled"), "authentication rate limiting is required");
        require(value("management.endpoints.web.exposure.include").equals("health"), "only health may be exposed over HTTP");
        require(value("management.endpoint.health.show-details").equals("never"), "health details must be hidden");
        require(value("management.endpoint.health.show-components").equals("never"), "health components must be hidden");
        require(value("management.endpoints.jmx.exposure.exclude").equals("*"), "management JMX exposure must be disabled");
        for (String key : Set.of("server.error.include-message", "server.error.include-stacktrace", "server.error.include-binding-errors"))
            require(value(key).equals("never"), key + " must be never");
        String forwarding = value("server.forward-headers-strategy");
        require(Set.of("none", "native").contains(forwarding), "forwarding must be none or native");
        if (forwarding.equals("native")) {
            String proxies = value("server.tomcat.remoteip.internal-proxies");
            require(!proxies.isBlank() && !Set.of(".*", ".+").contains(proxies), "explicit trusted proxy IP pattern is required");
            Pattern.compile(proxies);
        } else require(flag("server.ssl.enabled"), "direct TLS or explicitly trusted native proxy processing is required");
        for (String origin : value("app.cors.allowed-origins").split(",")) {
            if (origin.isBlank()) continue;
            URI uri = URI.create(origin.strip());
            require("https".equals(uri.getScheme()) && uri.getHost() != null && !origin.contains("*")
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && (uri.getPath() == null || uri.getPath().isEmpty()), "CORS must contain explicit HTTPS origins");
        }
    }

    private String value(String key) { return environment.getProperty(key, "").strip(); }
    private boolean flag(String key) { return environment.getProperty(key, Boolean.class, false); }
    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException("Unsafe production configuration: " + message + ".");
    }
}
