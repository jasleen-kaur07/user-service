package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.ErrorResponse;
import com.ecommerce.userservice.dto.UpdateUserRequest;
import com.ecommerce.userservice.dto.UserResponse;
import com.ecommerce.userservice.security.CurrentUser;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User profile. Consumed by the frontend and by Order/Cart Service.")
public class UserController {

    private final UserService userService;
    private final CurrentUser currentUser;

    public UserController(UserService userService, CurrentUser currentUser) {
        this.userService = userService;
        this.currentUser = currentUser;
    }

    @GetMapping("/{userId}")
    @Operation(
            summary = "Get a user profile",
            description = """
                    Returns the profile for the given user.

                    Order Service calls this at checkout to obtain the customer's email for the
                    confirmation mail. Cart Service calls it only if it needs more than the userId
                    it already holds from the JWT.

                    Requires `X-User-Id` / `X-User-Type` headers from the API Gateway, and the
                    caller must be the user in the path.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile found"),
            @ApiResponse(responseCode = "401", description = "No caller identity supplied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is a different user",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> getUser(
            @Parameter(description = "The user's UUID, as issued by Auth Service")
            @PathVariable UUID userId) {

        currentUser.requireSelf(userId);
        return ResponseEntity.ok(userService.getUser(userId));
    }

    @PatchMapping("/{userId}")
    @Operation(
            summary = "Update your own profile",
            description = """
                    Partially updates firstName, lastName and phone. Send only the fields you
                    want to change; an omitted field is left untouched, and an empty string
                    clears it.

                    `userId`, `email` and `userType` are absent from the request schema on
                    purpose and cannot be changed here - email and user type are owned by Auth
                    Service.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR - bad phone, or an empty body",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is a different user",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> updateUser(@PathVariable UUID userId,
                                                   @Valid @RequestBody UpdateUserRequest request) {
        currentUser.requireSelf(userId);
        return ResponseEntity.ok(userService.updateUser(userId, request));
    }
}
