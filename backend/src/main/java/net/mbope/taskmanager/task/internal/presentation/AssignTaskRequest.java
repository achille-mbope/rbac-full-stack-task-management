package net.mbope.taskmanager.task.internal.presentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

final class AssignTaskRequest extends CreateTaskRequest {
    @JsonProperty
    private UUID assigneeId;

    UUID assigneeId() {
        return assigneeId;
    }
}
