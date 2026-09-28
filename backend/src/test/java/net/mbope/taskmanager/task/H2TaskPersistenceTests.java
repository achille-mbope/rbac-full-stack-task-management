package net.mbope.taskmanager.task;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({"test", "h2"})
class H2TaskPersistenceTests extends TaskPersistenceContract {
}
