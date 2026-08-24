package com.ecommerce.userservice.client;

import com.ecommerce.userservice.client.dto.MerchantIdentitySyncRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@FeignClient(
        name = "merchant-service",
        url = "${integration.merchant-service.url}"
)
public interface MerchantServiceClient {

    @PostMapping("/api/internal/merchants")
    void syncMerchantIdentity(@RequestBody MerchantIdentitySyncRequest request);
}
