package net.mbope.taskmanager.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.mbope.taskmanager.task.internal.application.port.TaskAudit;
import net.mbope.taskmanager.task.internal.application.port.TaskAuditEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TaskPersistenceContract.AuditFailureConfiguration.class)
abstract class TaskPersistenceContract {
    @Autowired PersonalTasks personal;
    @Autowired TaskAdministration admin;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired FailingAudit audit;
    UUID alice;
    UUID bob;

    @BeforeEach
    void prepare() {
        clean();
        alice = account("alice@example.com");
        bob = account("bob@example.com");
        as(alice, false);
    }

    @AfterEach
    void clean() {
        audit.fail = false;
        SecurityContextHolder.clearContext();
        jdbc.update("delete from task_audit");
        jdbc.update("delete from tasks");
        jdbc.update("delete from user_accounts");
    }

    @Test
    void roundTripsUnicodeFieldsDatesAndImmutableAttribution() {
        String emoji = new String(Character.toChars(0x1F680));
        var original = personal.create(new TaskDraft(emoji.repeat(200), emoji.repeat(10000),
                TaskStatus.DONE, LocalDate.of(2000, 1, 1)));
        assertThat(personal.get(original.id())).isEqualTo(original);
        var changed = personal.update(original.id(), new TaskUpdate(FieldChange.set("Edited"),
                FieldChange.set(null), FieldChange.set(TaskStatus.TODO), FieldChange.set(null)));
        assertThat(personal.get(original.id())).isEqualTo(changed);
        assertThat(changed.assigneeId()).isEqualTo(alice);
        assertThat(changed.createdById()).isEqualTo(alice);
        assertThat(changed.createdAt()).isEqualTo(original.createdAt());
        assertThat(changed.updatedAt()).isAfterOrEqualTo(original.updatedAt());
        assertThat(changed.description()).isNull();
        assertThat(changed.dueDate()).isNull();
        personal.delete(original.id());
        assertThatThrownBy(() -> personal.get(original.id())).isInstanceOf(TaskNotFoundException.class);
        assertThat(count("task_audit")).isZero();
    }

