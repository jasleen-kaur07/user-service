package com.ecommerce.userservice.service;

import com.ecommerce.userservice.dto.CreateMerchantProfileRequest;
import com.ecommerce.userservice.dto.MerchantProfileResponse;
import com.ecommerce.userservice.dto.UpdateMerchantProfileRequest;
import com.ecommerce.userservice.entity.MerchantProfile;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.entity.UserType;
import com.ecommerce.userservice.exception.ConflictException;
import com.ecommerce.userservice.exception.ErrorCode;
import com.ecommerce.userservice.exception.ResourceNotFoundException;
import com.ecommerce.userservice.repository.MerchantProfileRepository;
import com.ecommerce.userservice.event.MerchantIdentityChangedEvent;
import com.ecommerce.userservice.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantProfileServiceTest {

    private static final UUID MERCHANT_USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID CUSTOMER_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MERCHANT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Mock
    private MerchantProfileRepository merchantProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MerchantProfileService merchantProfileService;

    private static CreateMerchantProfileRequest request() {
        return new CreateMerchantProfileRequest("EasyBuy", "EasyBuy@Gmail.com", "9876543210");
    }

    @Test
    @DisplayName("creates a merchant profile for a MERCHANT user and mints the merchantId")
    void createsProfileForMerchant() {
        when(userRepository.findById(MERCHANT_USER_ID))
                .thenReturn(Optional.of(new User(MERCHANT_USER_ID, "easybuy@gmail.com", UserType.MERCHANT)));
        when(merchantProfileRepository.existsByUserId(MERCHANT_USER_ID)).thenReturn(false);
        when(merchantProfileRepository.saveAndFlush(any(MerchantProfile.class)))
                .thenAnswer(call -> call.getArgument(0));

        MerchantProfileResponse response = merchantProfileService
                .createMerchantProfile(MERCHANT_USER_ID, request());

        assertThat(response.userId()).isEqualTo(MERCHANT_USER_ID);
        assertThat(response.businessName()).isEqualTo("EasyBuy");

        ArgumentCaptor<MerchantProfile> saved = ArgumentCaptor.forClass(MerchantProfile.class);
        verify(merchantProfileRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getBusinessEmail())
                .as("business email is normalised like any other email")
                .isEqualTo("easybuy@gmail.com");
    }

    @Test
    @DisplayName("a CUSTOMER cannot create a merchant profile")
    void customerCannotCreateProfile() {
        when(userRepository.findById(CUSTOMER_USER_ID))
                .thenReturn(Optional.of(new User(CUSTOMER_USER_ID, "jasleen@gmail.com", UserType.CUSTOMER)));

        assertThatThrownBy(() -> merchantProfileService.createMerchantProfile(CUSTOMER_USER_ID, request()))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_A_MERCHANT);

        verify(merchantProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a user may own at most one merchant profile")
    void rejectsDuplicateProfile() {
        when(userRepository.findById(MERCHANT_USER_ID))
                .thenReturn(Optional.of(new User(MERCHANT_USER_ID, "easybuy@gmail.com", UserType.MERCHANT)));
        when(merchantProfileRepository.existsByUserId(MERCHANT_USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> merchantProfileService.createMerchantProfile(MERCHANT_USER_ID, request()))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MERCHANT_PROFILE_ALREADY_EXISTS);

        verify(merchantProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("cannot create a profile for a user who does not exist")
    void rejectsUnknownUser() {
        when(userRepository.findById(MERCHANT_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantProfileService.createMerchantProfile(MERCHANT_USER_ID, request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ResourceNotFoundException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("publishes the merchantId and name for Merchant Service after creating a profile")
    void publishesIdentityEventOnCreate() {
        when(userRepository.findById(MERCHANT_USER_ID))
                .thenReturn(Optional.of(new User(MERCHANT_USER_ID, "easybuy@gmail.com", UserType.MERCHANT)));
        when(merchantProfileRepository.existsByUserId(MERCHANT_USER_ID)).thenReturn(false);
        when(merchantProfileRepository.saveAndFlush(any(MerchantProfile.class)))
                .thenAnswer(call -> call.getArgument(0));

        merchantProfileService.createMerchantProfile(MERCHANT_USER_ID, request());

        ArgumentCaptor<MerchantIdentityChangedEvent> published =
                ArgumentCaptor.forClass(MerchantIdentityChangedEvent.class);
        verify(eventPublisher).publishEvent(published.capture());

        assertThat(published.getValue().userId()).isEqualTo(MERCHANT_USER_ID);
        assertThat(published.getValue().businessName()).isEqualTo("EasyBuy");
        assertThat(published.getValue().newProfile()).isTrue();
    }

    @Test
    @DisplayName("publishes nothing when creation is rejected, so Merchant Service never hears about it")
    void publishesNothingWhenRejected() {
        when(userRepository.findById(CUSTOMER_USER_ID))
                .thenReturn(Optional.of(new User(CUSTOMER_USER_ID, "jasleen@gmail.com", UserType.CUSTOMER)));

        assertThatThrownBy(() -> merchantProfileService.createMerchantProfile(CUSTOMER_USER_ID, request()))
                .isInstanceOf(ConflictException.class);

        verify(eventPublisher, never()).publishEvent(any(MerchantIdentityChangedEvent.class));
    }

    @Test
    @DisplayName("a rename republishes, because Merchant Service holds a copy of the display name")
    void renamePublishesAgain() {
        MerchantProfile profile = new MerchantProfile(MERCHANT_USER_ID, "EasyBuy");
        when(merchantProfileRepository.findByUserId(MERCHANT_USER_ID)).thenReturn(Optional.of(profile));
        when(merchantProfileRepository.saveAndFlush(any(MerchantProfile.class)))
                .thenAnswer(call -> call.getArgument(0));

        merchantProfileService.updateMerchantProfile(MERCHANT_USER_ID,
                new UpdateMerchantProfileRequest("EasyBuy Retail", null, null));

        ArgumentCaptor<MerchantIdentityChangedEvent> published =
                ArgumentCaptor.forClass(MerchantIdentityChangedEvent.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue().businessName()).isEqualTo("EasyBuy Retail");
        assertThat(published.getValue().newProfile()).isFalse();
    }

    @Test
    @DisplayName("a contact-detail-only edit publishes nothing - Merchant Service does not hold those")
    void contactOnlyEditPublishesNothing() {
        MerchantProfile profile = new MerchantProfile(MERCHANT_USER_ID, "EasyBuy");
        when(merchantProfileRepository.findByUserId(MERCHANT_USER_ID)).thenReturn(Optional.of(profile));
        when(merchantProfileRepository.saveAndFlush(any(MerchantProfile.class)))
                .thenAnswer(call -> call.getArgument(0));

        merchantProfileService.updateMerchantProfile(MERCHANT_USER_ID,
                new UpdateMerchantProfileRequest(null, null, "9998887776"));

        verify(eventPublisher, never()).publishEvent(any(MerchantIdentityChangedEvent.class));
    }

    @Test
    @DisplayName("looks a merchant up by merchantId")
    void getsByMerchantId() {
        when(merchantProfileRepository.findById(MERCHANT_ID))
                .thenReturn(Optional.of(new MerchantProfile(MERCHANT_USER_ID, "EasyBuy")));

        MerchantProfileResponse response = merchantProfileService.getByMerchantId(MERCHANT_ID);

        assertThat(response.businessName()).isEqualTo("EasyBuy");
        assertThat(response.userId()).isEqualTo(MERCHANT_USER_ID);
    }

    @Test
    @DisplayName("unknown merchantId yields MERCHANT_PROFILE_NOT_FOUND")
    void unknownMerchantId() {
        when(merchantProfileRepository.findById(MERCHANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantProfileService.getByMerchantId(MERCHANT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ResourceNotFoundException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MERCHANT_PROFILE_NOT_FOUND);
    }

    @Test
    @DisplayName("looks a merchant up by userId - the call Merchant Service makes")
    void getsByUserId() {
        when(userRepository.findById(MERCHANT_USER_ID))
                .thenReturn(Optional.of(new User(MERCHANT_USER_ID, "easybuy@gmail.com", UserType.MERCHANT)));
        when(merchantProfileRepository.findByUserId(MERCHANT_USER_ID))
                .thenReturn(Optional.of(new MerchantProfile(MERCHANT_USER_ID, "EasyBuy")));

        assertThat(merchantProfileService.getByUserId(MERCHANT_USER_ID).businessName()).isEqualTo("EasyBuy");
    }

    @Test
    @DisplayName("a user who exists but has not onboarded yields MERCHANT_PROFILE_NOT_FOUND, not USER_NOT_FOUND")
    void existingUserWithoutProfile() {
        when(userRepository.findById(MERCHANT_USER_ID))
                .thenReturn(Optional.of(new User(MERCHANT_USER_ID, "easybuy@gmail.com", UserType.MERCHANT)));
        when(merchantProfileRepository.findByUserId(MERCHANT_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantProfileService.getByUserId(MERCHANT_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ResourceNotFoundException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MERCHANT_PROFILE_NOT_FOUND);
    }

    @Test
    @DisplayName("updates only the supplied contact fields")
    void updatesProfile() {
        MerchantProfile profile = new MerchantProfile(MERCHANT_USER_ID, "EasyBuy");
        profile.setBusinessPhone("9876543210");
        when(merchantProfileRepository.findByUserId(MERCHANT_USER_ID)).thenReturn(Optional.of(profile));
        when(merchantProfileRepository.saveAndFlush(any(MerchantProfile.class)))
                .thenAnswer(call -> call.getArgument(0));

        merchantProfileService.updateMerchantProfile(MERCHANT_USER_ID,
                new UpdateMerchantProfileRequest(null, null, "9998887776"));

        assertThat(profile.getBusinessPhone()).isEqualTo("9998887776");
        assertThat(profile.getBusinessName()).isEqualTo("EasyBuy");   // untouched
    }
}
