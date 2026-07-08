package com.sion.pos.support.portone;

import com.sion.pos.domain.payment.Payment;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "portone")
public record PortOneProperties(
        String storeId,
        String apiSecret,
        String apiBaseUrl,
        String webhookSecret,
        Map<String, ChannelKey> channelKeys
) {

    /** provider별 테스트/실연동 채널 키. 실결제 여부는 매장(store.kakaoPayLive)이 결정한다. */
    public record ChannelKey(String test, String live) {}

    public String channelKeyOf(Payment.Provider provider, boolean useLiveChannel) {
        if (provider == null) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "provider는 필수입니다.");
        }

        ChannelKey channelKey = channelKeys != null ? channelKeys.get(provider.name()) : null;
        String key = channelKey == null ? null : (useLiveChannel ? channelKey.live() : channelKey.test());
        if (key == null || key.isBlank()) {
            throw new PosApplicationException(ErrorType.INTERNAL_ERROR,
                    "PortOne 채널 키가 설정되지 않았습니다: " + provider + (useLiveChannel ? "(live)" : "(test)"));
        }

        return key;
    }
}