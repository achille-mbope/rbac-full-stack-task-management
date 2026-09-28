import { TestBed } from "@angular/core/testing";
import { HttpClient, provideHttpClient, withInterceptors } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";
import { provideRouter, Router } from "@angular/router";
import { vi } from "vitest";
import { AuthService, safeReturnUrl } from "./auth.service";
import { authInterceptor } from "./auth.interceptor";

export function tokenResponse(roles = ["USER"], seconds = 900) {
    return {
        accessToken:
            "header." +
            btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + seconds, roles }))
                .replace(/=/g, "")
                .replace(/\+/g, "-")
                .replace(/\//g, "_") +
            ".signature",
        tokenType: "Bearer",
        expiresIn: seconds,
    };
}

describe("Authentication session and HTTP boundary", () => {
    let auth: AuthService;
    let http: HttpClient;
    let requests: HttpTestingController;
    let navigate: ReturnType<typeof vi.spyOn>;
    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideRouter([]),
                provideHttpClient(withInterceptors([authInterceptor])),
                provideHttpClientTesting(),
            ],
        });
        auth = TestBed.inject(AuthService);
        http = TestBed.inject(HttpClient);
        requests = TestBed.inject(HttpTestingController);
        navigate = vi.spyOn(TestBed.inject(Router), "navigate").mockResolvedValue(true);
        vi.spyOn(TestBed.inject(Router), "navigateByUrl").mockResolvedValue(true);
    });
    afterEach(() => {
        requests.verify();
        TestBed.resetTestingModule();
        vi.useRealTimers();
        vi.restoreAllMocks();
    });
    function login(roles = ["USER"], seconds = 900) {
        const response = tokenResponse(roles, seconds);
        auth.login(" user@example.com ", " password with spaces ").subscribe();
        const request = requests.expectOne("/api/v1/auth/login");
        expect(request.request.body).toEqual({
            email: "user@example.com",
            password: " password with spaces ",
        });
        expect(request.request.headers.has("Authorization")).toBe(false);
        request.flush(response);
        return response.accessToken;
    }

    it("registers without establishing a session", () => {
        auth.register(" user@example.com ", " password with spaces ").subscribe();
        const request = requests.expectOne("/api/v1/auth/register");
        expect(request.request.body).toEqual({
            email: "user@example.com",
            password: " password with spaces ",
        });
        request.flush({ id: "account" }, { status: 201, statusText: "Created" });
        expect(auth.accessToken()).toBeNull();
    });

    it("adds the bearer token only to the same-origin API", () => {
        const token = login();
        for (const url of ["/api/v1/tasks", location.origin + "/api/v1/users"]) {
            http.get(url).subscribe();
            const request = requests.expectOne(url);
            expect(request.request.headers.get("Authorization")).toBe("Bearer " + token);
            request.flush([]);
        }
        for (const url of [
            "https://example.com/api/v1/tasks",
            "//example.com/api/v1/tasks",
            "/assets/config.json",
            "/api/v10/tasks",
            "/api/v1/../outside",
            "/api/v1/auth/login",
            "/api/v1/auth/register",
        ]) {
            http.get(url).subscribe();
            const request = requests.expectOne(url);
            expect(request.request.headers.has("Authorization")).toBe(false);
            request.flush({});
        }
    });

    it("expires automatically and redirects once", () => {
        vi.useFakeTimers();
        login(["USER"], 10);
        vi.advanceTimersByTime(10_000);
        expect(auth.accessToken()).toBeNull();
        expect(auth.signedIn()).toBe(false);
        expect(navigate).toHaveBeenCalledOnce();
    });

    it("checks wall-clock expiry even when the browser delays timers", () => {
        vi.useFakeTimers();
        login(["USER"], 10);
        vi.setSystemTime(Date.now() + 11_000);
        expect(auth.accessToken()).toBeNull();
        expect(navigate).toHaveBeenCalledOnce();
    });

    it("uses the earlier JWT expiry instead of extending it with expiresIn", () => {
        vi.useFakeTimers();
        auth.login("a@example.com", "password").subscribe();
        requests
            .expectOne("/api/v1/auth/login")
            .flush({ ...tokenResponse(["USER"], 5), expiresIn: 900 });
        vi.advanceTimersByTime(5_000);
        expect(auth.signedIn()).toBe(false);
    });

    it("clears a rejected session on 401 but retains it on 403", () => {
        login();
        http.get("/api/v1/users").subscribe({ error: () => {} });
        requests.expectOne("/api/v1/users").flush({}, { status: 403, statusText: "Forbidden" });
        expect(auth.signedIn()).toBe(true);
        http.get("/api/v1/tasks").subscribe({ error: () => {} });
        requests.expectOne("/api/v1/tasks").flush({}, { status: 401, statusText: "Unauthorized" });
        expect(auth.signedIn()).toBe(false);
        expect(navigate).toHaveBeenCalledOnce();
    });

    it("does not let an old request's 401 clear a newer session", () => {
        login();
        http.get("/api/v1/tasks").subscribe({ error: () => {} });
        const old = requests.expectOne("/api/v1/tasks");
        const current = login(["ADMIN"]);
        old.flush({}, { status: 401, statusText: "Unauthorized" });
        expect(auth.accessToken()).toBe(current);
        expect(auth.isAdmin()).toBe(true);
    });

    it("does not resurrect a session when a login completes after logout", () => {
        let failed = false;
        auth.login("a@example.com", "password").subscribe({ error: () => (failed = true) });
        const request = requests.expectOne("/api/v1/auth/login");
        auth.logout();
        request.flush(tokenResponse());
        expect(failed).toBe(true);
        expect(auth.signedIn()).toBe(false);
    });

    it("clears the token and expiry timer on logout", () => {
        vi.useFakeTimers();
        login();
        auth.logout();
        expect(auth.accessToken()).toBeNull();
        vi.advanceTimersByTime(900_000);
        expect(navigate).not.toHaveBeenCalled();
        expect(TestBed.inject(Router).navigateByUrl).toHaveBeenCalledWith("/login", {
            replaceUrl: true,
        });
    });

    it.each(["broken", "a." + btoa('{"exp":0}') + ".b", "a." + btoa("{}") + ".b"])(
        "rejects malformed or expired token %s",
        (accessToken) => {
            let failed = false;
            auth.login("a@example.com", "password").subscribe({ error: () => (failed = true) });
            requests
                .expectOne("/api/v1/auth/login")
                .flush({ accessToken, tokenType: "Bearer", expiresIn: 900 });
            expect(failed).toBe(true);
            expect(auth.signedIn()).toBe(false);
        }
    );

    it("starts without a session and limits return destinations to workspace routes", () => {
        expect(auth.accessToken()).toBeNull();
        for (const url of [
            "https://example.com",
            "//example.com",
            "/login",
            "/register",
            "/tasks(aux:evil)",
            null,
        ]) {
            expect(safeReturnUrl(url)).toBe("/home");
        }
        expect(safeReturnUrl("/tasks")).toBe("/tasks");
    });
});
