package com.sion.pos.interfaces.api;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.stream.Collectors;

@RestControllerAdvice(basePackages = "com.sion.pos.interfaces.api")
@Slf4j
public class ApiControllerAdvice {

    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handle(PosApplicationException e) {
        log.warn("PosApplicationException [{}]: {}", e.getErrorType(), e.getMessage(), e);
        return failureResponse(e.getErrorType(), e.getCustomMessage());
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String name = e.getName();
        String type = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown";
        Object value = e.getValue();
        String message = String.format("요청 파라미터 '%s' (타입: %s) 의 값 '%s' 이(가) 잘못되었습니다.", name, type, value);
        return failureResponse(ErrorType.BAD_REQUEST, message);
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleMissingParam(MissingServletRequestParameterException e) {
        String message = String.format("필수 요청 파라미터 '%s' 가 누락되었습니다.", e.getParameterName());
        return failureResponse(ErrorType.BAD_REQUEST, message);
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return failureResponse(ErrorType.BAD_REQUEST, errorMessage);
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleBadJson(HttpMessageNotReadableException e) {
        String errorMessage;
        Throwable rootCause = e.getRootCause();

        if (rootCause instanceof InvalidFormatException invalidFormat) {
            String fieldName = invalidFormat.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "?")
                    .collect(Collectors.joining("."));

            String valueIndicationMessage = "";
            if (invalidFormat.getTargetType().isEnum()) {
                Class<?> enumClass = invalidFormat.getTargetType();
                String enumValues = Arrays.stream(enumClass.getEnumConstants())
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));
                valueIndicationMessage = "사용 가능한 값 : [" + enumValues + "]";
            }

            String expectedType = invalidFormat.getTargetType().getSimpleName();
            Object value = invalidFormat.getValue();

            errorMessage = String.format("필드 '%s'의 값 '%s'이(가) 예상 타입(%s)과 일치하지 않습니다. %s",
                    fieldName, value, expectedType, valueIndicationMessage);

        } else if (rootCause instanceof MismatchedInputException mismatchedInput) {
            String fieldPath = mismatchedInput.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "?")
                    .collect(Collectors.joining("."));
            errorMessage = String.format("필수 필드 '%s'이(가) 누락되었습니다.", fieldPath);

        } else if (rootCause instanceof JsonMappingException jsonMapping) {
            String fieldPath = jsonMapping.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "?")
                    .collect(Collectors.joining("."));
            errorMessage = String.format("필드 '%s'에서 JSON 매핑 오류가 발생했습니다: %s",
                    fieldPath, jsonMapping.getOriginalMessage());

        } else {
            errorMessage = "요청 본문 JSON 형식이 올바르지 않습니다.";
        }

        return failureResponse(ErrorType.BAD_REQUEST, errorMessage);
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleNotFound(NoResourceFoundException e) {
        return failureResponse(ErrorType.NOT_FOUND, null);
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleAny(Throwable e) {
        log.error("Unhandled exception", e);
        return failureResponse(ErrorType.INTERNAL_ERROR, null);
    }

    private ResponseEntity<ApiResponse<?>> failureResponse(ErrorType errorType, String customMessage) {
        return ResponseEntity.status(errorType.getStatus())
                .body(ApiResponse.fail(errorType.getCode(), customMessage != null ? customMessage : errorType.getMessage()));
    }
}