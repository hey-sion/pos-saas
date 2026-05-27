package com.sion.pos.interfaces.api.customer;

import com.sion.pos.application.order.OrderFacade;
import com.sion.pos.interfaces.api.order.OrderCreateRequest;
import com.sion.pos.interfaces.api.order.OrderCreateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 손님 QR 셀프주문용 공개 주문 생성.
 * 생성된 주문은 PAYMENT_PENDING이라 결제 완료 전까지 사장님 대기 목록에 노출되지 않는다.
 */
@RestController
@RequestMapping("/api/v1/customer/stores/{storeId}/orders")
@RequiredArgsConstructor
public class CustomerOrderApiController {

    private final OrderFacade orderFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderCreateResponse create(
            @PathVariable Long storeId,
            @Valid @RequestBody OrderCreateRequest request
    ) {
        return OrderCreateResponse.from(orderFacade.createOrder(request.toCommand(storeId)));
    }
}