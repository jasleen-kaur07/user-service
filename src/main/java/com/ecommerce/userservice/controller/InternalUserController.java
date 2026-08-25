package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.CreateUserRequest;
import com.ecommerce.userservice.dto.ErrorResponse;
import com.ecommerce.userservice.dto.UserResponse;
import com.ecommerce.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/internal/users")
@Tag(name = "Internal - Users",
        description = "Called by Auth Service only. Must not be exposed through the API Gateway.")
public class InternalUserController {

    private final UserService userService;

    public InternalUserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @Operation(
            summary = "Create or sync a user profile from Auth Service",
            description = """
                    Auth Service calls this immediately after it registers an identity, passing the
                    userId it minted, the email and the classification (CUSTOMER or MERCHANT).
                    User Service reuses that userId verbatim - it never generates a competing one.

                    **This endpoint is idempotent**, because Auth Service may retry it:

                    | Situation | Result |
                    |---|---|
                    | New userId, unused email | `201 CREATED` |
                    | Known userId, identical email and role | `200 OK`, no write performed |
                    | Known userId, different email or role | `409 USER_IDENTITY_MISMATCH` |
                    | New userId, email owned by someone else | `409 EMAIL_ALREADY_IN_USE` |

                    The mismatch case is a conflict rather than an update on purpose: this endpoint
                    must not become a way to change a user's login email or to promote a customer
                    to a merchant.

                    No credential of any kind is accepted or stored - no password, hash, token or
                    OAuth identifier.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Profile created"),
            @ApiResponse(responseCode = "200", description = "Profile already existed and is unchanged"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR - missing userId, bad email, or role not CUSTOMER/MERCHANT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_IN_USE or USER_IDENTITY_MISMATCH",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> syncUser(@Valid @RequestBody CreateUserRequest request) {
        UserService.SyncResult result = userService.syncUserFromAuthService(request);
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.user());
    }
}