import { Routes } from "@angular/router";
import { adminGuard, authGuard, guestGuard } from "./auth/auth.guards";

export const routes: Routes = [
    { path: "", redirectTo: "/home", pathMatch: "full" },
    {
        path: "login",
        title: "Sign in | Task Manager",
        canActivate: [guestGuard],
        loadComponent: () => import("./pages/auth/auth-page").then((m) => m.AuthPage),
    },
    {
        path: "register",
        title: "Create account | Task Manager",
        canActivate: [guestGuard],
        data: { register: true },
        loadComponent: () => import("./pages/auth/auth-page").then((m) => m.AuthPage),
    },
    {
        path: "home",
        title: "Overview | Task Manager",
        canActivate: [authGuard],
        loadComponent: () => import("./pages/home/home").then((m) => m.Home),
    },
    {
        path: "tasks",
        title: "My tasks | Task Manager",
        canActivate: [authGuard],
        loadComponent: () => import("./pages/tasks/tasks").then((m) => m.Tasks),
    },
    {
        path: "users",
        title: "Users | Task Manager",
        canActivate: [authGuard, adminGuard],
        loadComponent: () => import("./pages/users/users").then((m) => m.Users),
    },
    { path: "**", redirectTo: "/home" },
];
