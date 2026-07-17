package com.sion.pos.support.sse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sion.pos.application.order.WaitingOrdersNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LocalWaitingOrdersNotifierTest {

    private final SseEmitterRegistry registry = mock(SseEmitterRegistry.class);
    private final WaitingOrdersNotifier notifier = new LocalWaitingOrdersNotifier(registry);

    @DisplayName("대기목록 변경 알림 발행 시, ")
    @Nested
    class WhenNotifying {

        @DisplayName("인스턴스 간 방송 없이 해당 매장의 연결에 곧바로 푸시한다.")
        @Test
        void pushesToRegistryDirectly() {
            notifier.notifyUpdated(1L);

            verify(registry).push(1L);
        }
    }
}