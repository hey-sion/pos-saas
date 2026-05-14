package com.sion.pos.domain.payment;

import com.sion.pos.domain.BaseEntity;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Method method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Channel channel;

    @Column(nullable = false)
    private Integer amount;

    @Column(name = "pg_payment_id", length = 100)
    private String pgPaymentId;

    @Column(name = "pg_transaction_key", length = 100)
    private String pgTransactionKey;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "fail_reason", length = 255)
    private String failReason;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    public static Payment createOffline(Long orderId, Method method, Integer amount, LocalDateTime paidAt) {
        validateCommon(orderId, amount);

        if (method == null) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "method는 필수입니다.");
        }
        if (method == Method.EASY_PAY) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "오프라인 결제는 EASY_PAY를 사용할 수 없습니다.");
        }
        if (paidAt == null) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "오프라인 결제는 paidAt이 필수입니다.");
        }

        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.method = method;
        payment.channel = Channel.OFFLINE;
        payment.amount = amount;
        payment.status = Status.COMPLETED;
        payment.paidAt = paidAt;
        return payment;
    }

    public static Payment createPg(Long orderId, Provider provider, Integer amount, String pgPaymentId) {
        validateCommon(orderId, amount);

        if (provider == null) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "provider는 필수입니다.");
        }
        if (pgPaymentId == null || pgPaymentId.isBlank()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "pgPaymentId는 필수입니다.");
        }

        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.method = Method.EASY_PAY;
        payment.channel = Channel.PG;
        payment.amount = amount;
        payment.provider = provider;
        payment.pgPaymentId = pgPaymentId;
        payment.status = Status.PENDING;
        return payment;
    }

    public void complete(LocalDateTime paidAt, String transactionKey) {
        if (paidAt == null) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "paidAt은 필수입니다.");
        }

        if (this.status != Status.PENDING) {
            throw new PosApplicationException(ErrorType.CONFLICT,
                    "결제 대기 상태에서만 완료 처리 가능합니다. 현재 상태: " + this.status);
        }

        this.status = Status.COMPLETED;
        this.paidAt = paidAt;
        this.pgTransactionKey = transactionKey;
    }

    public void fail(String reason) {
        if (this.status != Status.PENDING) {
            throw new PosApplicationException(ErrorType.CONFLICT,
                    "결제 대기 상태에서만 실패 처리 가능합니다. 현재 상태: " + this.status);
        }

        this.status = Status.FAILED;
        this.failReason = reason;
    }

    public boolean matchesAmount(Integer amount) {
        return this.amount.equals(amount);
    }

    private static void validateCommon(Long orderId, Integer amount) {
        if (orderId == null || orderId <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "orderId는 1 이상이어야 합니다.");
        }
        if (amount == null || amount <= 0) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "amount는 1 이상이어야 합니다.");
        }
    }

    public enum Status {
        PENDING,
        COMPLETED,
        FAILED
    }

    public enum Method {
        CASH,
        CARD,
        EASY_PAY
    }

    public enum Channel {
        OFFLINE,
        PG
    }

    public enum Provider {
        KAKAO_PAY;

        public String toPortOneCode() {
            return switch (this) {
                case KAKAO_PAY -> "KAKAOPAY";
            };
        }
    }
}