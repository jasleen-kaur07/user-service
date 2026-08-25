package com.ecommerce.userservice.integration;

import com.ecommerce.userservice.client.MerchantServiceClient;
import com.ecommerce.userservice.client.dto.MerchantIdentitySyncRequest;
import com.ecommerce.userservice.event.MerchantIdentityChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MerchantServiceNotifier {

    private static final Logger log = LoggerFactory.getLogger(MerchantServiceNotifier.class);

    private final MerchantServiceClient merchantServiceClient;
    private final boolean enabled;

    public MerchantServiceNotifier(MerchantServiceClient merchantServiceClient,
                                   @Value("${integration.merchant-service.enabled:true}") boolean enabled) {
        this.merchantServiceClient = merchantServiceClient;
        this.enabled = enabled;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMerchantIdentityChanged(MerchantIdentityChangedEvent event) {
        if (!enabled) {
            log.debug("Merchant Service sync is disabled; not pushing merchantId {}", event.merchantId());
            return;
        }

        var request = new MerchantIdentitySyncRequest(
                event.businessName()
        );

        try {
            merchantServiceClient.syncMerchantIdentity(request);
            log.info("Pushed merchantId {} ({}) to Merchant Service [{}]",
                    event.merchantId(), event.businessName(),
                    event.newProfile() ? "created" : "renamed");

        } catch (Exception ex) {
            log.error("Could not push merchantId {} ({}) to Merchant Service. "
                            + "The profile is created and valid; Merchant Service can pull it via "
                            + "GET /api/merchants/by-user/{}. Cause: {}",
                    event.merchantId(), event.businessName(), event.userId(), ex.toString());
        }
    }
}
