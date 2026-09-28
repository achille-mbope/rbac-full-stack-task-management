package net.mbope.taskmanager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.test.context.ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class TaskManagerApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired Environment environment;

    @Test
    void contextLoads() {
    }

    @Test
    void documentationIsDisabledByDefaultOutsideLocalDevelopment() throws Exception {
        assertThat(environment.getProperty("app.api-docs.enabled", Boolean.class, false)).isFalse();
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui/index.html").with(jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isForbidden());
    }
}
