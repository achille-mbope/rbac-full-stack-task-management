import { of } from "rxjs";
import { TaskService } from "./pages/tasks/task.service";
import { TestBed } from "@angular/core/testing";
import { provideRouter, Router } from "@angular/router";
import { provideHttpClient } from "@angular/common/http";
import { provideHttpClientTesting, HttpTestingController } from "@angular/common/http/testing";
import { App } from "./app";
import { routes } from "./app.routes";
import { AuthService } from "./auth/auth.service";

describe("Application authentication routing", () => {
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [App],
            providers: [
                {
                    provide: TaskService,
                    useValue: {
                        list: () =>
                            of({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
                    },
                },
                provideRouter(routes),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();
    });
    afterEach(() => TestBed.inject(HttpTestingController).verify());

    function login(roles = ["USER"]) {
        TestBed.inject(AuthService).login("user@example.com", "password").subscribe();
        TestBed.inject(HttpTestingController)
            .expectOne("/api/v1/auth/login")
            .flush({
                accessToken:
                    "header." +
                    btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 900, roles })) +
                    ".signature",
                tokenType: "Bearer",
                expiresIn: 900,
            });
    }
    async function open(url: string) {
        const fixture = TestBed.createComponent(App);
        fixture.detectChanges();
        const router = TestBed.inject(Router);
        await router.navigateByUrl(url);
        await fixture.whenStable();
        fixture.detectChanges();
        return { fixture, router, element: fixture.nativeElement as HTMLElement };
    }

    it.each(["/home", "/tasks", "/users"])("requires login for %s", async (url) => {
        const { router, element } = await open(url);
        expect(router.url).toBe("/login?returnUrl=" + encodeURIComponent(url));
        expect(element.querySelector("h1")?.textContent).toBe("Welcome back");
        expect(element.querySelector('nav a[href="/tasks"]')).toBeNull();
    });

    it("redirects root and unknown routes to login for guests", async () => {
        const { router } = await open("/");
        expect(router.url).toContain("/login");
        await router.navigateByUrl("/missing/page");
        expect(router.url).toContain("/login");
    });

    it("navigates to tasks for a signed-in user", async () => {
        login();
        const { fixture, router, element } = await open("/home");
        element.querySelector<HTMLAnchorElement>('nav a[href="/tasks"]')!.click();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(router.url).toBe("/tasks");
        expect(element.querySelector("main h1")?.textContent).toBe("My tasks");
        expect(element.querySelector('nav a[aria-current="page"]')?.getAttribute("href")).toBe(
            "/tasks"
        );
    });

    it("blocks non-admin users and hides administration links", async () => {
        login();
        const { router, element } = await open("/users");
        expect(router.url).toBe("/home");
        expect(element.querySelector('a[href="/users"]')).toBeNull();
    });

    it("allows administrators to load the user preview", async () => {
        login(["ADMIN"]);
        const { router, element } = await open("/users");
        expect(router.url).toBe("/users");
        expect(element.querySelector("main h1")?.textContent).toBe("Users");
    });

    it.each(["/login", "/register"])("redirects signed-in users away from %s", async (url) => {
        login();
        expect((await open(url)).router.url).toBe("/home");
    });

    it("logs out from the shell and protects back navigation", async () => {
        login();
        const { fixture, router, element } = await open("/tasks");
        element.querySelector<HTMLButtonElement>("header button")!.click();
        await fixture.whenStable();
        expect(router.url).toBe("/login");
        await router.navigateByUrl("/tasks");
        expect(router.url).toContain("/login");
        expect(TestBed.inject(AuthService).accessToken()).toBeNull();
    });
});
