package com.sion.pos.interfaces.api.hq;

import com.sion.pos.application.order.SalesRankingService;
import com.sion.pos.support.time.BusinessTime;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("cluster")
@RequestMapping("/api/v1/hq")
@RequiredArgsConstructor
public class HqSalesRankingApiController {

    private static final int DEFAULT_LIMIT = 10;

    private final SalesRankingService salesRankingService;

    @GetMapping("/sales-ranking")
    public List<StoreSalesRankResponse> getSalesRanking(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        LocalDate salesDate = date != null ? date : BusinessTime.today();

        return salesRankingService.getTopStores(salesDate, limit).stream()
                .map(StoreSalesRankResponse::from)
                .toList();
    }
}