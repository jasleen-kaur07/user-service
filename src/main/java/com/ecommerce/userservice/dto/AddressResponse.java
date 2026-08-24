package com.ecommerce.userservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record AddressResponse(
        UUID id,
        UUID userId,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String country,
        String pincode,
        @JsonProperty("isDefault") boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {
}
