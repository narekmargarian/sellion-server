package com.sellion.sellionserver.entity;

public enum ReturnStatus {
    DRAFT,      // Создан менеджером
    CONFIRMED,  // Подтвержден бухгалтером
    CANCELLED,
    PENDING,
}