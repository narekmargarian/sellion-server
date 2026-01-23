package com.sellion.sellionserver.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {
    @NotNull(message = "ID счета обязателен")
    private Long invoiceId;

    @NotNull(message = "Сумма обязательна")
    @Positive(message = "Сумма должна быть больше нуля")
    private BigDecimal amount;

    private String comment;
}