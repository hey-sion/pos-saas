package com.sion.pos.application.payment;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 미확정 PG 결제 재조회 배치 설정 — docs/plan/payment-gateway-hardening.md STEP 6
 *
 * @param grace     결제 생성 후 이 시간이 지나야 배치가 건드린다. 웹훅이 정상 도착할 시간
 * @param lookback  조회 창. 이보다 오래된 결제는 사람이 판단할 대상이라 자동 재조회하지 않는다
 * @param batchSize 한 주기에 조회할 최대 건수
 */
@ConfigurationProperties(prefix = "payment.reconcile")
public record PaymentReconcileProperties(Duration grace, Duration lookback, int batchSize) {
}