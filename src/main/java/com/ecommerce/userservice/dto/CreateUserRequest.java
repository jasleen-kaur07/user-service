package com.ecommerce.userservice.dto;

import com.ecommerce.userservice.entity.UserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;


public record CreateUserRequest(

        @NotNull(message = "userId is required")
        UUID userId,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 255, message = "email must not exceed 255 characters")
        String email,

        @NotNull(message = "role is required and must be CUSTOMER or MERCHANT")
        UserType role
) {
}