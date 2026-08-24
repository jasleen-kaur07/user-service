package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.AddressResponse;
import com.ecommerce.userservice.dto.CreateUserRequest;
import com.ecommerce.userservice.dto.ErrorResponse;
import com.ecommerce.userservice.dto.OrderUserDetailsResponse;
import com.ecommerce.userservice.dto.UpdateUserRequest;
import com.ecommerce.userservice.dto.UserResponse;
import com.ecommerce.userservice.service.AddressService;
import com.ecommerce.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User profile. Consumed by the frontend and by Order/Cart Service.")
public class UserController {

    private final UserService userService;
    private final AddressService addressService;

    public UserController(UserService userService, AddressService addressService) {
        this.userService = userService;
        this.addressService = addressService;
    }

    @GetMapping("/{userId}")
    @Operation(
            summary = "Get a user profile",
            description = """
                    Returns the profile for the given user.

                    Order Service can use this endpoint to obtain the customer's email.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile found"),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> getUser(
            @Parameter(description = "The user's UUID, as issued by Auth Service")
            @PathVariable UUID userId) {

        return ResponseEntity.ok(userService.getUser(userId));
    }

    @GetMapping("/{userId}/order-details")
    @Operation(
            summary = "Get user details required for an order",
            description = """
                    Returns the user's identity information and delivery address required
                    by Order Service during checkout.

                    Order Service calls this endpoint using the userId received from
                    Cart Service.

                    The response contains:
                    * userId
                    * email
                    * the user's default address, or the first available address if no
                      default address exists.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User order details found"),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OrderUserDetailsResponse> getOrderUserDetails(
            @Parameter(description = "The user's UUID")
            @PathVariable UUID userId) {

        UserResponse user = userService.getUser(userId);

        List<AddressResponse> addresses = addressService.getAddresses(userId);

        AddressResponse address = addresses.stream()
                .filter(AddressResponse::isDefault)
                .findFirst()
                .orElse(addresses.isEmpty() ? null : addresses.get(0));

        return ResponseEntity.ok(
                new OrderUserDetailsResponse(
                        user.id(),
                        user.email(),
                        address
                )
        );
    }

    @PatchMapping("/{userId}")
    @Operation(
            summary = "Update user profile",
            description = """
                    Partially updates firstName, lastName and phone. Send only the fields you
                    want to change; an omitted field is left untouched, and an empty string
                    clears it.

                    userId, email and userType cannot be changed here.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request) {

        return ResponseEntity.ok(userService.updateUser(userId, request));
    }

    @PostMapping("/internal/sync")
    public ResponseEntity<UserResponse> syncUser(
            @Valid @RequestBody CreateUserRequest request) {

        UserService.SyncResult result =
                userService.syncUserFromAuthService(request);

        return result.created()
                ? ResponseEntity.status(201).body(result.user())
                : ResponseEntity.ok(result.user());
    }
}