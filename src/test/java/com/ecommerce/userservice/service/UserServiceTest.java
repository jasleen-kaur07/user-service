package com.ecommerce.userservice.service;

import com.ecommerce.userservice.dto.CreateUserRequest;
import com.ecommerce.userservice.dto.UpdateUserRequest;
import com.ecommerce.userservice.dto.UserResponse;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.entity.UserType;
import com.ecommerce.userservice.exception.ConflictException;
import com.ecommerce.userservice.exception.ErrorCode;
import com.ecommerce.userservice.exception.ResourceNotFoundException;
import com.ecommerce.userservice.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Nested
    @DisplayName("identity sync from Auth Service")
    class Sync {

        @Test
        @DisplayName("creates the profile when the userId is unknown, and returns created=true")
        void createsNewUser() {
            var request = new CreateUserRequest(USER_ID, "Jasleen@Gmail.com", UserType.CUSTOMER);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
            when(userRepository.existsByEmail("jasleen@gmail.com")).thenReturn(false);
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));

            UserService.SyncResult result = userService.syncUserFromAuthService(request);

            assertThat(result.created()).isTrue();
            assertThat(result.user().id()).isEqualTo(USER_ID);
            assertThat(result.user().role()).isEqualTo(UserType.CUSTOMER);

            // The email must be stored lower-cased, or 'A@x.com' and 'a@x.com' become
            // two accounts despite the unique index.
            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getEmail()).isEqualTo("jasleen@gmail.com");
            assertThat(saved.getValue().getId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("is idempotent: a repeated call performs no write and returns created=false")
        void repeatedSyncIsANoOp() {
            var existing = new User(USER_ID, "jasleen@gmail.com", UserType.CUSTOMER);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

            var request = new CreateUserRequest(USER_ID, "jasleen@gmail.com", UserType.CUSTOMER);
            UserService.SyncResult result = userService.syncUserFromAuthService(request);

            assertThat(result.created()).isFalse();
            assertThat(result.user().email()).isEqualTo("jasleen@gmail.com");
            verify(userRepository, never()).saveAndFlush(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects a payload that contradicts the stored role rather than overwriting it")
        void rejectsUserTypeChange() {
            var existing = new User(USER_ID, "jasleen@gmail.com", UserType.CUSTOMER);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

            var request = new CreateUserRequest(USER_ID, "jasleen@gmail.com", UserType.MERCHANT);

            assertThatThrownBy(() -> userService.syncUserFromAuthService(request))
                    .isInstanceOf(ConflictException.class)
                    .extracting(ex -> ((ConflictException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.USER_IDENTITY_MISMATCH);
            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("rejects a payload that contradicts the stored email")
        void rejectsEmailChange() {
            var existing = new User(USER_ID, "jasleen@gmail.com", UserType.CUSTOMER);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

            var request = new CreateUserRequest(USER_ID, "someone.else@gmail.com", UserType.CUSTOMER);

            assertThatThrownBy(() -> userService.syncUserFromAuthService(request))
                    .isInstanceOf(ConflictException.class)
                    .extracting(ex -> ((ConflictException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.USER_IDENTITY_MISMATCH);
        }

        @Test
        @DisplayName("rejects a new userId whose email already belongs to somebody else")
        void rejectsDuplicateEmail() {
            UUID otherId = UUID.randomUUID();
            when(userRepository.findById(otherId)).thenReturn(Optional.empty());
            when(userRepository.existsByEmail("jasleen@gmail.com")).thenReturn(true);

            var request = new CreateUserRequest(otherId, "jasleen@gmail.com", UserType.CUSTOMER);

            assertThatThrownBy(() -> userService.syncUserFromAuthService(request))
                    .isInstanceOf(ConflictException.class)
                    .extracting(ex -> ((ConflictException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.EMAIL_ALREADY_IN_USE);
            verify(userRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("reading a profile")
    class Read {

        @Test
        @DisplayName("returns the profile when the user exists")
        void getsUser() {
            var user = new User(USER_ID, "jasleen@gmail.com", UserType.CUSTOMER);
            user.setFirstName("Jasleen");
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            UserResponse response = userService.getUser(USER_ID);

            assertThat(response.id()).isEqualTo(USER_ID);
            assertThat(response.firstName()).isEqualTo("Jasleen");
        }

        @Test
        @DisplayName("throws USER_NOT_FOUND for an unknown user")
        void userNotFound() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUser(USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting(ex -> ((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("updating a profile")
    class Update {

        @Test
        @DisplayName("applies only the fields that were supplied")
        void appliesPartialUpdate() {
            var user = new User(USER_ID, "jasleen@gmail.com", UserType.CUSTOMER);
            user.setFirstName("Old");
            user.setLastName("Name");
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));

            userService.updateUser(USER_ID, new UpdateUserRequest("New", null, "9876501234"));

            assertThat(user.getFirstName()).isEqualTo("New");
            assertThat(user.getLastName()).isEqualTo("Name");   // untouched
            assertThat(user.getPhone()).isEqualTo("9876501234");
        }

        @Test
        @DisplayName("treats an explicitly blank value as clearing the field")
        void blankClearsField() {
            var user = new User(USER_ID, "jasleen@gmail.com", UserType.CUSTOMER);
            user.setFirstName("Jasleen");
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));

            userService.updateUser(USER_ID, new UpdateUserRequest("   ", null, null));

            assertThat(user.getFirstName()).isNull();
        }

        @Test
        @DisplayName("cannot change email or role - the update never touches them")
        void doesNotTouchIdentityFields() {
            var user = new User(USER_ID, "jasleen@gmail.com", UserType.CUSTOMER);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));

            userService.updateUser(USER_ID, new UpdateUserRequest("Jasleen", "Kaur", null));

            assertThat(user.getEmail()).isEqualTo("jasleen@gmail.com");
            assertThat(user.getRole()).isEqualTo(UserType.CUSTOMER);
        }

        @Test
        @DisplayName("throws USER_NOT_FOUND when updating an unknown user")
        void updateUnknownUser() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateUser(USER_ID, new UpdateUserRequest("A", null, null)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}