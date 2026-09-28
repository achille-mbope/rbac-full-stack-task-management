package net.mbope.taskmanager.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "h2"})
class TaskHttpTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID administrator = UUID.randomUUID();
    private String aliceToken;
    private String bobToken;
    private String adminToken;

    @BeforeEach
    void prepare() {
        clean();
        for (UUID id : List.of(alice, bob, administrator)) {
            jdbc.update("insert into user_accounts (id,email,password_hash,is_admin,enabled,created_at,updated_at) values (?,?,?,?,true,?,?)",
                    id, id + "@example.com", "x".repeat(60), id.equals(administrator),
                    Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        }
        aliceToken = token(alice, false);
        bobToken = token(bob, false);
        adminToken = token(administrator, true);
    }

    @AfterEach
    void clean() {
        jdbc.update("delete from task_audit");
        jdbc.update("delete from tasks");
        jdbc.update("delete from user_accounts");
    }

    @Test
    void personalCrudReturnsContractFieldsAndFollowableLocation() throws Exception {
        var response = mvc.perform(post("/api/v1/tasks").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  Plan work  \"}"))
                .andExpect(status().isCreated()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse();
        JsonNode task = json.readTree(response.getContentAsString());
        assertThat(task.size()).isEqualTo(9);
        assertThat(task.path("title").asText()).isEqualTo("  Plan work  ");
        assertThat(task.path("assigneeId").asText()).isEqualTo(alice.toString());
        assertThat(task.path("createdById").asText()).isEqualTo(alice.toString());
        assertThat(task.path("status").asText()).isEqualTo("TODO");
        assertThat(task.path("description").isNull()).isTrue();
        assertThat(task.path("dueDate").isNull()).isTrue();
        assertThat(task.path("createdAt")).isEqualTo(task.path("updatedAt"));
        Instant.parse(task.path("createdAt").asText());
        String location = response.getHeader("Location");
        assertThat(location).isEqualTo("/api/v1/tasks/" + task.path("id").asText());
        mvc.perform(get(location).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk()).andExpect(content().json(task.toString()));
        mvc.perform(patch(location).header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Details\",\"dueDate\":\"2000-01-01\",\"status\":\"DONE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("  Plan work  "))
                .andExpect(jsonPath("$.dueDate").value("2000-01-01"))
                .andExpect(jsonPath("$.createdAt").value(task.path("createdAt").asText()));
        mvc.perform(patch(location).header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":null,\"dueDate\":null,\"status\":\"TODO\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.dueDate").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(delete(location).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get(location).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.type").value("urn:task-manager:problem:not-found"));
        assertThat(count("task_audit")).isZero();
    }

    @Test
    void inaccessibleAndMissingPersonalTasksHaveIdenticalProblems() throws Exception {
        JsonNode task = create(aliceToken, "{\"title\":\"Private assignment\"}");
        String route = "/api/v1/tasks/" + task.path("id").asText();
        String hidden = mvc.perform(get(route).header("Authorization", bearer(bobToken)))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = mvc.perform(get("/api/v1/tasks/" + UUID.randomUUID()).header("Authorization", bearer(bobToken)))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(hidden).path("detail")).isEqualTo(json.readTree(missing).path("detail"));
        for (String token : List.of(bobToken, adminToken)) {
            mvc.perform(patch(route).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Spoofed\"}"))
                    .andExpect(status().isNotFound());
            mvc.perform(delete(route).header("Authorization", bearer(token))).andExpect(status().isNotFound());
        }
        assertThat(count("tasks")).isEqualTo(1);
    }

    @Test
    void adminAssignmentUsesAdminLocationAndPersonalAccessRemainsAssigneeScoped() throws Exception {
        String body = json.writeValueAsString(Map.of("title", "Assigned", "assigneeId", bob));
        var response = mvc.perform(post("/api/v1/admin/tasks").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.assigneeId").value(bob.toString()))
                .andExpect(jsonPath("$.createdById").value(administrator.toString())).andReturn().getResponse();
        JsonNode task = json.readTree(response.getContentAsString());
        String id = task.path("id").asText();
        assertThat(response.getHeader("Location")).isEqualTo("/api/v1/admin/tasks/" + id);
        mvc.perform(get(response.getHeader("Location")).header("Authorization", bearer(adminToken))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/tasks/" + id).header("Authorization", bearer(adminToken))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tasks/" + id).header("Authorization", bearer(bobToken))).andExpect(status().isOk());
        var self = create(adminToken, "{\"title\":\"Admin self\"}");
        assertThat(self.path("assigneeId").asText()).isEqualTo(administrator.toString());
        assertThat(self.path("createdById").asText()).isEqualTo(administrator.toString());
        mvc.perform(post("/api/v1/admin/tasks").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "Admin recipient", "assigneeId", administrator))))
                .andExpect(status().isCreated());
    }

    @Test
    void adminMutationsOnUserCreatedTasksProduceAuditRecordsAndSurviveDeletion() throws Exception {
        var task = create(aliceToken, "{\"title\":\"Original\"}");
        String route = "/api/v1/admin/tasks/" + task.path("id").asText();
        jdbc.update("update user_accounts set enabled = false where id = ?", alice);
        mvc.perform(patch(route).header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Edited\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Edited"));
        mvc.perform(delete(route).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(count("tasks")).isZero();
        assertThat(count("task_audit")).isEqualTo(2);
        assertThat(jdbc.queryForList("select actor_id from task_audit", UUID.class))
                .containsOnly(administrator);
    }

    @Test
    void adminChecksPrecedeMalformedInputAndLookup() throws Exception {
        mvc.perform(post("/api/v1/admin/tasks").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(patch("/api/v1/admin/tasks/not-a-uuid").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/tasks").param("page", "bad").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/tasks/" + UUID.randomUUID()).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingAndDisabledRecipientsAndMissingAdminTasksUseProblemDetails() throws Exception {
        for (boolean disabled : List.of(false, true)) {
            UUID recipient = disabled ? bob : UUID.randomUUID();
            if (disabled) {
                jdbc.update("update user_accounts set enabled = false where id = ?", bob);
            }
            mvc.perform(post("/api/v1/admin/tasks").header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("title", "Task", "assigneeId", recipient))))
                    .andExpect(status().is(disabled ? 409 : 404))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.type").value("urn:task-manager:problem:" + (disabled ? "assignee-disabled" : "not-found")));
        }
        mvc.perform(get("/api/v1/admin/tasks/" + UUID.randomUUID()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
        assertThat(count("tasks")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]", "{", "{\"title\":null}", "{\"title\":\" \"}",
            "{\"title\":5}", "{\"title\":\"Task\",\"status\":null}", "{\"title\":\"Task\",\"status\":0}",
            "{\"title\":\"Task\",\"status\":\"UNKNOWN\"}", "{\"title\":\"Task\",\"description\":true}",
            "{\"title\":\"Task\",\"assigneeId\":null}", "{\"title\":\"Task\",\"createdById\":null}",
            "{\"title\":\"Task\",\"ownerId\":null}", "{\"title\":\"Task\",\"id\":null}",
            "{\"title\":\"Task\",\"createdAt\":null}", "{\"title\":\"Task\",\"dueDate\":\"2026-02-30\"}",
            "{\"title\":\"Task\",\"dueDate\":[2026,1,1]}", "{\"title\":\"Task\",\"dueDate\":5}",
            "{\"title\":\"Task\",\"dueDate\":\"2026-01-01T00:00:00Z\"}", "{\"title\":\"Task\"} {}"})
    void invalidCreationReturnsSafeValidationProblem(String body) throws Exception {
        mvc.perform(post("/api/v1/tasks").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/v1/tasks")).andExpect(jsonPath("$.errors[0].field").exists());
        assertThat(count("tasks")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]", "{\"title\":null}", "{\"status\":null}", "{\"status\":1}",
            "{\"dueDate\":\"2026-02-30\"}", "{\"dueDate\":[2026,1,1]}", "{\"description\":5}",
            "{\"assigneeId\":null}", "{\"createdById\":null}", "{\"updatedAt\":null}", "{\"id\":null}"})
    void invalidPatchesLeaveTaskUnchanged(String body) throws Exception {
        var task = create(aliceToken, "{\"title\":\"Original\"}");
        String route = "/api/v1/tasks/" + task.path("id").asText();
        mvc.perform(patch(route).header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").exists());
        mvc.perform(get(route).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk()).andExpect(content().json(task.toString()));
    }

    @Test
    void unicodeLimitsAndEveryStatusTransitionWorkThroughHttp() throws Exception {
        String emoji = new String(Character.toChars(0x1F680));
        var task = create(aliceToken, json.writeValueAsString(Map.of("title", emoji.repeat(200), "description", emoji.repeat(10000))));
        String route = "/api/v1/tasks/" + task.path("id").asText();
        for (TaskStatus from : TaskStatus.values()) {
            mvc.perform(patch(route).header("Authorization", bearer(aliceToken)).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("status", from))))
                    .andExpect(status().isOk());
            for (TaskStatus to : TaskStatus.values()) {
                mvc.perform(patch(route).header("Authorization", bearer(aliceToken)).contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("status", to))))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(to.name()));
            }
        }
        for (var invalid : List.of(Map.of("title", emoji.repeat(201)), Map.of("description", emoji.repeat(10001)))) {
            mvc.perform(patch(route).header("Authorization", bearer(aliceToken)).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void filteredListsRespectScopeCountsDefaultsAndAdminAssigneeFilter() throws Exception {
        create(aliceToken, "{\"title\":\"100%_Alpha\"}");
        create(aliceToken, "{\"title\":\"100XXAlpha\"}");
        create(aliceToken, "{\"title\":\"100%_Alpha done\",\"status\":\"DONE\"}");
        create(bobToken, "{\"title\":\"100%_ALPHA\"}");
        mvc.perform(get("/api/v1/tasks").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/v1/tasks").param("q", " %_alpha ").param("status", "TODO").param("size", "1")
                        .header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1)).andExpect(jsonPath("$.items[0].assigneeId").value(alice.toString()));
        mvc.perform(get("/api/v1/tasks").param("assigneeId", bob.toString()).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/v1/admin/tasks").param("assigneeId", bob.toString()).param("q", "%_alpha")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/admin/tasks").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/v1/tasks").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0)).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/tasks").param("page", "100").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void invalidQueriesIdsAndAdminRequestsReturn400() throws Exception {
        for (var query : List.of(Map.of("page", "-1"), Map.of("page", "bad"), Map.of("size", "101"),
                Map.of("size", "0"), Map.of("status", "UNKNOWN"), Map.of("q", " "), Map.of("q", "x".repeat(201)))) {
            var request = get("/api/v1/tasks").header("Authorization", bearer(aliceToken));
            query.forEach(request::param);
            mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").exists());
        }
        mvc.perform(get("/api/v1/tasks/bad").header("Authorization", bearer(aliceToken))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/tasks").param("assigneeId", "bad").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
        for (String body : List.of("{\"title\":\"Task\"}", "{\"title\":\"Task\",\"assigneeId\":\"bad\"}",
                "{\"title\":\"Task\",\"assigneeId\":null}", "{\"title\":\"Task\",\"createdById\":null}")) {
            mvc.perform(post("/api/v1/admin/tasks").header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void protectedRoutesMediaAndUnsupportedMethodsFollowContract() throws Exception {
        for (String route : List.of("/api/v1/tasks", "/api/v1/admin/tasks")) {
            mvc.perform(get(route)).andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"));
            mvc.perform(get(route).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/tasks").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.TEXT_PLAIN).content("Task")).andExpect(status().isUnsupportedMediaType());
        mvc.perform(put("/api/v1/tasks/" + UUID.randomUUID()).header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().exists("Allow"));
    }

    private JsonNode create(String token, String body) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/tasks").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String token(UUID id, boolean admin) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().subject(id.toString()).issuer("task-manager")
                .audience(List.of("task-manager-api")).issuedAt(now).expiresAt(now.plusSeconds(900))
                .claim("roles", admin ? List.of("USER", "ADMIN") : List.of("USER")).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
}
