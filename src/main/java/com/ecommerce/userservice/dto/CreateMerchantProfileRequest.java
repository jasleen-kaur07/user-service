package com.ecommerce.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating a merchant profile, i.e. for minting a merchantId.
 *
 * <p>Only {@code businessName} is mandatory: it is the name shown next to the
 * offer on the product page ("EasyBuy", "Telesoft"). Business email and phone are
 * optional contact details, validated only when present.
 *
 * <p>There is no field here for stock, price, rating or product count. Those live
 * in Merchant Service, which will look this merchantId up over REST and attach its
 * own commerce data to it.
 */
public record CreateMerchantProfileRequest(

        @NotBlank(message = "businessName is required")
        @Size(max = 255, message = "businessName must not exceed 255 characters")
        String businessName,

        @Email(message = "businessEmail must be a valid email address")
        @Size(max = 255, message = "businessEmail must not exceed 255 characters")
        String businessEmail,

        @Pattern(regexp = ValidationPatterns.PHONE,
                 message = "businessPhone must be 7-15 digits, optionally prefixed with +")
        String businessPhone
) {
}
