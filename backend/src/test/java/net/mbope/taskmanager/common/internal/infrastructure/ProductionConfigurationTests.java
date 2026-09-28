package net.mbope.taskmanager.common.internal.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class ProductionConfigurationTests {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ProductionConfiguration.class)
            .withPropertyValues("spring.profiles.active=prod", "DB_URL=jdbc:postgresql://localhost/taskmanager",
                    "DB_USERNAME=application", "DB_PASSWORD=test", "server.ssl.enabled=true");

    @Test
    void rejectsUnsafeSettingsBeforeAnyDatabaseBeanCanBeConstructed() {
        var touched = new java.util.concurrent.atomic.AtomicBoolean();
        context.withPropertyValues("spring.jpa.hibernate.ddl-auto=create-drop")
                .withBean("databaseSentinel", Object.class, () -> { touched.set(true); return new Object(); })
                .run(c -> { assertThat(c).hasFailed(); assertThat(touched).isFalse(); });
    }

    @Test
    void productionLoadsSafeProfileAndPostgresGroup() {
        context.run(c -> {
            assertThat(c).hasNotFailed();
            assertThat(c.getEnvironment().getActiveProfiles()).contains("prod", "postgres");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.profiles.active=prod,h2", "spring.datasource.url=jdbc:h2:mem:bad",
            "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=update", "spring.flyway.enabled=false",
            "app.api-docs.enabled=true", "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true",
            "spring.h2.console.enabled=true", "spring.jpa.show-sql=true", "app.security.require-https=false",
            "app.rate-limit.enabled=false", "management.endpoints.web.exposure.include=*",
            "management.endpoint.health.show-details=always", "management.endpoint.health.show-components=always",
            "management.endpoints.jmx.exposure.exclude=", "server.error.include-message=always",
            "server.error.include-stacktrace=on_param", "server.error.include-binding-errors=always",
            "server.forward-headers-strategy=framework", "server.ssl.enabled=false",
            "app.cors.allowed-origins=http://example.com", "app.cors.allowed-origins=https://example.com/path"})
    void unsafeOverridesFailStartup(String override) {
        context.withPropertyValues(override).run(c -> assertThat(c).hasFailed());
    }

    @Test
    void proxyModeRequiresAnExplicitTrustBoundary() {
        context.withPropertyValues("server.ssl.enabled=false", "server.forward-headers-strategy=native")
                .run(c -> assertThat(c).hasFailed());
        context.withPropertyValues("server.ssl.enabled=false", "server.forward-headers-strategy=native",
                        "server.tomcat.remoteip.internal-proxies=.*").run(c -> assertThat(c).hasFailed());
        context.withPropertyValues("server.ssl.enabled=false", "server.forward-headers-strategy=native",
                        "server.tomcat.remoteip.internal-proxies=127[.]0[.]0[.]1")
                .run(c -> assertThat(c).hasNotFailed());
    }
}
