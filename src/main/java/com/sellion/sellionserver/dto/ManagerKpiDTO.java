package com.sellion.sellionserver.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
public class ManagerKpiDTO {
    private final Long totalSales;
    private final Long totalReturns;
    private int efficiency;

    @Setter
    private BigDecimal targetAmount; // Новое поле для хранения цели

    public ManagerKpiDTO(BigDecimal sales, BigDecimal returns) {
        this.totalSales = sales != null ? sales.longValue() : null;
        this.totalReturns = returns != null ? returns.longValue() : null;

        if (sales != null && sales.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal netSales = sales.subtract(returns != null ? returns : BigDecimal.ZERO);
            this.efficiency = netSales.multiply(new BigDecimal(100))
                    .divide(sales, 0, RoundingMode.HALF_UP)
                    .intValue();

            if (this.efficiency < 0) this.efficiency = 0;
            if (this.efficiency > 100) this.efficiency = 100;
        } else {
            this.efficiency = 0;
        }
    }
}
