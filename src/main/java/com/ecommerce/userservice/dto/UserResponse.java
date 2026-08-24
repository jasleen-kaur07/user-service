package com.ecommerce.userservice.dto;

import com.ecommerce.userservice.entity.UserType;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phone,
        UserType userType,
        Instant createdAt,
        Instant updatedAt
) {
}
