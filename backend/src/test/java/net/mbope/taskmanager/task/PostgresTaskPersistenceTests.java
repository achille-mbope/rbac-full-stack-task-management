package net.mbope.taskmanager.task;

import net.mbope.taskmanager.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.*;

@Import(TestcontainersConfiguration.class)
class PostgresTaskPersistenceTests extends TaskPersistenceContract {
    @Test
    void migrationsCreateConstraintsAndAuditHasNoTaskForeignKey() {
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '2' and success", Long.class)).isEqualTo(1);
        var task = personal.create(new TaskDraft("Task"));
        assertThatThrownBy(() -> jdbc.update("update tasks set status = 'INVALID' where id = ?", task.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update tasks set title = ? where id = ?", "x".repeat(201), task.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update tasks set assignee_id = ? where id = ?",
                java.util.UUID.randomUUID(), task.id())).isInstanceOf(DataIntegrityViolationException.class);
    }
}
