package net.mbope.taskmanager.task.internal.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.mbope.taskmanager.task.*;
import net.mbope.taskmanager.task.internal.application.port.TaskAuditEntry;
import net.mbope.taskmanager.task.internal.domain.Task;

final class TaskValues {
    private TaskValues() {
    }

    static TaskDetails view(Task task) {
        return new TaskDetails(task.id(), task.title(), task.description(), task.status(), task.dueDate(),
                task.assigneeId(), task.createdById(), task.createdAt(), task.updatedAt());
    }

    static Task create(UUID assignee, UUID creator, TaskDraft draft, Instant now) {
        if (draft == null) {
            throw new TaskValidationException("$", "required", "Task values are required.");
        }
        return Task.create(assignee, creator, draft.title(), draft.description(), draft.status(), draft.dueDate(), now);
    }

    static UUID id(UUID id, String field) {
        if (id == null) {
            throw new TaskValidationException(field, "required", "An ID is required.");
        }
        return id;
    }

    static TaskQuery query(TaskQuery query) {
        if (query == null) {
            query = new TaskQuery();
        }
        if (query.page() < 0) {
            throw new TaskValidationException("page", "range", "Page must be zero or greater.");
        }
        if (query.size() < 1 || query.size() > 100) {
            throw new TaskValidationException("size", "range", "Size must be between 1 and 100.");
        }
        String q = query.q();
        if (q != null) {
            q = q.strip();
            if (q.isBlank() || q.codePointCount(0, q.length()) > 200) {
                throw new TaskValidationException("q", "invalid",
                        "Title filter must contain between 1 and 200 characters and not be blank.");
            }
        }
        return new TaskQuery(query.page(), query.size(), query.status(), q);
    }

    static void update(Task task, TaskUpdate update, Instant now) {
        if (update == null || update.isEmpty()) {
            throw new TaskValidationException("$", "empty", "At least one editable field is required.");
        }
        task.update(update.title().orElse(task.title()), update.description().orElse(task.description()),
                update.status().orElse(task.status()), update.dueDate().orElse(task.dueDate()), now);
    }

    static Map<String, TaskAuditEntry.Change> changes(TaskDetails before, TaskDetails after) {
        var result = new LinkedHashMap<String, TaskAuditEntry.Change>();
        var oldFields = fields(before);
        var newFields = after == null ? null : fields(after);
        oldFields.forEach((field, value) -> {
            Object changed = newFields == null ? null : newFields.get(field);
            if (after == null || !java.util.Objects.equals(value, changed)) {
                result.put(field, new TaskAuditEntry.Change(value, changed));
            }
        });
        return result;
    }

    private static Map<String, Object> fields(TaskDetails task) {
        var values = new LinkedHashMap<String, Object>();
        values.put("title", task.title());
        values.put("description", task.description());
        values.put("status", task.status());
        values.put("dueDate", task.dueDate());
        values.put("assigneeId", task.assigneeId());
        values.put("createdById", task.createdById());
        values.put("createdAt", task.createdAt());
        values.put("updatedAt", task.updatedAt());
        return values;
    }
}
