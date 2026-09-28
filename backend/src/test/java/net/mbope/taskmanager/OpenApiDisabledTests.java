package net.mbope.taskmanager;

import org.springdoc.webmvc.ui.SwaggerWelcomeWebMvc;

import org.springdoc.webmvc.api.OpenApiWebMvcResource;

import org.springframework.context.ApplicationContext;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.api-docs.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class OpenApiDisabledTests {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void documentationResourcesAreNotRegistered() {
        org.assertj.core.api.Assertions.assertThat(context.getBeansOfType(OpenApiWebMvcResource.class)).isEmpty();
        org.assertj.core.api.Assertions.assertThat(context.getBeansOfType(SwaggerWelcomeWebMvc.class)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/swagger-config",
            "/swagger-ui.html", "/swagger-ui/index.html", "/swagger-ui/swagger-ui-bundle.js", "/webjars/swagger-ui/index.html"})
    void documentationIsDeniedEvenToAdministrators(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(jwt().authorities(() -> "ROLE_USER"))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(jwt().authorities(() -> "ROLE_ADMIN"))).andExpect(status().isForbidden());
    }
}
