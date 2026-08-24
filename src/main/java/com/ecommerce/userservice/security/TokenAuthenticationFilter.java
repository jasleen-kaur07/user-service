package com.ecommerce.userservice.security;

import com.ecommerce.userservice.client.AuthServiceClient;
import com.ecommerce.userservice.client.dto.TokenIntrospectionResponse;
import com.ecommerce.userservice.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
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

public class TokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private static final Logger log = LoggerFactory.getLogger(TokenAuthenticationFilter.class);

    private final AuthServiceClient authServiceClient;
    private final ObjectMapper objectMapper;

    public TokenAuthenticationFilter(AuthServiceClient authServiceClient, ObjectMapper objectMapper) {
        this.authServiceClient = authServiceClient;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (alreadyAuthenticated() || !hasBearerToken(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);

        try {
            TokenIntrospectionResponse identity = authServiceClient.introspect(authorizationHeader);

            if (identity == null || !identity.isUsable()) {
                log.debug("Auth Service rejected the presented token");
                SecurityContextHolder.clearContext();
            } else {
                authenticate(new AuthenticatedUser(
                        identity.userId(), identity.email(), identity.userType()));
            }

        } catch (FeignException.Unauthorized | FeignException.Forbidden | FeignException.NotFound ex) {
            log.debug("Auth Service rejected the token with status {}", ex.status());
            SecurityContextHolder.clearContext();

        } catch (Exception ex) {
            log.error("Auth Service is unreachable; cannot authenticate request to {}",
                    request.getRequestURI(), ex);
            SecurityContextHolder.clearContext();
            RestAuthEntryPoints.write(objectMapper, request, response,
                    ErrorCode.UPSTREAM_UNAVAILABLE,
                    "Authentication is temporarily unavailable because Auth Service could not be reached. "
                            + "Please retry shortly.");
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(AuthenticatedUser principal) {
        var authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.userType().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static boolean alreadyAuthenticated() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser;
    }

    private static boolean hasBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        return header != null && header.startsWith(BEARER_PREFIX) && header.length() > BEARER_PREFIX.length();
    }
}
