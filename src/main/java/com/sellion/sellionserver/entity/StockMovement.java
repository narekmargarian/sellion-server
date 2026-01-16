package com.sellion.sellionserver.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@NoArgsConstructor
public class StockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String productName;
    private Integer quantityChange; // +10 (приход), -5 (продажа)
    private String type; // "INCOMING", "SALE", "RETURN", "ADJUSTMENT"
    private String reason; // "Заказ #15" или "Инвентаризация"
    private LocalDateTime timestamp = LocalDateTime.now();
    private String operator;
}

