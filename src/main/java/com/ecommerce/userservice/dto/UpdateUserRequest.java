package com.ecommerce.userservice.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


public record UpdateUserRequest(

        @Size(max = 100, message = "firstName must not exceed 100 characters")
        String firstName,

        @Size(max = 100, message = "lastName must not exceed 100 characters")
        String lastName,

        @Pattern(regexp = ValidationPatterns.PHONE,
                 message = "phone must be 7-15 digits, optionally prefixed with +")
        String phone
) {


    @AssertTrue(message = "at least one of firstName, lastName or phone must be provided")
    public boolean hasAtLeastOneField() {
        return firstName != null || lastName != null || phone != null;
    }
}
