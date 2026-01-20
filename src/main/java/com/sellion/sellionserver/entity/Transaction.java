package com.sellion.sellionserver.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long clientId;
    private String clientName;

    // Тип: "ORDER" (увеличивает долг), "PAYMENT" (уменьшает), "RETURN" (уменьшает)
    private String type;

    private Long referenceId; // ID заказа, платежа или возврата

    // ИСПРАВЛЕНО: BigDecimal вместо Double
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    private String comment;
    private LocalDateTime timestamp;
}