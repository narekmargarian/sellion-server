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

    // @JsonValue заставляет Jackson отправлять на фронтенд "Просрочка"
    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    // @JsonCreator говорит Jackson использовать этот метод при получении данных
    @JsonCreator
    public static ReasonsReturn fromString(String value) {
        if (value == null || value.trim().isEmpty()) return OTHER;

        for (ReasonsReturn r : ReasonsReturn.values()) {
            // Проверка по системному имени (EXPIRED) или по русскому (Просрочка)
            if (r.name().equalsIgnoreCase(value.trim()) ||
                    r.displayName.equalsIgnoreCase(value.trim())) {
                return r;
            }
        }
        return OTHER; // Защита от падения: если прислали бред, вернет "Другое"
    }
}
