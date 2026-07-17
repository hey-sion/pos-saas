package com.sion.pos.support.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterRegistryTest {

    private final SseEmitterRegistry registry = new SseEmitterRegistry();

    @DisplayName("연결 등록 시, ")
    @Nested
    class WhenRegistering {

        @DisplayName("해당 매장의 연결 수가 증가한다.")
        @Test
        void increasesConnectionCount() {
            // act
            registry.register(1L, mock(SseEmitter.class));

            // assert
            assertThat(registry.connectionCount(1L)).isEqualTo(1);
        }

        @DisplayName("연결이 완료되면 레지스트리에서 제거된다.")
        @Test
        void removesOnCompletion() {
            // arrange
            SseEmitter emitter = mock(SseEmitter.class);
            registry.register(1L, emitter);
            ArgumentCaptor<Runnable> onCompletion = ArgumentCaptor.forClass(Runnable.class);
            verify(emitter).onCompletion(onCompletion.capture());

            // act
            onCompletion.getValue().run();

            // assert
            assertThat(registry.connectionCount(1L)).isZero();
        }
    }

    @DisplayName("대기목록 변경 푸시 시, ")
    @Nested
    class WhenPushing {

        @DisplayName("해당 매장에 연결된 모든 화면에 이벤트를 보낸다.")
        @Test
        void sendsToAllConnectionsOfStore() throws IOException {
            // arrange
            SseEmitter first = mock(SseEmitter.class);
            SseEmitter second = mock(SseEmitter.class);
            registry.register(1L, first);
            registry.register(1L, second);

            // act
            registry.push(1L);

            // assert
            verify(first).send(any(SseEmitter.SseEventBuilder.class));
            verify(second).send(any(SseEmitter.SseEventBuilder.class));
        }

        @DisplayName("다른 매장의 연결에는 보내지 않는다.")
        @Test
        void doesNotSendToOtherStores() throws IOException {
            // arrange
            SseEmitter storeOne = mock(SseEmitter.class);
            SseEmitter storeTwo = mock(SseEmitter.class);
            registry.register(1L, storeOne);
            registry.register(2L, storeTwo);

            // act
            registry.push(1L);

            // assert
            verify(storeOne).send(any(SseEmitter.SseEventBuilder.class));
            verify(storeTwo, never()).send(any(SseEmitter.SseEventBuilder.class));
        }

        @DisplayName("전송에 실패한 연결은 레지스트리에서 정리한다.")
        @Test
        void removesFailedConnection() throws IOException {
            // arrange
            SseEmitter emitter = mock(SseEmitter.class);
            doThrow(new IOException("broken pipe")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
            registry.register(1L, emitter);

            // act
            registry.push(1L);

            // assert
            assertThat(registry.connectionCount(1L)).isZero();
        }

        @DisplayName("연결이 없는 매장에 푸시해도 예외가 발생하지 않는다.")
        @Test
        void doesNotThrowWhenNoConnections() {
            // act & assert
            assertThatCode(() -> registry.push(999L)).doesNotThrowAnyException();
        }
    }
}