import { Component, DestroyRef, inject, signal } from "@angular/core";
import { HttpErrorResponse } from "@angular/common/http";
import { FormControl, FormGroup, ReactiveFormsModule, ValidatorFn } from "@angular/forms";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { finalize, Observable } from "rxjs";
import { Task, TaskPage, TaskService, TaskStatus } from "./task.service";

const maxCharacters =
    (max: number): ValidatorFn =>
    (control) =>
        [...String(control.value ?? "")].length > max ? { tooLong: true } : null;
const titleRequired: ValidatorFn = (control) =>
    !String(control.value ?? "").trim() ? { required: true } : null;
const validDate: ValidatorFn = (control) => {
    const value = String(control.value ?? "");
    if (!value) return null;
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return { date: true };
    const date = new Date(value + "T00:00:00Z");
    return Number.isNaN(date.valueOf()) || date.toISOString().slice(0, 10) !== value
        ? { date: true }
        : null;
};

@Component({
    selector: "app-tasks",
    imports: [ReactiveFormsModule],
    templateUrl: "./tasks.html",
    styleUrl: "./tasks.scss",
})
export class Tasks {
    private readonly api = inject(TaskService);
    private readonly destroyRef = inject(DestroyRef);
    readonly page = signal<TaskPage | null>(null);
    readonly loading = signal(false);
    readonly busy = signal(false);
    readonly listError = signal("");
    readonly actionError = signal("");
    readonly notice = signal("");
    readonly editorOpen = signal(false);
    readonly editing = signal<Task | null>(null);
    readonly deleting = signal<Task | null>(null);
    readonly labels: Record<TaskStatus, string> = {
        TODO: "To do",
        IN_PROGRESS: "In progress",
        DONE: "Done",
    };
    readonly fields = ["title", "description", "status", "dueDate"] as const;
    readonly serverErrors = signal<Partial<Record<(typeof this.fields)[number], string>>>({});
    private requestedPage = 0;
    readonly form = new FormGroup({
        title: new FormControl("", {
            nonNullable: true,
            validators: [titleRequired, maxCharacters(200)],
        }),
        description: new FormControl("", { nonNullable: true, validators: [maxCharacters(10000)] }),
        status: new FormControl<TaskStatus>("TODO", {
            nonNullable: true,
            validators: [
                (control) =>
                    ["TODO", "IN_PROGRESS", "DONE"].includes(control.value)
                        ? null
                        : { status: true },
            ],
        }),
        dueDate: new FormControl("", { nonNullable: true, validators: [validDate] }),
    });

    constructor() {
        this.form.valueChanges
            .pipe(takeUntilDestroyed())
            .subscribe(() => this.serverErrors.set({}));
        this.load();
    }

