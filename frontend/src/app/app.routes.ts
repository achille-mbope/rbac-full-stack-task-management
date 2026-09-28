import { Routes } from "@angular/router";

export const routes: Routes = [
    { path: "", redirectTo: "/home", pathMatch: "full" },
    {
        path: "home",
        title: "Overview | Task Manager",
        loadComponent: () => import("./pages/home/home").then((m) => m.Home),
    },
    {
        path: "tasks",
        title: "My tasks | Task Manager",
        loadComponent: () => import("./pages/tasks/tasks").then((m) => m.Tasks),
    },
    {
        path: "users",
        title: "Users | Task Manager",
        loadComponent: () => import("./pages/users/users").then((m) => m.Users),
    },
    {
        path: "**",
        redirectTo: "/home",
    },
];
