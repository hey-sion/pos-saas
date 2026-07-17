package com.sion.pos.support.sse;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 매장(storeId)별 SSE 연결을 보관하고, 대기목록 변경 신호를 그 매장에 연결된 모든 화면에 푸시한다.
 * 인스턴스 로컬 — 각 인스턴스는 자기에게 붙은 연결만 들고 있고, 인스턴스 간 전파는 별도(Redis pub/sub)로 한다.
 */
@Component
public class SseEmitterRegistry {

    static final String EVENT_NAME = "waiting-orders-updated";

    private final Map<Long, Set<SseEmitter>> emittersByStore = new ConcurrentHashMap<>();

    public void register(Long storeId, SseEmitter emitter) {
        emittersByStore.computeIfAbsent(storeId, key -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(storeId, emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> remove(storeId, emitter));
    }

    /** 해당 매장에 연결된 모든 화면에 대기목록 변경 신호를 보낸다. 끊긴 연결은 정리한다. */
    public void push(Long storeId) {
        for (SseEmitter emitter : emittersByStore.getOrDefault(storeId, Set.of())) {
            try {
                emitter.send(SseEmitter.event().name(EVENT_NAME).data(Map.of("storeId", storeId)));
            } catch (IOException | IllegalStateException e) {
                remove(storeId, emitter);
            }
        }
    }

    int connectionCount(Long storeId) {
        return emittersByStore.getOrDefault(storeId, Set.of()).size();
    }

    private void remove(Long storeId, SseEmitter emitter) {
        Set<SseEmitter> emitters = emittersByStore.get(storeId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }
}