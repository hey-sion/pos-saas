package com.sion.pos.domain.payment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderIdIn(List<Long> orderIds);

    Optional<Payment> findByPgPaymentId(String pgPaymentId);

    @Query(value = """
            SELECT p.*
              FROM payment p
              JOIN orders o ON o.id = p.order_id
             WHERE p.id = :id
               AND o.store_id = :storeId
            """, nativeQuery = true)
    Optional<Payment> findByIdAndOrderStoreId(@Param("id") Long id, @Param("storeId") Long storeId);

    /** 재조회 배치 대상. 웹훅이 도착할 시간을 주고(createdBefore), 밀린 건을 한 번에 긁지 않도록 창과 건수를 제한한다. */
    @Query(value = """
            SELECT p.*
              FROM payment p
             WHERE p.channel = 'PG'
               AND p.status = 'PENDING'
               AND p.created_at < :createdBefore
               AND p.created_at >= :createdAfter
             ORDER BY p.created_at
             LIMIT :limit
            """, nativeQuery = true)
    List<Payment> findPendingPgPayments(@Param("createdBefore") LocalDateTime createdBefore,
                                        @Param("createdAfter") LocalDateTime createdAfter,
                                        @Param("limit") int limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE payment
               SET status = 'COMPLETED',
                   paid_at = :paidAt,
                   pg_transaction_key = :transactionKey,
                   fail_reason = NULL,
                   updated_at = CURRENT_TIMESTAMP(6)
             WHERE id = :id
               AND status = 'PENDING'
            """, nativeQuery = true)
    int completeIfPending(@Param("id") Long id,
                          @Param("paidAt") LocalDateTime paidAt,
                          @Param("transactionKey") String transactionKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE payment
               SET status = 'FAILED',
                   fail_reason = :reason,
                   updated_at = CURRENT_TIMESTAMP(6)
             WHERE id = :id
               AND status = 'PENDING'
            """, nativeQuery = true)
    int failIfPending(@Param("id") Long id, @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE payment
               SET status = 'AMOUNT_MISMATCH',
                   updated_at = CURRENT_TIMESTAMP(6)
             WHERE id = :id
               AND status = 'PENDING'
            """, nativeQuery = true)
    int markMismatchIfPending(@Param("id") Long id);
}
