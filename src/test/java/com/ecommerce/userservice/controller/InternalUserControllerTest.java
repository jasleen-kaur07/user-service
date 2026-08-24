package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.UserResponse;
import com.ecommerce.userservice.entity.UserType;
import com.ecommerce.userservice.exception.ConflictException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalUserController.class)
class InternalUserControllerTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private static UserResponse profile(UserType type) {
        return new UserResponse(
                USER_ID,
                "jasleen@gmail.com",
                null,
                null,
                null,
                type,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    @Test
    @DisplayName("a first sync returns 201 CREATED")
    void createsUser() throws Exception {

        when(userService.syncUserFromAuthService(any()))
                .thenReturn(
                        new UserService.SyncResult(
                                profile(UserType.CUSTOMER),
                                true
                        )
                );

        mockMvc.perform(
                        post("/api/internal/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId":"11111111-1111-1111-1111-111111111111",
                                          "email":"jasleen@gmail.com",
                                          "userType":"CUSTOMER"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id")
                        .value(USER_ID.toString()))
                .andExpect(jsonPath("$.userType")
                        .value("CUSTOMER"));
    }

    @Test
    @DisplayName("a repeated sync returns 200 OK, not 201, so Auth Service can retry safely")
    void repeatedSyncReturnsOk() throws Exception {

        when(userService.syncUserFromAuthService(any()))
                .thenReturn(
                        new UserService.SyncResult(
                                profile(UserType.CUSTOMER),
                                false
                        )
                );

        mockMvc.perform(
                        post("/api/internal/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId":"11111111-1111-1111-1111-111111111111",
                                          "email":"jasleen@gmail.com",
                                          "userType":"CUSTOMER"
                                        }
                                        """)
                )
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a merchant identity syncs the same way")
    void createsMerchant() throws Exception {

        when(userService.syncUserFromAuthService(any()))
                .thenReturn(
                        new UserService.SyncResult(
                                profile(UserType.MERCHANT),
                                true
                        )
                );

        mockMvc.perform(
                        post("/api/internal/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId":"11111111-1111-1111-1111-111111111111",
                                          "email":"easybuy@gmail.com",
                                          "userType":"MERCHANT"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userType")
                        .value("MERCHANT"));
    }

    @Test
    @DisplayName("a duplicate email is 409 EMAIL_ALREADY_IN_USE")
    void duplicateEmail() throws Exception {

        when(userService.syncUserFromAuthService(any()))
                .thenThrow(
                        ConflictException.emailInUse("jasleen@gmail.com")
                );

        mockMvc.perform(
                        post("/api/internal/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId":"33333333-3333-3333-3333-333333333333",
                                          "email":"jasleen@gmail.com",
                                          "userType":"CUSTOMER"
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("EMAIL_ALREADY_IN_USE"));
    }

    @Test
    @DisplayName("an invalid email is 400 and never reaches the service")
    void invalidEmail() throws Exception {

        mockMvc.perform(
                        post("/api/internal/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId":"11111111-1111-1111-1111-111111111111",
                                          "email":"not-an-email",
                                          "userType":"CUSTOMER"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("email"));

        verify(userService, never())
                .syncUserFromAuthService(any());
    }

    @Test
    @DisplayName("a userType outside the enum is 400 - the enum is the guard")
    void invalidUserType() throws Exception {

        mockMvc.perform(
                        post("/api/internal/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId":"11111111-1111-1111-1111-111111111111",
                                          "email":"jasleen@gmail.com",
                                          "userType":"ADMIN"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("BAD_REQUEST"));

        verify(userService, never())
                .syncUserFromAuthService(any());
    }

    @Test
    @DisplayName("a missing userId is 400")
    void missingUserId() throws Exception {

        mockMvc.perform(
                        post("/api/internal/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"jasleen@gmail.com\",\"userType\":\"CUSTOMER\"}"
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("userId"));
    }

    @Test
    @DisplayName("the internal endpoint needs no end-user identity headers")
    void needsNoIdentityHeaders() throws Exception {

        when(userService.syncUserFromAuthService(any()))
                .thenReturn(
                        new UserService.SyncResult(
                                profile(UserType.CUSTOMER),
                                true
                        )
                );

        mockMvc.perform(
                        post("/api/internal/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId":"11111111-1111-1111-1111-111111111111",
                                          "email":"jasleen@gmail.com",
                                          "userType":"CUSTOMER"
                                        }
                                        """)
                )
                .andExpect(status().isCreated());
    }
}