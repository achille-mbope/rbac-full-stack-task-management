import { TestBed } from "@angular/core/testing";
import { provideRouter, Router } from "@angular/router";
import { App } from "./app";
import { routes } from "./app.routes";

describe("Application routing", () => {
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [App],
            providers: [provideRouter(routes)],
        }).compileComponents();
    });

    async function open(url: string) {
        const fixture = TestBed.createComponent(App);
        fixture.detectChanges();
        const router = TestBed.inject(Router);
        await router.navigateByUrl(url);
        await fixture.whenStable();
        fixture.detectChanges();
        return { fixture, router, element: fixture.nativeElement as HTMLElement };
    }

    it("redirects the root to the overview inside the application shell", async () => {
        const { router, element } = await open("/");
        expect(router.url).toBe("/home");
        expect(element.querySelector("main h1")?.textContent).toContain("Keep your work in view.");
        expect(element.querySelector('nav a[aria-current="page"]')?.getAttribute("href")).toBe(
            "/home"
        );
    });

    it("navigates from the shell to the lazy task page", async () => {
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

    it("supports direct navigation to the users preview without loading account data", async () => {
        const { element } = await open("/users");
        expect(element.querySelector("main h1")?.textContent).toBe("Users");
        expect(element.querySelector("main")?.textContent).toContain(
            "This preview does not load accounts."
        );
    });

    it("redirects unknown paths to the overview", async () => {
        const { router, element } = await open("/missing/page");
        expect(router.url).toBe("/home");
        expect(element.querySelector("main h1")?.textContent).toContain("Keep your work in view.");
    });
});
