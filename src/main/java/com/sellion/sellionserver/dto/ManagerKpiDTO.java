package com.sellion.sellionserver.dto;

import lombok.Getter;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
public class ManagerKpiDTO {
    private final Long totalSales;
    private final Long totalReturns;
    private int efficiency; // Процент выполнения плана

    private BigDecimal targetAmount = BigDecimal.ZERO;

    public ManagerKpiDTO(BigDecimal sales, BigDecimal returns) {
        // Жесткая защита от null на входе в конструктор
        BigDecimal s = (sales != null) ? sales : BigDecimal.ZERO;
        BigDecimal r = (returns != null) ? returns : BigDecimal.ZERO;

        this.totalSales = s.longValue();
        this.totalReturns = r.longValue();
        this.efficiency = 0; // По умолчанию 0, пока контроллер не проставит план
    }

    // Кастомный сеттер для безопасного расчета выполнения плана
    public void setTargetAmount(BigDecimal targetAmount) {
        this.targetAmount = (targetAmount != null) ? targetAmount : BigDecimal.ZERO;

        // Безопасно переводим Long в BigDecimal с защитой от NullPointerException
        BigDecimal salesBc = (this.totalSales != null) ? BigDecimal.valueOf(this.totalSales) : BigDecimal.ZERO;
        BigDecimal returnsBc = (this.totalReturns != null) ? BigDecimal.valueOf(this.totalReturns) : BigDecimal.ZERO;

        // Чистые продажи = Продажи - Возвраты
        BigDecimal netSales = salesBc.subtract(returnsBc);

        // Расчет КПД: (Чистые продажи * 100) / План
        // Защита: План должен быть > 0 и Чистые продажи должны быть > 0
        if (this.targetAmount.compareTo(BigDecimal.ZERO) > 0 && netSales.compareTo(BigDecimal.ZERO) > 0) {
            this.efficiency = netSales.multiply(new BigDecimal(100))
                    .divide(this.targetAmount, 0, RoundingMode.HALF_UP)
                    .intValue();
        } else {
            this.efficiency = 0;
        }
    }
}
