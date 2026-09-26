package net.mbope.taskmanager.common.internal.presentation;

import net.mbope.taskmanager.common.ApiProblems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class HttpProblemAdvice extends ResponseEntityExceptionHandler {
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
                                                             HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpStatus httpStatus = HttpStatus.valueOf(status.value());
        ProblemDetail problem = httpStatus == HttpStatus.BAD_REQUEST
                ? ApiProblems.validation(path(request), ValidationErrors.from(exception))
                : httpProblem(httpStatus, path(request));
        var responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(problem, responseHeaders, status);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> forbidden(WebRequest request) {
        return ApiProblems.response(ApiProblems.problem(HttpStatus.FORBIDDEN, "forbidden",
                "Access is denied.", path(request)));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> unauthorized(WebRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ApiProblems.problem(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication is required.", path(request)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(WebRequest request) {
        return ApiProblems.response(httpProblem(HttpStatus.INTERNAL_SERVER_ERROR, path(request)));
    }

    private ProblemDetail httpProblem(HttpStatus status, String path) {
        return switch (status) {
            case NOT_FOUND -> ApiProblems.problem(status, "not-found", "Resource not found.", path);
            case METHOD_NOT_ALLOWED -> ApiProblems.problem(status, "method-not-allowed",
                    "HTTP method is not supported for this resource.", path);
            case UNSUPPORTED_MEDIA_TYPE -> ApiProblems.problem(status, "unsupported-media-type",
                    "Request body must use a supported media type.", path);
            case NOT_ACCEPTABLE -> ApiProblems.problem(status, "not-acceptable",
                    "Requested response media type is not supported.", path);
            default -> ApiProblems.problem(status, "internal-error", "An unexpected error occurred.", path);
        };
    }

    private String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }
}
