package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.AddressResponse;
import com.ecommerce.userservice.exception.ResourceNotFoundException;
import com.ecommerce.userservice.service.AddressService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AddressController.class)
class AddressControllerTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID ADDRESS_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final String VALID_BODY = """
            {"addressLine1":"123 Main Street",
             "addressLine2":"Apartment 4B",
             "city":"Noida",
             "state":"Uttar Pradesh",
             "country":"India",
             "pincode":"201301",
             "isDefault":true}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddressService addressService;

    private static AddressResponse address(boolean isDefault) {
        return new AddressResponse(
                ADDRESS_ID,
                USER_ID,
                "123 Main Street",
                "Apartment 4B",
                "Noida",
                "Uttar Pradesh",
                "India",
                "201301",
                isDefault,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    @Test
    @DisplayName("POST creates an address and returns 201 with a Location header")
    void addsAddress() throws Exception {

        when(addressService.addAddress(eq(USER_ID), any()))
                .thenReturn(address(true));

        mockMvc.perform(
                        post("/api/users/{userId}/addresses", USER_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_BODY)
                )
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/users/" + USER_ID + "/addresses/" + ADDRESS_ID
                ))
                .andExpect(jsonPath("$.isDefault").value(true))
                .andExpect(jsonPath("$.city").value("Noida"));
    }

    @Test
    @DisplayName("POST without the required fields is 400 and names every missing field")
    void rejectsIncompleteAddress() throws Exception {

        mockMvc.perform(
                        post("/api/users/{userId}/addresses", USER_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"addressLine2\":\"only line 2\"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(5));

        verify(addressService, never()).addAddress(any(), any());
    }

    @Test
    @DisplayName("GET lists the user's addresses")
    void listsAddresses() throws Exception {

        when(addressService.getAddresses(USER_ID))
                .thenReturn(List.of(address(true), address(false)));

        mockMvc.perform(
                        get("/api/users/{userId}/addresses", USER_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].isDefault").value(true));
    }

    @Test
    @DisplayName("GET returns one address")
    void getsOneAddress() throws Exception {

        when(addressService.getAddress(USER_ID, ADDRESS_ID))
                .thenReturn(address(true));

        mockMvc.perform(
                        get("/api/users/{userId}/addresses/{addressId}",
                                USER_ID, ADDRESS_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ADDRESS_ID.toString()));
    }

    @Test
    @DisplayName("PATCH updates an address")
    void updatesAddress() throws Exception {

        when(addressService.updateAddress(
                eq(USER_ID),
                eq(ADDRESS_ID),
                any()
        )).thenReturn(address(false));

        mockMvc.perform(
                        patch("/api/users/{userId}/addresses/{addressId}",
                                USER_ID, ADDRESS_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"city\":\"New Delhi\"}")
                )
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /default promotes the address")
    void setsDefault() throws Exception {

        when(addressService.setDefaultAddress(USER_ID, ADDRESS_ID))
                .thenReturn(address(true));

        mockMvc.perform(
                        patch(
                                "/api/users/{userId}/addresses/{addressId}/default",
                                USER_ID,
                                ADDRESS_ID
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    @DisplayName("DELETE returns 204 with no body")
    void deletesAddress() throws Exception {

        mockMvc.perform(
                        delete(
                                "/api/users/{userId}/addresses/{addressId}",
                                USER_ID,
                                ADDRESS_ID
                        )
                )
                .andExpect(status().isNoContent());

        verify(addressService).deleteAddress(USER_ID, ADDRESS_ID);
    }

    @Test
    @DisplayName("An address that does not exist returns 404")
    void addressNotFound() throws Exception {

        when(addressService.getAddress(USER_ID, ADDRESS_ID))
                .thenThrow(ResourceNotFoundException.address(ADDRESS_ID));

        mockMvc.perform(
                        get(
                                "/api/users/{userId}/addresses/{addressId}",
                                USER_ID,
                                ADDRESS_ID
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ADDRESS_NOT_FOUND"));
    }

    @Test
    @DisplayName("An unknown user returns 404 USER_NOT_FOUND")
    void userNotFound() throws Exception {

        when(addressService.getAddresses(USER_ID))
                .thenThrow(ResourceNotFoundException.user(USER_ID));

        mockMvc.perform(
                        get("/api/users/{userId}/addresses", USER_ID)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
    }
}