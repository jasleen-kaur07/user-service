package com.ecommerce.userservice.security;

import com.ecommerce.userservice.entity.UserType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class IdentityHeaderFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_EMAIL_HEADER = "X-User-Email";
    public static final String USER_TYPE_HEADER = "X-User-Type";

    private static final Logger log = LoggerFactory.getLogger(IdentityHeaderFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String rawUserId = request.getHeader(USER_ID_HEADER);
        String rawUserType = request.getHeader(USER_TYPE_HEADER);

        if (rawUserId != null && rawUserType != null) {
            try {
                AuthenticatedUser principal = new AuthenticatedUser(
                        UUID.fromString(rawUserId.trim()),
                        request.getHeader(USER_EMAIL_HEADER),
                        UserType.valueOf(rawUserType.trim().toUpperCase()));

                var authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.userType().name())));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (IllegalArgumentException ex) {
                // Malformed header: stay anonymous rather than half-authenticated.
                log.debug("Ignoring malformed identity headers: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
