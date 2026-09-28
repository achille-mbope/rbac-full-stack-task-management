package net.mbope.taskmanager.user.internal.presentation;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;

import net.mbope.taskmanager.user.Account;
import net.mbope.taskmanager.user.AccountRegistration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class RegistrationController {
    private final AccountRegistration registration;

    RegistrationController(AccountRegistration registration) {
        this.registration = registration;
    }

    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "register")
    @ApiResponse(responseCode = "201", useReturnTypeSchema = true)
    ResponseEntity<Account> register(@RequestBody RegisterRequest request) {
        Account account = registration.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }
}
