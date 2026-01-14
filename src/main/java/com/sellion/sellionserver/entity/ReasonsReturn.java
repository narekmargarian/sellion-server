package com.sellion.sellionserver.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;


public enum ReasonsReturn {
    EXPIRED("Просрочка"),
    DAMAGED("Поврежденная упаковка"),
    WAREHOUSE("На склад"),
    OTHER("Другое");

    private final String displayName;

    ReasonsReturn(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    @JsonCreator
    public static ReasonsReturn fromString(String value) {
        if (value == null) return OTHER;
        for (ReasonsReturn r : ReasonsReturn.values()) {
            if (r.name().equalsIgnoreCase(value) || r.displayName.equalsIgnoreCase(value)) {
                return r;
            }
        }
        return OTHER;
    }
}