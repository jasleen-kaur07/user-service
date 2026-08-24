package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.AddressRequest;
import com.ecommerce.userservice.dto.AddressResponse;
import com.ecommerce.userservice.dto.ErrorResponse;
import com.ecommerce.userservice.dto.UpdateAddressRequest;
import com.ecommerce.userservice.security.CurrentUser;
import com.ecommerce.userservice.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/users/{userId}/addresses")
@Tag(name = "Addresses", description = "Customer delivery addresses. Order Service reads these at checkout.")
public class AddressController {

    private final AddressService addressService;
    private final CurrentUser currentUser;

    public AddressController(AddressService addressService, CurrentUser currentUser) {
        this.addressService = addressService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @Operation(
            summary = "Add an address",
            description = """
                    Creates an address for the user.

                    Two rules apply. If `isDefault` is true, the user's previous default is demoted
                    in the same transaction. And a user's **first** address always becomes the
                    default regardless of the flag, so checkout always has something to preselect.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Address created; Location header points at it"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR - a required field is missing",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is a different user",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AddressResponse> addAddress(@PathVariable UUID userId,
                                                      @Valid @RequestBody AddressRequest request) {
        currentUser.requireSelf(userId);
        AddressResponse created = addressService.addAddress(userId, request);
        return ResponseEntity
                .created(URI.create("/api/users/" + userId + "/addresses/" + created.id()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "List all of the user's addresses",
               description = "Ordered with the default address first, then oldest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Addresses returned (possibly an empty list)"),
            @ApiResponse(responseCode = "403", description = "Caller is a different user",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<AddressResponse>> getAddresses(@PathVariable UUID userId) {
        currentUser.requireSelf(userId);
        return ResponseEntity.ok(addressService.getAddresses(userId));
    }

    @GetMapping("/{addressId}")
    @Operation(
            summary = "Get one address",
            description = """
                    Order Service calls this at checkout for the selected delivery address, then
                    **copies the values into its own database**. The order keeps the address it
                    actually shipped to, so a later edit here never rewrites delivery history.

                    An address that exists but belongs to another user returns 404, not 403: a 403
                    would confirm that the id is real.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Address found"),
            @ApiResponse(responseCode = "403", description = "Caller is a different user",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ADDRESS_NOT_FOUND or USER_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AddressResponse> getAddress(@PathVariable UUID userId,
                                                      @PathVariable UUID addressId) {
        currentUser.requireSelf(userId);
        return ResponseEntity.ok(addressService.getAddress(userId, addressId));
    }

    @PatchMapping("/{addressId}")
    @Operation(
            summary = "Update an address",
            description = """
                    Partial update; omitted fields are left alone.

                    `isDefault` is not accepted here. Promoting an address is a separate endpoint
                    because it also demotes a sibling row, and that multi-row transaction should
                    not hide inside what looks like a field edit.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Address updated"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR - empty body or bad pincode",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is a different user",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ADDRESS_NOT_FOUND or USER_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AddressResponse> updateAddress(@PathVariable UUID userId,
                                                         @PathVariable UUID addressId,
                                                         @Valid @RequestBody UpdateAddressRequest request) {
        currentUser.requireSelf(userId);
        return ResponseEntity.ok(addressService.updateAddress(userId, addressId, request));
    }

    @PatchMapping("/{addressId}/default")
    @Operation(
            summary = "Make this the user's default address",
            description = """
                    Demotes the current default and promotes this one, in a single transaction, so
                    the user can never end up with two defaults or none.

                    The invariant is also enforced in PostgreSQL by a partial unique index on
                    `addresses(user_id) WHERE is_default`, which is what makes it hold under
                    concurrent requests rather than just well-behaved ones.

                    Idempotent: promoting the address that is already default returns 200.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Address is now the default"),
            @ApiResponse(responseCode = "403", description = "Caller is a different user",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ADDRESS_NOT_FOUND or USER_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AddressResponse> setDefaultAddress(@PathVariable UUID userId,
                                                             @PathVariable UUID addressId) {
        currentUser.requireSelf(userId);
        return ResponseEntity.ok(addressService.setDefaultAddress(userId, addressId));
    }

    @DeleteMapping("/{addressId}")
    @Operation(
            summary = "Delete an address",
            description = """
                    Deletes the address. If it was the default and the user still has others, the
                    oldest remaining address is promoted, so a user with addresses always has a
                    default.

                    Orders already placed are unaffected: Order Service stored its own copy of the
                    address at checkout.""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "403", description = "Caller is a different user",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ADDRESS_NOT_FOUND or USER_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteAddress(@PathVariable UUID userId,
                                              @PathVariable UUID addressId) {
        currentUser.requireSelf(userId);
        addressService.deleteAddress(userId, addressId);
        return ResponseEntity.noContent().build();
    }
}
