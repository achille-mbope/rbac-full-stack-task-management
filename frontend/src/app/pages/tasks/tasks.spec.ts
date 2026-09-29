import { TestBed, ComponentFixture } from "@angular/core/testing";
import { provideHttpClient, withInterceptors } from "@angular/common/http";
import { HttpTestingController } from "@angular/common/http/testing";
import { provideHttpClientTesting } from "@angular/common/http/testing";
import { provideRouter, Router } from "@angular/router";
import { vi } from "vitest";
import { Tasks } from "./tasks";
import { Task } from "./task.service";
import { authInterceptor } from "../../auth/auth.interceptor";
import { AuthService } from "../../auth/auth.service";

const task: Task = {
    id: "one",
    title: "Plan release",
    description: "Write notes",
    status: "TODO",
    dueDate: "2026-10-01",
    assigneeId: "me",
    createdById: "me",
    createdAt: "2026-09-29T10:00:00Z",
    updatedAt: "2026-09-29T10:00:00Z",
};
describe("My tasks workflow", () => {
    let fixture: ComponentFixture<Tasks>;
    let component: Tasks;
    let http: HttpTestingController;
    let element: HTMLElement;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [Tasks],
            providers: [
                provideRouter([]),
                provideHttpClient(withInterceptors([authInterceptor])),
                provideHttpClientTesting(),
            ],
        });
        http = TestBed.inject(HttpTestingController);
        fixture = TestBed.createComponent(Tasks);
        component = fixture.componentInstance;
        element = fixture.nativeElement;
        fixture.detectChanges();
    });
    afterEach(() => {
        http.verify();
        vi.restoreAllMocks();
    });
    function list(items: Task[] = [task], page = 0, total = items.length) {
        const request = http.expectOne("/api/v1/tasks?page=" + page + "&size=20");
        expect(request.request.method).toBe("GET");
        request.flush({
            items,
            page,
            size: 20,
            totalElements: total,
            totalPages: Math.ceil(total / 20),
        });
        fixture.detectChanges();
    }
    function click(label: string) {
        const button = Array.from(element.querySelectorAll("button")).find(
            (b) => b.textContent?.trim() === label
        );
        expect(button, label).toBeTruthy();
        button!.click();
        fixture.detectChanges();
    }
    function fill(id: string, value: string) {
        const input = element.querySelector<HTMLInputElement>("#task-" + id)!;
        input.value = value;
        input.dispatchEvent(new Event(input.tagName === "SELECT" ? "change" : "input"));
        fixture.detectChanges();
    }

    it("shows loading, an empty state, and task details as plain text", () => {
        expect(element.textContent).toContain("Loading tasks");
        list([]);
        expect(element.textContent).toContain("No tasks yet");
        click("Refresh list");
        list([{ ...task, title: "<script>bad()</script>", description: "<b>not HTML</b>" }]);
        expect(element.querySelector("h3")?.textContent).toBe("<script>bad()</script>");
        expect(element.querySelector("script")).toBeNull();
        expect(element.querySelector(".description b")).toBeNull();
        expect(element.querySelector("time")?.textContent).toBe("2026-10-01");
    });

    it("creates a task from the form and reloads the first page", () => {
        list([]);
        click("New task");
        fill("title", " Keep spaces ");
        fill("description", "notes");
        fill("status", "IN_PROGRESS");
        fill("dueDate", "2020-02-29");
        click("Save task");
        component.save();
        const request = http.expectOne("/api/v1/tasks");
        expect(request.request.method).toBe("POST");
        expect(request.request.body).toEqual({
            title: " Keep spaces ",
            description: "notes",
            status: "IN_PROGRESS",
            dueDate: "2020-02-29",
        });
        request.flush(task, { status: 201, statusText: "Created" });
        list();
        expect(element.textContent).toContain("Task created.");
        expect(element.querySelector("form")).toBeNull();
    });

    it("edits a task and explicitly clears optional fields with null", () => {
        list();
        click("Edit");
        expect(element.querySelector<HTMLInputElement>("#task-title")!.value).toBe(task.title);
        fill("description", "");
        fill("dueDate", "");
        fill("status", "DONE");
        click("Save task");
        const request = http.expectOne("/api/v1/tasks/one");
        expect(request.request.method).toBe("PATCH");
        expect(request.request.body).toEqual({
            title: task.title,
            description: null,
            dueDate: null,
            status: "DONE",
        });
        request.flush({ ...task, status: "DONE", description: null, dueDate: null });
        list([{ ...task, status: "DONE" }]);
        expect(element.textContent).toContain("Task updated.");
        expect(
            Array.from(element.querySelectorAll("button")).some((b) => b.textContent === "Complete")
        ).toBe(false);
    });

    it("completes with a status-only PATCH and prevents duplicate actions", () => {
        list();
        click("Complete");
        component.complete(task);
        const request = http.expectOne("/api/v1/tasks/one");
        expect(request.request.body).toEqual({ status: "DONE" });
        expect(request.request.method).toBe("PATCH");
        request.flush({ ...task, status: "DONE" });
        list([{ ...task, status: "DONE" }]);
        expect(element.textContent).toContain("Task completed.");
    });

    it("requires confirmation to delete and supports cancelling", () => {
        list();
        click("Delete");
        http.expectNone("/api/v1/tasks/one");
        click("Keep task");
        expect(element.textContent).not.toContain("This cannot be undone");
        click("Delete");
        click("Confirm delete");
        component.confirmDelete();
        const request = http.expectOne("/api/v1/tasks/one");
        expect(request.request.method).toBe("DELETE");
        request.flush(null, { status: 204, statusText: "No Content" });
        list([]);
        expect(element.textContent).toContain("Task deleted.");
    });

    it("paginates and falls back when deletion removes the last page", () => {
        list([task], 0, 21);
        click("Next");
        list([task], 1, 21);
        click("Delete");
        click("Confirm delete");
        http.expectOne("/api/v1/tasks/one").flush(null, { status: 204, statusText: "No Content" });
        list([], 1, 20);
        list([task], 0, 20);
        expect(element.textContent).toContain("Page 1 of 1");
    });

    it("validates blank titles, Unicode character limits, and calendar dates", () => {
        list([]);
        click("New task");
        for (const title of ["  \n ", "a".repeat(201)]) {
            component.form.controls.title.setValue(title);
            click("Save task");
            http.expectNone("/api/v1/tasks");
            expect(element.querySelector("#title-error")?.textContent).toContain("1–200");
        }
        component.form.patchValue({
            title: "😀".repeat(200),
            description: "a".repeat(10001),
            dueDate: "2026-02-30",
        });
        click("Save task");
        expect(element.querySelector("#description-error")?.textContent).toContain("10,000");
        expect(element.querySelector("#dueDate-error")?.textContent).toContain("valid calendar");
        component.form.patchValue({ description: "", dueDate: "" });
        click("Save task");
        http.expectOne("/api/v1/tasks").flush(task);
        list();
    });

    it("shows server field validation and preserves the draft after failure", () => {
        list();
        click("Edit");
        fill("title", "My draft");
        click("Save task");
        http.expectOne("/api/v1/tasks/one").flush(
            {
                errors: [{ field: "title", code: "invalid", message: "Choose another title." }],
            },
            { status: 400, statusText: "Bad Request" }
        );
        fixture.detectChanges();
        expect(element.querySelector("#title-error")?.textContent).toContain(
            "Choose another title."
        );
        expect(element.querySelector<HTMLInputElement>("#task-title")!.value).toBe("My draft");
        expect(component.busy()).toBe(false);
        fill("title", "Corrected");
        expect(component.serverErrors()).toEqual({});
        click("Cancel");
    });

    it.each([
        [403, "permission"],
        [404, "no longer exists"],
        [409, "task changed"],
        [429, "Too many requests"],
        [500, "Unable to complete"],
    ])("reports mutation error %s without changing the task", (status, message) => {
        list();
        click("Complete");
        http.expectOne("/api/v1/tasks/one").flush(
            { detail: "internal detail" },
            { status, statusText: "Failure" }
        );
        fixture.detectChanges();
        expect(element.querySelector('[role="alert"]')?.textContent).toContain(message);
        expect(element.textContent).not.toContain("internal detail");
        expect(component.page()?.items[0].status).toBe("TODO");
        expect(component.busy()).toBe(false);
    });

    it("retries a list error and distinguishes failed refresh from successful save", () => {
        http.expectOne("/api/v1/tasks?page=0&size=20").error(new ProgressEvent("error"));
        fixture.detectChanges();
        expect(element.textContent).toContain("Cannot reach the server");
        click("Refresh list");
        list([]);
        click("New task");
        fill("title", "Saved");
        click("Save task");
        http.expectOne("/api/v1/tasks").flush(task);
        http.expectOne("/api/v1/tasks?page=0&size=20").flush(
            {},
            { status: 500, statusText: "Failure" }
        );
        fixture.detectChanges();
        expect(element.textContent).toContain("Task created.");
        expect(element.textContent).toContain("Unable to load tasks");
        expect(element.querySelector("form")).toBeNull();
    });

    it("warns about an uncertain network result without retrying the write", () => {
        list();
        click("Delete");
        click("Confirm delete");
        http.expectOne("/api/v1/tasks/one").error(new ProgressEvent("error"));
        fixture.detectChanges();
        expect(element.textContent).toContain("may have reached the server");
        click("Refresh list");
        list([]);
        expect(component.deleting()).toBeNull();
    });

    it("uses the existing bearer interceptor and expires a rejected session", () => {
        list();
        const auth = TestBed.inject(AuthService);
        auth.login("user@example.com", "password").subscribe();
        const token =
            "h." +
            btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 900, roles: ["USER"] })) +
            ".s";
        http.expectOne("/api/v1/auth/login").flush({
            accessToken: token,
            tokenType: "Bearer",
            expiresIn: 900,
        });
        vi.spyOn(TestBed.inject(Router), "navigate").mockResolvedValue(true);
        click("Refresh list");
        const request = http.expectOne("/api/v1/tasks?page=0&size=20");
        expect(request.request.headers.get("Authorization")).toBe("Bearer " + token);
        request.flush({}, { status: 401, statusText: "Unauthorized" });
        expect(auth.signedIn()).toBe(false);
    });

    it("cancels pending requests when the page is destroyed", () => {
        const request = http.expectOne("/api/v1/tasks?page=0&size=20");
        fixture.destroy();
        expect(request.cancelled).toBe(true);
    });
});
