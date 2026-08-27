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

@Service
public class MerchantProfileService {

    private static final Logger log = LoggerFactory.getLogger(MerchantProfileService.class);

    private final MerchantProfileRepository merchantProfileRepository;
    private final UserRepository userRepository;

    private final ApplicationEventPublisher eventPublisher;

    public MerchantProfileService(MerchantProfileRepository merchantProfileRepository,
                                  UserRepository userRepository,
                                  ApplicationEventPublisher eventPublisher) {
        this.merchantProfileRepository = merchantProfileRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

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

        MerchantProfile saved = merchantProfileRepository.saveAndFlush(profile);
        log.info("Minted merchantId {} for user {} ({})",
                saved.getMerchantId(), userId, saved.getBusinessName());

        eventPublisher.publishEvent(new MerchantIdentityChangedEvent(
                saved.getMerchantId(), userId, saved.getBusinessName(), true));

        return MerchantProfileMapper.toResponse(saved);
    }

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

        MerchantProfile saved = merchantProfileRepository.saveAndFlush(profile);
        log.debug("Updated merchant profile for user {}", userId);

        if (!Objects.equals(previousBusinessName, saved.getBusinessName())) {
            eventPublisher.publishEvent(new MerchantIdentityChangedEvent(
                    saved.getMerchantId(), userId, saved.getBusinessName(), false));
        }

        return MerchantProfileMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public MerchantProfileResponse getByMerchantId(UUID merchantId) {
        return merchantProfileRepository.findById(merchantId)
                .map(MerchantProfileMapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.merchantProfileById(merchantId));
    }

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
