import { computed, DestroyRef, inject, Injectable, signal } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Router } from "@angular/router";
import { defer, tap } from "rxjs";

interface TokenResponse {
    accessToken: string;
    tokenType: string;
    expiresIn: number;
}
interface Session {
    token: string;
    expiresAt: number;
    roles: string[];
}

export function safeReturnUrl(value: string | null): string {
    return value && ["/home", "/tasks", "/users"].includes(value) ? value : "/home";
}

@Injectable({ providedIn: "root" })
export class AuthService {
    private readonly http = inject(HttpClient);
    private readonly router = inject(Router);
    private readonly session = signal<Session | null>(null);
    private timer?: ReturnType<typeof setTimeout>;
    private revision = 0;
    readonly signedIn = computed(() => this.session() !== null);
    readonly isAdmin = computed(() => this.session()?.roles.includes("ADMIN") ?? false);

    constructor() {
        inject(DestroyRef).onDestroy(() => this.clear());
    }

    register(email: string, password: string) {
        return this.http.post<void>("/api/v1/auth/register", { email: email.trim(), password });
    }

    login(email: string, password: string) {
        return defer(() => {
            this.clear();
            const revision = this.revision;
            return this.http
                .post<TokenResponse>("/api/v1/auth/login", { email: email.trim(), password })
                .pipe(
                    tap((response) => {
                        // A response from an earlier login must not restore a logged-out session.
                        if (revision !== this.revision) throw new Error("Sign-in was cancelled.");
                        const parts = response.accessToken.split(".");
                        if (
                            parts.length !== 3 ||
                            response.tokenType !== "Bearer" ||
                            !Number.isFinite(response.expiresIn) ||
                            response.expiresIn <= 0
                        ) {
                            throw new Error("Invalid sign-in response.");
                        }
                        const payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
                        const claims = JSON.parse(atob(payload)) as {
                            exp?: number;
                            roles?: unknown;
                        };
                        if (typeof claims.exp !== "number" || !Number.isFinite(claims.exp)) {
                            throw new Error("Invalid token expiry.");
                        }
                        const expiresAt = Math.min(
                            claims.exp * 1000,
                            Date.now() + response.expiresIn * 1000
                        );
                        if (expiresAt <= Date.now())
                            throw new Error("The token has already expired.");
                        const roles = Array.isArray(claims.roles)
                            ? claims.roles.filter(
                                  (role): role is string => typeof role === "string"
                              )
                            : [];
                        // Claims support UI decisions only; the backend verifies the JWT and authorization.
                        this.session.set({ token: response.accessToken, expiresAt, roles });
                        this.scheduleExpiry();
                    })
                );
        });
    }

    accessToken(): string | null {
        const session = this.session();
        if (session && session.expiresAt <= Date.now()) {
            this.expire(session.token);
            return null;
        }
        return session?.token ?? null;
    }

    expire(token: string) {
        if (this.session()?.token !== token) return;
        const returnUrl = safeReturnUrl(this.router.url);
        this.clear();
        void this.router.navigate(["/login"], {
            queryParams: { reason: "expired", returnUrl },
            replaceUrl: true,
        });
    }

    logout() {
        this.clear();
        void this.router.navigateByUrl("/login", { replaceUrl: true });
    }

    private scheduleExpiry() {
        const session = this.session();
        if (!session) return;
        this.timer = setTimeout(
            () => {
                if (Date.now() >= session.expiresAt) this.expire(session.token);
                else this.scheduleExpiry();
            },
            Math.min(Math.max(0, session.expiresAt - Date.now()), 2_147_483_647)
        );
    }

    private clear() {
        clearTimeout(this.timer);
        this.session.set(null);
        this.revision++;
    }
}
