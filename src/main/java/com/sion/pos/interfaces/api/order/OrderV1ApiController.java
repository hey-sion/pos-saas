package com.sion.pos.interfaces.api.order;

import com.sion.pos.application.order.OrderFacade;
import com.sion.pos.application.order.OrderService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderV1ApiController {

    private final OrderFacade orderFacade;
    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderCreateResponse create(@Valid @RequestBody OrderCreateRequest request) {
        return OrderCreateResponse.from(orderFacade.createOffline(request.toCommand()));
    }

    @PostMapping("/easy-pay")
    @ResponseStatus(HttpStatus.CREATED)
    public EasyPayOrderCreateResponse createEasyPay(@Valid @RequestBody EasyPayOrderCreateRequest request) {
        return EasyPayOrderCreateResponse.from(orderFacade.createEasyPay(request.toCommand()));
    }

    @PatchMapping("/{orderId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderStatusUpdateRequest request
    ) {
        orderService.updateStatus(orderId, request.status());
    }

    @GetMapping("/waiting")
    public List<WaitingOrderResponse> getWaitingOrders(@RequestParam Long storeId) {
        return orderService.getWaitingOrders(storeId).stream()
                           .map(WaitingOrderResponse::from)
                           .toList();
    }

    @GetMapping("/daily-summary")
    public DailyOrderSummaryResponse getDailySummary(
            @RequestParam Long storeId,
            @RequestParam LocalDate date
    ) {
        return DailyOrderSummaryResponse.from(orderService.getDailySummary(storeId, date));
    }
}