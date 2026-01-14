package com.sellion.sellionserver.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;


public enum PaymentMethod {
    CASH("Наличный"),
    TRANSFER("Перевод");

    private final String displayName;
    PaymentMethod(String displayName) { this.displayName = displayName; }

    @JsonValue
    public String getDisplayName() { return displayName; }

    @JsonCreator
    public static PaymentMethod fromString(String value) {
        if (value == null) return CASH;
        for (PaymentMethod m : PaymentMethod.values()) {
            if (m.name().equalsIgnoreCase(value) || m.displayName.equalsIgnoreCase(value)) {
                return m;
            }
        }
        return TRANSFER;
    }
}