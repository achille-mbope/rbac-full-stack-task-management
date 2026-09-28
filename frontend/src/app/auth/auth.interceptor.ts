import { DOCUMENT } from "@angular/common";
import { HttpErrorResponse, HttpInterceptorFn } from "@angular/common/http";
import { inject } from "@angular/core";
import { catchError, throwError } from "rxjs";
import { AuthService } from "./auth.service";

export const authInterceptor: HttpInterceptorFn = (request, next) => {
    const document = inject(DOCUMENT);
    const origin = document.location.origin;
    const url = new URL(request.url, document.baseURI);
    const isApi = url.origin === origin && url.pathname.startsWith("/api/v1/");
    const isPublic = ["/api/v1/auth/login", "/api/v1/auth/register"].includes(url.pathname);
    if (!isApi || isPublic) return next(request);
    const auth = inject(AuthService);
    const token = auth.accessToken();
    const authorized = token
        ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
        : request;
    return next(authorized).pipe(
        catchError((error: unknown) => {
            if (token && error instanceof HttpErrorResponse && error.status === 401)
                auth.expire(token);
            return throwError(() => error);
        })
    );
};
