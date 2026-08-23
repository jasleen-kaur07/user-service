package com.ecommerce.userservice.repository;

import com.ecommerce.userservice.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findByUserIdOrderByIsDefaultDescCreatedAtAsc(UUID userId);

    Optional<Address> findByIdAndUserId(UUID id, UUID userId);

    Optional<Address> findByUserIdAndIsDefaultTrue(UUID userId);

    long countByUserId(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE Address a
              SET a.isDefault = false,
                  a.updatedAt = :now
            WHERE a.userId = :userId
              AND a.isDefault = true
           """)
    int clearDefaultForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
