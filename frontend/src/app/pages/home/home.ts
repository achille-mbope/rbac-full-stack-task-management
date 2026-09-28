import { Component, inject } from "@angular/core";
import { RouterLink } from "@angular/router";

import { AuthService } from "../../auth/auth.service";

@Component({
    selector: "app-home",
    imports: [RouterLink],
    templateUrl: "./home.html",
    styleUrl: "./home.scss",
})
export class Home {
    readonly auth = inject(AuthService);
}
