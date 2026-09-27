package net.mbope.taskmanager.task;

import java.util.List;

public record TaskPage(List<TaskDetails> items, int page, int size, long totalElements, int totalPages) {
    public TaskPage {
        items = List.copyOf(items);
    }
}
