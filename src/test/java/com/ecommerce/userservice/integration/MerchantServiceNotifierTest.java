package com.ecommerce.userservice.integration;

import com.ecommerce.userservice.client.MerchantServiceClient;
import com.ecommerce.userservice.client.dto.MerchantIdentitySyncRequest;
import com.ecommerce.userservice.event.MerchantIdentityChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MerchantServiceNotifierTest {

    private static final UUID MERCHANT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Mock
    private MerchantServiceClient merchantServiceClient;

    private static MerchantIdentityChangedEvent event(boolean created) {
        return new MerchantIdentityChangedEvent(MERCHANT_ID, USER_ID, "EasyBuy", created);
    }

    @Test
    @DisplayName("pushes merchantId, userId and business name when a merchant is onboarded")
    void pushesOnCreate() {
        var notifier = new MerchantServiceNotifier(merchantServiceClient, true);

        notifier.onMerchantIdentityChanged(event(true));

        ArgumentCaptor<MerchantIdentitySyncRequest> sent =
                ArgumentCaptor.forClass(MerchantIdentitySyncRequest.class);
        verify(merchantServiceClient).syncMerchantIdentity(sent.capture());

        assertThat(sent.getValue().merchantId()).isEqualTo(MERCHANT_ID);
        assertThat(sent.getValue().userId()).isEqualTo(USER_ID);
        assertThat(sent.getValue().businessName()).isEqualTo("EasyBuy");
    }

    @Test
    @DisplayName("pushes the same payload on a rename, so Merchant Service's copy cannot go stale")
    void pushesOnRename() {
        var notifier = new MerchantServiceNotifier(merchantServiceClient, true);

        notifier.onMerchantIdentityChanged(event(false));

        verify(merchantServiceClient).syncMerchantIdentity(any());
    }

    @Test
    @DisplayName("a Merchant Service outage is swallowed - onboarding has already succeeded")
    void swallowsUpstreamFailure() {
        var notifier = new MerchantServiceNotifier(merchantServiceClient, true);
        doThrow(new RuntimeException("Connection refused: localhost/127.0.0.1:8084"))
                .when(merchantServiceClient).syncMerchantIdentity(any());

        assertThatCode(() -> notifier.onMerchantIdentityChanged(event(true)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("no call is made when the integration is switched off")
    void respectsTheDisableSwitch() {
        var notifier = new MerchantServiceNotifier(merchantServiceClient, false);

        notifier.onMerchantIdentityChanged(event(true));

        verify(merchantServiceClient, never()).syncMerchantIdentity(any());
    }
}
