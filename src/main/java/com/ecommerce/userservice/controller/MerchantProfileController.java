package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.CreateMerchantProfileRequest;
import com.ecommerce.userservice.dto.ErrorResponse;
import com.ecommerce.userservice.dto.MerchantProfileResponse;
import com.ecommerce.userservice.dto.UpdateMerchantProfileRequest;
import com.ecommerce.userservice.service.MerchantProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users/{userId}/merchant-profile")
@Tag(name = "Merchant onboarding",
        description = "A merchant user creating and editing their own basic identity. This is where a merchantId is minted.")
public class MerchantProfileController {

    private final MerchantProfileService merchantProfileService;

    public MerchantProfileController(MerchantProfileService merchantProfileService) {
        this.merchantProfileService = merchantProfileService;
    }

    @PostMapping
    @Operation(
            summary = "Create your merchant profile (mints the merchantId)",
            description = """
                    Creates the user's basic merchant identity and returns the newly minted
                    merchantId. Merchant Service then keys its stock, pricing and offers on
                    that id.

                    Two business rules:
                    * only a user whose userType is MERCHANT may have a profile - a CUSTOMER gets
                      409 NOT_A_MERCHANT;
                    * one profile per user - a second attempt gets 409
                      MERCHANT_PROFILE_ALREADY_EXISTS.

                    Only businessName is required. Nothing about products, stock, price or rating
                    is accepted here; that data belongs to Merchant Service.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Profile created; response carries the merchantId"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR - businessName missing, or bad email/phone",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "NOT_A_MERCHANT or MERCHANT_PROFILE_ALREADY_EXISTS",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MerchantProfileResponse> createMerchantProfile(
            @PathVariable UUID userId,
            @Valid @RequestBody CreateMerchantProfileRequest request) {

        MerchantProfileResponse created =
                merchantProfileService.createMerchantProfile(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping
    @Operation(
            summary = "Update your merchant contact details",
            description = """
                    Partial update of businessName, businessEmail and businessPhone.

                    The merchantId and userId cannot change - they are absent from the request
                    schema, so a merchant can neither renumber themselves nor re-point their
                    profile at another user. Other services already hold the merchantId.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR - empty body, or bad email/phone",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "MERCHANT_PROFILE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MerchantProfileResponse> updateMerchantProfile(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMerchantProfileRequest request) {

        return ResponseEntity.ok(
                merchantProfileService.updateMerchantProfile(userId, request)
        );
    }
}