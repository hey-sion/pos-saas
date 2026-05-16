package com.sion.pos.application.order;

import com.sion.pos.domain.order.OrderNumberSequence;
import com.sion.pos.domain.order.OrderNumberSequenceRepository;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderNumberIssuer {

    private final OrderNumberSequenceRepository orderNumberSequenceRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public int issue(Long storeId, LocalDate orderDate) {
        if (storeId == null) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "storeId는 필수입니다.");
        }
        if (orderDate == null) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "orderDate는 필수입니다.");
        }

        orderNumberSequenceRepository.insertIfAbsent(storeId, orderDate);
        OrderNumberSequence sequence = orderNumberSequenceRepository.findByStoreIdAndOrderDate(storeId, orderDate)
                .orElseThrow(() -> new PosApplicationException(ErrorType.INTERNAL_ERROR, "주문번호 채번 정보를 찾을 수 없습니다."));

        return sequence.next();
    }
}