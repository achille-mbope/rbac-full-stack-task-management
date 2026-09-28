package net.mbope.taskmanager.task.internal.presentation;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;

import java.net.URI;
import java.util.UUID;
import net.mbope.taskmanager.task.PersonalTasks;
import net.mbope.taskmanager.task.TaskQuery;
import net.mbope.taskmanager.task.TaskStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks")
class PersonalTaskController {
    private final PersonalTasks tasks;

    PersonalTaskController(PersonalTasks tasks) {
        this.tasks = tasks;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createTask")
    @ApiResponse(responseCode = "201", useReturnTypeSchema = true)
    ResponseEntity<TaskResponse> create(@RequestBody CreateTaskRequest request) {
        var task = tasks.create(request.draft());
        return ResponseEntity.created(URI.create("/api/v1/tasks/" + task.id())).body(TaskResponse.from(task));
    }

    @GetMapping("/{taskId}")
    @Operation(operationId = "getTask")
    TaskResponse get(@PathVariable UUID taskId) {
        return TaskResponse.from(tasks.get(taskId));
    }

    @GetMapping
    @Operation(operationId = "listTasks")
    TaskPageResponse list(@RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size,
                          @RequestParam(required = false) TaskStatus status,
                          @RequestParam(required = false) String q) {
        return TaskPageResponse.from(tasks.list(new TaskQuery(page, size, status, q)));
    }

    @PatchMapping(path = "/{taskId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateTask")
    TaskResponse update(@PathVariable UUID taskId, @RequestBody UpdateTaskRequest request) {
        return TaskResponse.from(tasks.update(taskId, request.update()));
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteTask")
    void delete(@PathVariable UUID taskId) {
        tasks.delete(taskId);
    }
}
