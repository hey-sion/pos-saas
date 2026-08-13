package com.sion.pos.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.sion.pos.support.DatabaseCleanUp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class PaymentRepositoryTest {

    private static final Long ORDER_ID = 1L;
    private static final int AMOUNT = 4_000;
    private static final int LIMIT = 50;

    // 쿼리가 두 경계를 제대로 거는지만 보므로 값 자체는 임의다.
    // 운영값(payment.reconcile.*)과 무관하게 이 테스트가 정하는 경계 — 설정이 바뀌어도 여기는 안 깨진다.
    private static final Duration GRACE = Duration.ofMinutes(3);
    private static final Duration LOOKBACK = Duration.ofDays(1);
    private static final Duration OLDER_THAN_GRACE = GRACE.plusMinutes(1);
    private static final Duration OLDER_THAN_LOOKBACK = LOOKBACK.plusMinutes(1);

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("미확정 PG 결제 조회 시, ")
    class FindPendingPgPayments {

        @Test
        @DisplayName("유예 시간이 지난 PENDING 결제를 반환한다")
        void findsPendingPaymentOlderThanGrace() {
            // Arrange
            LocalDateTime now = LocalDateTime.now();
            Payment payment = savePgPayment("pg-1");
            updateCreatedAt(payment.getId(), now.minus(OLDER_THAN_GRACE));

            // Act
            List<Payment> found = findTargetsAt(now, LIMIT);

            // Assert
            assertThat(found).extracting(Payment::getId).containsExactly(payment.getId());
        }

        @Test
        @DisplayName("유예 시간이 지나지 않은 PENDING 결제는 제외한다")
        void excludesPaymentWithinGrace() {
            // Arrange — 방금 만든 결제라 유예 안쪽이다
            LocalDateTime now = LocalDateTime.now();
            savePgPayment("pg-1");

            // Act
            List<Payment> found = findTargetsAt(now, LIMIT);

            // Assert
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("조회 창보다 오래된 결제는 제외한다")
        void excludesPaymentOlderThanLookback() {
            // Arrange
            LocalDateTime now = LocalDateTime.now();
            Payment payment = savePgPayment("pg-1");
            updateCreatedAt(payment.getId(), now.minus(OLDER_THAN_LOOKBACK));

            // Act
            List<Payment> found = findTargetsAt(now, LIMIT);

            // Assert
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("이미 완료된 결제는 제외한다")
        void excludesCompletedPayment() {
            // Arrange
            LocalDateTime now = LocalDateTime.now();
            Payment payment = savePgPayment("pg-1");
            updateCreatedAt(payment.getId(), now.minus(OLDER_THAN_GRACE));
            changeStatus(payment.getId(), Payment.Status.COMPLETED);

            // Act
            List<Payment> found = findTargetsAt(now, LIMIT);

            // Assert
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("금액 불일치로 격리된 결제는 제외한다")
        void excludesAmountMismatchedPayment() {
            // Arrange
            LocalDateTime now = LocalDateTime.now();
            Payment payment = savePgPayment("pg-1");
            updateCreatedAt(payment.getId(), now.minus(OLDER_THAN_GRACE));
            changeStatus(payment.getId(), Payment.Status.AMOUNT_MISMATCH);

            // Act
            List<Payment> found = findTargetsAt(now, LIMIT);

            // Assert
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("한 번에 조회하는 건수를 제한한다")
        void limitsResultSize() {
            // Arrange
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < 3; i++) {
                updateCreatedAt(savePgPayment("pg-" + i).getId(), now.minus(OLDER_THAN_GRACE));
            }

            // Act
            List<Payment> found = findTargetsAt(now, 2);

            // Assert
            assertThat(found).hasSize(2);
        }

        @Test
        @DisplayName("오래된 결제부터 반환한다")
        void returnsOldestFirst() {
            // Arrange
            LocalDateTime now = LocalDateTime.now();
            Payment newer = savePgPayment("pg-newer");
            Payment older = savePgPayment("pg-older");
            updateCreatedAt(newer.getId(), now.minus(OLDER_THAN_GRACE));
            updateCreatedAt(older.getId(), now.minus(OLDER_THAN_GRACE).minusMinutes(1));

            // Act
            List<Payment> found = findTargetsAt(now, LIMIT);

            // Assert
            assertThat(found).extracting(Payment::getId).containsExactly(older.getId(), newer.getId());
        }
    }

    private List<Payment> findTargetsAt(LocalDateTime now, int limit) {
        return paymentRepository.findPendingPgPayments(now.minus(GRACE), now.minus(LOOKBACK), limit);
    }

    private Payment savePgPayment(String pgPaymentId) {
        return paymentRepository.save(Payment.createPg(
                ORDER_ID, Payment.Provider.KAKAO_PAY, AMOUNT, pgPaymentId));
    }

    private void updateCreatedAt(Long paymentId, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE payment SET created_at = ? WHERE id = ?", createdAt, paymentId);
    }

    private void changeStatus(Long paymentId, Payment.Status status) {
        jdbcTemplate.update("UPDATE payment SET status = ? WHERE id = ?", status.name(), paymentId);
    }
}