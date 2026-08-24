package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.MerchantProfileResponse;
import com.ecommerce.userservice.exception.ResourceNotFoundException;
import com.ecommerce.userservice.service.MerchantProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MerchantController.class)
class MerchantControllerTest {

    private static final UUID MERCHANT_ID =
            UUID.fromString("55555555-5555-5555-5555-555555555555");

    private static final UUID USER_ID =
            UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MerchantProfileService merchantProfileService;

    private static MerchantProfileResponse merchant() {
        return new MerchantProfileResponse(
                MERCHANT_ID,
                USER_ID,
                "EasyBuy",
                "easybuy@gmail.com",
                "9876543210",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    @Test
    @DisplayName("GET by merchantId returns the identity, with no identity headers required")
    void getsByMerchantId() throws Exception {

        when(merchantProfileService.getByMerchantId(MERCHANT_ID))
                .thenReturn(merchant());

        mockMvc.perform(
                        get("/api/merchants/{merchantId}", MERCHANT_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId")
                        .value(MERCHANT_ID.toString()))
                .andExpect(jsonPath("$.userId")
                        .value(USER_ID.toString()))
                .andExpect(jsonPath("$.businessName")
                        .value("EasyBuy"));
    }

    @Test
    @DisplayName("GET by userId returns the merchantId - the Merchant Service lookup")
    void getsByUserId() throws Exception {

        when(merchantProfileService.getByUserId(USER_ID))
                .thenReturn(merchant());

        mockMvc.perform(
                        get("/api/merchants/by-user/{userId}", USER_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId")
                        .value(MERCHANT_ID.toString()))
                .andExpect(jsonPath("$.businessName")
                        .value("EasyBuy"));
    }

    @Test
    @DisplayName("the response carries identity only - no stock, price or rating fields")
    void exposesIdentityOnly() throws Exception {

        when(merchantProfileService.getByMerchantId(MERCHANT_ID))
                .thenReturn(merchant());

        mockMvc.perform(
                        get("/api/merchants/{merchantId}", MERCHANT_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").doesNotExist())
                .andExpect(jsonPath("$.price").doesNotExist())
                .andExpect(jsonPath("$.rating").doesNotExist())
                .andExpect(jsonPath("$.products").doesNotExist());
    }

    @Test
    @DisplayName("an unknown merchantId is 404 MERCHANT_PROFILE_NOT_FOUND")
    void unknownMerchantId() throws Exception {

        when(merchantProfileService.getByMerchantId(MERCHANT_ID))
                .thenThrow(
                        ResourceNotFoundException.merchantProfileById(MERCHANT_ID)
                );

        mockMvc.perform(
                        get("/api/merchants/{merchantId}", MERCHANT_ID)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("MERCHANT_PROFILE_NOT_FOUND"));
    }

    @Test
    @DisplayName("a user with no merchant profile is 404 MERCHANT_PROFILE_NOT_FOUND")
    void userWithoutProfile() throws Exception {

        when(merchantProfileService.getByUserId(USER_ID))
                .thenThrow(
                        ResourceNotFoundException.merchantProfileByUser(USER_ID)
                );

        mockMvc.perform(
                        get("/api/merchants/by-user/{userId}", USER_ID)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("MERCHANT_PROFILE_NOT_FOUND"));
    }

    @Test
    @DisplayName("a malformed merchantId is 400")
    void malformedMerchantId() throws Exception {

        mockMvc.perform(
                        get("/api/merchants/{merchantId}", "not-a-uuid")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("BAD_REQUEST"));
    }
}