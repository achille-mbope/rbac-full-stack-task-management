package net.mbope.taskmanager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"server.forward-headers-strategy=native",
        "server.tomcat.remoteip.internal-proxies=192[.]0[.]2[.]10"})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "prod"})
class ProductionHttpTests {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("DB_URL", postgres::getJdbcUrl);
        properties.add("DB_USERNAME", postgres::getUsername);
        properties.add("DB_PASSWORD", postgres::getPassword);
    }
    @Autowired MockMvc mvc;
    @org.springframework.boot.test.web.server.LocalServerPort int port;

    @Test
    void untrustedNetworkPeerCannotSpoofHttps() throws Exception {
        try (var client = java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NEVER).build()) {
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + "/actuator/health"))
                    .header("X-Forwarded-Proto", "https").header("X-Forwarded-For", "192.0.2.50").GET().build();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
            org.assertj.core.api.Assertions.assertThat(response.statusCode()).isEqualTo(403);
            org.assertj.core.api.Assertions.assertThat(response.headers().firstValue("location")).isEmpty();
        }
    }

    @Test
    void productionStartsWithMigrationsAndOffersOnlyMinimalSecureHealth() throws Exception {
        mvc.perform(get("/actuator/health/readiness").secure(true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP")).andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/api/v1/tasks").secure(true)).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui/index.html").secure(true).with(jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void insecureRequestsAreRejectedEvenWhenCallerSuppliesForwardingHeadersToMvc() throws Exception {
        mvc.perform(get("/api/v1/tasks").header("X-Forwarded-Proto", "https"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.type").value("urn:task-manager:problem:https-required"));
    }
}
