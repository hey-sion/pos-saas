package com.sion.pos.domain.order;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SalesRankingRepository {

    void addAmount(LocalDate salesDate, Long storeId, int amount);

    void replaceAmount(LocalDate salesDate, Long storeId, long amount);

    List<StoreSalesRank> findTop(LocalDate salesDate, int limit);

    Optional<Long> findAmount(LocalDate salesDate, Long storeId);
}