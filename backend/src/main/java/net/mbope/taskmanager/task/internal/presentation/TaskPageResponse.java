package net.mbope.taskmanager.task.internal.presentation;

import java.util.List;
import net.mbope.taskmanager.task.TaskPage;

record TaskPageResponse(List<TaskResponse> items, int page, int size, long totalElements, int totalPages) {
    TaskPageResponse {
        items = List.copyOf(items);
    }

    static TaskPageResponse from(TaskPage page) {
        return new TaskPageResponse(page.items().stream().map(TaskResponse::from).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
