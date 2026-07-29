package com.sion.pos.domain.event;

import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEventId implements Serializable {

    private String consumer;
    private String eventId;
}