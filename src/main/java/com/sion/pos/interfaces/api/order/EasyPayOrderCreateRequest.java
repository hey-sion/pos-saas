package com.sion.pos.interfaces.api.order;

import com.sion.pos.application.order.EasyPayOrderCreateCommand;
import com.sion.pos.domain.payment.Payment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record EasyPayOrderCreateRequest(
        @NotNull(message = "storeId는 필수입니다.")
        @Positive(message = "storeId는 1 이상이어야 합니다.")
        Long storeId,

        @NotEmpty(message = "주문 항목이 비어 있습니다.")
        List<@Valid Line> items,

        @NotNull(message = "provider는 필수입니다.")
        Payment.Provider provider
) {

    public EasyPayOrderCreateCommand toCommand() {
        return new EasyPayOrderCreateCommand(
                storeId,
                items.stream()
                     .map(Line::toCommand)
                     .toList(),
                provider
        );
    }

    public record Line(
            @NotNull(message = "menuId는 필수입니다.")
            @Positive(message = "menuId는 1 이상이어야 합니다.")
            Long menuId,

            @NotNull(message = "quantity는 필수입니다.")
            @Positive(message = "quantity는 1 이상이어야 합니다.")
            Integer quantity
    ) {

        private EasyPayOrderCreateCommand.Line toCommand() {
            return new EasyPayOrderCreateCommand.Line(menuId, quantity);
        }
    }
}