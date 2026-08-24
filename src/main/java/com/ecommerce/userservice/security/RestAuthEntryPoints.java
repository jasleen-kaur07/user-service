package com.ecommerce.userservice.security;

import com.ecommerce.userservice.dto.ErrorResponse;
import com.ecommerce.userservice.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

public final class RestAuthEntryPoints {

    private RestAuthEntryPoints() {
    }

    public static AuthenticationEntryPoint unauthorized(ObjectMapper objectMapper) {
        return (request, response, authException) -> write(objectMapper, request, response,
                ErrorCode.UNAUTHORIZED,
                "Authentication is required. Supply either the X-User-Id and X-User-Type headers "
                        + "injected by the API Gateway, or an Authorization: Bearer token that Auth Service "
                        + "recognises.");
    }

    public static AccessDeniedHandler forbidden(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) -> write(objectMapper, request, response,
                ErrorCode.FORBIDDEN, "You are not allowed to access this resource");
    }

    public static void write(ObjectMapper objectMapper,
                             HttpServletRequest request,
                             HttpServletResponse response,
                             ErrorCode code,
                             String message) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(
                code.status().value(), code.name(), message, request.getRequestURI()));
    }
}
