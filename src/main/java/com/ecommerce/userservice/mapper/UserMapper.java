package com.ecommerce.userservice.mapper;

import com.ecommerce.userservice.dto.UserResponse;
import com.ecommerce.userservice.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getUserType(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
