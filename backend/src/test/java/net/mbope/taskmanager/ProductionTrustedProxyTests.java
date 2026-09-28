package net.mbope.taskmanager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "server.forward-headers-strategy=native",
        "server.tomcat.remoteip.internal-proxies=127[.]0[.]0[.]1",
        "app.rate-limit.login-per-client=1"})
@ActiveProfiles({"test", "prod"})
class ProductionTrustedProxyTests {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("DB_URL", postgres::getJdbcUrl);
        properties.add("DB_USERNAME", postgres::getUsername);
        properties.add("DB_PASSWORD", postgres::getPassword);
    }

    @LocalServerPort int port;

    @Test
    void trustedProxyAllowsSecureHealthButStillRequiresApiAuthentication() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var health = client.send(request("/actuator/health/readiness", "192.0.2.21").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.body()).isEqualTo("{\"status\":\"UP\"}");
            assertThat(health.headers().firstValue("X-Request-ID")).isPresent();
            var api = client.send(request("/api/v1/tasks", "192.0.2.21").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(api.statusCode()).isEqualTo(401);
            var insecure = client.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/actuator/health"))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(insecure.statusCode()).isEqualTo(403);
        }
    }

    @Test
    void nativeProxyResolutionKeepsClientQuotasSeparate() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var first = malformedLogin(client, "192.0.2.31");
            assertThat(first.statusCode()).isEqualTo(400);
            var limited = malformedLogin(client, "192.0.2.31");
            assertThat(limited.statusCode()).isEqualTo(429);
            assertThat(limited.headers().firstValue("Retry-After")).isPresent();
            assertThat(malformedLogin(client, "192.0.2.32").statusCode()).isEqualTo(400);
        }
    }

    private HttpResponse<String> malformedLogin(HttpClient client, String address) throws Exception {
        return client.send(request("/api/v1/auth/login", address).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{")).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path, String address) {
        return HttpRequest.newBuilder(URI.create(baseUrl() + path)).timeout(Duration.ofSeconds(10))
                .header("X-Forwarded-Proto", "https").header("X-Forwarded-For", address);
    }

    private String baseUrl() { return "http://127.0.0.1:" + port; }
}
