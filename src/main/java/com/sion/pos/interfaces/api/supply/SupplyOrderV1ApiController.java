package com.sion.pos.interfaces.api.supply;

import com.sion.pos.application.supply.SupplyOrderFacade;
import com.sion.pos.application.supply.SupplyOrderService;
import com.sion.pos.support.security.LoginStore;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/supply-orders")
@RequiredArgsConstructor
public class SupplyOrderV1ApiController {

    private final SupplyOrderFacade supplyOrderFacade;
    private final SupplyOrderService supplyOrderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplyOrderCreateResponse create(
            @LoginStore Long storeId,
            @Valid @RequestBody SupplyOrderCreateRequest request
    ) {
        return SupplyOrderCreateResponse.from(supplyOrderFacade.createOrder(request.toCommand(storeId)));
    }

    @GetMapping
    public List<SupplyOrderResponse> getSupplyOrders(@LoginStore Long storeId) {
        return supplyOrderService.getSupplyOrders(storeId).stream()
                                 .map(SupplyOrderResponse::from)
                                 .toList();
    }
}