package com.sion.pos.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProcessedEventRepositoryTest {

    private static final String SALES_AGGREGATION = "sales-aggregation";
    private static final String SALES_RANKING = "sales-ranking";
    private static final String EVENT_ID = "evt-1";

    @Autowired private ProcessedEventRepository processedEventRepository;

    @Nested
    @DisplayName("이벤트 처리를 기록할 때, ")
    class Record {

        @Test
        @DisplayName("처음 보는 이벤트는 기록된다")
        void recordsUnseenEvent() {
            // Arrange & Act
            int recorded = processedEventRepository.record(SALES_AGGREGATION, EVENT_ID);

            // Assert
            assertThat(recorded).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 소비자가 같은 이벤트를 다시 기록하면 무시된다")
        void ignoresDuplicateForSameConsumer() {
            // Arrange
            processedEventRepository.record(SALES_AGGREGATION, EVENT_ID);

            // Act
            int recorded = processedEventRepository.record(SALES_AGGREGATION, EVENT_ID);

            // Assert
            assertThat(recorded).isZero();
        }

        @Test
        @DisplayName("소비자가 다르면 같은 이벤트도 각각 기록된다")
        void recordsSameEventPerConsumer() {
            // Arrange
            processedEventRepository.record(SALES_AGGREGATION, EVENT_ID);

            // Act
            int recorded = processedEventRepository.record(SALES_RANKING, EVENT_ID);

            // Assert
            assertThat(recorded).isEqualTo(1);
        }
    }
}