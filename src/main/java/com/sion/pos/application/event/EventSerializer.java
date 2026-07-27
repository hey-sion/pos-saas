package com.sion.pos.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventSerializer {

    private final ObjectMapper objectMapper;

    public String serialize(Event<? extends EventPayload> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new PosApplicationException(ErrorType.INTERNAL_ERROR, "이벤트 직렬화에 실패했습니다.");
        }
    }

    public Event<EventPayload> deserialize(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            EventType type = EventType.valueOf(root.get("type").asText());
            EventPayload payload = objectMapper.treeToValue(root.get("payload"), type.payloadClass());
            return Event.of(root.get("eventId").asText(), type, payload);
        } catch (JsonProcessingException e) {
            throw new PosApplicationException(ErrorType.INTERNAL_ERROR, "이벤트 역직렬화에 실패했습니다.");
        }
    }
}