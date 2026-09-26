package net.mbope.taskmanager.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Shared HTTP error representation. Details must never contain rejected values or secrets.
 */
public final class ApiProblems {
    private ApiProblems() {
    }

    public record FieldError(String field, String code, String message) {
    }

    public static ProblemDetail problem(HttpStatus status, String code, String detail, String path) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:task-manager:problem:" + code));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(path));
        return problem;
    }

    public static ProblemDetail validation(String path, List<FieldError> errors) {
        var problem = problem(HttpStatus.BAD_REQUEST, "validation", "Request validation failed.", path);
        problem.setProperty("errors", errors);
        return problem;
    }

    public static ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    public static void write(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
                             HttpStatus status, String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), problem(status, code, detail, request.getRequestURI()));
    }
}
