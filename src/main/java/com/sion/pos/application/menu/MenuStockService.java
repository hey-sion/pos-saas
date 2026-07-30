package com.sion.pos.application.menu;

import com.sion.pos.domain.menu.MenuDailyStock;
import com.sion.pos.domain.menu.MenuDailyStockRepository;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuStockService {

    private final MenuDailyStockRepository menuDailyStockRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void deduct(Long storeId, LocalDate stockDate, List<MenuStockDeductLine> lines) {
        for (MenuStockDeductLine line : inLockOrder(lines, MenuStockDeductLine::menuId)) {
            menuDailyStockRepository.insertIfAbsent(storeId, line.menuId(), stockDate, line.limitQuantity());

            int deducted = menuDailyStockRepository.deduct(storeId, line.menuId(), stockDate, line.quantity());
            if (deducted == 0) {
                throw new PosApplicationException(ErrorType.OUT_OF_STOCK);
            }
        }
    }

    // 그날 재고 행이 있는 메뉴만 되돌린다. 판단 기준을 메뉴 설정이 아니라 차감된 기록에 두어
    // 주문 뒤 한정 판매를 해제해도 차감분은 돌아온다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void restore(Long storeId, LocalDate stockDate, List<MenuStockRestoreLine> lines) {
        for (MenuStockRestoreLine line : inLockOrder(deductedLines(storeId, stockDate, lines), MenuStockRestoreLine::menuId)) {
            menuDailyStockRepository.restore(storeId, line.menuId(), stockDate, line.quantity());
        }
    }

    private List<MenuStockRestoreLine> deductedLines(Long storeId, LocalDate stockDate, List<MenuStockRestoreLine> lines) {
        if (lines.isEmpty()) {
            return lines;
        }

        List<Long> menuIds = lines.stream().map(MenuStockRestoreLine::menuId).toList();
        Set<Long> deductedMenuIds = menuDailyStockRepository
                .findByStoreIdAndStockDateAndMenuIdInAndDeletedAtIsNull(storeId, stockDate, menuIds)
                .stream()
                .map(MenuDailyStock::getMenuId)
                .collect(Collectors.toSet());

        return lines.stream()
                    .filter(line -> deductedMenuIds.contains(line.menuId()))
                    .toList();
    }

    // 락 순서 고정 — 순환 대기 방지
    private <T> List<T> inLockOrder(List<T> lines, Function<T, Long> menuId) {
        return lines.stream()
                    .sorted(Comparator.comparing(menuId))
                    .toList();
    }
}