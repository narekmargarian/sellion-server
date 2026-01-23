package com.sellion.sellionserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class OrderRequest {
    @NotBlank(message = "Название магазина обязательно")
    private String shopName;

    @NotEmpty(message = "Заказ не может быть пустым")
    private Map<Long, Integer> items;

    @NotNull(message = "Способ оплаты обязателен")
    private String paymentMethod;

    private String comment;
    private String androidId;
    private String managerId;
    private String deliveryDate; // Будет распарсено в сервисе
}
