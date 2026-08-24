package com.ecommerce.userservice.security;

import com.ecommerce.userservice.entity.UserType;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String email, UserType userType) {

    public boolean is(UUID candidateUserId) {
        return userId.equals(candidateUserId);
    }
}
