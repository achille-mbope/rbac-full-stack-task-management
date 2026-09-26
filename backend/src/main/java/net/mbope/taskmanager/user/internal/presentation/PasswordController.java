package net.mbope.taskmanager.user.internal.presentation;

import net.mbope.taskmanager.user.UserCredentials;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
class PasswordController {
    private final UserCredentials credentials;

    PasswordController(UserCredentials credentials) {
        this.credentials = credentials;
    }

    @PutMapping(path = "/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        credentials.changePassword(request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
