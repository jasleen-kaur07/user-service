package com.ecommerce.userservice.dto;

public final class ValidationPatterns {

    public static final String PHONE = "^\\+?[0-9]{7,15}$";

    public static final String PINCODE = "^[A-Za-z0-9][A-Za-z0-9 -]{2,19}$";

    private ValidationPatterns() {
    }
}
