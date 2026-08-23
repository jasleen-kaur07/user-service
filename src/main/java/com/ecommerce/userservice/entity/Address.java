package com.ecommerce.userservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A delivery address belonging to one user.
 *
 * <p><b>Why {@code userId} is a plain UUID and not {@code @ManyToOne User}:</b>
 * we never need to navigate from an address to the user's profile fields. Keeping
 * it as a raw id avoids lazy-loading surprises (we run with
 * {@code open-in-view=false}), keeps the ownership check a trivial UUID compare,
 * and means loading a user's addresses does not drag the user row along with it.
 * The referential integrity we want is still there - it lives in the
 * {@code fk_addresses_user} foreign key.
 *
 * <p>Unlike {@link User}, the id IS generated here: an address is created by this
 * service, so this service owns its identifier.
 */
@Entity
@Table(name = "addresses")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "address_line1", nullable = false, length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "pincode", nullable = false, length = 20)
    private String pincode;

    /**
     * At most one address per user may have this set. Enforced by the partial
     * unique index {@code uq_addresses_one_default_per_user}, so the invariant
     * survives concurrent requests, not just well-behaved ones.
     */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. Not for application use. */
    protected Address() {
    }

    public Address(UUID userId) {
        this.userId = userId;
    }

    /** True when this address belongs to the given user. The ownership check. */
    public boolean belongsTo(UUID candidateUserId) {
        return Objects.equals(this.userId, candidateUserId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Only equal when both sides have been persisted and share an id. A transient
     * address (id == null) is equal only to itself - the standard JPA-safe
     * contract, which stops two unsaved addresses from collapsing into one inside
     * a {@code Set}.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Address address)) {
            return false;
        }
        return id != null && Objects.equals(id, address.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Address{id=" + id + ", userId=" + userId + ", isDefault=" + isDefault + '}';
    }
}
