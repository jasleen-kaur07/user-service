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

/**
 * Business logic for user profiles.
 *
 * <p>Constructor injection, no field injection: the dependencies are final, the
 * class is trivially unit-testable with plain Mockito, and Spring's default
 * singleton scope means exactly one instance exists - which is where the
 * "singleton" requirement is satisfied, without a hand-rolled singleton class.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Outcome of an identity sync, so the controller can answer 201 for a genuine
     * creation and 200 for a repeat call without re-querying.
     */
    public record SyncResult(UserResponse user, boolean created) {
    }

    /**
     * Creates the local profile for an identity that Auth Service has just
     * registered - or returns the existing one unchanged.
     *
     * <p><b>Idempotent by design.</b> Auth Service may retry this call after a
     * timeout, a redeploy or a replayed request, and must never end up with two
     * profiles. The contract is:
     *
     * <ul>
     *   <li>unknown userId, unused email -> create, {@code created = true}</li>
     *   <li>known userId, same email and userType -> return it, {@code created = false},
     *       no write at all</li>
     *   <li>known userId, <i>different</i> email or userType -> 409
     *       {@code USER_IDENTITY_MISMATCH}. Silently overwriting would let this
     *       endpoint become a back door for changing a user's login email or
     *       promoting a customer to merchant.</li>
     *   <li>new userId but the email belongs to someone else -> 409
     *       {@code EMAIL_ALREADY_IN_USE}</li>
     * </ul>
     */
    @Transactional
    public SyncResult syncUserFromAuthService(CreateUserRequest request) {
        UUID userId = request.userId();
        String email = normaliseEmail(request.email());

        var existing = userRepository.findById(userId);
        if (existing.isPresent()) {
            User user = existing.get();
            if (!Objects.equals(user.getEmail(), email) || user.getUserType() != request.userType()) {
                throw ConflictException.identityMismatch(userId);
            }
            log.debug("Identity sync for {} was a no-op; profile already exists", userId);
            return new SyncResult(UserMapper.toResponse(user), false);
        }

        if (userRepository.existsByEmail(email)) {
            throw ConflictException.emailInUse(email);
        }

        User user = new User(userId, email, request.userType());
        try {
            User saved = userRepository.saveAndFlush(user);
            log.info("Created profile for user {} ({})", saved.getId(), saved.getUserType());
            return new SyncResult(UserMapper.toResponse(saved), true);
        } catch (DataIntegrityViolationException ex) {
            // Two concurrent syncs for the same identity: one inserted first. Since
            // this endpoint is meant to be retry-safe, treat the loser as a no-op
            // rather than surfacing a constraint error to Auth Service.
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

    /**
     * Applies a partial profile update. Only firstName, lastName and phone can
     * reach this method - {@link UpdateUserRequest} has no other fields - so email,
     * userType and the id are safe by construction rather than by filtering.
     */
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

        // saveAndFlush, not a bare return: @UpdateTimestamp is applied when Hibernate
        // flushes, which by default happens at commit - i.e. AFTER we would have read
        // the entity. Without the explicit flush the response would carry the old
        // updatedAt while the database held the new one.
        User saved = userRepository.saveAndFlush(user);
        log.debug("Updated profile for user {}", userId);
        return UserMapper.toResponse(saved);
    }

    /** Loads a user or fails with 404. The single place that decision is made. */
    @Transactional(readOnly = true)
    public User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.user(userId));
    }

    /**
     * Emails are stored lower-cased and trimmed so that {@code A@x.com} and
     * {@code a@x.com} cannot become two accounts, while the unique index on the
     * raw column still does the work.
     */
    private static String normaliseEmail(String email) {
        return email.trim().toLowerCase();
    }

    /** An empty string in a PATCH means "clear this field", stored as NULL. */
    private static String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
