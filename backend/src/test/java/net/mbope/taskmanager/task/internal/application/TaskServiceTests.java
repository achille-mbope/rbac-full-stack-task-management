package net.mbope.taskmanager.task.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.mbope.taskmanager.task.*;
import net.mbope.taskmanager.task.internal.application.port.*;
import net.mbope.taskmanager.task.internal.domain.Task;
import net.mbope.taskmanager.user.AccountLookup;
import net.mbope.taskmanager.user.AccountReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import static org.assertj.core.api.Assertions.*;

class TaskServiceTests {
    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private final MemoryStore store = new MemoryStore();
    private final Actor actor = new Actor();
    private final Accounts accounts = new Accounts();
    private final List<TaskAuditEntry> audit = new ArrayList<>();
    private final PersonalTaskService personal = new PersonalTaskService(store, actor, CLOCK);
    private final TaskAdministrationService admin = new TaskAdministrationService(store, actor, accounts, audit::add, CLOCK);

    @Test
    void personalCreationDerivesBothIdsAndPreservesDefaultsForUsersAndAdmins() {
        for (boolean administrator : List.of(false, true)) {
            actor.admin = administrator;
            var task = personal.create(new TaskDraft("Task"));
            assertThat(task.id()).isNotNull();
            assertThat(task.assigneeId()).isEqualTo(actor.id);
            assertThat(task.createdById()).isEqualTo(actor.id);
            assertThat(task.status()).isEqualTo(TaskStatus.TODO);
            assertThat(task.description()).isNull();
            assertThat(task.dueDate()).isNull();
            assertThat(task.createdAt()).isEqualTo(NOW);
            assertThat(task.updatedAt()).isEqualTo(NOW);
        }
        assertThat(accounts.lookups).isZero();
        assertThat(audit).isEmpty();
    }

