package net.mbope.taskmanager.task.internal.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import net.mbope.taskmanager.task.TaskStatus;
import net.mbope.taskmanager.task.TaskValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class TaskTests {
    private static final UUID ASSIGNEE = UUID.randomUUID();
    private static final UUID CREATOR = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00.123456789Z");

    @Test
    void creationDefaultsAndAttributionFollowContract() {
        var task = Task.create(ASSIGNEE, CREATOR, "  Plan work  ", NOW);
        assertThat(task.id()).isNull();
        assertThat(task.assigneeId()).isEqualTo(ASSIGNEE);
        assertThat(task.createdById()).isEqualTo(CREATOR);
        assertThat(task.title()).isEqualTo("  Plan work  ");
        assertThat(task.description()).isNull();
        assertThat(task.dueDate()).isNull();
        assertThat(task.status()).isEqualTo(TaskStatus.TODO);
        assertThat(task.createdAt()).isEqualTo(Instant.parse("2026-09-27T10:00:00.123456Z"));
        assertThat(task.updatedAt()).isEqualTo(task.createdAt());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void rejectsMissingOrBlankTitles(String title) {
        assertThatThrownBy(() -> Task.create(ASSIGNEE, CREATOR, title, NOW))
                .isInstanceOfSatisfying(TaskValidationException.class, error -> {
                    assertThat(error.field()).isEqualTo("title");
                    assertThat(error.code()).isEqualTo("required");
                });
    }

    @Test
    void limitsCountUnicodeCodePointsAndPreserveText() {
        String character = new String(Character.toChars(0x1F680));
        String title = character.repeat(200);
        String description = character.repeat(10_000);
        var task = Task.create(ASSIGNEE, CREATOR, title, description, TaskStatus.DONE, null, NOW);
        assertThat(task.title()).isEqualTo(title);
        assertThat(task.description()).isEqualTo(description);
        assertThatThrownBy(() -> Task.create(ASSIGNEE, CREATOR, title + character, NOW))
                .isInstanceOfSatisfying(TaskValidationException.class, error -> {
                    assertThat(error.field()).isEqualTo("title");
                    assertThat(error.code()).isEqualTo("too_long");
                });
        assertThatThrownBy(() -> task.update(title, description + character, TaskStatus.TODO, null, NOW))
                .isInstanceOfSatisfying(TaskValidationException.class, error -> {
                    assertThat(error.field()).isEqualTo("description");
                    assertThat(error.code()).isEqualTo("too_long");
                });
        assertThat(task.description()).isEqualTo(description);
        assertThat(task.status()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void everyStatusTransitionIncludingReopeningAndSameStatusIsAllowed() {
        for (var from : TaskStatus.values()) {
            for (var to : TaskStatus.values()) {
                var task = Task.create(ASSIGNEE, CREATOR, "Task", null, from, null, NOW);
                task.update(task.title(), task.description(), to, task.dueDate(), NOW.plusSeconds(1));
                assertThat(task.status()).isEqualTo(to);
                assertThat(task.updatedAt()).isEqualTo(task.createdAt().plusSeconds(1));
            }
        }
    }

    @Test
    void updatesPreserveIdentityAndAttributionAndCanClearOptionalFields() {
        UUID id = UUID.randomUUID();
        LocalDate past = LocalDate.of(2000, 1, 1);
        var task = Task.restore(id, ASSIGNEE, CREATOR, "Original", "Details",
                TaskStatus.TODO, past, NOW, NOW.plusSeconds(1));
        assertThat(task.dueDate()).isEqualTo(past);
        assertThat(task.status()).isEqualTo(TaskStatus.TODO);
        assertThat(task.updatedAt()).isEqualTo(task.createdAt().plusSeconds(1));

        task.update("Edited", null, TaskStatus.IN_PROGRESS, null, NOW.plusSeconds(2));

        assertThat(task.id()).isEqualTo(id);
        assertThat(task.assigneeId()).isEqualTo(ASSIGNEE);
        assertThat(task.createdById()).isEqualTo(CREATOR);
        assertThat(task.createdAt()).isEqualTo(Instant.parse("2026-09-27T10:00:00.123456Z"));
        assertThat(task.updatedAt()).isEqualTo(task.createdAt().plusSeconds(2));
        assertThat(task.title()).isEqualTo("Edited");
        assertThat(task.description()).isNull();
        assertThat(task.dueDate()).isNull();
    }

    @Test
    void invalidUpdatesLeaveAllStateUnchanged() {
        var task = Task.create(ASSIGNEE, CREATOR, "Original", "Details",
                TaskStatus.TODO, LocalDate.of(2000, 1, 1), NOW);
        assertThatThrownBy(() -> task.update("Edited", null, null, null, NOW.plusSeconds(1)))
                .isInstanceOfSatisfying(TaskValidationException.class,
                        error -> assertThat(error.field()).isEqualTo("status"));
        assertThatThrownBy(() -> task.update(" ", null, TaskStatus.DONE, null, NOW.plusSeconds(1)))
                .isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> task.update("Edited", null, TaskStatus.DONE, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThat(task.title()).isEqualTo("Original");
        assertThat(task.description()).isEqualTo("Details");
        assertThat(task.status()).isEqualTo(TaskStatus.TODO);
        assertThat(task.dueDate()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(task.updatedAt()).isEqualTo(task.createdAt());
    }

    @Test
    void requiredAttributionAndRestoredIdentityCannotBeMissing() {
        assertThatThrownBy(() -> Task.create(null, CREATOR, "Task", NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Task.create(ASSIGNEE, null, "Task", NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Task.restore(null, ASSIGNEE, CREATOR, "Task", null,
                TaskStatus.TODO, null, NOW, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Task.restore(UUID.randomUUID(), ASSIGNEE, CREATOR, " ", null,
                TaskStatus.TODO, null, NOW, NOW)).isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> Task.create(ASSIGNEE, CREATOR, "Task", null, null, null, NOW))
                .isInstanceOf(TaskValidationException.class);
    }
}