    load(page = this.requestedPage) {
        if (this.loading() || this.busy()) return;
        this.requestedPage = Math.max(0, page);
        this.deleting.set(null);
        this.loading.set(true);
        this.listError.set("");
        this.page.set(null);
        this.api
            .list(this.requestedPage)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (result) => {
                    this.loading.set(false);
                    // Deletion or another client may have removed the last page.
                    if (result.page > 0 && result.items.length === 0) {
                        this.load(Math.max(0, Math.min(result.page - 1, result.totalPages - 1)));
                        return;
                    }
                    this.page.set(result);
                },
                error: (error) => {
                    this.loading.set(false);
                    this.listError.set(this.errorMessage(error, "load tasks"));
                },
            });
    }

    openEditor(task: Task | null = null) {
        if (this.busy() || this.loading()) return;
        this.editing.set(task);
        this.deleting.set(null);
        this.actionError.set("");
        this.notice.set("");
        this.serverErrors.set({});
        this.form.reset({
            title: task?.title ?? "",
            description: task?.description ?? "",
            status: task?.status ?? "TODO",
            dueDate: task?.dueDate ?? "",
        });
        this.editorOpen.set(true);
    }

    cancelEditor() {
        if (this.busy()) return;
        this.editorOpen.set(false);
        this.editing.set(null);
        this.actionError.set("");
        this.serverErrors.set({});
    }

    save() {
        if (this.busy() || this.loading() || !this.editorOpen()) return;
        this.actionError.set("");
        this.serverErrors.set({});
        this.form.markAllAsTouched();
        if (this.form.invalid) {
            this.actionError.set("Check the highlighted fields.");
            return;
        }
        const values = this.form.getRawValue();
        const input = {
            ...values,
            description: values.description === "" ? null : values.description,
            dueDate: values.dueDate || null,
        };
        const task = this.editing();
        this.mutate(
            task ? this.api.update(task.id, input) : this.api.create(input),
            task ? "save changes" : "create the task",
            task ? "Task updated." : "Task created.",
            task ? this.requestedPage : 0
        );
    }

    complete(task: Task) {
        if (
            this.busy() ||
            this.loading() ||
            this.editorOpen() ||
            this.deleting() ||
            task.status === "DONE"
        )
            return;
        this.mutate(
            this.api.update(task.id, { status: "DONE" }),
            "complete the task",
            "Task completed."
        );
    }

    askDelete(task: Task) {
        if (this.busy() || this.loading() || this.editorOpen()) return;
        this.deleting.set(task);
        this.actionError.set("");
        this.notice.set("");
    }

    cancelDelete() {
        if (this.busy()) return;
        this.deleting.set(null);
        this.actionError.set("");
    }

    confirmDelete() {
        const task = this.deleting();
        if (!task || this.busy() || this.loading()) return;
        this.mutate(this.api.delete(task.id), "delete the task", "Task deleted.");
    }

    fieldError(field: (typeof this.fields)[number]): string {
        const server = this.serverErrors()[field];
        if (server) return server;
        const control = this.form.controls[field];
        if (!control.touched || !control.invalid) return "";
        if (field === "title")
            return "Enter a title with 1–200 characters, including a non-whitespace character.";
        if (field === "description") return "Use at most 10,000 characters.";
        if (field === "dueDate") return "Enter a valid calendar date (YYYY-MM-DD).";
        return "Choose a valid status.";
    }

    private mutate(
        request: Observable<unknown>,
        action: string,
        success: string,
        page = this.requestedPage
    ) {
        this.busy.set(true);
        this.actionError.set("");
        this.notice.set("");
        request
            .pipe(
                takeUntilDestroyed(this.destroyRef),
                finalize(() => this.busy.set(false))
            )
            .subscribe({
                next: () => {
                    this.busy.set(false);
                    this.editorOpen.set(false);
                    this.editing.set(null);
                    this.deleting.set(null);
                    this.notice.set(success);
                    this.load(page);
                },
                error: (error) => {
                    this.actionError.set(this.errorMessage(error, action));
                    if (
                        error instanceof HttpErrorResponse &&
                        error.status === 400 &&
                        Array.isArray(error.error?.errors)
                    ) {
                        const errors: Partial<Record<(typeof this.fields)[number], string>> = {};
                        for (const item of error.error.errors) {
                            if (
                                item &&
                                this.fields.includes(item.field) &&
                                typeof item.message === "string"
                            ) {
                                errors[item.field as (typeof this.fields)[number]] = item.message;
                            }
                        }
                        this.serverErrors.set(errors);
                    }
                },
            });
    }

    private errorMessage(error: unknown, action: string): string {
        if (error instanceof HttpErrorResponse) {
            switch (error.status) {
                case 0:
                    return action === "load tasks"
                        ? "Cannot reach the server. Check your connection and retry."
                        : "Connection lost. Your change may have reached the server. Refresh the list before retrying.";
                case 400:
                    return "Some values were rejected. Check the fields and try again.";
                case 401:
                    return "Your session has ended. Please sign in again.";
                case 403:
                    return "You do not have permission to perform this action.";
                case 404:
                    return "This task no longer exists or is no longer assigned to you. Refresh the list.";
                case 409:
                    return "The task changed. Refresh the list before trying again.";
                case 429:
                    return "Too many requests. Please wait and try again.";
            }
        }
        return `Unable to ${action}. Please try again.`;
    }
}
