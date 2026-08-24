package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.ErrorResponse;
import com.ecommerce.userservice.dto.MerchantProfileResponse;
import com.ecommerce.userservice.service.MerchantProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/merchants")
@Tag(name = "Merchant identity",
     description = "Basic merchant identity for Merchant Service and Order Service. Identity only - no stock, pricing or ratings.")
public class MerchantController {

    private final MerchantProfileService merchantProfileService;

    public MerchantController(MerchantProfileService merchantProfileService) {
        this.merchantProfileService = merchantProfileService;
    }

    @GetMapping("/{merchantId}")
    @Operation(
            summary = "Get a merchant by merchantId",
            description = """
                    Resolves a merchantId to its business identity.

                    Order Service uses this to show "sold by EasyBuy" against an order line, and
                    Merchant Service uses it to confirm a merchantId is real before attaching
                    stock or pricing to it.

                    The merchantId is the primary key of `merchant_profiles` - User Service mints
                    it, and it is the shared identifier across the platform.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Merchant found"),
            @ApiResponse(responseCode = "400", description = "merchantId is not a valid UUID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "MERCHANT_PROFILE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MerchantProfileResponse> getByMerchantId(
            @Parameter(description = "The merchantId, i.e. merchant_profiles.id")
            @PathVariable UUID merchantId) {

        return ResponseEntity.ok(merchantProfileService.getByMerchantId(merchantId));
    }

    @GetMapping("/by-user/{userId}")
    @Operation(
            summary = "Get a merchant by userId",
            description = """
                    Turns a userId into a merchantId. This is the call Merchant Service makes: it
                    knows who is logged in from the JWT, and needs the merchantId to key its own
                    stock and pricing on.

                    Returns 404 MERCHANT_PROFILE_NOT_FOUND if the user exists but has not completed
                    merchant onboarding, and 404 USER_NOT_FOUND if the user does not exist at all -
                    so the caller can tell the two apart.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Merchant found"),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND or MERCHANT_PROFILE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MerchantProfileResponse> getByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(merchantProfileService.getByUserId(userId));
    }
}
