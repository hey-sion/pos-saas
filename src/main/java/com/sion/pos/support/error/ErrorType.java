package com.sion.pos.support.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorType {

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "일시적인 오류가 발생했습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.getReasonPhrase(), "로그인이 필요합니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.getReasonPhrase(), "잘못된 요청입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.getReasonPhrase(), "존재하지 않는 리소스입니다."),
    CONFLICT(HttpStatus.CONFLICT, HttpStatus.CONFLICT.getReasonPhrase(), "현재 상태에서 처리할 수 없는 요청입니다."),
    // 클라이언트가 항목별로 안내해야 하므로 HTTP 상태만으로는 부족해 도메인 코드를 따로 둔다
    OUT_OF_STOCK(HttpStatus.CONFLICT, "OUT_OF_STOCK", "주문할 수 없는 메뉴가 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
