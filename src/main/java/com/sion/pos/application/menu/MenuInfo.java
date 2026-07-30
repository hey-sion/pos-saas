package com.sion.pos.application.menu;

/**
 * 메뉴 한 건. remainingQuantity 가 null 이면 한정 판매 메뉴가 아니고, 0 이면 오늘 다 팔렸다.
 */
public record MenuInfo(Long id, String name, Integer price, Integer sortOrder, Integer remainingQuantity) {
}