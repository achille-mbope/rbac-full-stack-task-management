import { inject } from "@angular/core";
import { CanActivateFn, Router } from "@angular/router";
import { AuthService, safeReturnUrl } from "./auth.service";

export const authGuard: CanActivateFn = (_route, state) => {
    return inject(AuthService).accessToken()
        ? true
        : inject(Router).createUrlTree(["/login"], {
              queryParams: { returnUrl: safeReturnUrl(state.url) },
          });
};

export const adminGuard: CanActivateFn = () => {
    const auth = inject(AuthService);
    return auth.accessToken() && auth.isAdmin() ? true : inject(Router).parseUrl("/home");
};

export const guestGuard: CanActivateFn = () =>
    inject(AuthService).accessToken() ? inject(Router).parseUrl("/home") : true;
