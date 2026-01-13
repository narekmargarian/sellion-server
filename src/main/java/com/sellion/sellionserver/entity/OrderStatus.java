package com.sellion.sellionserver.entity;

public enum OrderStatus {
    NEW,        // Только что пришел с Android
    ACCEPTED,
    PENDING,//
    INVOICED,   // Счет создан (редактирование запрещено)
    CANCELLED,
    PROCESSED// Отменен
}