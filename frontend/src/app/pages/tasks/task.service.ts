import { inject, Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";

export type TaskStatus = "TODO" | "IN_PROGRESS" | "DONE";
export interface TaskInput {
    title: string;
    description: string | null;
    status: TaskStatus;
    dueDate: string | null;
}
export interface Task extends TaskInput {
    id: string;
    assigneeId: string;
    createdById: string;
    createdAt: string;
    updatedAt: string;
}
export interface TaskPage {
    items: Task[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}
@Injectable({ providedIn: "root" })
export class TaskService {
    private readonly http = inject(HttpClient);
    private readonly url = "/api/v1/tasks";
    list(page = 0) {
        return this.http.get<TaskPage>(this.url, { params: { page, size: 20 } });
    }
    create(input: TaskInput) {
        return this.http.post<Task>(this.url, input);
    }
    update(id: string, input: Partial<TaskInput>) {
        return this.http.patch<Task>(this.url + "/" + encodeURIComponent(id), input);
    }
    delete(id: string) {
        return this.http.delete<void>(this.url + "/" + encodeURIComponent(id));
    }
}
