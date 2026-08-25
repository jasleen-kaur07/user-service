package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.UserResponse;
import com.ecommerce.userservice.entity.UserType;
import com.ecommerce.userservice.exception.ResourceNotFoundException;
import com.ecommerce.userservice.service.AddressService;
import com.ecommerce.userservice.service.UserService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AddressService addressService;

    private static UserResponse profile() {
        return new UserResponse(
                USER_ID,
                "jasleen@gmail.com",
                "Jasleen",
                "Kaur",
                "9876501234",
                UserType.CUSTOMER,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );
    }

    @Test
    @DisplayName("GET returns the user profile")
    void getsProfile() throws Exception {

        when(userService.getUser(USER_ID))
                .thenReturn(profile());

        mockMvc.perform(
                        get("/api/users/{userId}", USER_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(USER_ID.toString()))
                .andExpect(jsonPath("$.email")
                        .value("jasleen@gmail.com"))
                .andExpect(jsonPath("$.role")
                        .value("CUSTOMER"));
    }

    @Test
    @DisplayName("GET an unknown user is 404 USER_NOT_FOUND")
    void unknownUser() throws Exception {

        when(userService.getUser(USER_ID))
                .thenThrow(
                        ResourceNotFoundException.user(USER_ID)
                );

        mockMvc.perform(
                        get("/api/users/{userId}", USER_ID)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH updates the profile")
    void updatesProfile() throws Exception {

        when(userService.updateUser(
                eq(USER_ID),
                any()
        )).thenReturn(profile());

        mockMvc.perform(
                        patch("/api/users/{userId}", USER_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"firstName\":\"Jasleen\",\"lastName\":\"Kaur\"}"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName")
                        .value("Jasleen"));
    }

    @Test
    @DisplayName("PATCH with an invalid phone is 400 with the offending field named")
    void rejectsBadPhone() throws Exception {

        mockMvc.perform(
                        patch("/api/users/{userId}", USER_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"phone\":\"not-a-phone\"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("phone"));
    }

    @Test
    @DisplayName("PATCH with an empty body is rejected rather than silently doing nothing")
    void rejectsEmptyPatch() throws Exception {

        mockMvc.perform(
                        patch("/api/users/{userId}", USER_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("a malformed UUID in the path is 400, not 500")
    void rejectsMalformedUuid() throws Exception {

        mockMvc.perform(
                        get("/api/users/{userId}", "not-a-uuid")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("BAD_REQUEST"));
    }
}