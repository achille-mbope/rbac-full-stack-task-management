package net.mbope.taskmanager.common.internal.infrastructure;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import java.math.BigDecimal;
import java.util.*;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Supplements discovered HTTP shapes with constraints enforced by application/domain code. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.api-docs.enabled", havingValue = "true")
@SuppressWarnings({"rawtypes", "unchecked"})
class OpenApiConfiguration {
    @Bean
    OpenAPI taskManagerApi() {
        return new OpenAPI().info(new Info().title("Task Manager API").version("0.1.0-draft")
                .description("Bearer-header-only authentication. ADMIN is required for /api/v1/admin/**. "
                        + "Personal task access is assignee-scoped. Unknown request fields are rejected."))
                .servers(List.of(new Server().url("/")))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .security(List.of(new SecurityRequirement().addList("bearerAuth")));
    }

    @Bean
    OpenApiCustomizer apiContractDetails() {
        return api -> {
            var schemas = api.getComponents().getSchemas();
            addProblemSchemas(schemas);
            schemas.forEach(this::describeSchema);
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                boolean publicEndpoint = path.startsWith("/api/v1/auth/");
                boolean admin = path.startsWith("/api/v1/admin/");
                operation.setTags(List.of(publicEndpoint ? "Authentication" : path.contains("tasks")
                        ? (admin ? "Administrative tasks" : "Tasks") : "Accounts"));
                if (publicEndpoint) {
                    operation.setSecurity(List.of());
                    operation.getResponses().addApiResponse("429", problem("Authentication request limit exceeded; retry after the indicated seconds.", "Problem")
                            .addHeaderObject("Retry-After", header(new StringSchema().pattern("^[0-9]+$"), "Seconds until retry")));
                }
                if (admin) operation.addExtension("x-required-roles", List.of("ADMIN"));
                if (admin) operation.setDescription("Requires ADMIN. Authorization precedes validation and resource lookup.");
                var responses = operation.getResponses();
                responses.addApiResponse("400", problem("Invalid JSON, unknown fields, malformed parameters, or constraint violations.", "ValidationProblem"));
                responses.addApiResponse("500", problem("Unexpected server error; no internal details are disclosed.", "Problem"));
                responses.addApiResponse("default", problem("Other application-controlled error. Unsupported methods return 405 with an Allow header.", "Problem"));
                if (!publicEndpoint || path.endsWith("/login")) {
                    var unauthorized = problem("Invalid credentials or missing, invalid, or expired bearer token.", "Problem");
                    if (!publicEndpoint) unauthorized.addHeaderObject("WWW-Authenticate", header(new StringSchema(), "Bearer challenge"));
                    responses.addApiResponse("401", unauthorized);
                }
                if (admin) responses.addApiResponse("403", problem("The token does not contain ADMIN.", "Problem"));
                if (path.contains("{") || (admin && method == PathItem.HttpMethod.POST))
                    responses.addApiResponse("404", problem("Resource not found. Missing and inaccessible tasks have identical responses.", "Problem"));
                if (path.endsWith("/register") || (admin && method == PathItem.HttpMethod.POST))
                    responses.addApiResponse("409", problem(path.endsWith("/register")
                            ? "An account with this normalized email already exists."
                            : "The selected account is disabled.", "Problem"));
                if (operation.getRequestBody() != null)
                    responses.addApiResponse("415", problem("Request content type must be application/json.", "Problem"));
                // MVC return types do not declare a response media type when produces is omitted.
                responses.forEach((code, response) -> {
                    if (code.startsWith("2") && response.getContent() != null) {
                        var content = response.getContent();
                        if (content.containsKey("*/*")) response.setContent(new Content().addMediaType("application/json", content.get("*/*")));
                    }
                });
                if (path.endsWith("/login")) responses.get("200").addHeaderObject("Cache-Control",
                        header(new StringSchema()._const("no-store"), "Do not cache tokens"));
                if (path.contains("tasks") && method == PathItem.HttpMethod.POST)
                    responses.get("201").addHeaderObject("Location", header(new StringSchema().format("uri-reference"), "Created task URL"));
                if (operation.getParameters() != null) operation.getParameters().forEach(parameter -> {
                    var schema = parameter.getSchema();
                    switch (parameter.getName()) {
                        case "page" -> schema.setMinimum(BigDecimal.ZERO);
                        case "size" -> { schema.setMinimum(BigDecimal.ONE); schema.setMaximum(BigDecimal.valueOf(100)); }
                        case "q" -> { schema.setMinLength(1); schema.setMaxLength(200); schema.setPattern("\\S");
                            parameter.setDescription("Trimmed, case-insensitive literal substring of title; blank is invalid."); }
                        default -> { }
                    }
                });
            }));
        };
    }

    private void describeSchema(String name, Schema schema) {
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null || name.equals("Problem") || name.equals("ValidationProblem")) return;
        schema.setAdditionalProperties(false);
        switch (name) {
            case "CreateTaskRequest" -> schema.setRequired(List.of("title"));
            case "AdminCreateTaskRequest" -> schema.setRequired(List.of("title", "assigneeId"));
            case "UpdateTaskRequest" -> {
                schema.setRequired(null);
                schema.setMinProperties(1);
                schema.setDescription("Omitted fields remain unchanged. Explicit null clears description/dueDate only. At least one field is required.");
            }
            default -> schema.setRequired(new ArrayList<>(properties.keySet()));
        }
        properties.forEach((field, value) -> {
            if (name.equals("UpdateTaskRequest")) value.setWriteOnly(null);
            switch (field) {
                case "email" -> { value.setFormat("email"); value.setMaxLength(254);
                    value.setDescription("Trimmed and lowercased before use."); }
                case "password", "currentPassword", "newPassword" -> {
                    value.setFormat("password"); value.setWriteOnly(true);
                    value.setMinLength(name.equals("RegisterRequest") || field.equals("newPassword") ? 15 : 1);
                    value.setMaxLength(72);
                    value.setDescription("Preserved verbatim; at most 72 UTF-8 bytes. New passwords need at least 15 Unicode code points.");
                }
                case "roles" -> { value.setMinItems(1); value.setMaxItems(2); value.setUniqueItems(true);
                    value.setContains(new Schema()._const("USER")); }
                case "title" -> { value.setMinLength(1); value.setMaxLength(200); value.setPattern("\\S"); }
                case "description" -> { nullableString(value); value.setMaxLength(10000); }
                case "dueDate" -> { nullableString(value); value.setFormat("date"); }
                case "page", "totalElements", "totalPages" -> value.setMinimum(BigDecimal.ZERO);
                case "size" -> { value.setMinimum(BigDecimal.ONE); value.setMaximum(BigDecimal.valueOf(100)); }
                case "tokenType" -> value.setConst("Bearer");
                case "expiresIn" -> value.setConst(900);
                default -> { }
            }
            if ((name.equals("CreateTaskRequest") || name.equals("AdminCreateTaskRequest")) && field.equals("status"))
                value.setDefault("TODO");
        });
    }

    private static void nullableString(Schema schema) {
        schema.setType(null);
        schema.setTypes(new LinkedHashSet<>(List.of("string", "null")));
    }

    private static Header header(Schema schema, String description) {
        return new Header().required(true).description(description).schema(schema);
    }

    private static ApiResponse problem(String description, String schema) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/problem+json",
                new MediaType().schema(new Schema().$ref("#/components/schemas/" + schema))));
    }

    private static void addProblemSchemas(Map<String, Schema> schemas) {
        schemas.put("FieldError", new ObjectSchema().additionalProperties(false)
                .addProperty("field", new StringSchema()).addProperty("code", new StringSchema())
                .addProperty("message", new StringSchema()).required(List.of("field", "code", "message")));
        schemas.put("Problem", new ObjectSchema()
                .addProperty("type", new StringSchema().format("uri-reference"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema().minimum(BigDecimal.valueOf(400)).maximum(BigDecimal.valueOf(599)))
                .addProperty("detail", new StringSchema()).addProperty("instance", new StringSchema().format("uri-reference"))
                .addProperty("errors", new ArraySchema().minItems(1).items(new Schema().$ref("#/components/schemas/FieldError")))
                .required(List.of("type", "title", "status", "detail", "instance")));
        schemas.put("ValidationProblem", new ComposedSchema().addAllOfItem(new Schema().$ref("#/components/schemas/Problem"))
                .addAllOfItem(new ObjectSchema().required(List.of("errors"))
                        .addProperty("type", new Schema()._const("urn:task-manager:problem:validation"))
                        .addProperty("status", new Schema()._const(400))));
    }
}
