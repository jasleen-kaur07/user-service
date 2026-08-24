package com.ecommerce.userservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(

        @NotBlank(message = "addressLine1 is required")
        @Size(max = 255, message = "addressLine1 must not exceed 255 characters")
        String addressLine1,

        @Size(max = 255, message = "addressLine2 must not exceed 255 characters")
        String addressLine2,

        @NotBlank(message = "city is required")
        @Size(max = 100, message = "city must not exceed 100 characters")
        String city,

        @NotBlank(message = "state is required")
        @Size(max = 100, message = "state must not exceed 100 characters")
        String state,

        @NotBlank(message = "country is required")
        @Size(max = 100, message = "country must not exceed 100 characters")
        String country,

        @NotBlank(message = "pincode is required")
        @Pattern(regexp = ValidationPatterns.PINCODE,
                 message = "pincode must be 3-20 letters, digits, spaces or hyphens")
        String pincode,

        @JsonProperty("isDefault")
        Boolean isDefault
) {
}
