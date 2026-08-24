package com.ecommerce.userservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * An address as returned by the API.
 *
 * <p>Order Service reads this at checkout and <b>copies the values into its own
 * database</b> as an immutable order snapshot. That is why the response carries
 * the full address rather than a reference: once an order ships to "123 Main
 * Street", a later edit of this address must not rewrite delivery history.
 */
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
