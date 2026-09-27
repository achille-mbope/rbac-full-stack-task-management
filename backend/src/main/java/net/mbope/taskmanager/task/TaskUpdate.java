package net.mbope.taskmanager.task;

import java.time.LocalDate;
import java.util.Objects;

/** Only editable fields are represented; null wrappers mean omitted fields. */
public record TaskUpdate(FieldChange<String> title, FieldChange<String> description,
                         FieldChange<TaskStatus> status, FieldChange<LocalDate> dueDate) {
    public TaskUpdate {
        title = Objects.requireNonNullElse(title, FieldChange.unchanged());
        description = Objects.requireNonNullElse(description, FieldChange.unchanged());
        status = Objects.requireNonNullElse(status, FieldChange.unchanged());
        dueDate = Objects.requireNonNullElse(dueDate, FieldChange.unchanged());
    }

    public boolean isEmpty() {
        return !title.present() && !description.present() && !status.present() && !dueDate.present();
    }
}
