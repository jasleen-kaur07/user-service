package com.ecommerce.userservice.service;

import com.ecommerce.userservice.dto.AddressRequest;
import com.ecommerce.userservice.dto.AddressResponse;
import com.ecommerce.userservice.dto.UpdateAddressRequest;
import com.ecommerce.userservice.entity.Address;
import com.ecommerce.userservice.exception.ResourceNotFoundException;
import com.ecommerce.userservice.mapper.AddressMapper;
import com.ecommerce.userservice.repository.AddressRepository;
import com.ecommerce.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AddressService {

    private static final Logger log =
            LoggerFactory.getLogger(AddressService.class);

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public AddressService(
            AddressRepository addressRepository,
            UserRepository userRepository) {

        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AddressResponse addAddress(
            UUID userId,
            AddressRequest request) {

        requireUserExists(userId);

        boolean isFirstAddress =
                addressRepository.countByUserId(userId) == 0;

        boolean shouldBeDefault =
                Boolean.TRUE.equals(request.isDefault())
                        || isFirstAddress;

        if (shouldBeDefault) {
            addressRepository.clearDefaultForUser(
                    userId,
                    Instant.now()
            );
        }

        Address address = new Address(userId);

        address.setAddressLine1(request.addressLine1());
        address.setAddressLine2(request.addressLine2());
        address.setCity(request.city());
        address.setState(request.state());
        address.setCountry(request.country());
        address.setPincode(request.pincode());
        address.setDefault(shouldBeDefault);

        Address saved =
                addressRepository.saveAndFlush(address);

        log.info(
                "Added address {} for user {} (default={})",
                saved.getId(),
                userId,
                shouldBeDefault
        );

        return AddressMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(
            UUID userId) {

        requireUserExists(userId);

        return AddressMapper.toResponses(
                addressRepository
                        .findByUserIdOrderByIsDefaultDescCreatedAtAsc(userId)
        );
    }

    @Transactional(readOnly = true)
    public AddressResponse getDefaultAddress(
            UUID userId) {

        requireUserExists(userId);

        return addressRepository
                .findByUserIdOrderByIsDefaultDescCreatedAtAsc(userId)
                .stream()
                .findFirst()
                .map(AddressMapper::toResponse)
                .orElseThrow(
                        () -> ResourceNotFoundException.user(userId)
                );
    }

    @Transactional(readOnly = true)
    public AddressResponse getAddress(
            UUID userId,
            UUID addressId) {

        requireUserExists(userId);

        return AddressMapper.toResponse(
                requireOwnedAddress(userId, addressId)
        );
    }

    @Transactional
    public AddressResponse updateAddress(
            UUID userId,
            UUID addressId,
            UpdateAddressRequest request) {

        requireUserExists(userId);

        Address address =
                requireOwnedAddress(userId, addressId);

        if (request.addressLine1() != null) {
            address.setAddressLine1(
                    request.addressLine1()
            );
        }

        if (request.addressLine2() != null) {
            address.setAddressLine2(
                    blankToNull(request.addressLine2())
            );
        }

        if (request.city() != null) {
            address.setCity(
                    request.city()
            );
        }

        if (request.state() != null) {
            address.setState(
                    request.state()
            );
        }

        if (request.country() != null) {
            address.setCountry(
                    request.country()
            );
        }

        if (request.pincode() != null) {
            address.setPincode(
                    request.pincode()
            );
        }

        Address saved =
                addressRepository.saveAndFlush(address);

        log.debug(
                "Updated address {} for user {}",
                addressId,
                userId
        );

        return AddressMapper.toResponse(saved);
    }

    @Transactional
    public AddressResponse setDefaultAddress(
            UUID userId,
            UUID addressId) {

        requireUserExists(userId);

        Address address =
                requireOwnedAddress(userId, addressId);

        if (address.isDefault()) {
            return AddressMapper.toResponse(address);
        }

        addressRepository.clearDefaultForUser(
                userId,
                Instant.now()
        );

        Address reloaded =
                requireOwnedAddress(userId, addressId);

        reloaded.setDefault(true);

        Address saved =
                addressRepository.saveAndFlush(reloaded);

        log.info(
                "Address {} is now the default for user {}",
                addressId,
                userId
        );

        return AddressMapper.toResponse(saved);
    }

    @Transactional
    public void deleteAddress(
            UUID userId,
            UUID addressId) {

        requireUserExists(userId);

        Address address =
                requireOwnedAddress(userId, addressId);

        boolean wasDefault =
                address.isDefault();

        addressRepository.delete(address);
        addressRepository.flush();

        if (wasDefault) {

            addressRepository
                    .findByUserIdOrderByIsDefaultDescCreatedAtAsc(userId)
                    .stream()
                    .findFirst()
                    .ifPresent(next -> {

                        next.setDefault(true);

                        addressRepository.saveAndFlush(next);

                        log.info(
                                "Promoted address {} to default for user {} after deleting {}",
                                next.getId(),
                                userId,
                                addressId
                        );
                    });
        }

        log.info(
                "Deleted address {} for user {}",
                addressId,
                userId
        );
    }

    private void requireUserExists(UUID userId) {

        if (!userRepository.existsById(userId)) {
            throw ResourceNotFoundException.user(userId);
        }
    }

    private Address requireOwnedAddress(
            UUID userId,
            UUID addressId) {

        return addressRepository
                .findByIdAndUserId(addressId, userId)
                .orElseThrow(
                        () -> ResourceNotFoundException.address(addressId)
                );
    }

    private static String blankToNull(String value) {

        String trimmed = value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }
}