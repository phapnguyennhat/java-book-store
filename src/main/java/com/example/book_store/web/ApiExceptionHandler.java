package com.example.book_store.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Problem details in the request's language (messages*.properties): title by status ({@code status.<code>}),
 * detail by Spring's {@code problemDetail.<exception class>} codes, plus an {@code errors} map on validation failures.
 */
@Slf4j
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final String VALIDATION = "validation"; // code for any field-level validation failure

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        ex.getBody().setProperty(ApiErrors.CODE, VALIDATION);
        ex.getBody().setProperty("errors", fieldErrors(ex.getBindingResult()));
        return super.handleMethodArgumentNotValid(ex, headers, status, request);
    }

    /** Validation run by a service (not by @Valid on a request body): same 400 body with an errors map. */
    @ExceptionHandler(BindException.class)
    ResponseEntity<Object> handleBind(BindException ex, WebRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail body = createProblemDetail(ex, status, "Invalid request content.",
                ErrorResponse.getDefaultDetailMessageCode(MethodArgumentNotValidException.class, null), null, request);
        body.setProperty(ApiErrors.CODE, VALIDATION);
        body.setProperty("errors", fieldErrors(ex.getBindingResult()));
        return createResponseEntity(body, new HttpHeaders(), status, request);
    }

    private Map<String, String> fieldErrors(BindingResult result) {
        return result.getFieldErrors().stream().collect(Collectors.toMap(
                // codes like NotBlank.password -> NotBlank, falling back to the validator's English default
                FieldError::getField, e -> getMessageSource().getMessage(e, LocaleContextHolder.getLocale()),
                (a, b) -> a, TreeMap::new));
    }

    /** Anything unexpected: logged with its stack trace, answered without internals. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ProblemDetail body = createProblemDetail(ex, status, "Internal server error", "error.internal", null, request);
        body.setProperty(ApiErrors.CODE, "error.internal");
        return createResponseEntity(body, new HttpHeaders(), status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode status,
                                                          WebRequest request) {
        if (body instanceof ProblemDetail pd) {
            pd.setTitle(getMessageSource().getMessage("status." + status.value(), null, pd.getTitle(),
                    LocaleContextHolder.getLocale()));
        }
        return super.createResponseEntity(body, headers, status, request);
    }
}
