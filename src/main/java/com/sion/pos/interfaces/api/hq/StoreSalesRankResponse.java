package com.sion.pos.interfaces.api.hq;

import com.sion.pos.domain.order.StoreSalesRank;

public record StoreSalesRankResponse(
        Long storeId,
        Long salesAmount
) {

    public static StoreSalesRankResponse from(StoreSalesRank rank) {
        return new StoreSalesRankResponse(rank.storeId(), rank.salesAmount());
    }
}