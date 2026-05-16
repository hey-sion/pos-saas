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
        Map<String, String> channelKeys
) {

    public String channelKeyOf(Payment.Provider provider) {
        if (provider == null) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "provider는 필수입니다.");
        }

        String key = channelKeys != null ? channelKeys.get(provider.name()) : null;
        if (key == null || key.isBlank()) {
            throw new PosApplicationException(ErrorType.INTERNAL_ERROR,
                    "PortOne 채널 키가 설정되지 않았습니다: " + provider);
        }

        return key;
    }
}