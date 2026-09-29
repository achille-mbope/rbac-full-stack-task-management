import { Component, DestroyRef, inject, signal } from "@angular/core";
import { HttpErrorResponse } from "@angular/common/http";
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from "@angular/forms";
import { ActivatedRoute, Router, RouterLink } from "@angular/router";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { finalize, Observable } from "rxjs";
import { AuthService, safeReturnUrl } from "../../auth/auth.service";

@Component({
    selector: "app-auth-page",
    imports: [ReactiveFormsModule, RouterLink],
    templateUrl: "./auth-page.html",
    styleUrl: "./auth-page.scss",
})
export class AuthPage {
    private readonly auth = inject(AuthService);
    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly destroyRef = inject(DestroyRef);
    readonly registering = this.route.snapshot.data["register"] === true;
    readonly returnUrl = safeReturnUrl(this.route.snapshot.queryParamMap.get("returnUrl"));
    readonly expired = this.route.snapshot.queryParamMap.get("reason") === "expired";
    readonly registered = this.route.snapshot.queryParamMap.get("registered") === "true";
    readonly busy = signal(false);
    readonly error = signal("");
    readonly form = new FormGroup({
        email: new FormControl("", {
            nonNullable: true,
            validators: [Validators.required, Validators.email, Validators.maxLength(254)],
        }),
        password: new FormControl("", { nonNullable: true, validators: [Validators.required] }),
    });

    submit() {
        if (this.busy()) return;
        this.error.set("");
        this.form.markAllAsTouched();
        if (this.form.invalid) return;
        const { email, password } = this.form.getRawValue();
        if (
            this.registering &&
            ([...password].length < 15 || new TextEncoder().encode(password).length > 72)
        ) {
            this.error.set(
                "Use at least 15 characters and at most 72 UTF-8 bytes for your password."
            );
            return;
        }
        this.busy.set(true);
        const request: Observable<unknown> = this.registering
            ? this.auth.register(email, password)
            : this.auth.login(email, password);
        request
            .pipe(
                takeUntilDestroyed(this.destroyRef),
                finalize(() => this.busy.set(false))
            )
            .subscribe({
                next: () => {
                    this.form.controls.password.reset();
                    if (this.registering) {
                        void this.router.navigate(["/login"], {
                            queryParams: { registered: true, returnUrl: this.returnUrl },
                            replaceUrl: true,
                        });
                    } else {
                        void this.router.navigateByUrl(this.returnUrl, { replaceUrl: true });
                    }
                },
                error: (error: unknown) => {
                    this.form.controls.password.reset();
                    let message = "Unable to sign in. Please try again.";
                    if (this.registering)
                        message = "Unable to create your account. Please try again.";
                    if (error instanceof HttpErrorResponse) {
                        switch (error.status) {
                            case 0:
                                message =
                                    "Cannot reach the server. Check your connection and try again.";
                                break;
                            case 400:
                                message = "Check your email and password requirements.";
                                break;
                            case 401:
                                message =
                                    "Email or password is incorrect, or this account is unavailable.";
                                break;
                            case 409:
                                message =
                                    "An account with this email already exists. Try signing in.";
                                break;
                            case 429:
                                message = "Too many attempts. Please wait before trying again.";
                                break;
                        }
                    }
                    this.error.set(message);
                },
            });
    }
}
