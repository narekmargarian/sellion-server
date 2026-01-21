package com.sellion.sellionserver.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
public class ManagerKpiDTO {
    private final Long totalSales;
    private final Long totalReturns;
    private final int efficiency;

    @Setter
    private BigDecimal targetAmount = BigDecimal.ZERO;

    public ManagerKpiDTO(BigDecimal sales, BigDecimal returns) {
        // Защита от Null
        BigDecimal s = (sales != null) ? sales : BigDecimal.ZERO;
        BigDecimal r = (returns != null) ? returns : BigDecimal.ZERO;

        this.totalSales = s.longValue();
        this.totalReturns = r.longValue();

        // ИСПРАВЛЕНО: Надежная проверка на деление на ноль
        if (s.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal netSales = s.subtract(r);
            // Если возвратов больше чем продаж (бывает при корректировках)
            if (netSales.compareTo(BigDecimal.ZERO) <= 0) {
                this.efficiency = 0;
            } else {
                this.efficiency = netSales.multiply(new BigDecimal(100))
                        .divide(s, 0, RoundingMode.HALF_UP)
                        .intValue();
            }
        } else {
            this.efficiency = 0;
        }
    }
}
