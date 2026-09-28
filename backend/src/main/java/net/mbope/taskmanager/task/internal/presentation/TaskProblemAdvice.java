package net.mbope.taskmanager.task.internal.presentation;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import net.mbope.taskmanager.common.ApiProblems;
import net.mbope.taskmanager.task.AssigneeDisabledException;
import net.mbope.taskmanager.task.AssigneeNotFoundException;
import net.mbope.taskmanager.task.TaskNotFoundException;
import net.mbope.taskmanager.task.TaskValidationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class TaskProblemAdvice {
    @ExceptionHandler(TaskValidationException.class)
    ResponseEntity<ProblemDetail> invalid(TaskValidationException failure, HttpServletRequest request) {
        return ApiProblems.response(ApiProblems.validation(request.getRequestURI(),
                List.of(new ApiProblems.FieldError(failure.field(), failure.code(), failure.getMessage()))));
    }

    @ExceptionHandler({TaskNotFoundException.class, AssigneeNotFoundException.class})
    ResponseEntity<ProblemDetail> missing(HttpServletRequest request) {
        return ApiProblems.response(ApiProblems.problem(HttpStatus.NOT_FOUND, "not-found",
                "Resource not found.", request.getRequestURI()));
    }

    @ExceptionHandler(AssigneeDisabledException.class)
    ResponseEntity<ProblemDetail> disabled(HttpServletRequest request) {
        return ApiProblems.response(ApiProblems.problem(HttpStatus.CONFLICT, "assignee-disabled",
                "The selected account is disabled.", request.getRequestURI()));
    }
}
