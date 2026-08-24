package com.ecommerce.userservice.service;

import com.ecommerce.userservice.dto.AddressRequest;
import com.ecommerce.userservice.dto.AddressResponse;
import com.ecommerce.userservice.dto.UpdateAddressRequest;
import com.ecommerce.userservice.entity.Address;
import com.ecommerce.userservice.exception.ErrorCode;
import com.ecommerce.userservice.exception.ResourceNotFoundException;
import com.ecommerce.userservice.repository.AddressRepository;
import com.ecommerce.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AddressServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADDRESS_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AddressService addressService;

    @BeforeEach
    void userExists() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(addressRepository.saveAndFlush(any(Address.class))).thenAnswer(call -> call.getArgument(0));
        when(addressRepository.save(any(Address.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static AddressRequest request(Boolean isDefault) {
        return new AddressRequest("123 Main Street", "Apartment 4B", "Noida",
                "Uttar Pradesh", "India", "201301", isDefault);
    }

    private static Address address(boolean isDefault) {
        Address address = new Address(USER_ID);
        address.setAddressLine1("123 Main Street");
        address.setCity("Noida");
        address.setState("Uttar Pradesh");
        address.setCountry("India");
        address.setPincode("201301");
        address.setDefault(isDefault);
        return address;
    }

    @Test
    @DisplayName("adds an address")
    void addsAddress() {
        when(addressRepository.countByUserId(USER_ID)).thenReturn(2L);

        AddressResponse response = addressService.addAddress(USER_ID, request(false));

        assertThat(response.addressLine1()).isEqualTo("123 Main Street");
        assertThat(response.city()).isEqualTo("Noida");
        assertThat(response.isDefault()).isFalse();
    }

    @Test
    @DisplayName("a user's FIRST address becomes the default even when the flag is false")
    void firstAddressIsAlwaysDefault() {
        when(addressRepository.countByUserId(USER_ID)).thenReturn(0L);

        AddressResponse response = addressService.addAddress(USER_ID, request(false));

        assertThat(response.isDefault())
                .as("a user with addresses must always have one default for checkout to preselect")
                .isTrue();
    }

    @Test
    @DisplayName("adding with isDefault=true demotes the previous default first")
    void addingDefaultDemotesPrevious() {
        when(addressRepository.countByUserId(USER_ID)).thenReturn(3L);

        addressService.addAddress(USER_ID, request(true));

        verify(addressRepository).clearDefaultForUser(eq(USER_ID), any(Instant.class));
        ArgumentCaptor<Address> saved = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isDefault()).isTrue();
    }

    @Test
    @DisplayName("adding a non-default address does not disturb the existing default")
    void addingNonDefaultLeavesDefaultAlone() {
        when(addressRepository.countByUserId(USER_ID)).thenReturn(3L);

        addressService.addAddress(USER_ID, request(false));

        verify(addressRepository, never()).clearDefaultForUser(any(), any());
    }

    @Test
    @DisplayName("lists the user's addresses")
    void listsAddresses() {
        when(addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtAsc(USER_ID))
                .thenReturn(List.of(address(true), address(false)));

        List<AddressResponse> responses = addressService.getAddresses(USER_ID);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).isDefault()).isTrue();
    }

    @Test
    @DisplayName("returns a specific address")
    void getsSpecificAddress() {
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.of(address(false)));

        assertThat(addressService.getAddress(USER_ID, ADDRESS_ID).city()).isEqualTo("Noida");
    }

    @Test
    @DisplayName("an address belonging to a different user is reported as ADDRESS_NOT_FOUND, not FORBIDDEN")
    void addressOfAnotherUserIsNotFound() {
        // findByIdAndUserId returns empty because the ownership check is in the query,
        // so 'someone else's id' and 'no such id' are indistinguishable to the caller -
        // which is the point: a 403 here would confirm the id exists.
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.getAddress(USER_ID, ADDRESS_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ResourceNotFoundException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ADDRESS_NOT_FOUND);
    }

    @Test
    @DisplayName("throws USER_NOT_FOUND when the user in the path does not exist")
    void userNotFound() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.existsById(unknown)).thenReturn(false);

        assertThatThrownBy(() -> addressService.getAddresses(unknown))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ResourceNotFoundException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("updates only the supplied fields")
    void updatesAddress() {
        Address existing = address(false);
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID)).thenReturn(Optional.of(existing));

        addressService.updateAddress(USER_ID, ADDRESS_ID,
                new UpdateAddressRequest(null, null, "New Delhi", null, null, null));

        assertThat(existing.getCity()).isEqualTo("New Delhi");
        assertThat(existing.getState()).isEqualTo("Uttar Pradesh");   // untouched
    }

    @Test
    @DisplayName("promoting an address demotes the old default in the same transaction")
    void setsDefault() {
        Address target = address(false);
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID)).thenReturn(Optional.of(target));

        AddressResponse response = addressService.setDefaultAddress(USER_ID, ADDRESS_ID);

        verify(addressRepository).clearDefaultForUser(eq(USER_ID), any(Instant.class));
        assertThat(response.isDefault()).isTrue();
        assertThat(target.isDefault()).isTrue();
    }

    @Test
    @DisplayName("promoting the address that is already default is a harmless no-op")
    void setDefaultIsIdempotent() {
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.of(address(true)));

        AddressResponse response = addressService.setDefaultAddress(USER_ID, ADDRESS_ID);

        assertThat(response.isDefault()).isTrue();
        verify(addressRepository, never()).clearDefaultForUser(any(), any());
    }

    @Test
    @DisplayName("cannot promote an address that belongs to somebody else")
    void cannotSetDefaultOnForeignAddress() {
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.setDefaultAddress(USER_ID, ADDRESS_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(addressRepository, never()).clearDefaultForUser(any(), any());
    }

    @Test
    @DisplayName("deletes an address")
    void deletesAddress() {
        Address existing = address(false);
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID)).thenReturn(Optional.of(existing));

        addressService.deleteAddress(USER_ID, ADDRESS_ID);

        verify(addressRepository).delete(existing);
    }

    @Test
    @DisplayName("deleting the default promotes the oldest remaining address")
    void deletingDefaultPromotesNext() {
        Address beingDeleted = address(true);
        Address survivor = address(false);
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID)).thenReturn(Optional.of(beingDeleted));
        when(addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtAsc(USER_ID))
                .thenReturn(List.of(survivor));

        addressService.deleteAddress(USER_ID, ADDRESS_ID);

        assertThat(survivor.isDefault())
                .as("otherwise deleting your default would leave checkout with nothing preselected")
                .isTrue();
    }

    @Test
    @DisplayName("deleting a non-default address promotes nothing")
    void deletingNonDefaultPromotesNothing() {
        Address survivor = address(true);
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID)).thenReturn(Optional.of(address(false)));
        when(addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtAsc(USER_ID))
                .thenReturn(List.of(survivor));

        addressService.deleteAddress(USER_ID, ADDRESS_ID);

        verify(addressRepository, never()).findByUserIdOrderByIsDefaultDescCreatedAtAsc(USER_ID);
    }

    @Test
    @DisplayName("cannot delete an address that belongs to somebody else")
    void cannotDeleteForeignAddress() {
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.deleteAddress(USER_ID, ADDRESS_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(addressRepository, never()).delete(any());
    }
}
