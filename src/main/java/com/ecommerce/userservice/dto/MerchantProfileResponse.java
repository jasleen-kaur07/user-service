package com.ecommerce.userservice.dto;

import java.time.Instant;
import java.util.UUID;

public record MerchantProfileResponse(
        UUID merchantId,
        UUID userId,
        String businessName,
        String businessEmail,
        String businessPhone,
        Instant createdAt,
        Instant updatedAt
) {
}
