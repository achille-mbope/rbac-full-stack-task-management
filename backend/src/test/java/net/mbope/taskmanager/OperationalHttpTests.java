package net.mbope.taskmanager;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.rate-limit.enabled=true", "app.rate-limit.login-per-client=1",
        "app.rate-limit.register-per-client=1"})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "h2"})
class OperationalHttpTests {
    @Autowired MockMvc mvc;
    @Autowired io.micrometer.core.instrument.MeterRegistry metrics;

    @Test
    void rateLimitPrecedesParsingAndCannotBeBypassedWithForwardedHeaders() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(r -> { r.setRemoteAddr("192.0.2.1"); return r; })
                .contentType("application/json").content("{")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/login").with(r -> { r.setRemoteAddr("192.0.2.1"); return r; })
                        .header("X-Forwarded-For", "192.0.2.99").header("Forwarded", "for=192.0.2.99")
                        .contentType("application/json").content("{"))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:task-manager:problem:rate-limited"));
        assertThat(metrics.counter("auth.rate.limit.rejections", "operation", "login").count()).isGreaterThanOrEqualTo(1);
        mvc.perform(post("/api/v1/auth/login").with(r -> { r.setRemoteAddr("192.0.2.2"); return r; })
                .contentType("application/json").content("{")).andExpect(status().isBadRequest());
    }

    @Test
    void registrationHasItsOwnQuotaAndPreflightIsNotCharged() throws Exception {
        mvc.perform(options("/api/v1/auth/register").header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/register").header("Origin", "http://localhost:4200")
                        .contentType("application/json").content("{"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Expose-Headers", org.hamcrest.Matchers.containsString("Retry-After")));
        mvc.perform(get("/api/v1/tasks")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsMinimalAndOtherManagementEndpointsAreDeniedEvenToAdmin() throws Exception {
        for (String path : new String[]{"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist()).andExpect(jsonPath("$.details").doesNotExist());
        }
        for (String path : new String[]{"/actuator", "/actuator/env", "/actuator/metrics", "/actuator/health/db"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(jwt().authorities(() -> "ROLE_ADMIN"))).andExpect(status().isForbidden());
        }
    }

    @Test
    void requestIdsAreGeneratedRatherThanEchoedAndMdcIsCleared() throws Exception {
        var response = mvc.perform(get("/api/v1/tasks").header("X-Request-ID", "untrusted-secret"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse();
        assertThat(UUID.fromString(response.getHeader("X-Request-ID"))).isNotNull();
        assertThat(org.slf4j.MDC.get("requestId")).isNull();
    }
}
