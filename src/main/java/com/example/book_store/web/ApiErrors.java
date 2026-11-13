package com.example.book_store.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public final class ApiErrors {
    private ApiErrors() {
    }

    /** Body property with a stable, untranslated error code that clients can branch on (detail is for humans). */
    public static final String CODE = "code";

    /**
     * Problem-detail error whose detail is translated from {@code code} in messages*.properties ({@code args} fill
     * {0}, {1}...); {@code fallback} is used if the key is missing. The code itself is also sent as {@link #CODE}.
     */
    public static ErrorResponseException of(HttpStatus status, String code, String fallback, Object... args) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, fallback);
        body.setProperty(CODE, code);
        return new ErrorResponseException(status, body, null, code, args.length == 0 ? null : args);
    }
}
