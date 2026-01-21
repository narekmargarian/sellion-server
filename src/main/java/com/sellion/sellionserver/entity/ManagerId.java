package com.sellion.sellionserver.entity;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public enum ManagerId {
    M1011("1011"),
    M1012("1012"),
    M1013("1013"),
    M1014("1014"),
    M1015("1015"),
    M1016("1016"),
    M1017("1017"),
    M1018("1018"),
    M1019("1019"),
    M1020("1020"),
    OFFICE("Менеджер Офис"); // Оставляем его тут для веб-интерфейса

    private final String displayName;

    ManagerId(String displayName) {
        this.displayName = displayName;
    }

    // Метод для получения ВСЕХ логинов/дисплейных имен (для веба)
    public static List<String> getAllDisplayNames() {
        return Arrays.stream(ManagerId.values())
                .map(ManagerId::getDisplayName)
                .collect(Collectors.toList());
    }

    // НОВЫЙ МЕТОД: Только для полевых менеджеров (для Android)
    public static List<String> getFieldManagerDisplayNames() {
        return Arrays.stream(ManagerId.values())
                .filter(manager -> manager != OFFICE) // Фильтруем OFFICE
                .map(ManagerId::getDisplayName)
                .collect(Collectors.toList());
    }

}