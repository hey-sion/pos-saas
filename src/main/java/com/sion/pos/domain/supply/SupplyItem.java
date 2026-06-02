package com.sion.pos.domain.supply;

import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 매장이 본사에 발주할 수 있는 재료 품목 카탈로그.
 * 가격은 이 서버 상수를 단일 진실원천으로 삼는다 — 발주 생성 시 클라이언트가 보낸 금액은 신뢰하지 않는다.
 * 본사가 품목/가격을 직접 관리하게 되는 시점에 DB 테이블로 분리한다(단계별 도입).
 */
@Getter
@RequiredArgsConstructor
public enum SupplyItem {

    DOUGH_MIX("반죽믹스", "포", 35_000, null, null),
    PLASTIC_BAG("비닐봉투", "묶음", 152_000, 6_000, "매");

    private final String itemName;
    private final String unit;
    private final int unitPrice;
    private final Integer packSize;
    private final String packUnit;

    public static SupplyItem from(String code) {
        if (code == null || code.isBlank()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "itemCode는 필수입니다.");
        }

        return Arrays.stream(values())
                     .filter(item -> item.name().equals(code))
                     .findFirst()
                     .orElseThrow(() -> new PosApplicationException(ErrorType.NOT_FOUND, "존재하지 않는 재료 품목입니다: " + code));
    }
}