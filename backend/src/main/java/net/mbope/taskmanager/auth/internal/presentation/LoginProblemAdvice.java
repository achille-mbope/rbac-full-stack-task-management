package net.mbope.taskmanager.auth.internal.presentation;

import jakarta.servlet.http.HttpServletRequest;
import net.mbope.taskmanager.auth.internal.application.LoginFailedException;
import net.mbope.taskmanager.common.ApiProblems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class LoginProblemAdvice {
    @ExceptionHandler(LoginFailedException.class)
    ResponseEntity<ProblemDetail> rejectedLogin(HttpServletRequest request) {
        return ApiProblems.response(ApiProblems.problem(HttpStatus.UNAUTHORIZED, "unauthorized",
                "Invalid email or password.", request.getRequestURI()));
    }
}
