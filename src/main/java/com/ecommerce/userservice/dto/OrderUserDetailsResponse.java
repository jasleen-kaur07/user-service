package com.ecommerce.userservice.dto;

import java.util.UUID;

public record OrderUserDetailsResponse(
        UUID userId,
        String email,
        AddressResponse address
) {
}