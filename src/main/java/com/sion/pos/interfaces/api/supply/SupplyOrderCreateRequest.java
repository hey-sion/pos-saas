package com.sion.pos.interfaces.api.supply;

import com.sion.pos.application.supply.SupplyOrderCreateCommand;
import com.sion.pos.application.supply.SupplyOrderLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record SupplyOrderCreateRequest(
        @NotEmpty(message = "발주 항목이 비어 있습니다.")
        List<@Valid Line> items
) {

    public SupplyOrderCreateCommand toCommand(Long storeId) {
        return new SupplyOrderCreateCommand(
                storeId,
                items.stream()
                     .map(Line::toCommand)
                     .toList()
        );
    }

    public record Line(
            @NotBlank(message = "itemCode는 필수입니다.")
            String itemCode,

            @NotNull(message = "quantity는 필수입니다.")
            @Positive(message = "quantity는 1 이상이어야 합니다.")
            Integer quantity
    ) {

        private SupplyOrderLine toCommand() {
            return new SupplyOrderLine(itemCode, quantity);
        }
    }
}