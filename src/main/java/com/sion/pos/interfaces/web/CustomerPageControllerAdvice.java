package com.sion.pos.interfaces.web;

import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice(assignableTypes = CustomerPageController.class)
@Slf4j
public class CustomerPageControllerAdvice {

    @ExceptionHandler(PosApplicationException.class)
    public String handle(PosApplicationException e, Model model, HttpServletResponse response) {
        log.warn("Customer page exception [{}]: {}", e.getErrorType(), e.getMessage(), e);
        response.setStatus(e.getErrorType().getStatus().value());
        addErrorAttributes(model, e.getErrorType(), e.getCustomMessage());
        return "error/customer-error";
    }

    @ExceptionHandler(Throwable.class)
    public String handleAny(Throwable e, Model model, HttpServletResponse response) {
        log.error("Unhandled customer page exception", e);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        addErrorAttributes(model, ErrorType.INTERNAL_ERROR, null);
        return "error/customer-error";
    }

    private void addErrorAttributes(Model model, ErrorType errorType, String customMessage) {
        model.addAttribute("title", "주문을 진행할 수 없습니다.");
        model.addAttribute("message", customMessage != null ? customMessage : errorType.getMessage());
        model.addAttribute("guide", guideOf(errorType));
    }

    private String guideOf(ErrorType errorType) {
        return switch (errorType) {
            case BAD_REQUEST -> "QR 코드를 다시 확인해 주세요.";
            case NOT_FOUND -> "QR 코드를 다시 스캔해 주세요.";
            default -> "관리자에게 문의해 주세요.";
        };
    }
}