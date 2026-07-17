package com.sion.pos.application.order;

/**
 * 매장의 대기목록이 바뀌었음을 알리는 애플리케이션 이벤트
 */
public record WaitingOrdersUpdatedEvent(Long storeId) {
}