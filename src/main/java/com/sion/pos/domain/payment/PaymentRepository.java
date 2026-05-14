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
}