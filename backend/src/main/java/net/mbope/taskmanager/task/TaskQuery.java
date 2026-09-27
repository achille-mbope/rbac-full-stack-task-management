package net.mbope.taskmanager.task;

/** Null filters are absent. Validation runs in the use case after authorization. */
public record TaskQuery(int page, int size, TaskStatus status, String q) {
    public TaskQuery() {
        this(0, 20, null, null);
    }
}
