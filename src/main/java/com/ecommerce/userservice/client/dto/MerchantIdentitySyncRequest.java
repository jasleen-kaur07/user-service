package com.ecommerce.userservice.client.dto;

import java.util.UUID;


public record MerchantIdentitySyncRequest(
        UUID merchantId,
        UUID userId,
        String businessName
) {
}
