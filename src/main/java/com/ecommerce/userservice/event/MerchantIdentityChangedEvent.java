package com.ecommerce.userservice.event;

import java.util.UUID;

public record MerchantIdentityChangedEvent(
        UUID merchantId,
        UUID userId,
        String businessName,
        boolean newProfile
) {
}
