package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.MerchantProfileResponse;
import com.ecommerce.userservice.exception.ConflictException;
import com.ecommerce.userservice.service.MerchantProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc slice test for merchant onboarding. */
@WebMvcTest(MerchantProfileController.class)
class MerchantProfileControllerTest {

    private static final UUID MERCHANT_USER_ID =
            UUID.fromString("99999999-9999-9999-9999-999999999999");

    private static final UUID CUSTOMER_USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID MERCHANT_ID =
            UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MerchantProfileService merchantProfileService;

    private static MerchantProfileResponse merchant() {
        return new MerchantProfileResponse(
                MERCHANT_ID,
                MERCHANT_USER_ID,
                "EasyBuy",
                "easybuy@gmail.com",
                "9876543210",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    @Test
    @DisplayName("POST mints a merchantId and returns 201")
    void createsProfile() throws Exception {

        when(merchantProfileService.createMerchantProfile(
                eq(MERCHANT_USER_ID),
                any()
        )).thenReturn(merchant());

        mockMvc.perform(
                        post(
                                "/api/users/{userId}/merchant-profile",
                                MERCHANT_USER_ID
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"businessName":"EasyBuy",
                                         "businessEmail":"easybuy@gmail.com",
                                         "businessPhone":"9876543210"}""")
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.merchantId")
                        .value(MERCHANT_ID.toString()))
                .andExpect(jsonPath("$.businessName")
                        .value("EasyBuy"));
    }

    @Test
    @DisplayName("a CUSTOMER creating a merchant profile is 409 NOT_A_MERCHANT")
    void customerCannotCreateProfile() throws Exception {

        when(merchantProfileService.createMerchantProfile(
                eq(CUSTOMER_USER_ID),
                any()
        )).thenThrow(
                ConflictException.notAMerchant(CUSTOMER_USER_ID)
        );

        mockMvc.perform(
                        post(
                                "/api/users/{userId}/merchant-profile",
                                CUSTOMER_USER_ID
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"businessName\":\"Sneaky Shop\"}")
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("NOT_A_MERCHANT"));
    }

    @Test
    @DisplayName("a second profile for the same user is 409 MERCHANT_PROFILE_ALREADY_EXISTS")
    void duplicateProfile() throws Exception {

        when(merchantProfileService.createMerchantProfile(
                eq(MERCHANT_USER_ID),
                any()
        )).thenThrow(
                ConflictException.merchantProfileExists(MERCHANT_USER_ID)
        );

        mockMvc.perform(
                        post(
                                "/api/users/{userId}/merchant-profile",
                                MERCHANT_USER_ID
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"businessName\":\"EasyBuy Again\"}")
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("MERCHANT_PROFILE_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("businessName is required")
    void requiresBusinessName() throws Exception {

        mockMvc.perform(
                        post(
                                "/api/users/{userId}/merchant-profile",
                                MERCHANT_USER_ID
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"businessEmail\":\"easybuy@gmail.com\"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("businessName"));

        verify(
                merchantProfileService,
                never()
        ).createMerchantProfile(any(), any());
    }

    @Test
    @DisplayName("an invalid business email is rejected when supplied")
    void validatesBusinessEmail() throws Exception {

        mockMvc.perform(
                        post(
                                "/api/users/{userId}/merchant-profile",
                                MERCHANT_USER_ID
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"businessName":"EasyBuy",
                                         "businessEmail":"nope"}""")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("businessEmail"));
    }

    @Test
    @DisplayName("PATCH updates contact details")
    void updatesProfile() throws Exception {

        when(merchantProfileService.updateMerchantProfile(
                eq(MERCHANT_USER_ID),
                any()
        )).thenReturn(merchant());

        mockMvc.perform(
                        patch(
                                "/api/users/{userId}/merchant-profile",
                                MERCHANT_USER_ID
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"businessPhone\":\"9998887776\"}"
                                )
                )
                .andExpect(status().isOk());
    }
}