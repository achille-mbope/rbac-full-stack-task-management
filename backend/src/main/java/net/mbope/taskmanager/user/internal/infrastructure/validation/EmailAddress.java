package net.mbope.taskmanager.user.internal.infrastructure.validation;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

import net.mbope.taskmanager.user.UserValidationException;
import net.mbope.taskmanager.user.internal.application.port.EmailNormalizer;
import org.springframework.stereotype.Component;

@Component
final class EmailAddress implements EmailNormalizer {
    private final Validator validator;

    EmailAddress(Validator validator) {
        this.validator = validator;
    }

    public String normalize(String email) {
        String normalized = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        if (!validator.validate(new Address(normalized)).isEmpty()) {
            throw new UserValidationException("email", "invalid", "A valid email address of at most 254 characters is required.");
        }
        return normalized;
    }

    private record Address(@NotBlank @Email @Size(max = 254) String value) {
    }
}
