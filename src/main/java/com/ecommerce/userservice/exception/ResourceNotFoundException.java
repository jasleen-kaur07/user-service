package com.ecommerce.userservice.exception;

import java.util.UUID;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static ResourceNotFoundException user(UUID userId) {
        return new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found: " + userId);
    }

    public static ResourceNotFoundException address(UUID addressId) {
        return new ResourceNotFoundException(ErrorCode.ADDRESS_NOT_FOUND, "Address not found: " + addressId);
    }

    public static ResourceNotFoundException merchantProfileById(UUID merchantId) {
        return new ResourceNotFoundException(ErrorCode.MERCHANT_PROFILE_NOT_FOUND,
                "Merchant profile not found for merchantId: " + merchantId);
    }

    public static ResourceNotFoundException merchantProfileByUser(UUID userId) {
        return new ResourceNotFoundException(ErrorCode.MERCHANT_PROFILE_NOT_FOUND,
                "Merchant profile not found for userId: " + userId);
    }
}
