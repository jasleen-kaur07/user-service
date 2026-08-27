package com.ecommerce.userservice.service;

import com.ecommerce.userservice.dto.CreateUserRequest;
import com.ecommerce.userservice.dto.UpdateUserRequest;
import com.ecommerce.userservice.dto.UserResponse;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.exception.ConflictException;
import com.ecommerce.userservice.exception.ResourceNotFoundException;
import com.ecommerce.userservice.mapper.UserMapper;
import com.ecommerce.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record SyncResult(UserResponse user, boolean created) {
    }

    @Transactional
    public SyncResult syncUserFromAuthService(CreateUserRequest request) {
        UUID userId = request.userId();
        String email = normaliseEmail(request.email());

        var existing = userRepository.findById(userId);
        if (existing.isPresent()) {
            User user = existing.get();
            if (!Objects.equals(user.getEmail(), email) || user.getRole() != request.role()) {
                throw ConflictException.identityMismatch(userId);
            }
            log.debug("Identity sync for {} was a no-op; profile already exists", userId);
            return new SyncResult(UserMapper.toResponse(user), false);
        }

        if (userRepository.existsByEmail(email)) {
            throw ConflictException.emailInUse(email);
        }

        User user = new User(userId, email, request.role());
        try {
            User saved = userRepository.saveAndFlush(user);
            log.info("Created profile for user {} ({})", saved.getId(), saved.getRole());
            return new SyncResult(UserMapper.toResponse(saved), true);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Concurrent identity sync for {}; re-reading the winner", userId);
            User winner = userRepository.findById(userId)
                    .orElseThrow(() -> ConflictException.emailInUse(email));
            return new SyncResult(UserMapper.toResponse(winner), false);
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId) {
        return UserMapper.toResponse(requireUser(userId));
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        User user = requireUser(userId);

        if (request.firstName() != null) {
            user.setFirstName(blankToNull(request.firstName()));
        }
        if (request.lastName() != null) {
            user.setLastName(blankToNull(request.lastName()));
        }
        if (request.phone() != null) {
            user.setPhone(blankToNull(request.phone()));
        }

        User saved = userRepository.saveAndFlush(user);
        log.debug("Updated profile for user {}", userId);
        return UserMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.user(userId));
    }

    private static String normaliseEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }


}