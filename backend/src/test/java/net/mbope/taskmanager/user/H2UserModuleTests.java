package net.mbope.taskmanager.user;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"test", "h2"})
class H2UserModuleTests extends UserModuleContract {

    @Test
    void disposableDevelopmentProfileSupportsRegistrationAndTheSameDuplicateError() {
        String password = "a long development password";
        Account account = registration.register("dev@example.com", password);
        assertThat(credentials.verify("dev@example.com", password).orElseThrow().id()).isEqualTo(account.id());
        assertThatThrownBy(() -> registration.register(" DEV@example.com ", password))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }
}
