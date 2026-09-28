package net.mbope.taskmanager.common.internal.presentation;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

import java.util.List;

import net.mbope.taskmanager.common.ApiProblems.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

final class ValidationErrors {
    private ValidationErrors() {
    }

    static List<FieldError> from(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException validation) {
            return bindingErrors(validation);
        }
        return List.of(requestError(exception));
    }

    private static List<FieldError> bindingErrors(MethodArgumentNotValidException validation) {
        var errors = validation.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), "invalid", "Invalid request field."))
                .toList();
        return errors.isEmpty() ? List.of(new FieldError("$", "invalid", "Invalid request.")) : errors;
    }

    private static FieldError requestError(Exception exception) {
        String field = exception instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : "$";
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnrecognizedPropertyException unknown) {
                return new FieldError(unknown.getPropertyName(), "unknown_field", "Invalid request field.");
            }
            if (cause instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
                String name = mapping.getPath().getFirst().getFieldName();
                if (name != null) {
                    field = name;
                }
            }
        }
        return new FieldError(field, "invalid", "Invalid request field.");
    }
}
