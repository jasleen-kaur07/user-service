package com.ecommerce.userservice.service;

import com.ecommerce.userservice.dto.CreateMerchantProfileRequest;
import com.ecommerce.userservice.dto.MerchantProfileResponse;
import com.ecommerce.userservice.dto.UpdateMerchantProfileRequest;
import com.ecommerce.userservice.entity.MerchantProfile;
import com.ecommerce.userservice.event.MerchantIdentityChangedEvent;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.exception.ConflictException;
import com.ecommerce.userservice.exception.ResourceNotFoundException;
import com.ecommerce.userservice.mapper.MerchantProfileMapper;
import com.ecommerce.userservice.repository.MerchantProfileRepository;
import com.ecommerce.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Business logic for basic merchant identity.
 *
 * <p>This service mints the <b>merchantId</b> that the rest of the platform keys
 * its commerce data on. Everything it does not do is just as important: no stock,
 * no pricing, no offers, no ratings, no sales figures. Those belong to Merchant
 * Service, which discovers the merchantId through
 * {@code GET /api/merchants/by-user/{userId}} and then owns its own data against it.
 */
@Service
public class MerchantProfileService {

    private static final Logger log = LoggerFactory.getLogger(MerchantProfileService.class);

    private final MerchantProfileRepository merchantProfileRepository;
    private final UserRepository userRepository;

    /**
     * Used to hand the new merchantId to Merchant Service <i>after</i> this
     * transaction commits. Publishing an event rather than calling the Feign client
     * here keeps the remote call outside the database transaction - see
     * {@link com.ecommerce.userservice.integration.MerchantServiceNotifier}.
     */
    private final ApplicationEventPublisher eventPublisher;

    public MerchantProfileService(MerchantProfileRepository merchantProfileRepository,
                                  UserRepository userRepository,
                                  ApplicationEventPublisher eventPublisher) {
        this.merchantProfileRepository = merchantProfileRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates the merchant profile for a user, minting their merchantId.
     *
     * <p>Two rules, both enforced here rather than in SQL:
     * <ul>
     *   <li><b>MERCHANT only.</b> A CUSTOMER gets 409 {@code NOT_A_MERCHANT}. The
     *       user type comes from Auth Service and cannot be changed through the
     *       profile API, so a customer cannot self-promote in order to pass this
     *       check.</li>
     *   <li><b>At most one per user.</b> A second attempt gets 409
     *       {@code MERCHANT_PROFILE_ALREADY_EXISTS}. The
     *       {@code uq_merchant_profiles_user_id} constraint backs this up if two
     *       requests race.</li>
     * </ul>
     */
    @Transactional
    public MerchantProfileResponse createMerchantProfile(UUID userId, CreateMerchantProfileRequest request) {
        User user = requireUser(userId);

        if (!user.isMerchant()) {
            throw ConflictException.notAMerchant(userId);
        }
        if (merchantProfileRepository.existsByUserId(userId)) {
            throw ConflictException.merchantProfileExists(userId);
        }

        MerchantProfile profile = new MerchantProfile(userId, request.businessName().trim());
        profile.setBusinessEmail(normaliseEmail(request.businessEmail()));
        profile.setBusinessPhone(blankToNull(request.businessPhone()));

        // saveAndFlush so the generated merchantId and the timestamp columns are set
        // before mapping; the merchantId is the whole point of this response.
        MerchantProfile saved = merchantProfileRepository.saveAndFlush(profile);
        log.info("Minted merchantId {} for user {} ({})",
                saved.getMerchantId(), userId, saved.getBusinessName());

        // Delivered to Merchant Service only once this transaction commits, so a
        // Merchant Service outage can never prevent a merchant from onboarding.
        eventPublisher.publishEvent(new MerchantIdentityChangedEvent(
                saved.getMerchantId(), userId, saved.getBusinessName(), true));

        return MerchantProfileMapper.toResponse(saved);
    }

    /** Partial update of contact details. The merchantId and userId are immutable. */
    @Transactional
    public MerchantProfileResponse updateMerchantProfile(UUID userId, UpdateMerchantProfileRequest request) {
        MerchantProfile profile = merchantProfileRepository.findByUserId(userId)
                .orElseThrow(() -> ResourceNotFoundException.merchantProfileByUser(userId));

        String previousBusinessName = profile.getBusinessName();
        if (request.businessName() != null) {
            profile.setBusinessName(request.businessName().trim());
        }
        if (request.businessEmail() != null) {
            profile.setBusinessEmail(normaliseEmail(request.businessEmail()));
        }
        if (request.businessPhone() != null) {
            profile.setBusinessPhone(blankToNull(request.businessPhone()));
        }

        // Flush so that @UpdateTimestamp has fired before we map the response.
        MerchantProfile saved = merchantProfileRepository.saveAndFlush(profile);
        log.debug("Updated merchant profile for user {}", userId);

        // Merchant Service holds a copy of the display name, so a rename has to reach
        // it too - otherwise "sold by ..." goes stale. Contact-detail-only edits raise
        // nothing, because Merchant Service does not hold those.
        if (!Objects.equals(previousBusinessName, saved.getBusinessName())) {
            eventPublisher.publishEvent(new MerchantIdentityChangedEvent(
                    saved.getMerchantId(), userId, saved.getBusinessName(), false));
        }

        return MerchantProfileMapper.toResponse(saved);
    }

    /**
     * Looks a merchant up by merchantId. This is what Order Service calls when it
     * has a merchantId on an order line and needs the business name to show.
     */
    @Transactional(readOnly = true)
    public MerchantProfileResponse getByMerchantId(UUID merchantId) {
        return merchantProfileRepository.findById(merchantId)
                .map(MerchantProfileMapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.merchantProfileById(merchantId));
    }

    /**
     * Looks a merchant up by userId. This is the call Merchant Service makes: it
     * knows who is logged in (a userId from the JWT) and needs the merchantId to
     * attach stock and pricing to.
     */
    @Transactional(readOnly = true)
    public MerchantProfileResponse getByUserId(UUID userId) {
        requireUser(userId);
        return merchantProfileRepository.findByUserId(userId)
                .map(MerchantProfileMapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.merchantProfileByUser(userId));
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.user(userId));
    }

    private static String normaliseEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim().toLowerCase();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
