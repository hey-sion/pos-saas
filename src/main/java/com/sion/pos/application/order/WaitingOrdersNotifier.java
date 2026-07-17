package com.sion.pos.application.order;

/**
 * 추가/취소/완료/결제 등 주문 대기 목록의 변경이 있을 때 호출
 */
public interface WaitingOrdersNotifier {

    void notifyUpdated(Long storeId);
}