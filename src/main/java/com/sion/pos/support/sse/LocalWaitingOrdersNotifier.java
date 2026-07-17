package com.sion.pos.support.sse;

import com.sion.pos.application.order.WaitingOrdersNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 기본 프로파일(단일 인스턴스)용 대기목록 알림.
 * 인스턴스 간 방송 없이 자기에게 붙은 연결에 곧바로 푸시.
 */
@Component
@Profile("!cluster")
@RequiredArgsConstructor
public class LocalWaitingOrdersNotifier implements WaitingOrdersNotifier {

    private final SseEmitterRegistry registry;

    @Override
    public void notifyUpdated(Long storeId) {
        registry.push(storeId);
    }
}