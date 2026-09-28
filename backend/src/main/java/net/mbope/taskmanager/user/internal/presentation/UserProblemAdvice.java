package net.mbope.taskmanager.user.internal.presentation;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

import net.mbope.taskmanager.common.ApiProblems;
import net.mbope.taskmanager.user.AccountNotFoundException;
import net.mbope.taskmanager.user.EmailAlreadyRegisteredException;
import net.mbope.taskmanager.user.UserValidationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class UserProblemAdvice {
    @ExceptionHandler(UserValidationException.class)
    ResponseEntity<ProblemDetail> invalid(UserValidationException failure, HttpServletRequest request) {
        return ApiProblems.response(ApiProblems.validation(request.getRequestURI(),
                List.of(new ApiProblems.FieldError(failure.field(), failure.code(), failure.getMessage()))));
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ResponseEntity<ProblemDetail> duplicate(HttpServletRequest request) {
        return ApiProblems.response(ApiProblems.problem(HttpStatus.CONFLICT, "email-conflict",
                "An account with this email already exists.", request.getRequestURI()));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ResponseEntity<ProblemDetail> missing(HttpServletRequest request) {
        return ApiProblems.response(ApiProblems.problem(HttpStatus.NOT_FOUND, "not-found",
                "Resource not found.", request.getRequestURI()));
    }
}
