package com.ecommerce.userservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    BAD_REQUEST(HttpStatus.BAD_REQUEST),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND),
    MERCHANT_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND),

    EMAIL_ALREADY_IN_USE(HttpStatus.CONFLICT),

    USER_IDENTITY_MISMATCH(HttpStatus.CONFLICT),
    MERCHANT_PROFILE_ALREADY_EXISTS(HttpStatus.CONFLICT),

    NOT_A_MERCHANT(HttpStatus.CONFLICT),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    UPSTREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