    @Test
    void scopedQueriesPreventOtherAssigneeAccessEvenForAdminCreators() {
        as(alice, true);
        var other = admin.create(bob, new TaskDraft("Assigned"));
        assertThatThrownBy(() -> personal.get(other.id())).isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> personal.update(other.id(), new TaskUpdate(FieldChange.set("Spoof"), null, null, null)))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> personal.delete(other.id())).isInstanceOf(TaskNotFoundException.class);
        assertThat(personal.list(null).totalElements()).isZero();
        as(bob, false);
        assertThat(personal.get(other.id()).createdById()).isEqualTo(alice);
        assertThatThrownBy(() -> admin.get(other.id())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> admin.create(UUID.randomUUID(), null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void filtersAreLiteralCaseInsensitiveAndCombinedBeforeCountsAndPages() {
        personal.create(new TaskDraft("literal %_!\\ ALPHA"));
        personal.create(new TaskDraft("literal %_!\\ alpha two"));
        personal.create(new TaskDraft("literal other ALPHA"));
        personal.create(new TaskDraft("literal %_!\\ ALPHA done", null, TaskStatus.DONE, null));
        as(bob, false);
        personal.create(new TaskDraft("literal %_!\\ ALPHA"));
        as(alice, true);
        var query = new TaskQuery(0, 1, TaskStatus.TODO, "  %_!\\ aLpHa  ");
        var first = personal.list(query);
        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.items()).hasSize(1);
        var second = personal.list(new TaskQuery(1, 1, TaskStatus.TODO, query.q()));
        assertThat(second.items()).hasSize(1);
        assertThat(second.items().getFirst().id()).isNotEqualTo(first.items().getFirst().id());
        assertThat(personal.list(new TaskQuery(Integer.MAX_VALUE, 100, null, null)).items()).isEmpty();
        assertThat(admin.list(query, null).totalElements()).isEqualTo(3);
        assertThat(admin.list(query, bob).totalElements()).isEqualTo(1);
        assertThat(admin.list(query, UUID.randomUUID()).totalPages()).isZero();
        assertThat(personal.list(new TaskQuery(0, 20, null, "absent")).totalElements()).isZero();
    }

    @Test
    void orderingUsesCreationDescendingThenUuidAscending() {
        var first = personal.create(new TaskDraft("First"));
        var second = personal.create(new TaskDraft("Second"));
        Instant earlier = Instant.parse("2020-01-01T00:00:00Z");
        jdbc.update("update tasks set created_at = ? where id = ?", Timestamp.from(earlier), first.id());
        assertThat(personal.list(null).items()).extracting(TaskDetails::id).containsExactly(second.id(), first.id());
        jdbc.update("update tasks set created_at = ?", Timestamp.from(earlier));
        List<UUID> sorted = jdbc.queryForList("select id from tasks order by id asc", UUID.class);
        assertThat(personal.list(null).items()).extracting(TaskDetails::id).containsExactlyElementsOf(sorted);
    }

    @Test
    void adminAuditsCommitWithMutationsAndSurviveDeletion() throws Exception {
        var task = personal.create(new TaskDraft("Original", "Details", TaskStatus.TODO, null));
        as(bob, true);
        admin.update(task.id(), new TaskUpdate(FieldChange.set("Edited"), FieldChange.set(null), null, null));
        assertThat(admin.get(task.id()).title()).isEqualTo("Edited");
        String changes = jdbc.queryForObject("select changes_json from task_audit where task_id = ? and action = 'UPDATE'",
                String.class, task.id());
        var tree = json.readTree(changes);
        assertThat(tree.path("title").path("before").asText()).isEqualTo("Original");
        assertThat(tree.path("title").path("after").asText()).isEqualTo("Edited");
        assertThat(tree.path("description").path("after").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select actor_id from task_audit where task_id = ?", UUID.class, task.id()))
                .isEqualTo(bob);
        admin.delete(task.id());
        assertThat(count("tasks")).isZero();
        assertThat(count("task_audit")).isEqualTo(2);
        var deletion = json.readTree(jdbc.queryForObject(
                "select changes_json from task_audit where task_id = ? and action = 'DELETE'", String.class, task.id()));
        assertThat(deletion.path("title").path("before").asText()).isEqualTo("Edited");
        assertThat(deletion.path("assigneeId").path("before").asText()).isEqualTo(alice.toString());
    }

    @Test
    void failureAfterAuditFlushRollsBackBothUpdateAndDelete() {
        var original = personal.create(new TaskDraft("Original"));
        as(bob, true);
        audit.fail = true;
        assertThatThrownBy(() -> admin.update(original.id(),
                new TaskUpdate(FieldChange.set("Must roll back"), null, null, null)))
                .isInstanceOf(RuntimeException.class);
        assertThat(admin.get(original.id())).isEqualTo(original);
        assertThat(count("task_audit")).isZero();
        assertThatThrownBy(() -> admin.delete(original.id())).isInstanceOf(RuntimeException.class);
        assertThat(admin.get(original.id())).isEqualTo(original);
        assertThat(count("task_audit")).isZero();
    }

    @Test
    void recipientValidationUsesDatabaseButExistingAssignmentsSurviveDisabling() {
        as(alice, true);
        var task = admin.create(bob, new TaskDraft("Assigned"));
        jdbc.update("update user_accounts set enabled = false where id = ?", bob);
        assertThatThrownBy(() -> admin.create(bob, new TaskDraft("Rejected")))
                .isInstanceOf(AssigneeDisabledException.class);
        assertThatThrownBy(() -> admin.create(UUID.randomUUID(), new TaskDraft("Missing")))
                .isInstanceOf(AssigneeNotFoundException.class);
        assertThat(admin.get(task.id()).id()).isEqualTo(task.id());
        // Existing token authority is retained; database status is not rechecked for access.
        as(bob, false);
        assertThat(personal.get(task.id()).id()).isEqualTo(task.id());
    }

    @Test
    void principalAdapterRejectsMissingAndMalformedIdentities() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> personal.list(null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("not-a-uuid", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertThatThrownBy(() -> personal.create(new TaskDraft("Task")))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> admin.list(null, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThat(count("tasks")).isZero();
    }

    long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    private UUID account(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into user_accounts (id,email,password_hash,is_admin,enabled,created_at,updated_at) values (?,?,?,false,true,?,?)",
                id, email, "x".repeat(60), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        return id;
    }

    private void as(UUID id, boolean admin) {
        var roles = admin ? List.of("ROLE_USER", "ROLE_ADMIN") : List.of("ROLE_USER");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(id.toString(), null,
                        roles.stream().map(SimpleGrantedAuthority::new).toList()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AuditFailureConfiguration {
        @Bean
        @Primary
        FailingAudit failingAudit(@Qualifier("jpaTaskAudit") TaskAudit delegate) {
            return new FailingAudit(delegate);
        }
    }

    static class FailingAudit implements TaskAudit {
        private final TaskAudit delegate;
        boolean fail;

        FailingAudit(TaskAudit delegate) {
            this.delegate = delegate;
        }

        public void append(TaskAuditEntry entry) {
            delegate.append(entry);
            if (fail) {
                throw new IllegalStateException("Simulated failure after audit flush");
            }
        }
    }
}
