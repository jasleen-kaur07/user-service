package com.ecommerce.userservice.client;

import com.ecommerce.userservice.client.dto.TokenIntrospectionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "auth-service",
        url = "${integration.auth-service.url}"
)
public interface AuthServiceClient {

    @GetMapping("/api/internal/auth/introspect")
    TokenIntrospectionResponse introspect(
            @RequestHeader("Authorization") String authorizationHeader);
}
