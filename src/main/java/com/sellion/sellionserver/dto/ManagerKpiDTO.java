package com.sellion.sellionserver.dto;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
public class ManagerKpiDTO {
    private final Long totalSales;
    private final Long totalReturns;
    private int efficiency; // Переменная теперь не final, чтобы её можно было рассчитать после установки плана

    private BigDecimal targetAmount = BigDecimal.ZERO;

    public ManagerKpiDTO(BigDecimal sales, BigDecimal returns) {
        // Защита от Null
        BigDecimal s = (sales != null) ? sales : BigDecimal.ZERO;
        BigDecimal r = (returns != null) ? returns : BigDecimal.ZERO;

        this.totalSales = s.longValue();
        this.totalReturns = r.longValue();
        this.efficiency = 0; // По умолчанию 0, пока не задан план
    }

    // Кастомный сеттер: когда контроллер передает план, мы сразу считаем КПД выполнения плана
    public void setTargetAmount(BigDecimal targetAmount) {
        this.targetAmount = (targetAmount != null) ? targetAmount : BigDecimal.ZERO;

        // Чистые продажи = Продажи - Возвраты
        BigDecimal netSales = BigDecimal.valueOf(this.totalSales).subtract(BigDecimal.valueOf(this.totalReturns));

        // КПД = (Чистые продажи * 100) / План
        if (this.targetAmount.compareTo(BigDecimal.ZERO) > 0 && netSales.compareTo(BigDecimal.ZERO) > 0) {
            this.efficiency = netSales.multiply(new BigDecimal(100))
                    .divide(this.targetAmount, 0, RoundingMode.HALF_UP)
                    .intValue();
        } else {
            this.efficiency = 0;
        }
    }
}
