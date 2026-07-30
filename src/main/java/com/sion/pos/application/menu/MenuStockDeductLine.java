package com.sion.pos.application.menu;

/**
 * 한정 메뉴 한 줄의 재고 차감 요청. 한도가 있는 메뉴만 이 줄이 만들어진다.
 * limitQuantity 는 그날 재고 행을 처음 만들 때 복사해 둘 한도다.
 */
public record MenuStockDeductLine(Long menuId, String menuName, int limitQuantity, int quantity) {
}