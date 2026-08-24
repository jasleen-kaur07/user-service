package com.ecommerce.userservice.mapper;

import com.ecommerce.userservice.dto.AddressResponse;
import com.ecommerce.userservice.entity.Address;

import java.util.List;

public final class AddressMapper {

    private AddressMapper() {
    }

    public static AddressResponse toResponse(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getUserId(),
                address.getAddressLine1(),
                address.getAddressLine2(),
                address.getCity(),
                address.getState(),
                address.getCountry(),
                address.getPincode(),
                address.isDefault(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }

    public static List<AddressResponse> toResponses(List<Address> addresses) {
        return addresses.stream().map(AddressMapper::toResponse).toList();
    }
}
