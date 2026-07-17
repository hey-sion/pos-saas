package com.sion.pos.interfaces.api.order;

import com.sion.pos.support.security.LoginStore;
import com.sion.pos.support.sse.SseEmitterRegistry;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * POS 대기목록 실시간 갱신용 SSE 스트림. 로그인 매장의 화면이 연결하면 레지스트리에 등록되고,
 * 이후 그 매장의 대기목록 변경이 발행되면 waiting-orders-updated 이벤트를 받는다.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class WaitingOrderStreamController {

    // 프록시 idle 종료보다 넉넉히. 만료돼 끊겨도 클라이언트(EventSource)가 자동 재연결한다.
    private static final long STREAM_TIMEOUT_MS = 30 * 60 * 1000L;

    private final SseEmitterRegistry registry;

    @GetMapping(value = "/waiting/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamWaitingOrders(@LoginStore Long storeId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        registry.register(storeId, emitter);
        sendConnected(emitter);
        return emitter;
    }

    // 연결 직후 코멘트 한 줄을 흘려 응답 헤더를 즉시 flush(프록시 버퍼링 방지)하고 개통을 알린다.
    private void sendConnected(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}