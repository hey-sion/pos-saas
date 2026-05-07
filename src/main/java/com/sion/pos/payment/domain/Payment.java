package com.sion.pos.payment.domain;

import com.sion.pos.global.domain.BaseEntity;
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

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Method method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Channel channel;

    @Column(nullable = false)
    private Integer amount;

    @Column(name = "pg_order_code", length = 100)
    private String pgOrderCode;

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
        KAKAO_PAY,
        NAVER_PAY,
        TOSS_PAY
    }
}
