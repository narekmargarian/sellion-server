package com.sellion.sellionserver.entity;

import lombok.Data;

import java.util.Map;

@Data
public class WriteOffRequest {
    private Long productId;
    private Integer quantity;
    private String reason;
    // Используем Boolean (объект), чтобы избежать ошибки при null,
    // или принудительно ставим false в конструкторе
    private Boolean isBroken = false;
}