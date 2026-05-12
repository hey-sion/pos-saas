package com.sion.pos.support.portone;

import com.sion.pos.domain.payment.PaymentGateway;
import com.sion.pos.domain.payment.PaymentGatewayResult;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortOnePaymentGateway implements PaymentGateway {

    private final PortOneProperties properties;
    private RestClient restClient;

    @PostConstruct
    void init() {
        this.restClient = RestClient.builder()
                .baseUrl(properties.apiBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "PortOne " + properties.apiSecret())
                .build();
    }

    @Override
    public PaymentGatewayResult lookup(String pgPaymentId) {
        try {
            PortOnePaymentResponse response = restClient.get()
                                                        .uri("/payments/{paymentId}", pgPaymentId)
                                                        .retrieve()
                                                        .body(PortOnePaymentResponse.class);

            if (response == null) {
                throw new PosApplicationException(ErrorType.INTERNAL_ERROR, "PortOne 응답이 비어 있습니다.");
            }

            return new PaymentGatewayResult(
                    mapStatus(response.status()),
                    response.amount() != null ? response.amount().total() : null,
                    response.pgTxId(),
                    response.failure() != null ? response.failure().reason() : null
            );
        } catch (RestClientException e) {
            log.error("PortOne 결제 조회 실패: pgPaymentId={}", pgPaymentId, e);
            throw new PosApplicationException(ErrorType.INTERNAL_ERROR,
                    "PortOne 결제 조회에 실패했습니다: " + e.getMessage());
        }
    }

    static PaymentGatewayResult.Status mapStatus(String status) {
        if (status == null) {
            log.warn("PortOne 응답에 status가 없습니다. PENDING으로 임시 처리");
            return PaymentGatewayResult.Status.PENDING;
        }

        return switch (status) {
            case "PAID" -> PaymentGatewayResult.Status.PAID;
            case "FAILED", "CANCELLED", "PARTIAL_CANCELLED" -> PaymentGatewayResult.Status.FAILED;
            case "READY", "PENDING", "VIRTUAL_ACCOUNT_ISSUED", "PAY_PENDING" -> PaymentGatewayResult.Status.PENDING;
            default -> {
                log.warn("PortOne 응답의 알 수 없는 status='{}'. PENDING으로 임시 처리", status);
                yield PaymentGatewayResult.Status.PENDING;
            }
        };
    }

    record PortOnePaymentResponse(String status, Amount amount, String pgTxId, Failure failure) {
        record Amount(Integer total) {}

        record Failure(String reason) {}
    }
}