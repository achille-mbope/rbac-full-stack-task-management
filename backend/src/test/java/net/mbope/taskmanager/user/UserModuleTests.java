package net.mbope.taskmanager.user;

import net.mbope.taskmanager.TestcontainersConfiguration;
import org.springframework.context.annotation.Import;

@org.springframework.test.context.ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class UserModuleTests extends UserModuleContract {
}
