package com.ecommerce.userservice.repository;

import com.ecommerce.userservice.entity.MerchantProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantProfileRepository extends JpaRepository<MerchantProfile, UUID> {

    Optional<MerchantProfile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
