package net.mbope.taskmanager.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "h2"})
class UserHttpTests {
    private static final String PASSWORD = "a long original password";
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JwtEncoder encoder;

    @BeforeEach
    void resetAccounts() {
        jdbc.update("delete from user_accounts");
    }

    @Test
    void registrationAndLoginReturnSafeResponsesAndDuplicateProblem() throws Exception {
        var account = register(" Alice@Example.com ");
        assertThat(account.path("roles").toString()).isEqualTo("[\"USER\"]");
        assertThat(account.path("email").asText()).isEqualTo("alice@example.com");
        assertThat(account.has("passwordHash")).isFalse();
        assertThat(account.has("accessToken")).isFalse();
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "ALICE@example.com", "password", PASSWORD))))
                .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:task-manager:problem:email-conflict"))
                .andExpect(jsonPath("$.status").value(409)).andExpect(jsonPath("$.instance").value("/api/v1/auth/register"));
        login("alice@example.com", PASSWORD);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"email\":\"a@example.com\",\"password\":\"a long original password\",\"roles\":[\"ADMIN\"]}",
            "{\"email\":\"a@example.com\",\"password\":\"short\"}",
            "{\"email\":5,\"password\":\"a long original password\"}",
            "[]", "null", "{", "{}",
            "{\"email\":\"a@example.com\",\"password\":\"a long original password\"} {}"
    })
    void rejectsInvalidRegistrationWithoutPersisting(String request) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").exists());
        assertThat(jdbc.queryForObject("select count(*) from user_accounts", Long.class)).isZero();
    }

    @Test
    void rejectsUtf8PasswordsOverBcryptLimitWithoutEchoingThem() throws Exception {
        var password = "é".repeat(37);
        var response = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "a@example.com", "password", password))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("password"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(password);
    }

    @Test
    void unknownDisabledAndWrongPasswordLoginsHaveIdenticalErrors() throws Exception {
        var account = register("a@example.com");
        String wrong = failedLogin("a@example.com", "wrong");
        assertThat(failedLogin("unknown@example.com", PASSWORD)).isEqualTo(wrong);
        jdbc.update("update user_accounts set enabled = false where id = ?", UUID.fromString(account.path("id").asText()));
        assertThat(failedLogin("a@example.com", PASSWORD)).isEqualTo(wrong);
    }

    @Test
    void passwordChangeUsesSubjectAndKeepsExistingTokenValid() throws Exception {
        register("a@example.com");
        String token = login("a@example.com", PASSWORD);
        mvc.perform(put("/api/v1/users/me/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", PASSWORD, "newPassword", "a different long password"))))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        failedLogin("a@example.com", PASSWORD);
        login("a@example.com", "a different long password");
        mvc.perform(put("/api/v1/users/me/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", "wrong", "newPassword", "another long password"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].code").value("incorrect_password"));
    }

    @Test
    void nonAdminIsRejectedBeforeMalformedBodyOrTargetLookup() throws Exception {
        register("a@example.com");
        String token = login("a@example.com", PASSWORD);
        mvc.perform(put("/api/v1/admin/users/not-a-uuid/roles").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListManageAndDisableSelfWithStaleAuthorityUntilExpiry() throws Exception {
        var account = register("admin@example.com");
        String id = account.path("id").asText();
        // Exercise the documented operator bootstrap before first administrator login.
        jdbc.update("update user_accounts set is_admin = true where id = ?", UUID.fromString(id));
        String token = login("admin@example.com", PASSWORD);
        mvc.perform(get("/api/v1/admin/users").param("size", "1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id)).andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
        mvc.perform(put("/api/v1/admin/users/" + id + "/roles").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"USER\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("USER"));
        mvc.perform(put("/api/v1/admin/users/" + id + "/status").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        failedLogin("admin@example.com", PASSWORD);
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void adminValidationAndMissingTargetsUseProblems() throws Exception {
        String token = adminToken();
        String id = UUID.randomUUID().toString();
        mvc.perform(put("/api/v1/admin/users/" + id + "/roles").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"USER\"]}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.type").value("urn:task-manager:problem:not-found"));
        for (String roles : List.of("[]", "[\"ADMIN\"]", "[\"USER\",\"USER\"]", "[0]", "[null]", "[\"OTHER\"]")) {
            mvc.perform(put("/api/v1/admin/users/" + id + "/roles").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":" + roles + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").exists());
        }
        for (String request : List.of("{}", "{\"enabled\":null}", "{\"enabled\":\"false\"}", "{\"enabled\":0}")) {
            mvc.perform(put("/api/v1/admin/users/" + id + "/status").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").exists());
        }
        mvc.perform(get("/api/v1/admin/users").param("size", "101").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/users").param("page", "no").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("page"));
    }

    @Test
    void protectedRoutesRejectMissingInvalidAndNonHeaderTokens() throws Exception {
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer")).andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        String token = adminToken();
        mvc.perform(get("/api/v1/admin/users").param("access_token", token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/users").cookie(new Cookie("access_token", token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void securityFilterRejectsExpiredTokensAndWrongSignatures() throws Exception {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString()).issuer("task-manager").audience(List.of("task-manager-api"))
                .issuedAt(now.minusSeconds(901)).expiresAt(now.minusSeconds(1))
                .claim("roles", List.of("USER", "ADMIN")).build();
        String expired = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(
                        MacAlgorithm.HS256).build(), claims)).getTokenValue();
        String valid = adminToken();
        String[] parts = valid.split("\\.");
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        signature[0] ^= 1;
        String tampered = parts[0] + "." + parts[1] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        for (String rejected : List.of(expired, tampered)) {
            mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + rejected))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401))
                    .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""));
        }
    }

    @Test
    void unknownRoutesMethodAndMediaErrorsFollowContract() throws Exception {
        mvc.perform(get("/api/v1/unknown")).andExpect(status().isUnauthorized());
        String token = adminToken();
        mvc.perform(get("/api/v1/unknown").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(get("/api/v1/auth/register")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow")).andExpect(jsonPath("$.status").value(405));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.TEXT_PLAIN).content("body"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void corsAllowsOnlyConfiguredDevelopmentOrigin() throws Exception {
        mvc.perform(options("/api/v1/admin/users").header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        mvc.perform(options("/api/v1/admin/users").header("Origin", "https://other.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.instance").value("/api/v1/admin/users"));
    }

    private JsonNode register(String email) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isCreated()).andReturn().getResponse();
        return json.readTree(response.getContentAsString());
    }

    private String login(String email, String password) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse();
        assertThat(response.getCookie("JSESSIONID")).isNull();
        return json.readTree(response.getContentAsString()).path("accessToken").asText();
    }

    private String failedLogin(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "password", password))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Invalid email or password."))
                .andReturn().getResponse().getContentAsString();
    }

    private String adminToken() throws Exception {
        var account = register("admin@example.com");
        jdbc.update("update user_accounts set is_admin = true where id = ?", UUID.fromString(account.path("id").asText()));
        return login("admin@example.com", PASSWORD);
    }

    private String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }
}
