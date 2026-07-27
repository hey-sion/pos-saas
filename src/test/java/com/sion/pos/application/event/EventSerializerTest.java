package com.sion.pos.application.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.sion.pos.application.order.OrderDeliveredEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EventSerializerTest {

    @Autowired private EventSerializer eventSerializer;

    @Test
    @DisplayName("봉투를 JSON으로 직렬화했다가 되돌리면 타입과 내용이 보존된다")
    void preservesTypeAndPayloadThroughRoundTrip() {
        // arrange
        OrderDeliveredEvent payload = new OrderDeliveredEvent(
                "ORDER_DELIVERED:3:2026-07-27:2", 3L, LocalDateTime.of(2026, 7, 27, 14, 3), 9000);
        Event<OrderDeliveredEvent> event = Event.of(payload.eventId(), EventType.ORDER_DELIVERED, payload);

        // act
        String json = eventSerializer.serialize(event);
        Event<EventPayload> restored = eventSerializer.deserialize(json);

        // assert
        assertThat(restored.type()).isEqualTo(EventType.ORDER_DELIVERED);
        assertThat(restored.payload()).isInstanceOf(OrderDeliveredEvent.class);
        OrderDeliveredEvent restoredPayload = (OrderDeliveredEvent) restored.payload();
        assertThat(restoredPayload.eventId()).isEqualTo("ORDER_DELIVERED:3:2026-07-27:2");
        assertThat(restoredPayload.storeId()).isEqualTo(3L);
        assertThat(restoredPayload.totalAmount()).isEqualTo(9000);
    }
}