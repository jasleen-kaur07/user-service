package com.ecommerce.userservice.client.dto;

import com.ecommerce.userservice.entity.UserType;

import java.util.UUID;


public record TokenIntrospectionResponse(
        boolean active,
        UUID userId,
        String email,
        UserType userType
) {

    public boolean isUsable() {
        return active && userId != null && userType != null;
    }
}
