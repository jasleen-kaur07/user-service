package com.ecommerce.userservice.repository;

import com.ecommerce.userservice.entity.Address;
import com.ecommerce.userservice.entity.MerchantProfile;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.entity.UserType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
class DatabaseConstraintIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private MerchantProfileRepository merchantProfileRepository;

    private User user;

    @BeforeEach
    void createUser() {
        user = userRepository.saveAndFlush(new User(
                UUID.randomUUID(), UUID.randomUUID() + "@example.com", UserType.CUSTOMER));
    }

    private Address newAddress(boolean isDefault) {
        Address address = new Address(user.getId());
        address.setAddressLine1("123 Main Street");
        address.setCity("Noida");
        address.setState("Uttar Pradesh");
        address.setCountry("India");
        address.setPincode("201301");
        address.setDefault(isDefault);
        return address;
    }

    @Test
    @DisplayName("the database itself refuses a second default address for the same user")
    void partialUniqueIndexRejectsTwoDefaults() {
        addressRepository.saveAndFlush(newAddress(true));

        assertThatThrownBy(() -> addressRepository.saveAndFlush(newAddress(true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("many NON-default addresses for one user are perfectly legal")
    void allowsManyNonDefaults() {
        addressRepository.saveAndFlush(newAddress(true));
        addressRepository.saveAndFlush(newAddress(false));
        addressRepository.saveAndFlush(newAddress(false));

        assertThat(addressRepository.countByUserId(user.getId())).isEqualTo(3);
        assertThat(addressRepository.findByUserIdAndIsDefaultTrue(user.getId())).isPresent();
    }

    @Test
    @DisplayName("demote-then-promote succeeds where promote-first would violate the index")
    void demoteThenPromoteWorks() {
        Address first = addressRepository.saveAndFlush(newAddress(true));
        Address second = addressRepository.saveAndFlush(newAddress(false));

        int demoted = addressRepository.clearDefaultForUser(user.getId(), Instant.now());
        assertThat(demoted).isEqualTo(1);

        Address promoted = addressRepository.findById(second.getId()).orElseThrow();
        promoted.setDefault(true);
        addressRepository.saveAndFlush(promoted);

        assertThat(addressRepository.findByUserIdAndIsDefaultTrue(user.getId()))
                .get()
                .extracting(Address::getId)
                .isEqualTo(second.getId());
        assertThat(addressRepository.findById(first.getId()).orElseThrow().isDefault()).isFalse();
    }

    @Test
    @DisplayName("clearDefaultForUser refreshes updated_at, which a bulk update would otherwise skip")
    void bulkDemoteUpdatesTimestamp() {
        Address address = addressRepository.saveAndFlush(newAddress(true));
        Instant before = address.getUpdatedAt();

        Instant now = Instant.now().plusSeconds(1);
        addressRepository.clearDefaultForUser(user.getId(), now);
        entityManager.clear();

        Address reloaded = addressRepository.findById(address.getId()).orElseThrow();
        assertThat(reloaded.isDefault()).isFalse();
        assertThat(reloaded.getUpdatedAt()).isAfter(before);
    }

    @Test
    @DisplayName("email is unique across users")
    void emailIsUnique() {
        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User(UUID.randomUUID(), user.getEmail(), UserType.CUSTOMER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a user can own at most one merchant profile")
    void oneMerchantProfilePerUser() {
        User merchant = userRepository.saveAndFlush(new User(
                UUID.randomUUID(), UUID.randomUUID() + "@shop.com", UserType.MERCHANT));

        merchantProfileRepository.saveAndFlush(new MerchantProfile(merchant.getId(), "EasyBuy"));

        assertThatThrownBy(() -> merchantProfileRepository.saveAndFlush(
                new MerchantProfile(merchant.getId(), "EasyBuy Duplicate")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the CHECK constraint rejects a role outside the enum, even from raw SQL")
    void checkConstraintRejectsUnknownRole() {
        assertThatThrownBy(() -> {
            entityManager.createNativeQuery("""
                    INSERT INTO users (id, email, role, created_at, updated_at)
                    VALUES (:id, :email, 'ADMIN', now(), now())
                    """)
                    .setParameter("id", UUID.randomUUID())
                    .setParameter("email", UUID.randomUUID() + "@example.com")
                    .executeUpdate();
            entityManager.flush();
        }).hasMessageContaining("ck_users_role");
    }

    @Test
    @DisplayName("deleting a user cascades to their addresses, leaving no orphans")
    void deletingUserCascadesToAddresses() {
        addressRepository.saveAndFlush(newAddress(true));
        addressRepository.saveAndFlush(newAddress(false));
        assertThat(addressRepository.countByUserId(user.getId())).isEqualTo(2);

        userRepository.delete(user);
        userRepository.flush();
        entityManager.clear();

        assertThat(addressRepository.countByUserId(user.getId())).isZero();
    }

    @Test
    @DisplayName("findByIdAndUserId will not return another user's address")
    void ownershipIsEnforcedInTheQuery() {
        Address mine = addressRepository.saveAndFlush(newAddress(true));
        UUID somebodyElse = UUID.randomUUID();

        assertThat(addressRepository.findByIdAndUserId(mine.getId(), user.getId())).isPresent();
        assertThat(addressRepository.findByIdAndUserId(mine.getId(), somebodyElse)).isEmpty();
    }
}