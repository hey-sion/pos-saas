package com.sion.pos.application.order;

import com.sion.pos.domain.order.SalesRankingRepository;
import com.sion.pos.domain.order.StoreSalesRank;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("cluster")
@RequiredArgsConstructor
public class SalesRankingService {

    private static final int MAX_LIMIT = 100;

    private final SalesRankingRepository salesRankingRepository;

    public List<StoreSalesRank> getTopStores(LocalDate salesDate, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "limit은 1 이상 " + MAX_LIMIT + " 이하여야 합니다.");
        }

        return salesRankingRepository.findTop(salesDate, limit);
    }
}