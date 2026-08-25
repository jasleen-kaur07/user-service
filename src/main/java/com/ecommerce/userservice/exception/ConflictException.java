package com.ecommerce.userservice.exception;

import java.util.UUID;

public class ConflictException extends ApiException {

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static ConflictException emailInUse(String email) {
        return new ConflictException(ErrorCode.EMAIL_ALREADY_IN_USE,
                "Email is already registered to a different user: " + email);
    }

    public static ConflictException identityMismatch(UUID userId) {
        return new ConflictException(ErrorCode.USER_IDENTITY_MISMATCH,
                "A user with id " + userId + " already exists with different identity details. "
                        + "Auth Service must not change a user's email or role through the sync endpoint.");
    }

    public static ConflictException merchantProfileExists(UUID userId) {
        return new ConflictException(ErrorCode.MERCHANT_PROFILE_ALREADY_EXISTS,
                "User " + userId + " already has a merchant profile. A user may own at most one.");
    }

    public static ConflictException notAMerchant(UUID userId) {
        return new ConflictException(ErrorCode.NOT_A_MERCHANT,
                "User " + userId + " is not a MERCHANT and therefore cannot own a merchant profile.");
    }
}