    @Test
    void personalOperationsRequireAuthenticationBeforeValidationOrStorage() {
        actor.authenticated = false;
        assertThatThrownBy(() -> personal.create(null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> personal.get(null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> personal.list(new TaskQuery(-1, 0, null, null)))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> personal.update(null, null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> personal.delete(null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThat(store.calls).isZero();
    }

    @Test
    void creatorsAndAdminsCannotAccessOtherAssigneeTasksThroughPersonalUseCases() {
        var other = seed(UUID.randomUUID(), actor.id, "Other");
        for (boolean administrator : List.of(false, true)) {
            actor.admin = administrator;
            for (UUID id : List.of(other.id(), UUID.randomUUID())) {
                assertThatThrownBy(() -> personal.get(id)).isInstanceOf(TaskNotFoundException.class)
                        .hasMessage("Task not found.");
                assertThatThrownBy(() -> personal.update(id, null)).isInstanceOf(TaskNotFoundException.class)
                        .hasMessage("Task not found.");
                assertThatThrownBy(() -> personal.delete(id)).isInstanceOf(TaskNotFoundException.class)
                        .hasMessage("Task not found.");
            }
            assertThat(personal.list(null).items()).isEmpty();
        }
        assertThat(store.rows).containsKey(other.id());
        assertThat(store.globalLookups).isZero();
    }

    @Test
    void assigneesCanReadUpdateAndDeleteTasksCreatedByOtherAccounts() {
        var task = seed(actor.id, UUID.randomUUID(), "Assigned");
        assertThat(personal.get(task.id()).title()).isEqualTo("Assigned");
        var changed = personal.update(task.id(), new TaskUpdate(FieldChange.set("Edited"), null, null, null));
        assertThat(changed.title()).isEqualTo("Edited");
        assertThat(changed.createdById()).isEqualTo(task.createdById());
        assertThat(changed.assigneeId()).isEqualTo(task.assigneeId());
        personal.delete(task.id());
        assertThatThrownBy(() -> personal.get(task.id())).isInstanceOf(TaskNotFoundException.class);
        assertThat(audit).isEmpty();
    }

    @Test
    void patchesDistinguishOmissionFromNullAndAllowReopening() {
        var original = personal.create(new TaskDraft("Original", "Details", TaskStatus.DONE, LocalDate.of(2000, 1, 1)));
        var changed = personal.update(original.id(), new TaskUpdate(null, FieldChange.set(null),
                FieldChange.set(TaskStatus.TODO), null));
        assertThat(changed.title()).isEqualTo(original.title());
        assertThat(changed.description()).isNull();
        assertThat(changed.dueDate()).isEqualTo(original.dueDate());
        assertThat(changed.status()).isEqualTo(TaskStatus.TODO);
        changed = personal.update(original.id(), new TaskUpdate(null, FieldChange.set(""), null, FieldChange.set(null)));
        assertThat(changed.description()).isEmpty();
        assertThat(changed.dueDate()).isNull();
    }

    @Test
    void invalidPatchesCannotMutateStoredStateOrWriteAudit() {
        actor.admin = true;
        var original = personal.create(new TaskDraft("Original"));
        var patches = List.of(new TaskUpdate(null, null, null, null),
                new TaskUpdate(FieldChange.set(null), null, null, null),
                new TaskUpdate(null, null, FieldChange.set(null), null),
                new TaskUpdate(FieldChange.set("Edited"), FieldChange.set("x".repeat(10_001)), null, null));
        for (var patch : patches) {
            assertThatThrownBy(() -> personal.update(original.id(), patch)).isInstanceOf(TaskValidationException.class);
            assertThatThrownBy(() -> admin.update(original.id(), patch)).isInstanceOf(TaskValidationException.class);
        }
        assertThatThrownBy(() -> personal.update(original.id(), null)).isInstanceOf(TaskValidationException.class);
        assertThat(personal.get(original.id())).isEqualTo(original);
        assertThat(store.saves).isZero();
        assertThat(audit).isEmpty();
    }

    @Test
    void adminsAreCheckedBeforeEveryAdministrativeLookupOrValidation() {
        assertThatThrownBy(() -> admin.create(null, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> admin.get(null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> admin.list(new TaskQuery(-1, 0, null, " "), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> admin.update(null, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> admin.delete(null)).isInstanceOf(AccessDeniedException.class);
        assertThat(store.calls).isZero();
        assertThat(accounts.lookups).isZero();
        assertThat(audit).isEmpty();
    }

    @Test
    void adminCreationAcceptsEnabledRecipientsIncludingSelfAndAttributesCaller() {
        actor.admin = true;
        for (UUID recipient : List.of(actor.id, UUID.randomUUID())) {
            accounts.rows.put(recipient, new AccountReference(recipient, true));
            var task = admin.create(recipient, new TaskDraft("Assigned"));
            assertThat(task.assigneeId()).isEqualTo(recipient);
            assertThat(task.createdById()).isEqualTo(actor.id);
        }
        assertThat(accounts.lookups).isEqualTo(2);
        assertThat(audit).isEmpty();
    }

    @Test
    void missingAndDisabledRecipientsAreRejectedWithoutSaving() {
        actor.admin = true;
        UUID recipient = UUID.randomUUID();
        assertThatThrownBy(() -> admin.create(recipient, new TaskDraft("Task")))
                .isInstanceOf(AssigneeNotFoundException.class);
        accounts.rows.put(recipient, new AccountReference(recipient, false));
        assertThatThrownBy(() -> admin.create(recipient, new TaskDraft("Task")))
                .isInstanceOf(AssigneeDisabledException.class);
        assertThat(store.rows).isEmpty();
    }

    @Test
    void adminsCanManageAllTasksWithoutRecheckingRecipientStatus() {
        actor.admin = true;
        var task = seed(UUID.randomUUID(), UUID.randomUUID(), "Other");
        accounts.rows.put(task.assigneeId(), new AccountReference(task.assigneeId(), false));
        assertThat(admin.get(task.id()).id()).isEqualTo(task.id());
        assertThat(admin.list(null, null).totalElements()).isEqualTo(1);
        admin.update(task.id(), new TaskUpdate(null, null, FieldChange.set(TaskStatus.DONE), null));
        admin.delete(task.id());
        assertThat(accounts.lookups).isZero();
        assertThat(store.rows).isEmpty();
        assertThat(audit).hasSize(2);
    }

    @Test
    void missingAdminTargetsProduceNotFoundWithoutAudit() {
        actor.admin = true;
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> admin.get(id)).isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> admin.update(id, null)).isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> admin.delete(id)).isInstanceOf(TaskNotFoundException.class);
        assertThat(audit).isEmpty();
    }

    @Test
    void auditCapturesActorTaskTimeAndActualChangesIncludingNulls() {
        actor.admin = true;
        var task = seed(UUID.randomUUID(), UUID.randomUUID(), "Original");
        admin.update(task.id(), new TaskUpdate(FieldChange.set("Edited"), FieldChange.set("Details"), null, null));
        var entry = audit.getFirst();
        assertThat(entry.actorId()).isEqualTo(actor.id);
        assertThat(entry.taskId()).isEqualTo(task.id());
        assertThat(entry.occurredAt()).isEqualTo(NOW);
        assertThat(entry.action()).isEqualTo(TaskAuditEntry.Action.UPDATE);
        assertThat(entry.changes()).containsEntry("title", new TaskAuditEntry.Change("Original", "Edited"))
                .containsEntry("description", new TaskAuditEntry.Change(null, "Details"))
                .doesNotContainKeys("status", "assigneeId", "createdById");
        admin.update(task.id(), new TaskUpdate(null, FieldChange.set(null), null, null));
        assertThat(audit.get(1).changes()).containsEntry("description", new TaskAuditEntry.Change("Details", null));
        admin.delete(task.id());
        var deletion = audit.getLast();
        assertThat(deletion.action()).isEqualTo(TaskAuditEntry.Action.DELETE);
        assertThat(deletion.changes()).containsEntry("title", new TaskAuditEntry.Change("Edited", null))
                .containsEntry("assigneeId", new TaskAuditEntry.Change(task.assigneeId(), null));
        assertThat(store.rows).doesNotContainKey(task.id());
        assertThatThrownBy(() -> deletion.changes().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void sameValueAdminUpdatesStillProduceAnAuditRecord() {
        actor.admin = true;
        var task = personal.create(new TaskDraft("Task"));
        admin.update(task.id(), new TaskUpdate(FieldChange.set("Task"), null, null, null));
        assertThat(audit).hasSize(1);
        assertThat(audit.getFirst().action()).isEqualTo(TaskAuditEntry.Action.UPDATE);
    }

    @Test
    void auditFailuresPropagateForTransactionRollback() {
        actor.admin = true;
        var failing = new TaskAdministrationService(store, actor, accounts,
                entry -> { throw new IllegalStateException("Audit unavailable"); }, CLOCK);
        var task = personal.create(new TaskDraft("Task"));
        assertThatThrownBy(() -> failing.update(task.id(),
                new TaskUpdate(FieldChange.set("Edited"), null, null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Audit unavailable");
        assertThatThrownBy(() -> failing.delete(task.id()))
                .isInstanceOf(IllegalStateException.class).hasMessage("Audit unavailable");
        // Actual database rollback requires transactional adapters and integration tests.
    }

    @Test
    void listsPassScopeAndNormalizedLiteralFiltersBeforePagination() {
        seed(actor.id, UUID.randomUUID(), "100%_Alpha");
        seed(actor.id, UUID.randomUUID(), "100XXAlpha");
        seed(UUID.randomUUID(), actor.id, "100%_Alpha");
        var result = personal.list(new TaskQuery(0, 1, TaskStatus.TODO, "  %_ALPHA  "));
        assertThat(store.lastScope).isEqualTo(actor.id);
        assertThat(store.lastQuery.q()).isEqualTo("%_ALPHA");
        assertThat(result.items()).extracting(TaskDetails::title).containsExactly("100%_Alpha");
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(personal.list(new TaskQuery(1, 1, null, "%_alpha")).items()).isEmpty();
        assertThat(personal.list(new TaskQuery(0, 20, TaskStatus.DONE, null)).totalElements()).isZero();
        actor.admin = true;
        assertThat(personal.list(null).totalElements()).isEqualTo(2);
        assertThat(admin.list(null, null).totalElements()).isEqualTo(3);
        assertThat(store.lastScope).isNull();
        assertThat(admin.list(null, actor.id).totalElements()).isEqualTo(2);
        assertThat(admin.list(null, UUID.randomUUID()).totalPages()).isZero();
        assertThat(accounts.lookups).isZero();
    }

    @Test
    void invalidQueriesAreRejectedBeforeStorageAndUnicodeFilterLimitIsAccepted() {
        for (var query : List.of(new TaskQuery(-1, 20, null, null), new TaskQuery(0, 0, null, null),
                new TaskQuery(0, 101, null, null), new TaskQuery(0, 20, null, " "),
                new TaskQuery(0, 20, null, "x".repeat(201)))) {
            assertThatThrownBy(() -> personal.list(query)).isInstanceOf(TaskValidationException.class);
        }
        assertThat(store.calls).isZero();
        String unicode = new String(Character.toChars(0x1F680)).repeat(200);
        personal.list(new TaskQuery(0, 20, null, unicode));
        assertThat(store.lastQuery.q()).isEqualTo(unicode);
    }

    @Test
    void invalidCreationAndMissingIdsReturnValidationErrors() {
        assertThatThrownBy(() -> personal.create(null)).isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> personal.create(new TaskDraft(" "))).isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> personal.get(null)).isInstanceOf(TaskValidationException.class);
        actor.admin = true;
        assertThatThrownBy(() -> admin.create(null, new TaskDraft("Task"))).isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> admin.get(null)).isInstanceOf(TaskValidationException.class);
        assertThat(store.calls).isZero();
    }

    private Task seed(UUID assignee, UUID creator, String title) {
        return store.add(Task.create(assignee, creator, title, NOW.minusSeconds(60)));
    }

    private static final class Actor implements CurrentTaskActor {
        private final UUID id = UUID.randomUUID();
        private boolean admin;
        private boolean authenticated = true;

        public UUID id() {
            if (!authenticated) {
                throw new AuthenticationCredentialsNotFoundException("Authentication required");
            }
            return id;
        }

        public void requireAdmin() {
            id();
            if (!admin) {
                throw new AccessDeniedException("ADMIN required");
            }
        }
    }

    private static final class Accounts implements AccountLookup {
        private final Map<UUID, AccountReference> rows = new HashMap<>();
        private int lookups;

        public Optional<AccountReference> findById(UUID id) {
            lookups++;
            return Optional.ofNullable(rows.get(id));
        }
    }

    /** A detached in-memory adapter; database semantics need a separate adapter contract test. */
    private static final class MemoryStore implements TaskStore {
        private final Map<UUID, Task> rows = new HashMap<>();
        private int calls;
        private int globalLookups;
        private int saves;
        private TaskQuery lastQuery;
        private UUID lastScope;

        public Task add(Task task) {
            calls++;
            Task saved = Task.restore(UUID.randomUUID(), task.assigneeId(), task.createdById(), task.title(),
                    task.description(), task.status(), task.dueDate(), task.createdAt(), task.updatedAt());
            rows.put(saved.id(), copy(saved));
            return saved;
        }

        public Optional<Task> findById(UUID id) {
            calls++;
            globalLookups++;
            return Optional.ofNullable(rows.get(id)).map(MemoryStore::copy);
        }

        public Optional<Task> findAssignedById(UUID id, UUID assignee) {
            calls++;
            return Optional.ofNullable(rows.get(id)).filter(task -> task.assigneeId().equals(assignee))
                    .map(MemoryStore::copy);
        }

        public void save(Task task) {
            calls++;
            saves++;
            rows.put(task.id(), copy(task));
        }

        public void delete(Task task) {
            calls++;
            rows.remove(task.id());
        }

        public TaskPage list(TaskQuery query, UUID assignee) {
            calls++;
            lastQuery = query;
            lastScope = assignee;
            var filtered = rows.values().stream()
                    .filter(task -> assignee == null || task.assigneeId().equals(assignee))
                    .filter(task -> query.status() == null || task.status() == query.status())
                    .filter(task -> query.q() == null || task.title().toLowerCase(Locale.ROOT)
                            .contains(query.q().toLowerCase(Locale.ROOT)))
                    .sorted(Comparator.comparing(Task::createdAt).reversed().thenComparing(task -> task.id().toString()))
                    .map(TaskValues::view).toList();
            var items = filtered.stream().skip((long) query.page() * query.size()).limit(query.size()).toList();
            return new TaskPage(items, query.page(), query.size(), filtered.size(),
                    (filtered.size() + query.size() - 1) / query.size());
        }

        private static Task copy(Task task) {
            return Task.restore(task.id(), task.assigneeId(), task.createdById(), task.title(), task.description(),
                    task.status(), task.dueDate(), task.createdAt(), task.updatedAt());
        }
    }
}
