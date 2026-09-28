package net.mbope.taskmanager.task.internal.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

@Schema(name = "AdminCreateTaskRequest")
final class AssignTaskRequest extends CreateTaskRequest {
    @JsonProperty
    private UUID assigneeId;

    UUID assigneeId() {
        return assigneeId;
    }
}
