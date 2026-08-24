package com.ecommerce.userservice.exception;

public abstract class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    protected ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
