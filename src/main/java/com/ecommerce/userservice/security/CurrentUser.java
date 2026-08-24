package com.ecommerce.userservice.security;

import com.ecommerce.userservice.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class CurrentUser {

    public Optional<AuthenticatedUser> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public void requireSelf(UUID pathUserId) {
        AuthenticatedUser caller = get().orElseThrow(
                () -> new ForbiddenException("No authenticated caller for this request"));

        if (!caller.is(pathUserId)) {
            throw new ForbiddenException(
                    "Authenticated user may only access their own profile and addresses");
        }
    }
}
