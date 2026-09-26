package net.mbope.taskmanager.user.internal.presentation;

import jakarta.validation.Valid;

import java.util.UUID;

import net.mbope.taskmanager.user.Account;
import net.mbope.taskmanager.user.AccountAdministration;
import net.mbope.taskmanager.user.AccountPage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
class AccountAdministrationController {
    private final AccountAdministration administration;

    AccountAdministrationController(AccountAdministration administration) {
        this.administration = administration;
    }

    @GetMapping
    AccountPage list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return administration.list(page, size);
    }

    @PutMapping(path = "/{userId}/roles", consumes = MediaType.APPLICATION_JSON_VALUE)
    Account replaceRoles(@PathVariable UUID userId, @Valid @RequestBody ReplaceRolesRequest request) {
        return administration.replaceRoles(userId, request.uniqueRoles());
    }

    @PutMapping(path = "/{userId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    Account setEnabled(@PathVariable UUID userId, @Valid @RequestBody AccountStatusRequest request) {
        return administration.setEnabled(userId, request.enabled());
    }
}
