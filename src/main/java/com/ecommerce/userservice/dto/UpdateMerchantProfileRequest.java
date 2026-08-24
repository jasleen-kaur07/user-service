package com.ecommerce.userservice.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


public record UpdateMerchantProfileRequest(

        @Size(max = 255, message = "businessName must not exceed 255 characters")
        String businessName,

        @Email(message = "businessEmail must be a valid email address")
        @Size(max = 255, message = "businessEmail must not exceed 255 characters")
        String businessEmail,

        @Pattern(regexp = ValidationPatterns.PHONE,
                 message = "businessPhone must be 7-15 digits, optionally prefixed with +")
        String businessPhone
) {

    @AssertTrue(message = "at least one of businessName, businessEmail or businessPhone must be provided")
    public boolean hasAtLeastOneField() {
        return businessName != null || businessEmail != null || businessPhone != null;
    }
}
