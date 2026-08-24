package com.ecommerce.userservice.mapper;

import com.ecommerce.userservice.dto.MerchantProfileResponse;
import com.ecommerce.userservice.entity.MerchantProfile;

public final class MerchantProfileMapper {

    private MerchantProfileMapper() {
    }

    public static MerchantProfileResponse toResponse(MerchantProfile profile) {
        return new MerchantProfileResponse(
                profile.getMerchantId(),
                profile.getUserId(),
                profile.getBusinessName(),
                profile.getBusinessEmail(),
                profile.getBusinessPhone(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
