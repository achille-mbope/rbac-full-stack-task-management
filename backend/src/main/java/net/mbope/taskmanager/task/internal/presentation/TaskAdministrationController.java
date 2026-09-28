package net.mbope.taskmanager.task.internal.presentation;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;

import java.net.URI;
import java.util.UUID;
import net.mbope.taskmanager.task.TaskAdministration;
import net.mbope.taskmanager.task.TaskQuery;
import net.mbope.taskmanager.task.TaskStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/tasks")
class TaskAdministrationController {
    private final TaskAdministration tasks;

    TaskAdministrationController(TaskAdministration tasks) {
        this.tasks = tasks;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createAssignedTask")
    @ApiResponse(responseCode = "201", useReturnTypeSchema = true)
    ResponseEntity<TaskResponse> create(@RequestBody AssignTaskRequest request) {
        var task = tasks.create(request.assigneeId(), request.draft());
        return ResponseEntity.created(URI.create("/api/v1/admin/tasks/" + task.id())).body(TaskResponse.from(task));
    }

    @GetMapping("/{taskId}")
    @Operation(operationId = "getAnyTask")
    TaskResponse get(@PathVariable UUID taskId) {
        return TaskResponse.from(tasks.get(taskId));
    }

    @GetMapping
    @Operation(operationId = "listAllTasks")
    TaskPageResponse list(@RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size,
                          @RequestParam(required = false) TaskStatus status,
                          @RequestParam(required = false) String q,
                          @RequestParam(required = false) UUID assigneeId) {
        return TaskPageResponse.from(tasks.list(new TaskQuery(page, size, status, q), assigneeId));
    }

    @PatchMapping(path = "/{taskId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateAnyTask")
    TaskResponse update(@PathVariable UUID taskId, @RequestBody UpdateTaskRequest request) {
        return TaskResponse.from(tasks.update(taskId, request.update()));
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteAnyTask")
    void delete(@PathVariable UUID taskId) {
        tasks.delete(taskId);
    }
}
