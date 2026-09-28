import { TestBed } from "@angular/core/testing";
import { provideRouter, Router } from "@angular/router";
import { provideHttpClient, withInterceptors } from "@angular/common/http";
import { provideHttpClientTesting, HttpTestingController } from "@angular/common/http/testing";
import { App } from "../../app";
import { routes } from "../../app.routes";
import { AuthService } from "../../auth/auth.service";
import { authInterceptor } from "../../auth/auth.interceptor";

describe("Authentication forms", () => {
    beforeEach(() =>
        TestBed.configureTestingModule({
            imports: [App],
            providers: [
                provideRouter(routes),
                provideHttpClient(withInterceptors([authInterceptor])),
                provideHttpClientTesting(),
            ],
        })
    );
    afterEach(() => TestBed.inject(HttpTestingController).verify());
    async function open(url: string) {
        const fixture = TestBed.createComponent(App);
        fixture.detectChanges();
        const router = TestBed.inject(Router);
        await router.navigateByUrl(url);
        await fixture.whenStable();
        fixture.detectChanges();
        const element = fixture.nativeElement as HTMLElement;
        function fill(email = "person@example.com", password = "a long password with spaces") {
            for (const [id, value] of Object.entries({ email, password })) {
                const input = element.querySelector<HTMLInputElement>("#" + id)!;
                input.value = value;
                input.dispatchEvent(new Event("input"));
            }
            fixture.detectChanges();
        }
        function submit() {
            element
                .querySelector("form")!
                .dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
            fixture.detectChanges();
        }
        return {
            fixture,
            router,
            element,
            fill,
            submit,
            requests: TestBed.inject(HttpTestingController),
        };
    }

    it("registers once and asks the user to sign in without issuing a login request", async () => {
        const { fixture, router, element, fill, submit, requests } = await open(
            "/register?returnUrl=%2Ftasks"
        );
        fill();
        submit();
        submit();
        const request = requests.expectOne("/api/v1/auth/register");
        expect(request.request.body).toEqual({
            email: "person@example.com",
            password: "a long password with spaces",
        });
        expect(element.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(
            true
        );
        request.flush({}, { status: 201, statusText: "Created" });
        await fixture.whenStable();
        fixture.detectChanges();
        expect(router.url).toContain("/login?registered=true");
        expect(element.textContent).toContain("Your account is ready.");
        expect(TestBed.inject(AuthService).signedIn()).toBe(false);
        requests.expectNone("/api/v1/auth/login");
    });

    it("signs in and returns to the originally requested protected page", async () => {
        const { fixture, router, fill, submit, requests } = await open("/tasks");
        fill();
        submit();
        requests.expectOne("/api/v1/auth/login").flush({
            accessToken:
                "h." +
                btoa(
                    JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 900, roles: ["USER"] })
                ) +
                ".s",
            tokenType: "Bearer",
            expiresIn: 900,
        });
        await fixture.whenStable();
        expect(router.url).toBe("/tasks");
        expect(TestBed.inject(AuthService).signedIn()).toBe(true);
    });

    it.each([
        [400, "Check your email"],
        [401, "Email or password is incorrect"],
        [409, "already exists"],
        [429, "Too many attempts"],
        [500, "Unable to sign in"],
        [0, "Cannot reach the server"],
    ])("displays a safe actionable message for status %s", async (status, message) => {
        const { fixture, element, fill, submit, requests } = await open("/login");
        fill();
        submit();
        const request = requests.expectOne("/api/v1/auth/login");
        if (status === 0) request.error(new ProgressEvent("error"));
        else
            request.flush(
                { detail: "private backend details" },
                { status: status as number, statusText: "Failure" }
            );
        fixture.detectChanges();
        expect(element.querySelector('[role="alert"]')?.textContent).toContain(message);
        expect(element.textContent).not.toContain("private backend details");
        expect(element.querySelector<HTMLInputElement>("#password")!.value).toBe("");
        expect(element.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(
            false
        );
    });

    it("validates registration password code points and UTF-8 bytes", async () => {
        const { element, fill, submit, requests } = await open("/register");
        for (const password of ["short", "😀".repeat(14), "😀".repeat(19)]) {
            fill("person@example.com", password);
            submit();
            expect(element.querySelector('[role="alert"]')?.textContent).toContain("15 characters");
            requests.expectNone("/api/v1/auth/register");
        }
        fill("person@example.com", "😀".repeat(15));
        submit();
        requests
            .expectOne("/api/v1/auth/register")
            .flush({}, { status: 409, statusText: "Conflict" });
    });

    it("rejects invalid email without calling the backend", async () => {
        const { fill, submit, requests, element } = await open("/login");
        fill("invalid");
        submit();
        requests.expectNone("/api/v1/auth/login");
        expect(element.querySelector("#email-error")?.textContent).toContain("valid email");
    });

    it("cancels an in-flight login when leaving the form", async () => {
        const { router, fill, submit, requests } = await open("/login");
        fill();
        submit();
        const request = requests.expectOne("/api/v1/auth/login");
        await router.navigateByUrl("/register");
        expect(request.cancelled).toBe(true);
        expect(TestBed.inject(AuthService).signedIn()).toBe(false);
    });
});
