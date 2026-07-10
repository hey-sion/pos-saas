package com.sion.pos.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApiControllerAdviceTest {

    private final ApiControllerAdvice advice = new ApiControllerAdvice();

    @Nested
    @DisplayName("PosApplicationException 처리 시, ")
    class HandlePosApplicationException {

        @Test
        @DisplayName("5xx 예외는 내부 메시지를 숨기고 일반 문구로 응답한다")
        void masksCustomMessageForServerError() {
            PosApplicationException exception = new PosApplicationException(
                    ErrorType.INTERNAL_ERROR, "PortOne 채널 키가 설정되지 않았습니다: KAKAO_PAY(live)");

            ResponseEntity<ApiResponse<?>> response = advice.handle(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().meta().message()).isEqualTo(ErrorType.INTERNAL_ERROR.getMessage());
            assertThat(response.getBody().meta().message()).doesNotContain("채널 키");
        }

        @Test
        @DisplayName("4xx 예외는 클라이언트에게 커스텀 메시지를 그대로 노출한다")
        void exposesCustomMessageForClientError() {
            PosApplicationException exception = new PosApplicationException(
                    ErrorType.CONFLICT, "이미 결제 완료된 주문입니다.");

            ResponseEntity<ApiResponse<?>> response = advice.handle(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().meta().message()).isEqualTo("이미 결제 완료된 주문입니다.");
        }

        @Test
        @DisplayName("4xx 예외에 커스텀 메시지가 없으면 기본 문구로 응답한다")
        void fallsBackToDefaultMessageWhenNoCustomMessage() {
            PosApplicationException exception = new PosApplicationException(ErrorType.NOT_FOUND);

            ResponseEntity<ApiResponse<?>> response = advice.handle(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().meta().message()).isEqualTo(ErrorType.NOT_FOUND.getMessage());
        }
    }
}