package com.ecommerce.userservice.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAddressRequest(

        @Size(max = 255, message = "addressLine1 must not exceed 255 characters")
        String addressLine1,

        @Size(max = 255, message = "addressLine2 must not exceed 255 characters")
        String addressLine2,

        @Size(max = 100, message = "city must not exceed 100 characters")
        String city,

        @Size(max = 100, message = "state must not exceed 100 characters")
        String state,

        @Size(max = 100, message = "country must not exceed 100 characters")
        String country,

        @Pattern(regexp = ValidationPatterns.PINCODE,
                 message = "pincode must be 3-20 letters, digits, spaces or hyphens")
        String pincode
) {

    @AssertTrue(message = "at least one address field must be provided")
    public boolean hasAtLeastOneField() {
        return addressLine1 != null || addressLine2 != null || city != null
                || state != null || country != null || pincode != null;
    }
}
