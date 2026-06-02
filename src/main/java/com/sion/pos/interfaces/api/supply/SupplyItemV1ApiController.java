package com.sion.pos.interfaces.api.supply;

import com.sion.pos.domain.supply.SupplyItem;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/supply-items")
public class SupplyItemV1ApiController {

    @GetMapping
    public List<SupplyItemResponse> getSupplyItems() {
        return Arrays.stream(SupplyItem.values())
                     .map(SupplyItemResponse::from)
                     .toList();
    }
}