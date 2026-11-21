package io.github.rkaushikdev.staplewright.api;

import com.fasterxml.jackson.databind.JsonMappingException;
import io.github.rkaushikdev.staplewright.Catalogue;
import io.github.rkaushikdev.staplewright.StaplewrightError;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Every refusal turned into a response in one place.
 *
 * <p>A refusal's status comes off the refusal catalogue rather than a map declared here, and its
 * body is the same wherever it came from: the code, the message, and the facts that produced it. A
 * request the service cannot read never reaches a route and is answered 422 with a {@code detail}
 * list naming where each problem is ({@code ["body", "tag"]}) and what it is.
 */
@RestControllerAdvice
public class Errors {

    /** The status a refusal is answered with. */
    public static int statusFor(StaplewrightError error) {
        return Catalogue.statusFor(error);
    }

    @ExceptionHandler(StaplewrightError.class)
    public ResponseEntity<Map<String, Object>> refused(StaplewrightError error) {
        return ResponseEntity.status(statusFor(error)).body(error.asDict());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException failure) {
        List<Object> where = new ArrayList<>(List.of("body"));
        String why = "the body could not be read";
        if (failure.getCause() instanceof JsonMappingException mapping) {
            mapping.getPath()
                    .forEach(step -> where.add(step.getFieldName() != null ? step.getFieldName() : step.getIndex()));
            why = mapping.getOriginalMessage();
        }
        return unprocessable(List.of(problem(where, why, "invalid")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException failure) {
        List<Map<String, Object>> problems = new ArrayList<>();
        for (FieldError error : failure.getBindingResult().getFieldErrors()) {
            problems.add(problem(List.of("body", error.getField()), error.getDefaultMessage(), "invalid"));
        }
        return unprocessable(problems);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> missing(MissingServletRequestParameterException failure) {
        return unprocessable(
                List.of(problem(List.of("query", failure.getParameterName()), "Field required", "missing")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> mismatched(MethodArgumentTypeMismatchException failure) {
        String root = failure.getParameter().hasParameterAnnotation(PathVariable.class) ? "path" : "query";
        Throwable cause = failure.getMostSpecificCause();
        String why = cause instanceof StaplewrightError refusal ? refusal.message() : "the value could not be read";
        return unprocessable(List.of(problem(List.of(root, failure.getName()), why, "invalid")));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> outOfRange(HandlerMethodValidationException failure) {
        List<Map<String, Object>> problems = new ArrayList<>();
        failure.getParameterValidationResults().forEach(result -> result.getResolvableErrors()
                .forEach(error -> problems.add(problem(
                        List.of(
                                "query",
                                String.valueOf(result.getMethodParameter().getParameterName())),
                        error.getDefaultMessage(),
                        "invalid"))));
        return unprocessable(problems);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> violated(ConstraintViolationException failure) {
        List<Map<String, Object>> problems = new ArrayList<>();
        failure.getConstraintViolations()
                .forEach(violation -> problems.add(problem(
                        List.of("query", String.valueOf(violation.getPropertyPath())),
                        violation.getMessage(),
                        "invalid")));
        return unprocessable(problems);
    }

    private static Map<String, Object> problem(List<Object> where, String why, String type) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", type);
        out.put("loc", List.copyOf(where));
        out.put("msg", why);
        return out;
    }

    private static ResponseEntity<Map<String, Object>> unprocessable(List<Map<String, Object>> problems) {
        return ResponseEntity.unprocessableEntity().body(Map.of("detail", List.copyOf(problems)));
    }
}
