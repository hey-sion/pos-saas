package com.sion.pos.support.time;

import java.time.LocalDate;
import java.time.ZoneId;

/** 매장 영업 기준 시각 — 서버 타임존과 무관하게 한국 기준 날짜 사용 */
public final class BusinessTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private BusinessTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}