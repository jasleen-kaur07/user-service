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

/**
 * Business logic for user addresses, including the single-default rule.
 *
 * <p>Every method takes the {@code userId} as well as the {@code addressId} and
 * loads through {@code findByIdAndUserId}. Ownership is therefore enforced by the
 * query, not by a comparison afterwards that a future edit might drop.
 */
@Service
public class AddressService {

    private static final Logger log = LoggerFactory.getLogger(AddressService.class);

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public AddressService(AddressRepository addressRepository, UserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    /**
     * Adds an address.
     *
     * <p>Two rules combine here:
     * <ul>
     *   <li>if the caller asks for {@code isDefault = true}, the previous default is
     *       demoted first, in the same transaction;</li>
     *   <li>a user's <b>first</b> address becomes the default automatically,
     *       whatever the flag says, so that checkout always has an address to
     *       preselect. A user with addresses but no default is a state nothing
     *       downstream wants to handle.</li>
     * </ul>
     */
    @Transactional
    public AddressResponse addAddress(UUID userId, AddressRequest request) {
        requireUserExists(userId);

        boolean isFirstAddress = addressRepository.countByUserId(userId) == 0;
        boolean shouldBeDefault = Boolean.TRUE.equals(request.isDefault()) || isFirstAddress;

        if (shouldBeDefault) {
            addressRepository.clearDefaultForUser(userId, Instant.now());
        }

        Address address = new Address(userId);
        address.setAddressLine1(request.addressLine1());
        address.setAddressLine2(request.addressLine2());
        address.setCity(request.city());
        address.setState(request.state());
        address.setCountry(request.country());
        address.setPincode(request.pincode());
        address.setDefault(shouldBeDefault);

        // saveAndFlush so that the generated id and the @CreationTimestamp columns are
        // populated before we map the response. A plain save() only queues the insert,
        // and the caller would receive createdAt/updatedAt as null.
        Address saved = addressRepository.saveAndFlush(address);
        log.info("Added address {} for user {} (default={})", saved.getId(), userId, shouldBeDefault);
        return AddressMapper.toResponse(saved);
    }

    /** Default address first, then oldest first. */
    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(UUID userId) {
        requireUserExists(userId);
        return AddressMapper.toResponses(
                addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtAsc(userId));
    }

    @Transactional(readOnly = true)
    public AddressResponse getAddress(UUID userId, UUID addressId) {
        requireUserExists(userId);
        return AddressMapper.toResponse(requireOwnedAddress(userId, addressId));
    }

    /** Partial update. {@code isDefault} is not touched here - that has its own endpoint. */
    @Transactional
    public AddressResponse updateAddress(UUID userId, UUID addressId, UpdateAddressRequest request) {
        requireUserExists(userId);
        Address address = requireOwnedAddress(userId, addressId);

        if (request.addressLine1() != null) {
            address.setAddressLine1(request.addressLine1());
        }
        if (request.addressLine2() != null) {
            address.setAddressLine2(blankToNull(request.addressLine2()));
        }
        if (request.city() != null) {
            address.setCity(request.city());
        }
        if (request.state() != null) {
            address.setState(request.state());
        }
        if (request.country() != null) {
            address.setCountry(request.country());
        }
        if (request.pincode() != null) {
            address.setPincode(request.pincode());
        }

        // Flush so that @UpdateTimestamp has fired before we map the response.
        Address saved = addressRepository.saveAndFlush(address);
        log.debug("Updated address {} for user {}", addressId, userId);
        return AddressMapper.toResponse(saved);
    }

    /**
     * Promotes an address to be the user's default.
     *
     * <p>Demote-then-promote inside one transaction. The order matters: the partial
     * unique index would reject the promotion if the old default were still set, so
     * doing it the other way round would fail. Because both statements share a
     * transaction, a crash between them cannot leave the user with zero or two
     * defaults.
     *
     * <p>Already-default is treated as success rather than an error: setting the
     * default twice is a harmless, idempotent request.
     */
    @Transactional
    public AddressResponse setDefaultAddress(UUID userId, UUID addressId) {
        requireUserExists(userId);
        Address address = requireOwnedAddress(userId, addressId);

        if (address.isDefault()) {
            return AddressMapper.toResponse(address);
        }

        addressRepository.clearDefaultForUser(userId, Instant.now());

        // clearDefaultForUser is a bulk update with clearAutomatically = true, which
        // detaches everything in the persistence context. Re-read so we hold a
        // managed instance again before mutating it.
        Address reloaded = requireOwnedAddress(userId, addressId);
        reloaded.setDefault(true);
        Address saved = addressRepository.saveAndFlush(reloaded);

        log.info("Address {} is now the default for user {}", addressId, userId);
        return AddressMapper.toResponse(saved);
    }

    /**
     * Deletes an address.
     *
     * <p>If the deleted address was the default and the user still has others, the
     * oldest remaining address is promoted. This keeps the "a user with addresses
     * has a default" invariant that {@link #addAddress} establishes; without it,
     * deleting your default would leave checkout with nothing preselected.
     */
    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        requireUserExists(userId);
        Address address = requireOwnedAddress(userId, addressId);
        boolean wasDefault = address.isDefault();

        addressRepository.delete(address);
        addressRepository.flush();

        if (wasDefault) {
            addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtAsc(userId).stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setDefault(true);
                        addressRepository.saveAndFlush(next);
                        log.info("Promoted address {} to default for user {} after deleting {}",
                                next.getId(), userId, addressId);
                    });
        }

        log.info("Deleted address {} for user {}", addressId, userId);
    }

    /**
     * A request for an address under a user who does not exist is a 404 about the
     * user, not about the address - it tells the caller which part of the path is
     * wrong.
     */
    private void requireUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw ResourceNotFoundException.user(userId);
        }
    }

    /**
     * Loads an address that belongs to this user, or 404.
     *
     * <p>An address that exists but belongs to someone else also yields 404, not
     * 403: a 403 would confirm the id is real, which is more than an unrelated
     * caller should learn.
     */
    private Address requireOwnedAddress(UUID userId, UUID addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> ResourceNotFoundException.address(addressId));
    }

    private static String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
