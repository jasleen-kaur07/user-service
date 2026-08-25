package com.ecommerce.userservice.exception;

import com.ecommerce.userservice.dto.ErrorResponse;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(
            ApiException ex,
            HttpServletRequest request) {

        ErrorCode code = ex.getErrorCode();
        log.debug("Handled {} at {}: {}", code, request.getRequestURI(), ex.getMessage());

        return build(code, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        List<ErrorResponse.FieldError> fieldErrors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(error -> new ErrorResponse.FieldError(
                                error.getField(),
                                error.getDefaultMessage()))
                        .toList();

        ErrorCode code = ErrorCode.VALIDATION_ERROR;

        return ResponseEntity.status(code.status()).body(
                ErrorResponse.of(
                        code.status().value(),
                        code.name(),
                        "Request validation failed",
                        request.getRequestURI(),
                        fieldErrors
                )
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        List<ErrorResponse.FieldError> fieldErrors =
                ex.getConstraintViolations().stream()
                        .map(violation -> new ErrorResponse.FieldError(
                                violation.getPropertyPath().toString(),
                                violation.getMessage()))
                        .toList();

        ErrorCode code = ErrorCode.VALIDATION_ERROR;

        return ResponseEntity.status(code.status()).body(
                ErrorResponse.of(
                        code.status().value(),
                        code.name(),
                        "Request validation failed",
                        request.getRequestURI(),
                        fieldErrors
                )
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        log.debug(
                "Unreadable request body at {}: {}",
                request.getRequestURI(),
                ex.getMessage()
        );

        return build(
                ErrorCode.BAD_REQUEST,
                "Request body is malformed or contains an invalid value. "
                        + "Check that role is CUSTOMER or MERCHANT and that all ids are valid UUIDs.",
                request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        return build(
                ErrorCode.BAD_REQUEST,
                "Parameter '" + ex.getName() + "' is not a valid "
                        + (ex.getRequiredType() != null
                        ? ex.getRequiredType().getSimpleName()
                        : "value"),
                request
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        log.warn(
                "Database constraint violated at {}",
                request.getRequestURI(),
                ex
        );

        return build(
                ErrorCode.BAD_REQUEST,
                "The request conflicts with a database constraint. If you were setting a default "
                        + "address, another request may have changed it concurrently; please retry.",
                request
        );
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ErrorResponse> handleUpstreamFailure(
            FeignException ex,
            HttpServletRequest request) {

        log.error(
                "Upstream service call failed while handling {} {} (status {})",
                request.getMethod(),
                request.getRequestURI(),
                ex.status(),
                ex
        );

        return build(
                ErrorCode.UPSTREAM_UNAVAILABLE,
                "A service this request depends on is currently unavailable. Please retry shortly.",
                request
        );
    }

    @ExceptionHandler({
            NoResourceFoundException.class,
            HttpRequestMethodNotSupportedException.class
    })
    public ResponseEntity<ErrorResponse> handleNoHandler(
            Exception ex,
            HttpServletRequest request) {

        return build(
                ErrorCode.BAD_REQUEST,
                "No endpoint " + request.getMethod() + " " + request.getRequestURI(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request) {

        log.error(
                "Unhandled exception at {} {}",
                request.getMethod(),
                request.getRequestURI(),
                ex
        );

        return build(
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred",
                request
        );
    }

    private ResponseEntity<ErrorResponse> build(
            ErrorCode code,
            String message,
            HttpServletRequest request) {

        HttpStatus status = code.status();

        return ResponseEntity.status(status)
                .body(
                        ErrorResponse.of(
                                status.value(),
                                code.name(),
                                message,
                                request.getRequestURI()
                        )
                );
    }
}