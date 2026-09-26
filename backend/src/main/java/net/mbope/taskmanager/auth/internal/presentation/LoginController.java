package net.mbope.taskmanager.auth.internal.presentation;

import net.mbope.taskmanager.auth.internal.application.LoginService;
import net.mbope.taskmanager.auth.internal.application.TokenResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class LoginController {
    private final LoginService loginService;

    LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        TokenResponse token = loginService.login(request.email(), request.password());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(token);
    }
}
