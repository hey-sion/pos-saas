package com.sion.pos.support.error;

import lombok.Getter;

@Getter
public class PosApplicationException extends RuntimeException {

    private final ErrorType errorType;
    private final String customMessage;

    public PosApplicationException(ErrorType errorType) {
        this(errorType, null);
    }

    public PosApplicationException(ErrorType errorType, String customMessage) {
        super(customMessage != null ? customMessage : errorType.getMessage());
        this.errorType = errorType;
        this.customMessage = customMessage;
    }
}