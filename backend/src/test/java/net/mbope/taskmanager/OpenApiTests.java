package net.mbope.taskmanager;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "h2"})
class OpenApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void generatedDocumentMatchesIndependentContract() throws Exception {
        JsonNode generated = json.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/openapi.json"), generated.toPrettyString());
        JsonNode contract;
        try (var stream = getClass().getResourceAsStream("/contract/openapi.json")) {
            assertThat(stream).isNotNull();
            contract = json.readTree(stream);
        }
        assertThat(generated.path("openapi").asText()).isEqualTo("3.1.0");
        assertThat(normalize(generated.path("components").path("securitySchemes"), generated))
                .isEqualTo(normalize(contract.path("components").path("securitySchemes"), contract));
        var actual = operations(generated);
        var expected = operations(contract);
        assertThat(actual.keySet()).containsExactlyInAnyOrderElementsOf(expected.keySet());
        for (String key : expected.keySet()) assertThat(actual.get(key)).as(key).isEqualTo(expected.get(key));
    }

    @Test
    void uiAndYamlAreAvailableWithoutAuthenticationButApiRemainsProtected() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("swagger-ui-bundle.js")));
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/swagger-initializer.js")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("petstore.swagger.io"))));
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"))
                .andExpect(jsonPath("$.persistAuthorization").value(false));
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("openapi: 3.1.0")));
        mvc.perform(get("/api/v1/tasks")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/tasks")).andExpect(status().isUnauthorized());
    }

    private Map<String, JsonNode> operations(JsonNode document) {
        Map<String, JsonNode> result = new TreeMap<>();
        String base = document.path("servers").get(0).path("url").asText().replaceAll("/$", "");
        document.path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            if (!Set.of("get", "post", "put", "patch", "delete").contains(method.getKey())) return;
            var operation = (ObjectNode) method.getValue().deepCopy();
            if (!operation.has("security")) operation.set("security", document.path("security"));
            ArrayNode parameters = json.createArrayNode();
            path.getValue().path("parameters").forEach(parameters::add);
            operation.path("parameters").forEach(parameters::add);
            var sorted = new ArrayList<JsonNode>();
            parameters.forEach(sorted::add);
            sorted.sort(Comparator.comparing(p -> p.path("in").asText() + p.path("name").asText()));
            operation.set("parameters", json.valueToTree(sorted));
            result.put(method.getKey().toUpperCase(Locale.ROOT) + " " + base + path.getKey(), normalize(operation, document));
        }));
        return result;
    }

    /** Ignore presentation and representation choices, preserving wire-level constraints. */
    private JsonNode normalize(JsonNode value, JsonNode document) {
        if (value.isArray()) {
            var result = json.createArrayNode();
            value.forEach(item -> result.add(normalize(item, document)));
            return result;
        }
        if (!value.isObject()) return value;
        ObjectNode source = (ObjectNode) value.deepCopy();
        if (source.has("$ref")) {
            String ref = source.remove("$ref").asText();
            assertThat(ref).startsWith("#/");
            ObjectNode expanded = (ObjectNode) document.at(ref.substring(1)).deepCopy();
            expanded.setAll(source);
            source = expanded;
        }
        ObjectNode result = json.createObjectNode();
        source.fields().forEachRemaining(field -> {
            String key = field.getKey();
            JsonNode item = field.getValue();
            if (Set.of("description", "summary", "tags", "example", "examples", "externalDocs").contains(key)) return;
            if (key.equals("format") && Set.of("int32", "int64").contains(item.asText())) return;
            if (key.equals("default") && item.isNull()) return; // Omitted nullable fields already default to null.
            if (key.equals("required") && item.isBoolean() && !item.asBoolean()) return;
            if (key.equals("required") && item.isArray() && item.isEmpty()) return;
            if (Set.of("type", "required", "enum").contains(key) && item.isArray()) {
                var sorted = new ArrayList<JsonNode>(); item.forEach(sorted::add);
                sorted.sort(Comparator.comparing(JsonNode::toString));
                result.set(key, json.valueToTree(sorted));
            } else if (Set.of("properties", "headers", "content").contains(key)) {
                var entries = json.createObjectNode();
                item.fields().forEachRemaining(entry -> entries.set(entry.getKey(), normalize(entry.getValue(), document)));
                result.set(key, entries);
            } else result.set(key, normalize(item, document));
        });
        return result;
    }
}
